package core.projection.to.jdbc

import akka.actor.typed.ActorSystem
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.ProjectionId
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.ExactlyOnceProjection
import core.repository.scalike.ScalikeJdbcSession
import core.services.connectors.ConnectionManagerEntity

object MqttConnectionManagerProjection {

  def createProjectionFor(system: ActorSystem[_], repository: MqttConnectionManagerRepository, indexTag: Int): ExactlyOnceProjection [Offset, EventEnvelope[ConnectionManagerEntity.Event]] = {

    val tag = "connection-manager-tag-" + indexTag

    val sourcedProvider = EventSourcedProvider.eventsByTag[ConnectionManagerEntity.Event](
      system = system,
      readJournalPluginId = JdbcReadJournal.Identifier,
      tag = tag
    )

    JdbcProjection.exactlyOnce(
      projectionId = ProjectionId("MqttConnectionManagerProjection", tag),
      sourceProvider = sourcedProvider,
      handler = () => new MqttConnectionManagerEventHandler(repository),
      sessionFactory = () => ScalikeJdbcSession()
    )(system)
  }
}
