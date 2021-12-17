package dev.toniogela

import scala.concurrent.duration._

import cats.effect.std.Random

import OneTimeStore._

class OneTimeStore[F[_]: Async: Random: Parallel] private (
    val stateRef: Ref[F, Map[String, String]],
    val timeout: FiniteDuration
) {

  private def clean(key: String): F[Unit] = Async[F].sleep(timeout) >> stateRef.update(_ - key)

  def get(key: String): F[Option[String]] = stateRef.get.map(_.get(key)) <* stateRef.update(_ - key)

  def save(s: String): F[String] = for {
    k <- randomKey
    _ <- stateRef.update(_ + (k -> s))
    _ <- Async[F].start(clean(k))
  } yield k

}

object OneTimeStore {

  def empty[F[_]: Async: Parallel](timeout: FiniteDuration): F[OneTimeStore[F]] = for {
    stateRef                     <- Ref.of(Map.empty[String, String])
    implicit0(random: Random[F]) <- Random.scalaUtilRandom
  } yield new OneTimeStore(stateRef, timeout)

  private def randomKey[F[_]: Sync: Random: Parallel]: F[String] = List.fill(64)(Random[F].nextAlphaNumeric).parSequence
    .map(_.mkString)
}
