import org.slf4j.LoggerFactory
import server.SourceAggregateServer
import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils

import scala.util.control.NonFatal
import scala.concurrent.ExecutionContext
import akka.actor.typed.scaladsl.Behaviors
import repository.scalike.ScalikeJdbcSetup
import akka.cluster.sharding.typed.scaladsl.*
import akka.management.scaladsl.AkkaManagement
import akka.actor.typed.ActorSystem
import projection.to.jdbc.{JDBCSourceProjection, JDBCSourceRepository, ProjectionFactory, SourceProjectionServer}
import scalikejdbc.{GlobalSettings, LoggingSQLAndTimeSettings}
import service.DeviceProvisioningService
object Main {

  val logger = LoggerFactory.getLogger(Main.toString)

  def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "system-supervisor") // must be same as in the config

    try {
      val sharding: ClusterSharding = ClusterSharding(system)
      implicit val ec: ExecutionContext = system.executionContext

      GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
        enabled = true,
        singleLineMode = true,
        logLevel = "debug"
      )


      AkkaManagement(system).start()
      // ClusterBootstrap(system).start() TODO: when forming a cluster use Akka Discovery without seed node definition in application.conf
      ScalikeJdbcSetup.init(system)

      SchemaUtils.createIfNotExists()
      // --------------- GRPC Service
      SourceAggregateServer.start(system, sharding, ec)
      DeviceProvisioningService.start(system, sharding, ec)
      // Other Services for other functionalities
      // --------------- GRPC Service


      val sourceRepository = new JDBCSourceRepository()
      SourceProjectionServer.start(sourceRepository) // CQRS: READ Side
      ProjectionFactory.initProjection(system)

    } catch {
      case NonFatal(e) =>
        logger.error(s"Terminating ...\nReason: [$e]")
        system.terminate()
    }
  }
}




