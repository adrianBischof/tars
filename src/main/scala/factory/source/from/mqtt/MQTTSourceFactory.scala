package factory.source.from.mqtt

import aggregate.SourceAggregate
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.Materializer
import factory.source.from.{ISourceConnector, ISourceFactory, NothingSource}
import utility.{ISourceConfig, MQTTConfig}

import scala.concurrent.ExecutionContext

object MQTTSourceFactory extends ISourceFactory {

  override def newSource(sourceType: ISourceConfig, aggregator: ActorRef[SourceAggregate.Command])(implicit system: ActorSystem[_], ec: ExecutionContext, mat: Materializer): ISourceConnector = {
       sourceType match
         case _: utility.MQTTConfig =>
           new MQTTSource(sourceType.asInstanceOf[MQTTConfig], aggregator)
         case _ =>
           NothingSource(s"Wrong type for MQTT Source")
  }
}
