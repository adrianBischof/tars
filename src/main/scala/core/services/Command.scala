package core.services

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import grpc.entity.DeviceProvisioning.CommandServiceHandler

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Command {

  def start(implicit system: ActorSystem[_], sharding: ClusterSharding): Future[Http.ServerBinding] = {

    implicit val ec: ExecutionContext = system.executionContext

    val provisioningService: HttpRequest => Future[HttpResponse] = CommandServiceHandler.withServerReflection(
      new CommandAPI()
    )


    val host: String = system.settings.config.getString("services.grpc.host")
    val port: Int = system.settings.config.getInt("services.grpc.command.port")

    val binding = Http().newServerAt(host, port).bind(provisioningService)

    // output logs
    binding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"IoT - Command Service online at ${address.getAddress.toString}:${address.getPort.toString}")
      case Failure(ex) =>
        system.log.error("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }

    binding
  }
}
