package core.services.query

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import core.repository.scalike.ScalikeJdbcSession
import grpc.projection.DeviceRecords
import grpc.projection.DeviceRecords.{Device, Record}
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef

import scala.concurrent.{ExecutionContext, Future}

class MqttConnectionManagerQueryImpl(system: ActorSystem[_]) extends DeviceRecords.DeviceRecords {

  implicit private val jdbcExecutor: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.fromConfig("akka.projection.jdbc.blocking-jdbc-dispatcher"))


  override def getLatestRecord(in: Device): Future[Record] = {
    Future {
      ScalikeJdbcSession.withSession { session =>
        session.db.readOnly { implicit dbSession =>
          sql"""
               SELECT * from device_records where device_id = ${in.deviceId}
             """.map{ result =>
            Record(
              data = result.string("data"),
              timestamp = result.string("timestamp_start"),
              info = result.string("info"),
              device = Some(Device(deviceId = result.string("device_id"), deviceName = Some(result.string("device_name")))))
          }.list.apply().last
        }
      }

    }
  }
}
