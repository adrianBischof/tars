
package core.projection.to.jdbc

import core.repository.scalike.ScalikeJdbcSession
import grpc.projection.DeviceRecords
import scalikejdbc.*



trait MqttConnectionManagerRepository {
  def insertRecord(deviceId: String, tenantId: String, device_name: String, data: String, info: String, timestampStart: Long, timestampEnd: Long, session: ScalikeJdbcSession): Unit
}

class MqttConnectionManagerRepositoryImpl extends MqttConnectionManagerRepository {

  override def insertRecord(deviceId: String, tenantId: String, device_name: String, data: String, info: String, timestampStart: Long, timestampEnd: Long, session: ScalikeJdbcSession): Unit = {
    session.db.withinTx {
      implicit dbSession =>
        sql"""
              INSERT INTO device_records (device_id, tenant_id, device_name, data, info, timestamp_start, timestamp_end)
              VALUES ($deviceId, $tenantId, $device_name, $data::jsonb, $info, $timestampStart, $timestampEnd);
             """.execute.apply()
    }
  }
}