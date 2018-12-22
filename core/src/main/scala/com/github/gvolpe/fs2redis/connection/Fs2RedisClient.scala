/*
 * Copyright 2018-2019 Fs2 Redis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.gvolpe.fs2redis.connection

import cats.effect.{ Concurrent, Resource, Sync }
import cats.syntax.apply._
import cats.syntax.functor._
import com.github.gvolpe.fs2redis.domain.{ DefaultRedisClient, Fs2RedisClient }
import com.github.gvolpe.fs2redis.effect.JRFuture
import io.chrisdavenport.log4cats.Logger
import io.lettuce.core.{ RedisClient, RedisURI }

object Fs2RedisClient {

  private[fs2redis] def acquireAndRelease[F[_]: Concurrent: Logger](
      uri: RedisURI
  ): (F[Fs2RedisClient], Fs2RedisClient => F[Unit]) = {
    val acquire: F[Fs2RedisClient] = Sync[F].delay { DefaultRedisClient(RedisClient.create(uri)) }

    val release: Fs2RedisClient => F[Unit] = client =>
      Logger[F].info(s"Releasing Redis connection: $uri") *>
        JRFuture.fromCompletableFuture(Sync[F].delay(client.underlying.shutdownAsync())).void

    (acquire, release)
  }

  private[fs2redis] def acquireAndReleaseWithoutUri[F[_]: Concurrent: Logger]
    : (F[Fs2RedisClient], Fs2RedisClient => F[Unit]) = acquireAndRelease(new RedisURI())

  def apply[F[_]: Concurrent: Logger](uri: RedisURI): Resource[F, Fs2RedisClient] = {
    val (acquire, release) = acquireAndRelease(uri)
    Resource.make(acquire)(release)
  }

}
