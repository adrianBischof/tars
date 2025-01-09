
package core.projection.to.jdbc

import akka.actor.typed.DispatcherSelector
import core.repository.scalike.ScalikeJdbcSession
import grpc.projection.DeviceRecords
import grpc.projection.DeviceRecords.{Device, Record, DeviceRecords}
import scalikejdbc.*

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}


trait MqttConnectionManagerRepository {
  def insertRecord(deviceId: String, tenantId: String, device_name: String, data: String, info: String, timestamp: LocalDateTime, session: ScalikeJdbcSession): Unit
}

class MqttConnectionManagerRepositoryImpl extends MqttConnectionManagerRepository {

  override def insertRecord(deviceId: String, tenantId: String, device_name: String, data: String, info: String, timestamp: LocalDateTime, session: ScalikeJdbcSession): Unit = {
    session.db.withinTx {
      implicit dbSession =>
        sql"""
              INSERT INTO device_records (device_id, tenant_id, device_name, data, info, timestamp)
              VALUES ($deviceId, $tenantId, $device_name, $data::jsonb, $info, $timestamp);
             """.execute.apply()
    }
  }
}