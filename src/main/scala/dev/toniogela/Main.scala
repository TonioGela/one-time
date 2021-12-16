package dev.toniogela

import scala.concurrent.duration._

import com.comcast.ip4s._
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import org.http4s.HttpApp
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server

object Main extends CommandIOApp("one-time", "Stores secrets for one-time view via REST APIs") {

  def oneTimeStoreApp(timeout: FiniteDuration, baseUrl: String, limit: Long): IO[HttpApp[IO]] = OneTimeStore
    .empty[IO](timeout).map(OneTimeRoutes[IO](_)(baseUrl, limit).orNotFound)

  def server(timeout: FiniteDuration, baseUrl: String, port: Port, limit: Long): IO[Resource[IO, Server]] =
    oneTimeStoreApp(timeout, baseUrl, limit)
      .map(app => EmberServerBuilder.default[IO].withHost(ipv4"0.0.0.0").withPort(port).withHttpApp(app).build)

  override def main: Opts[IO[ExitCode]] = (
    Opts.option[FiniteDuration]("timeout", "timeout before a secret expires").withDefault(1.minute),
    Opts.option[String]("baseUrl", "base url to prepend to responses"),
    Opts.option[Int]("port", "the port to serve the app on"),
    Opts.option[Long]("limit", "max payload size in bytes").withDefault(4 * 1024 * 1024L)
  ).mapN((timeout, baseUrl, port, limit) =>
    Port.fromInt(port).fold(IO.pure(ExitCode.Error))(goodPort =>
      server(timeout, baseUrl, goodPort, limit).flatMap(_.useForever.as(ExitCode.Success))
    )
  )

}
