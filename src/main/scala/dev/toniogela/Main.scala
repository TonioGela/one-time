package dev.toniogela

import scala.concurrent.duration._

import com.comcast.ip4s._
import com.monovore.decline.effect.CommandIOApp
import com.monovore.decline.{Argument, Opts}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.{HttpApp, Query, Uri}

object Main extends CommandIOApp("one-time", "Stores secrets for one-time view via REST APIs") {

  override def main: Opts[IO[ExitCode]] = server.map(_.useForever.as(ExitCode.Success))

  def oneTimeStoreApp(timeout: FiniteDuration, uri: String, limit: Long): IO[HttpApp[IO]] = OneTimeStore
    .empty[IO](timeout).map(OneTimeRoutes[IO](_)(uri, limit).orNotFound)

  def serverResource(timeout: FiniteDuration, uri: String, port: Port, limit: Long): Resource[IO, Server] = Resource
    .eval(oneTimeStoreApp(timeout, uri, limit))
    .flatMap(app => EmberServerBuilder.default[IO].withHost(ipv4"0.0.0.0").withPort(port).withHttpApp(app).build)

  implicit val port: Argument[Port] = Argument.from("port")(Port.fromString(_).toValidNel("Invalid port"))

  implicit val uri: Argument[Uri] = Argument
    .from("uri")(Uri.fromString(_).toValidated.leftMap(_.message).toValidatedNel)
    .map(_.withoutFragment.copy(query = Query.empty) / "")

  def server: Opts[Resource[IO, Server]] = (
    Opts.option[FiniteDuration]("timeout", "timeout before a secret expires").withDefault(1.minute),
    Opts.option[Uri]("baseUri", "base uri to prepend to responses").map(_.renderString),
    Opts.option[Port]("port", "the port to serve the app on"),
    Opts.option[Long]("limit", "max payload size in bytes").withDefault(4 * 1024 * 1024L)
  ).mapN((timeout, uri, port, limit) => serverResource(timeout, uri, port, limit))

}
