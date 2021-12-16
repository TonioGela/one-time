package dev.toniogela

import scala.concurrent.duration._
import scala.util.Try

import org.scalacheck.Gen
import org.scalacheck.rng.Seed
import weaver.SimpleIOSuite
import weaver.scalacheck.{CheckConfig, Checkers}

object OneTimeStoreSpec extends SimpleIOSuite with Checkers {

  val foo: Try[Seed] = Seed.fromBase64("WIeh3okA_9JZo3AUDpU7DaWmwiKKJl30K-HWfpd84rL=")

  override def checkConfig: CheckConfig = super.checkConfig.copy(minimumSuccessful = 1000, perPropertyParallelism = 100)

  test("OneTimeStore should not offer values if empty")(for {
    store   <- OneTimeStore.empty[IO](1.minute)
    results <- store.get("x").replicateA(10)
  } yield forEach(results)(r => expect.eql(r, None)))

  test("OneTimeStore should offer just once a value")(OneTimeStore.empty[IO](1.minute).flatMap(store =>
    forall(Gen.asciiStr) { string =>
      for {
        key          <- store.save(string)
        presentValue <- store.get(key)
        emptyValue   <- store.get(key)
      } yield expect.eql(presentValue, string.some).and(expect.eql(emptyValue, None))
    }
  ))

  test("OneTimeStore should not offer a value after the timeout")(OneTimeStore.empty[IO](5.millis).flatMap(store =>
    forall(Gen.asciiStr) { string =>
      for {
        key          <- store.save(string)
        _            <- IO.sleep(100.millis)
        expiredValue <- store.get(key)
      } yield expect.eql(expiredValue, None)
    }
  ))

  test("OneTimeStore should offer exactly the value stored for that key") {
    val strings = for {
      a <- Gen.asciiStr
      b <- Gen.asciiStr
    } yield (a, b)

    OneTimeStore.empty[IO](2.seconds).flatMap(store =>
      forall(strings) { case (string1, string2) =>
        for {
          key  <- store.save(string1)
          _    <- store.save(string2)
          duck <- store.get(key)
        } yield expect.eql(duck, string1.some)
      }
    )
  }

}
