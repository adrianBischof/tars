package projection.to.jdbc

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import grpc.projection.SourceProto
import grpc.projection.SourceProto.{SourceProjectionService, Statistics}
import repository.scalike.ScalikeJdbcSession

import scala.concurrent.{ExecutionContext, Future}

class SourceProjectionServiceImpl(system: ActorSystem[_], repository: JDBCSourceRepository) extends SourceProjectionService {

  implicit private val jdbcExecutor: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.fromConfig("akka.projection.jdbc.blocking-jdbc-dispatcher"))


  override def sourceEntityStatistics(in: SourceProto.SourceEntityStatisticsById): Future[Statistics] = {
    Future {
      ScalikeJdbcSession.withSession { session =>
        ???
        /*val stats = repository.stats(in.sourceId, in.key, session)

        stats.getOrElse(Statistics(in.sourceId, 0))*/
      }
    }
  }
}
