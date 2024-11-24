package factory.source.from.dummy

import akka.NotUsed
import aggregate.SourceAggregate
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.projection.javadsl.SourceProvider
import akka.stream.{KillSwitches, Materializer, OverflowStrategy, SharedKillSwitch}
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSink
import akka.util.Timeout
import factory.source.from.ISourceConnector
import utility.DummyConfig

import concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext

/** Source Connector to a MQTT broker - see: https://doc.akka.io/docs/alpakka/current/mqtt.html
 *
 * @constructor The connection setting for the MQTT broker.
 * @param config     the Dummy source connection settings
 * @param aggregator reference of the Source Aggregator collecting the incoming messages
 */
class DummySource(config: DummyConfig, aggregator: ActorRef[SourceAggregate.Command])(implicit system: ActorSystem[_], ec: ExecutionContext, mat: Materializer) extends ISourceConnector {

  implicit val timeout: Timeout = 3.seconds
  private val dummySource: Source[Int, NotUsed] = Source(config.start to config.end)
  private val killSwitch: SharedKillSwitch = KillSwitches.shared("dummy-kill-switch")
  

  override def run(): Unit = {
    // TODO: Currently only number from x to y are persisted to db -> persist as json ...
    val actorSink = ActorSink.actorRefWithBackpressure(
      ref = aggregator,
      messageAdapter = (responseActorRef: ActorRef[SourceAggregate.Ack], data: Int) => SourceAggregate.ProcessData(config.sourceId, data.toString, responseActorRef), //TODO: fix input of SourceId
      onInitMessage = (responseActorRef: ActorRef[SourceAggregate.Ack]) => SourceAggregate.Init(responseActorRef),
      ackMessage = SourceAggregate.Ack,
      onCompleteMessage = SourceAggregate.Complete,
      onFailureMessage = (ex) => SourceAggregate.Fail(ex)
    )

    dummySource
      .via(killSwitch.flow)
      .throttle(100, 1.second)
      .buffer(5, OverflowStrategy.backpressure)
      .runWith(actorSink)

  }
  
  

  override def stop(): Unit = {
    println(s"Kill - switch: ${killSwitch.name} triggered!") // TODO: proper logging
    killSwitch.shutdown()
  }
}
