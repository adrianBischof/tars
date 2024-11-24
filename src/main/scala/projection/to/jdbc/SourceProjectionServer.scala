package projection.to.jdbc

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import grpc.projection.SourceProto.SourceProjectionServiceHandler
import scala.concurrent.{ExecutionContext, Future}


object SourceProjectionServer {
  
  def start(repository: JDBCSourceRepository)(implicit ex: ExecutionContext, system: ActorSystem[_]): Unit = {
    
    val projectionService: HttpRequest => Future[HttpResponse] = SourceProjectionServiceHandler.withServerReflection(
      new SourceProjectionServiceImpl(system, repository))

    val host: String = system.settings.config.getString("services.host")
    val port: Int = system.settings.config.getInt("services.sensor.projection.port")

    val binding = Http().newServerAt(host, port).bind(projectionService)
  }

}
