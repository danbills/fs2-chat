package fs2chat
package client

import cats.ApplicativeError
import cats.effect.{Concurrent, Temporal}
import cats.implicits._
import com.comcast.ip4s.{IpAddress, SocketAddress}
import fs2.{RaiseThrowable, Stream}
import fs2.io.net.Network
import java.net.ConnectException
import scala.concurrent.duration._
import fs2chat.Command

object Client:
  def start[F[_]: Temporal: Network: Console](
      address: SocketAddress[IpAddress],
      desiredUsername: Username
  ): Stream[F, Unit] =
    connect(address, desiredUsername).handleErrorWith {
      case _: ConnectException =>
        val retryDelay = 5.seconds
        Stream.exec(Console[F].errorln(s"Failed to connect. Retrying in $retryDelay.")) ++
          start(address, desiredUsername)
            .delayBy(retryDelay)
      case _: UserQuit => Stream.empty
      case t           => Stream.raiseError(t)
    }

  private def connect[F[_]: Concurrent: Network: Console](
      address: SocketAddress[IpAddress],
      desiredUsername: Username
  ): Stream[F, Unit] =
    Stream.exec(Console[F].info(s"Connecting to server $address")) ++
      Stream
        .resource(Network[F].client(address))
        .flatMap { socket =>
          Stream.exec(Console[F].info("🎉 Connected! 🎊")) ++
            Stream
              .eval(
                MessageSocket(
                  socket,
                  Protocol.ServerCommand.codec,
                  Protocol.ClientCommand.codec,
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

  private def processOutgoing[F[_]: RaiseThrowable: Console: Concurrent](
      messageSocket: MessageSocket[F, Protocol.ServerCommand, Protocol.ClientCommand]
  ): Stream[F, Unit] =
    Stream
      .repeatEval(Console[F].readLine("> "))
      .flatMap {
        case Some(txt) => Stream(txt)
        case None      => Stream.raiseError[F](new UserQuit)
      }
      .evalMap { txt =>
        if txt.startsWith("/") then
          for {
            wasHandled <- Console[F].executeCommand(txt)
            _ <-
              if !wasHandled then
                Command.fromString(txt) match
                  case Some(Command.Quit) => Concurrent[F].raiseError(new UserQuit)
                  case Some(Command.Users) =>
                    messageSocket.write1(Protocol.ClientCommand.SendMessage("/users"))
                  case Some(Command.Private(user, msg)) =>
                    messageSocket.write1(Protocol.ClientCommand.SendMessage(s"/msg $user $msg"))
                  case _ => Concurrent[F].unit
              else Concurrent[F].unit
          } yield ()
        else messageSocket.write1(Protocol.ClientCommand.SendMessage(txt))
      }
