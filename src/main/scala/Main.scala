import org.slf4j.{Logger, LoggerFactory}
import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils

import scala.util.control.NonFatal
import scala.concurrent.ExecutionContext
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.*
import akka.management.scaladsl.AkkaManagement
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.projection.ProjectionBehavior
import core.projection.to.jdbc.{MqttConnectionManagerProjection, MqttConnectionManagerRepositoryImpl}
import core.repository.scalike.ScalikeJdbcSetup
import core.services.query.MqttConnectionManagerQueryImpl
import scalikejdbc.{GlobalSettings, LoggingSQLAndTimeSettings}
import core.services.{Command, IoTProvisioning, Query}

object Main {

  val logger: Logger = LoggerFactory.getLogger(Main.toString)

  def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "middleware-supervisor") // must be same as in the config

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
      
      IoTProvisioning.start(system, sharding)
      Command.start(system, sharding, ec) // CQRS: WRITE Side



      val repo = new MqttConnectionManagerQueryImpl(system)
      Query.start(repo) // CQRS: READ Side
      //ProjectionFactory.initProjection(system)

      ShardedDaemonProcess(system).init(
        name = "device-state-projection",
        3,
        index =>
          ProjectionBehavior(
            MqttConnectionManagerProjection.createProjectionFor(
              system,
              new MqttConnectionManagerRepositoryImpl(),
              index
            )
          ),
        ShardedDaemonProcessSettings(system),
        Some(ProjectionBehavior.Stop)
      )

    } catch {
      case NonFatal(e) =>
        logger.error(s"Terminating ...\nReason: [$e]")
        system.terminate()
    }
  }
}




