package fs2chat

import cats.Eq
import scodec.Codec

case class Username(value: String) extends Ordered[Username] derives Codec:
  def compare(that: Username): Int = value.compare(that.value)
  override def toString: String = value

object Username:
  implicit val eqInstance: Eq[Username] = Eq.fromUniversalEquals[Username]
