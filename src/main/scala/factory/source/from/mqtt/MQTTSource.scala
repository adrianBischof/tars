package factory.source.from.mqtt

import aggregate.SourceAggregate
import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.alpakka.mqtt.*
import akka.stream.alpakka.mqtt.scaladsl.{MqttMessageWithAck, MqttSource}
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.typed.scaladsl.ActorSink
import akka.stream.{KillSwitches, Materializer, SharedKillSwitch}
import factory.source.from.ISourceConnector
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import spray.json.JsonReader
import utility.MQTTConfig

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

/** Source Connector to a MQTT broker - see: https://doc.akka.io/docs/alpakka/current/mqtt.html
 *
 * @constructor The connection setting for the MQTT broker.
 * @param sourceConfig     the MQTT source connection settings
 * @param aggregator reference of the Source Aggregator collecting the incoming messages
 */
class MQTTSource(sourceConfig: MQTTConfig, aggregator: ActorRef[SourceAggregate.Command])(implicit system: ActorSystem[_], ec: ExecutionContext, mat: Materializer) extends ISourceConnector {

  private val persistence = new MemoryPersistence
  private val connectionSettings = MqttConnectionSettings(broker = sourceConfig.broker, clientId = sourceConfig.sourceId, persistence = persistence)
    .withCleanSession(sourceConfig.cleanSession)
    .withKeepAliveInterval(1.minute)

  private val topics: Map[String, MqttQoS] = sourceConfig.topics.map(t => t -> MqttQoS.AtLeastOnce).toMap
  private val mqttSubscriptions = MqttSubscriptions(topics)
  private val mqttSource: Source[MqttMessageWithAck, Future[Done]] = MqttSource.atLeastOnce(connectionSettings, mqttSubscriptions, sourceConfig.backpressure)

  private val killSwitch: SharedKillSwitch = KillSwitches.shared("mqtt-kill-switch")

  /** Creates a Flow an processes incoming messages from the MQTT source. Each
   * message payload is extracted and sent to the Source Aggregate actor.
   */
  override def run(): Unit = {

    val actorSink = ActorSink.actorRefWithBackpressure(
      ref = aggregator,
      messageAdapter = (responseActorRef: ActorRef[SourceAggregate.Ack], data: MqttMessage) => {
        
        SourceAggregate.ProcessData(data.topic, data.payload.utf8String, responseActorRef)
      }, //TODO: fix input of SourceId
      onInitMessage = (responseActorRef: ActorRef[SourceAggregate.Ack]) => SourceAggregate.Init(responseActorRef),
      ackMessage = SourceAggregate.Ack,
      onCompleteMessage = SourceAggregate.Complete,
      onFailureMessage = (ex) => SourceAggregate.Fail(ex)
    )

    mqttSource
      .via(killSwitch.flow)
      .mapAsync(10) { messageWithAck =>
        messageWithAck.ack().map(_ => messageWithAck.message)
      }
      .runWith(actorSink)
  }

  override def stop(): Unit = {
    println(s"Kill - switch: ${killSwitch.name} triggered!") // TODO: proper logging
    killSwitch.shutdown()
  }
}