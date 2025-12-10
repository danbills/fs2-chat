package fs2chat

import cats.effect.Async
import fs2.io.net.tls.TLSContext
import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object Tls {
  def loadContext[F[_]: Async]: F[TLSContext[F]] = Async[F].blocking {
    val ksStream = new FileInputStream("keystore.jks")
    val ks = KeyStore.getInstance("JKS")
    ks.load(ksStream, "password".toCharArray)
    ksStream.close()

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, "password".toCharArray)

    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(ks)

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(kmf.getKeyManagers, tmf.getTrustManagers, null)

    TLSContext.Builder.forAsync[F].fromSSLContext(sslContext)
  }
}
