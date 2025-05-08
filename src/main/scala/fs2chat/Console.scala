package fs2chat

import cats.effect.Sync
import cats.implicits._
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.reader.Parser
import org.jline.reader.impl.DefaultParser
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.terminal.Terminal
import org.jline.utils.{AttributedStringBuilder, AttributedStyle}
import java.util.function.Consumer

trait Console[F[_]]:
  def println(msg: String): F[Unit]
  def info(msg: String): F[Unit]
  def alert(msg: String): F[Unit]
  def errorln(msg: String): F[Unit]
  def readLine(prompt: String): F[Option[String]]
  def executeCommand(input: String): F[Boolean]

/** Commands available in chat client */
enum Command:
  case Help
  case Users
  case Clear
  case Private(user: String, message: String)
  case Quit

object Command:
  def fromString(input: String): Option[Command] =
    input match
      case "/help"  => Some(Command.Help)
      case "/users" => Some(Command.Users)
      case "/clear" => Some(Command.Clear)
      case "/quit"  => Some(Command.Quit)
      case s if s.startsWith("/msg ") =>
        val parts = s.stripPrefix("/msg ").split(" ", 2)
        if parts.length == 2 then Some(Command.Private(parts(0), parts(1)))
        else None
      case _ => None

object Console:

  def apply[F[_]](implicit F: Console[F]): F.type = F

  def create[F[_]: Sync]: F[Console[F]] =
    Sync[F].delay {
      new Console[F] {
        // Available commands for auto-completion
        private val completers = new StringsCompleter(
          "/help",
          "/users",
          "/clear",
          "/quit",
          "/msg "
        )

        private val parser = new DefaultParser()

        // Create a terminal
        private val terminal = org.jline.terminal.TerminalBuilder.terminal()

        private val reader =
          LineReaderBuilder
            .builder()
            .appName("fs2chat")
            .completer(completers)
            .parser(parser)
            .terminal(terminal)
            .build()

        reader.setOpt(org.jline.reader.LineReader.Option.ERASE_LINE_ON_FINISH)

        def println(msg: String): F[Unit] =
          Sync[F].blocking(reader.printAbove(msg))

        def info(msg: String): F[Unit] =
          println("*** " + msg)

        def alert(msg: String): F[Unit] =
          println(
            new AttributedStringBuilder()
              .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE))
              .append("📢 " + msg)
              .toAnsi
          )

        def errorln(msg: String): F[Unit] =
          println(
            new AttributedStringBuilder()
              .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
              .append("❌ " + msg)
              .toAnsi
          )

        def readLine(prompt: String): F[Option[String]] =
          Sync[F]
            .blocking(Some(reader.readLine(prompt)): Option[String])
            .handleErrorWith {
              case _: EndOfFileException     => (None: Option[String]).pure[F]
              case _: UserInterruptException => (None: Option[String]).pure[F]
              case t                         => Sync[F].raiseError(t)
            }

        def executeCommand(input: String): F[Boolean] =
          Command.fromString(input) match
            case Some(Command.Help) =>
              alert("""Available commands:
                      |  /help - Show this help
                      |  /users - List connected users
                      |  /clear - Clear the screen
                      |  /msg <user> <message> - Send private message
                      |  /quit - Exit the chat""".stripMargin) *>
                Sync[F].pure(true)
            case Some(Command.Clear) =>
              Sync[F].blocking(terminal.flush()) *>
                Sync[F].blocking(terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen)) *>
                Sync[F].pure(true)
            case Some(Command.Quit) =>
              Sync[F].pure(false) // Let client handle quit
            case _ =>
              Sync[F].pure(false) // Not a command or unimplemented
      }
    }
