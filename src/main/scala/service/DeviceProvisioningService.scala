package service

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.util.Timeout
import entity.ConfigStore
import grpc.entity.DeviceProvisioning.DeviceProvisioningServiceHandler

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object DeviceProvisioningService {

  def start(implicit system: ActorSystem[_], sharding: ClusterSharding, ec: ExecutionContext): Future[Http.ServerBinding] = {
    val provisioningService: HttpRequest => Future[HttpResponse] = DeviceProvisioningServiceHandler.withServerReflection(
      new DeviceProvisioningServiceImpl()
    )

    val host: String = system.settings.config.getString("services.host")
    val port: Int = system.settings.config.getInt("services.provisioning.port")

    val binding = Http().newServerAt(host, port).bind(provisioningService)

    // output logs
    binding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Device Provisioning Service is accessible under ${address.getAddress.toString}:${address.getPort.toString}")
      case Failure(ex) =>
        system.log.error("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }

    binding
  }

}

private class DeviceProvisioningServiceImpl(implicit sharding: ClusterSharding) extends grpc.entity.DeviceProvisioning.DeviceProvisioningService {

  import entity.PersistentWorker
  import grpc.entity.DeviceProvisioning.*


  implicit val timeout: Timeout = 3.seconds // timeout after 2 seconds with no response
  implicit val executionContext: ExecutionContext = ExecutionContext.global // adapts threading model -> work stealing thread model is used by default

  sharding.init(Entity(PersistentWorker.TypeKey)(ec => PersistentWorker(ec.entityId))) // Init shard entity

  override def registerGRPC(in: gRPC): Future[gRPC] = ???

  override def registerMQTT(in: MQTT): Future[MQTT] = {

    val workerShard = sharding.entityRefFor(PersistentWorker.TypeKey, in.iD) // For every action taken first the config shard is requested to perform an action. either a config is added and after succes it is done so on the worker.
    val configShard = sharding.entityRefFor(ConfigStore.TypeKey, in.iD)
    
    def utilityRegisterMQTT(in: MQTT)(replyTo: ActorRef[MQTT])= PersistentWorker.RegisterMQTT()

    workerShard.ask(utilityRegisterMQTT(in))

  }

  override def remove(in: ID): Future[Response] = ???

  override def status(in: ID): Future[State] = ???
}
