package projection.to.jdbc

import aggregate.SourceAggregate
import akka.{Done, NotUsed}
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.{Offset, PersistenceQuery}
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.ExactlyOnceProjection
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory
import factory.source.to.influx.InfluxDB
import repository.scalike.ScalikeJdbcSession
import utility.JsonExtractor


trait IProjection {
  def init(system: ActorSystem[_]): Unit
}


object JDBCSourceProjection extends IProjection {

  override def init(system: ActorSystem[_]): Unit = {
    ShardedDaemonProcess(system).init(
      name = "jdbc-projection",
      numberOfInstances = SourceAggregate.tags.size,
      index =>
        ProjectionBehavior(
          createJDBCProjection(system, new JDBCSourceRepository(), index)
        ),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop))
  }

  private def createJDBCProjection(system: ActorSystem[_], repository: JDBCSourceRepository, index: Int): ExactlyOnceProjection[Offset, EventEnvelope[SourceAggregate.Event]] = {

    val tag = SourceAggregate.tags(index)

    // reads from the event journal; read-side
    val sourceProvider = EventSourcedProvider.eventsByTag[SourceAggregate.Event](
      system = system,
      readJournalPluginId = JdbcReadJournal.Identifier,
      tag = tag)

    JdbcProjection.exactlyOnce(
      projectionId = ProjectionId("SourceProjection", tag),
      sourceProvider = sourceProvider,
      handler = () => new JDBCSourceProjectionHandler(repository), // handler to write to the database;
      sessionFactory = () => new ScalikeJdbcSession())(system) // pool of connections to provide to the handler;
  }
}

object InfluxProjection extends IProjection {


  override def init(system: ActorSystem[_]): Unit = {
    createInfluxProjection(system, 0)
  }

  private def createInfluxProjection(system: ActorSystem[_], index: Int): Unit = {
    
    val influx = InfluxDB()
    val tag = SourceAggregate.tags(index)
    val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
    val source = readJournal.eventsByTag(tag, Offset.noOffset)

    source.runForeach(e => e.event match
      case SourceAggregate.DataProcessed(sourceId, aggregateId, data) =>

        val rh = JsonExtractor.extract(data, "rh")
        val tc = JsonExtractor.extract(data, "tC")

          println(s"Projection for rh: $rh")
          println(s"Projection for tC: $tc")
          println("--------------------------")


        if rh.isDefined then influx.persist(sourceId, "rh", rh.get.noSpaces)

        if tc.isDefined then influx.persist(sourceId, "tC", tc.get.noSpaces)
      case x =>
        println("Coult not process event")
    )(Materializer(system))
  }
}

object ProjectionFactory {

  def initProjection(system: ActorSystem[_]): Unit = {
    val conf = ConfigFactory.load()
    val factory = conf.getString("akka.factory.projection")
    println("Using projection: " + factory)

    factory match {
      case "jdbc" =>
        JDBCSourceProjection.init(system)
      case "influx" =>
        InfluxProjection.init(system)
    }
  }
}

