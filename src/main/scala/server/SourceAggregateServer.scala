package server

import akka.http.scaladsl.Http
import akka.actor.typed.ActorSystem

import scala.util.{Failure, Success}
import grpc.entity.SourceProto.SourceAggregateServiceHandler

import scala.concurrent.{ExecutionContext, Future}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding



object SourceAggregateServer {

  def start(implicit system: ActorSystem[_], sharding: ClusterSharding, ec: ExecutionContext): Future[Http.ServerBinding] = {

    val sensorService: HttpRequest => Future[HttpResponse] = SourceAggregateServiceHandler.withServerReflection(
      new SourceAggregateSharding()
    )

    val host: String = system.settings.config.getString("services.host")
    val port: Int = system.settings.config.getInt("services.sensor.port")

    val binding = Http().newServerAt(host, port).bind(sensorService)

    // output logs
    binding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"IoT - Middleware online at gRPC server ${address.getAddress.toString}:${address.getPort.toString}")
      case Failure(ex) =>
        system.log.error("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }

    binding // return binding
  }

}
