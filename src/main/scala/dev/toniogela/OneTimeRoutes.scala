package dev.toniogela

import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.server.middleware.{CORS, CORSPolicy, EntityLimiter}
import org.http4s.{HttpRoutes, MediaType, Method}

class OneTimeRoutes[F[_]: Async: Http4sDsl] private (store: OneTimeStore[F])(baseUrl: String, limit: Long) {
  val dsl: Http4sDsl[F] = implicitly[Http4sDsl[F]]
  import dsl._

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root        => Ok(OneTimeRoutes.homepage(baseUrl), `Content-Type`(MediaType.text.html))
    case GET -> Root / key  => store.get(key).flatMap {
        case Some(value) => Ok(value)
        case None        => NotFound(s"No value or timeout expired")
      }
    case req @ POST -> Root => takeLimited(req.body, limit).map(bs => new String(bs.toArray).trim).attempt.flatMap {
        case Right(content) => store.save(content) >>= (key => Ok(s"$baseUrl$key"))
        case Left(_)        => PayloadTooLarge(s"Max accepted payload size is $limit bytes")
      }

  }

  private def takeLimited(s: Stream[F, Byte], limit: Long) = s.pull.take(limit).flatMap {
    case Some(_) => Pull.raiseError[F](EntityLimiter.EntityTooLarge(limit))
    case None    => Pull.done
  }.stream.compile.toList

}

object OneTimeRoutes {

  val corsPolicy: CORSPolicy = CORS.policy.withAllowOriginAll.withAllowMethodsIn(Set(Method.GET, Method.POST))
    .withMaxAgeDisableCaching

  def homepage(baseUrl: String): String = s"""|<!DOCTYPE html>
                                              |<html lang="en">
                                              |<head>
                                              |    <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
                                              |    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1">
                                              |    <title>One Time Store</title>
                                              |</head>
                                              |<body>
                                              |    <p><textarea id="content" cols="50" rows="5" placeholder="Text to save"></textarea></p>
                                              |    <p><button id="submit" onclick="submit()">Save</button></p>
                                              |    <p><div id="result"></div></p>
                                              |</body>
                                              |<script>
                                              |    function submit() {
                                              |        let content = document.getElementById("content").value;
                                              |        fetch("$baseUrl", {
                                              |            method: "POST",
                                              |            body: content
                                              |        }).then(response => response.text())
                                              |            .then(text => {
                                              |                document.getElementById("content").value = "";
                                              |                document.getElementById("result").innerHTML = text;
                                              |                navigator.clipboard.writeText(text);
                                              |            })
                                              |    }
                                              |</script>
                                              |</html>""".stripMargin

  def apply[F[_]: Async](store: OneTimeStore[F])(baseUrl: String, limit: Long): HttpRoutes[F] = {
    implicit val dsl = Http4sDsl[F]
    EntityLimiter.httpRoutes(corsPolicy(new OneTimeRoutes[F](store)(baseUrl, limit).routes), limit = limit)
  }
}
