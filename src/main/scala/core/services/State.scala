package core.services

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import grpc.entity.State.StateServiceHandler

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object State {

  def start(implicit system: ActorSystem[_], sharding: ClusterSharding): Future[Http.ServerBinding] = {

    implicit val executionContext: ExecutionContext = ExecutionContext.global // adapt threading model -> work stealing thread model is used by default

    val provisioningService: HttpRequest => Future[HttpResponse] = StateServiceHandler.withServerReflection(
      new StateAPI()
    )


    val host: String = system.settings.config.getString("services.grpc.host")
    val port: Int = system.settings.config.getInt("services.grpc.state.port")

    val binding = Http().newServerAt(host, port).bind(provisioningService)

    // output logs
    binding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"IoT - State Service online at ${address.getAddress.toString}:${address.getPort.toString}")
      case Failure(ex) =>
        system.log.error("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }
    binding
  }
}

