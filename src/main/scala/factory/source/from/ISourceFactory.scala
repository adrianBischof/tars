package factory.source.from

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.Materializer
import utility.ISourceConfig

import scala.concurrent.ExecutionContext

trait ISourceFactory {
  
  def newSource(sourceType: ISourceConfig, aggregator: ActorRef[aggregate.SourceAggregate.Command])(implicit system: ActorSystem[_], ec: ExecutionContext, mat: Materializer): ISourceConnector

}
