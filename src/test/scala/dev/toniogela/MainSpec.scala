package dev.toniogela

import java.net.ServerSocket

import com.monovore.decline.{Command, Help}
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.Server
import org.http4s.{Method, Request, Uri}
import weaver.SimpleIOSuite

object MainSpec extends SimpleIOSuite {

  // This may seem a useless test, but I spent more than than I want to
  // admit debugging a command line argument parsing issue

  val portRes: Resource[IO, Int]                 = Resource.eval(IO.delay(new ServerSocket(0).getLocalPort()))
  val clientBuilderRes: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build

  def request(port: Int): Request[IO] = Request[IO](
    method = Method.POST,
    uri = Uri.unsafeFromString(s"http://localhost:$port"),
    body = Stream.emits("foo".getBytes())
  )

  test("Passed Uri must always be prepended in reponses") {

    def serverRes(port: Int): Resource[IO, Either[Help, Server]] = Command("test", "test")(Main.server)
      .parse(List("--baseUri", "http://localhost:8080/foo", "--port", s"$port")).sequence

    val serverClientRes: Resource[IO, (Either[Help, Server], IO[String])] = for {
      port   <- portRes
      server <- serverRes(port)
      client <- clientBuilderRes
    } yield (server, client.expect[String](request(port)))

    serverClientRes.use { case (server, response) =>
      server.fold(
        _ => failure("Unable to correctly parse command line arguments").pure[IO],
        _ =>
          response.map(_.startsWith("http://localhost:8080/foo/"))
            .map(expect(_, "The returned uri doesn't start with the passed baseUri"))
      )
    }
  }

}
