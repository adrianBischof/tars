package core.services

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import core.services.query.MqttConnectionManagerQueryImpl
import grpc.projection.DeviceRecords.DeviceRecordsHandler

import scala.concurrent.{ExecutionContext, Future}

object Query {

  def start(repository: MqttConnectionManagerQueryImpl)(implicit system: ActorSystem[_]): Unit = {

    implicit val ec: ExecutionContext = system.executionContext

    val projectionService: HttpRequest => Future[HttpResponse] = DeviceRecordsHandler.withServerReflection(
      new MqttConnectionManagerQueryImpl(system))

    val host: String = system.settings.config.getString("services.jdbc.projection.host")
    val port: Int = system.settings.config.getInt("services.jdbc.projection.port")

    val binding = Http().newServerAt(host, port).bind(projectionService)
  }

}