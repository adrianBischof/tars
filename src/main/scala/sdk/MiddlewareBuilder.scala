package sdk

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, ShardedDaemonProcess}
import akka.management.scaladsl.AkkaManagement
import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils
import akka.projection.ProjectionBehavior
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import core.projection.to.jdbc.{MqttConnectionManagerProjection, MqttConnectionManagerRepositoryImpl}
import core.repository.scalike.ScalikeJdbcSetup
import core.services.connectors.{ConfigurationEntity, ConfigurationValue, GrpcConfig, MqttConfig}
import core.services.query.MqttConnectionManagerQueryImpl
import core.services.{Command, CommandAPI, IoTProvisioning, IoTProvisioningAPI, Query}
import grpc.entity.DeviceProvisioning.{CommandRequest, CommandResponse, MQTT}
import grpc.projection.DeviceRecords.Device
import scalikejdbc.{GlobalSettings, LoggingSQLAndTimeSettings}

import concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}


class MiddlewareBuilder(appConfig: String) {

  private[this] val applicationConfiguration = ConfigFactory.load(appConfig)
  private[this] implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "middleware-supervisor")
  private[this] implicit val sharding: ClusterSharding = ClusterSharding(system)


  private[this] implicit val timeout: Timeout = 2.seconds // timeout after 2 seconds with no response
  private[this] implicit val eC: ExecutionContext = ExecutionContext.global // adapt threading model -> work stealing thread model is used by default


  GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
    enabled = true,
    singleLineMode = true,
    logLevel = "debug"
  )

  AkkaManagement(system).start()
  ScalikeJdbcSetup.init(system)
  SchemaUtils.createIfNotExists()

  IoTProvisioning.start(system, sharding, eC)

  def withDevice(in: ConfigurationValue): Unit = {

    val a = new IoTProvisioningAPI()

    in match
      case MqttConfig(value) =>
        a.addBrokerConfig(value)
      case GrpcConfig(value) =>
        a.addGRPCConfig(value)
  }

  def withQueryService(): Unit = {
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
  }

  def withCommandService(): CommandAPI = {
    Command.start(system, sharding, eC)
    new CommandAPI()
  }

  def sendCommand(cR: CommandRequest, commandAPI: CommandAPI): Future[CommandResponse] = {
    commandAPI.sendCommandToDevice(CommandRequest(cR.deviceId, cR.tenantId, cR.command, cR.webhookUrl))
  }

  def getLatestReading(device: Device): Future[String] = {
    val a = new MqttConnectionManagerQueryImpl(system)
    a.getLatestRecord(device).mapTo[grpc.projection.DeviceRecords.Record].map {
      r => r.data
    }

  }

  def getSystem: ActorSystem[Nothing] = {
    system
  }
}


@main
def SDK(): Unit = {

  val config = MqttConfig(MQTT(deviceId = "Shelly5", name = "Shelly HT", location = "Room", server = "tcp://78.47.113.0", cleanSession = false, autoReconnect = true, topics = Seq("shellyplusht-e86beae8d784/status/temperature:0"), provisionService = "???", backpressure = 15, tenantId = "tenant"))

  val sdk = new MiddlewareBuilder("application.conf")

  sdk.withDevice(config)
  sdk.withQueryService()
  val a = sdk.withCommandService()

  val device = Device(deviceId="Shelly5", tenantId="tenant")
  val request = CommandRequest("Shelly5", "tenant", "hoppel hase", Some("www.test.com"))

  while (true) {
    Thread.sleep(5000)
    val res = Await.result(sdk.getLatestReading(device), 2.seconds)

    if res.equals("{\"id\": 0, \"tC\": 22.8, \"tF\": 73.0}") then
      print("got response sending command")
      val c = Await.result(sdk.sendCommand(request, a), 2.seconds)
  }

}

