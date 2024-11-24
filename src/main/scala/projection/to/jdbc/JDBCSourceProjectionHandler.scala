package projection.to.jdbc

import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import org.slf4j.LoggerFactory
import repository.scalike.ScalikeJdbcSession
import aggregate.SourceAggregate
import akka.persistence.typed.javadsl.EventHandler
import factory.source.to.influx.InfluxDB
import upickle.default.*
import utility.JsonExtractor
import akka.projection.scaladsl.AtLeastOnceProjection


class JDBCSourceProjectionHandler(repository: JDBCSourceRepository) extends JdbcHandler[EventEnvelope[SourceAggregate.Event], ScalikeJdbcSession] {

  val logger = LoggerFactory.getLogger(getClass)

  override def process(session: ScalikeJdbcSession, envelope: EventEnvelope[SourceAggregate.Event]): Unit = {

    val sink = new InfluxDB()

    envelope.event match {
      case SourceAggregate.DataProcessed(sourceId, aggregateId, data) =>
        logger.info(s"$sourceId:$aggregateId:$data")
        repository.insertSourceReading(sourceId, aggregateId, data, session)

        val rh = JsonExtractor.extract(data, "rh")
        val tc = JsonExtractor.extract(data, "tC")

        println(s"Projection for rh: $rh")
        println(s"Projection for tC: $tc")
        println("--------------------------")


        if rh.isDefined then sink.persist(sourceId, "rh", rh.get.noSpaces)

        if tc.isDefined then sink.persist(sourceId, "tC", tc.get.noSpaces)

      case x =>
        logger.info("ignoring event {} in projection", x)
    }
  }
}
