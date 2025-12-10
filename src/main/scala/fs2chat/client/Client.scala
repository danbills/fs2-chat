package fs2chat
package client

import cats.ApplicativeError
import cats.effect.{Async, Concurrent}
import com.comcast.ip4s.{IpAddress, SocketAddress}
import fs2.{RaiseThrowable, Stream}
import fs2.io.net.Network
import fs2.io.net.tls.TLSContext
import scodec.Codec

import java.net.ConnectException
import scala.concurrent.duration.*

object Client:
  def start[F[_]: Async: Network: Console](
      address: SocketAddress[IpAddress],
      desiredUsername: Username
  ): Stream[F, Unit] =
    Stream.eval(Tls.loadContext[F]).flatMap { tlsContext =>
      connectLoop(address, desiredUsername, tlsContext)
    }

  private def connectLoop[F[_]: Async: Network: Console](
      address: SocketAddress[IpAddress],
      desiredUsername: Username,
      tlsContext: TLSContext[F]
  ): Stream[F, Unit] =
    connect(address, desiredUsername, tlsContext).handleErrorWith {
      case _: ConnectException =>
        val retryDelay = 5.seconds
        Stream.exec(Console[F].errorln(s"Failed to connect. Retrying in $retryDelay.")) ++
          connectLoop(address, desiredUsername, tlsContext)
            .delayBy(retryDelay)
      case _: UserQuit => Stream.empty
      case t           => Stream.raiseError(t)
    }

  private def connect[F[_]: Async: Network: Console](
      address: SocketAddress[IpAddress],
      desiredUsername: Username,
      tlsContext: TLSContext[F]
  ): Stream[F, Unit] =
    Stream.exec(Console[F].info(s"Connecting to server $address")) ++
      Stream
        .resource(Network[F].connect(address))
        .flatMap { socket =>
          Stream.resource(tlsContext.client(socket)).flatMap { secureSocket =>
            Stream.exec(Console[F].info("🎉 Connected! 🎊")) ++
              Stream
                .eval(
                  MessageSocket(
                    secureSocket,
                    summon[Codec[Protocol.ServerCommand]],
                    summon[Codec[Protocol.ClientCommand]],
                    128
                  )
                )
                .flatMap { messageSocket =>
                  Stream.exec(
                    messageSocket.write1(Protocol.ClientCommand.RequestUsername(desiredUsername))
                  ) ++
                    processIncoming(messageSocket).concurrently(
                      processOutgoing(messageSocket)
                    )
                }
          }
        }

  private def processIncoming[F[_]: Console](
      messageSocket: MessageSocket[F, Protocol.ServerCommand, Protocol.ClientCommand]
  )(implicit F: ApplicativeError[F, Throwable]): Stream[F, Unit] =
    messageSocket.read.evalMap {
      case Protocol.ServerCommand.Alert(txt) =>
        Console[F].alert(txt)
      case Protocol.ServerCommand.Message(username, txt) =>
        Console[F].println(s"$username> $txt")
      case Protocol.ServerCommand.SetUsername(username) =>
        Console[F].alert("Assigned username: " + username)
      case Protocol.ServerCommand.Disconnect =>
        F.raiseError[Unit](new UserQuit)
    }

  private def processOutgoing[F[_]: RaiseThrowable: Console](
      messageSocket: MessageSocket[F, Protocol.ServerCommand, Protocol.ClientCommand]
  ): Stream[F, Unit] =
    Stream
      .repeatEval(Console[F].readLine("> "))
      .flatMap {
        case Some(txt) => Stream(txt)
        case None      => Stream.raiseError[F](new UserQuit)
      }
      .map(txt => Protocol.ClientCommand.SendMessage(txt))
      .evalMap(messageSocket.write1)
