package factory.source.from.dummy

import aggregate.SourceAggregate
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.Materializer
import factory.source.from.{ISourceConnector, ISourceFactory, NothingSource}
import utility.{DummyConfig, ISourceConfig}

import scala.concurrent.ExecutionContext

object DummySourceFactory extends ISourceFactory {

  override def newSource(sourceType: ISourceConfig, aggregator: ActorRef[SourceAggregate.Command])(implicit system: ActorSystem[_], ec: ExecutionContext, mat: Materializer): ISourceConnector = {
    sourceType match 
      case _: utility.DummyConfig =>
        new DummySource(sourceType.asInstanceOf[DummyConfig], aggregator)
      case _ =>
        NothingSource("Wrong type for Dummy Source")
  }
}
