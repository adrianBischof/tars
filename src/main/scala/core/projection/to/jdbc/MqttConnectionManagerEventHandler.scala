package core.projection.to.jdbc

import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import core.repository.scalike.ScalikeJdbcSession
import core.services.connectors.ConnectionManagerEntity
import org.slf4j.LoggerFactory


class MqttConnectionManagerEventHandler(repository: MqttConnectionManagerRepository) extends JdbcHandler[EventEnvelope[ConnectionManagerEntity.Event], ScalikeJdbcSession] {

  val logger = LoggerFactory.getLogger(classOf[MqttConnectionManagerEventHandler])

  override def process(session: ScalikeJdbcSession, envelope: EventEnvelope[ConnectionManagerEntity.Event]): Unit = {

    envelope.event match
      case ConnectionManagerEntity.RecordProcessed(deviceId, tenantId, device_name, data, info, timestamp_start, timestamp_end) =>
        repository.insertRecord(deviceId, tenantId, device_name, data, info, timestamp_start, timestamp_end, session)
      case x =>
        logger.debug("ignoring event {} in projection.", x)
  }


}