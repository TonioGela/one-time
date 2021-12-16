package dev.toniogela

import scala.concurrent.duration._

import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import org.scalacheck.Gen
import weaver.SimpleIOSuite
import weaver.scalacheck.{CheckConfig, Checkers}

object OneTimeRoutesSpec extends SimpleIOSuite with Checkers {

  override def checkConfig: CheckConfig = super.checkConfig.copy(minimumSuccessful = 1000, perPropertyParallelism = 100)

  private def clientF(payloadLimit: Long, timeout: FiniteDuration): IO[Client[IO]] = OneTimeStore.empty[IO](timeout)
    .map(OneTimeRoutes[IO](_)("", payloadLimit).orNotFound).map(Client.fromHttpApp[IO](_))

  private def POST(body: String): Request[IO] = Request(Method.POST, body = Stream.emits[IO, Byte](body.getBytes))
  private def GET(path: String): Request[IO]  = Request(method = Method.GET, uri = Uri.unsafeFromString(path))

  private def inToStatusAndBody(r: Response[IO]): IO[(Status, String)] = r.body.compile.toList.map(_.toArray)
    .map(new String(_)).tupleLeft(r.status)

  private def inToStatusHeadersAndBody(r: Response[IO]): IO[(Status, Headers, String)] = r.body.compile.toList
    .map(_.toArray).map(new String(_)).map((r.status, r.headers, _))

  // TODO for some reason Gen.asciiChar breaks the httpApp
  private def stringBiggerThan(limit: Long): Gen[String] = for {
    number <- Gen.chooseNum(limit, limit * 100)
    bytes  <- Gen.listOfN(number.toInt, Gen.alphaNumChar.map(_.toByte))
  } yield new String(bytes.toArray)

  private def stringLittlerThan(limit: Long): Gen[String] = for {
    number <- Gen.chooseNum(0, limit / 2)
    bytes  <- Gen.listOfN(number.toInt, Gen.alphaNumChar.map(_.toByte)).filter(_.size < limit)
  } yield new String(bytes.toArray)

  test("OneTimeRoutes should accept payloads littler than limit") {
    val limitedStrings: Gen[(Long, String)] = for {
      limit  <- Gen.posNum[Long]
      string <- stringLittlerThan(limit)
    } yield (limit, string)

    forall(limitedStrings) { case (limit, string) =>
      for {
        client             <- clientF(limit, 1.minute)
        (statusCode, body) <- client.run(POST(string)).use(inToStatusAndBody)
      } yield expect.eql(statusCode, Status.Ok).and(expect.eql(body.length, 64))
    }
  }

  test("OneTimeRoutes should NOT accept payloads bigger than limit") {
    val exceedingStrings: Gen[(Long, String)] = for {
      limit  <- Gen.posNum[Long]
      string <- stringBiggerThan(limit)
    } yield (limit, string)

    forall(exceedingStrings) { case (limit, string) =>
      for {
        client             <- clientF(limit, 1.minute)
        (statusCode, body) <- client.run(POST(string)).use(inToStatusAndBody)
      } yield expect.eql(statusCode, Status.PayloadTooLarge)
        .and(expect.eql(body, s"Max accepted payload size is $limit bytes"))
    }
  }

  test("OneTimeRoutes should return a specific secret just once") {
    val limitedStrings: Gen[(Long, String)] = for {
      limit  <- Gen.posNum[Long]
      string <- stringLittlerThan(limit)
    } yield (limit, string)

    forall(limitedStrings) { case (limit, string) =>
      for {
        client                 <- clientF(limit, 1.minute)
        (_, key)               <- client.run(POST(string)).use(inToStatusAndBody)
        (goodStatus, goodBody) <- client.run(GET(s"/$key")).use(inToStatusAndBody)
        (badStatus, badBody)   <- client.run(GET(s"/$key")).use(inToStatusAndBody)
      } yield expect.eql(goodStatus, Status.Ok).and(expect.eql(goodBody, string))
        .and(expect.eql(badStatus, Status.NotFound)).and(expect.eql(badBody, "No value or timeout expired"))
    }
  }

  test("OneTimeRoutes should not return a specific secret after timeout")(for {
    client              <- clientF(4096, 100.millis)
    key                 <- client.fetchAs[String](POST("This is a really long string"))
    emptyResponseStatus <- IO.sleep(200.millis) >> client.status(GET(s"/$key"))
  } yield expect.eql(Status.NotFound, emptyResponseStatus))

  test("OneTimeRoutes should always serve an homepage")(
    for {
      client                  <- clientF(4096, 100.millis)
      (status, headers, body) <- client.run(GET("/")).use(inToStatusHeadersAndBody)
      maybeMediaType = headers.get[`Content-Type`].map(_.mediaType)
    } yield expect.eql(Status.Ok, status).and(expect(body.nonEmpty))
      .and(expect.eql(maybeMediaType, MediaType.text.html.some))
  )

}
