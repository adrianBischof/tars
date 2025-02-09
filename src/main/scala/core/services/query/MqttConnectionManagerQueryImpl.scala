package core.services.query

import java.util.NoSuchElementException
import akka.actor.typed.{ActorSystem, DispatcherSelector}
import com.google.common.cache.CacheBuilder
import core.repository.scalike.ScalikeJdbcSession
import grpc.projection.DeviceRecords
import grpc.projection.DeviceRecords.{Device, Record}
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

class MqttConnectionManagerQueryImpl(system: ActorSystem[_]) extends DeviceRecords.DeviceRecords {

  private val jdbcExecutor: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.fromConfig("akka.db-dispatcher"))
  private val recordCache = CacheBuilder.newBuilder()
    .expireAfterWrite(5, TimeUnit.SECONDS)
    .maximumSize(10000)
    .build[String, Record]()

  override def getLatestRecord(in: Device): Future[Record] = {
    Future {
      recordCache.get(in.deviceId, () => {
        ScalikeJdbcSession.withSession { session =>
          session.db.readOnly { implicit dbSession =>
            sql"""
            SELECT * FROM device_records
            WHERE device_id = ${in.deviceId}
            ORDER BY timestamp_start DESC
            LIMIT 1
          """.map { result =>
              Record(
                data = result.string("data"),
                timestamp = result.string("timestamp_start"),
                info = result.string("info"),
                device = Some(Device(deviceId = result.string("device_id"), deviceName = Some(result.string("device_name"))))
              )
            }.single.apply().getOrElse(Record())
          }
        }
      })
    }(jdbcExecutor)
  }
}