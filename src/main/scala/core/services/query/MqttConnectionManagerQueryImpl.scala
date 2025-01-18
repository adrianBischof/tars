package core.services.query

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import core.repository.scalike.ScalikeJdbcSession
import grpc.projection.DeviceRecords
import grpc.projection.DeviceRecords.{Device, Record}
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef

import scala.concurrent.{ExecutionContext, Future}

class MqttConnectionManagerQueryImpl(system: ActorSystem[_]) extends DeviceRecords.DeviceRecords {

  implicit private val jdbcExecutor: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.fromConfig("akka.db-dispatcher"))


  override def getLatestRecord(in: Device): Future[Record] = {
    Future {
      ScalikeJdbcSession.withSession { session =>
        session.db.readOnly { implicit dbSession =>
          val result =
            sql"""
               SELECT * from device_records where device_id = ${in.deviceId} LIMIT 1
             """.map { result =>
              Record(
                data = result.string("data"),
                timestamp = result.string("timestamp_start"),
                info = result.string("info"),
                device = Some(Device(deviceId = result.string("device_id"), deviceName = Some(result.string("device_name")))))
            }.single.apply()

          result match
            case Some(record: Record) => record
            case None => NoSuchElementException(s"No records found for device ID: ${in.deviceId}")
        }
      }
    }(jdbcExecutor)
  }
}
