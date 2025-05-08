package fs2chat

import scodec.Codec
import scodec.codecs._

/** Defines the messages exchanged between the client and server. */
object Protocol:

  private val username: Codec[Username] =
    utf8_32.as[Username]

  /** Base trait for messages sent from the client to the server. */
  enum ClientCommand derives Codec:
    case RequestUsername(name: Username)
    case SendMessage(value: String)

  /** Base trait for messages sent from the server to the client. */
  enum ServerCommand derives Codec:
    case SetUsername(name: Username)
    case Alert(text: String)
    case Message(name: Username, text: String)
    case Disconnect
