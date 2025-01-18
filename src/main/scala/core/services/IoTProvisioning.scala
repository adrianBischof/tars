package core.services

import akka.actor.typed.{ActorRef, ActorSystem, DispatcherSelector}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.util.Timeout
import grpc.entity.DeviceProvisioning.DeviceProvisioningServiceHandler

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object IoTProvisioning {

  def start(implicit system: ActorSystem[_], sharding: ClusterSharding): Future[Http.ServerBinding] = {

    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.fromConfig("akka.blocking-io-dispatcher"))
    
    val provisioningService: HttpRequest => Future[HttpResponse] = DeviceProvisioningServiceHandler.withServerReflection(
      new IoTProvisioningAPI()
    )
    

    val host: String = system.settings.config.getString("services.grpc.host")
    val port: Int = system.settings.config.getInt("services.grpc.provisioning.port")

    val binding = Http().newServerAt(host, port).bind(provisioningService)

    // output logs
    binding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"IoT - Provisioning Service online at ${address.getAddress.toString}:${address.getPort.toString}")
      case Failure(ex) =>
        system.log.error("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }
    binding
  }
}



  /*
  private class IoTProvisioningSharding(implicit sharding: ClusterSharding) extends DeviceProvisioningService {
  
  
  
  import concurrent.duration.DurationInt
  
  implicit val timeout: Timeout = 2.seconds // timeout after 2 seconds with no response
  implicit val executionContext: ExecutionContext = ExecutionContext.global // adapt threading model -> work stealing thread model is used by default
  
  sharding.init(Entity(ConfigurationEntity.TypeKey)(eCtx => ConfigurationEntity(eCtx.entityId))) // Init shard entity
  sharding.init(Entity(ConfigurationEntity.TypeKey)(eCtx => ConfigurationEntity(eCtx.entityId)))
  
  val cm =  sharding.entityRefFor(ConnectionManagerEntity.TypeKey, "test")
  cm ! ConnectionManagerEntity.Tick
  
  
  override def addBrokerConfig(in: MQTT): Future[Response] = {
    val configStore = sharding.entityRefFor(ConfigurationEntity.TypeKey, in.deviceId) // TODO: tenant
  
    def utilityRegisterMqttBroker(in: MQTT)(replyTo: ActorRef[ConfigurationEntity.Response]) =
      ConfigurationEntity.AddBrokerConfig(in.deviceId, MqttDTO.toDTO(in), replyTo)
  
    def utilityCheckIfExists(in: MQTT)(replyTo: ActorRef[ConfigurationEntity.Response]) = {
      ConfigurationEntity.HasConfig(in.deviceId, replyTo)
    }
  
    configStore.ask(utilityCheckIfExists(in)).flatMap {
      case ConfigurationEntity.Success => configStore.ask(utilityRegisterMqttBroker(in)).mapTo[ConfigAddedSuccessfully].map(_ => Response("OK"))
      case ConfigurationEntity.Failure => Future.successful(Response("Config already exists!"))
    }
  }
  
  override def removeBrokerConfig(in: ID): Future[Response] = {
    val configStore = sharding.entityRefFor(ConfigurationEntity.TypeKey, in.deviceId) // TODO: tenant
  
    def utilityRemoveBrokerConfig(in: ID)(replyTo: ActorRef[ConfigurationEntity.Response]) =
      ConfigurationEntity.RemoveBrokerConfig(in.deviceId, replyTo)
  
    configStore.ask(utilityRemoveBrokerConfig(in)).mapTo[ConfigRemovedSuccessfully].map(_ => Response(s"Removed ${in.deviceId}+${in.deviceId}")) // TODO: tenant
  }
  
  
  override def getBrokerConfig(in: ID): Future[Response] = {
    val configStore = sharding.entityRefFor(ConfigurationEntity.TypeKey, in.deviceId) // TODO: tenant
  
    def utilityGetBrokerConfig(in: ID)(replyTo: ActorRef[ConfigurationEntity.Response]) =
      ConfigurationEntity.GetBrokerConfig(in.deviceId, replyTo)
  
    configStore.ask(utilityGetBrokerConfig(in)).mapTo[ConfigShow].map { e =>
      if e.config.isDefined then Response(status= "ok", MqttDTO.toProto(e.config.get).toProtoString) else Response()
    }
  }
  
  
  */



