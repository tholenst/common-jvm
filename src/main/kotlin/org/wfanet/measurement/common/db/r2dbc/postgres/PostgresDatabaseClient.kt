/*
 * Copyright 2022 The Cross-Media Measurement Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wfanet.measurement.common.db.r2dbc.postgres

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.pool.PoolingConnectionFactoryProvider
import io.r2dbc.postgresql.api.PostgresTransactionDefinition
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.IsolationLevel
import java.time.Duration
import kotlinx.coroutines.reactive.awaitSingle
import org.wfanet.measurement.common.db.postgres.PostgresFlags
import org.wfanet.measurement.common.db.r2dbc.ConnectionProvider
import org.wfanet.measurement.common.db.r2dbc.DatabaseClient

/** PostgreSQL implementation of [DatabaseClient]. */
class PostgresDatabaseClient(getConnection: ConnectionProvider) : DatabaseClient(getConnection) {
  override val readTransactionDefinition: PostgresTransactionDefinition
    get() = Companion.readTransactionDefinition
  override val readWriteTransactionDefinition: PostgresTransactionDefinition
    get() = Companion.readWriteTransactionDefinition

  companion object {
    private val isolationLevel = IsolationLevel.SERIALIZABLE
    private val readTransactionDefinition =
      PostgresTransactionDefinition.from(isolationLevel).readOnly()
    private val readWriteTransactionDefinition =
      PostgresTransactionDefinition.from(isolationLevel).readWrite()

    fun fromFlags(flags: PostgresFlags): PostgresDatabaseClient {
      val connectionFactory =
        ConnectionFactories.get(
          ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER,"pool")
            .option(ConnectionFactoryOptions.PROTOCOL,"postgresql")
            .option(ConnectionFactoryOptions.HOST, flags.host)
            .option(ConnectionFactoryOptions.PORT, flags.port)
            .option(ConnectionFactoryOptions.USER, flags.user)
            .option(ConnectionFactoryOptions.PASSWORD, flags.password)
            .option(ConnectionFactoryOptions.DATABASE, flags.database)
            .option(ConnectionFactoryOptions.CONNECT_TIMEOUT, Duration.ofSeconds(30L))
            .build()
        )

      val connectionPool = connectionFactory.buildConnectionPool()
      return PostgresDatabaseClient { connectionPool.create().awaitSingle() }
    }

    fun fromConnectionFactory(connectionFactory: ConnectionFactory): PostgresDatabaseClient {
      return PostgresDatabaseClient { connectionFactory.create().awaitSingle() }
    }

    private fun ConnectionFactory.buildConnectionPool(): ConnectionPool {
      return ConnectionPool(
        ConnectionPoolConfiguration.builder(this)
          .acquireRetry(3)
          .maxAcquireTime(Duration.ofSeconds(30L))
          .maxCreateConnectionTime(Duration.ofSeconds(30L))
          .initialSize(15)
          .minIdle(15)
          .maxIdleTime(Duration.ofMinutes(30L))
          .maxSize(20)
          .build()
      )
    }
  }
}
