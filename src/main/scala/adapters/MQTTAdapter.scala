package adapters

import aggregate.SourceAggregate
import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.stream.alpakka.mqtt.scaladsl.{MqttMessageWithAck, MqttSource}
import akka.stream.{KillSwitches, Materializer, SharedKillSwitch}
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS, MqttSubscriptions}
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSink
import entity.PersistentWorker
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.{ExecutionContext, Future}
import concurrent.duration.DurationInt


class MQTTAdapter(settings: grpc.entity.DeviceProvisioning.MQTT, worker: ActorRef[PersistentWorker.Command])(using system: ActorSystem[_], ec: ExecutionContext, mat: Materializer) extends Connectable {

  private val persistence = new MemoryPersistence
  private val connectionSettings = MqttConnectionSettings(broker = settings.server, clientId = settings.iD, persistence = persistence)
    .withCleanSession(settings.cleanSession)
    .withKeepAliveInterval(1.minute)

  private val topics: Map[String, MqttQoS] = settings.topics.map(t => t -> MqttQoS.AtLeastOnce).toMap
  private val mqttSubscriptions = MqttSubscriptions(topics)
  private val mqttSource: Source[MqttMessageWithAck, Future[Done]] = MqttSource.atLeastOnce(connectionSettings, mqttSubscriptions, settings.backpressure)

  private val killSwitch: SharedKillSwitch = KillSwitches.shared("mqtt-kill-switch")

  /** Creates a Flow an processes incoming messages from the MQTT source. Each
   * message payload is extracted and sent to the persistent worker actor.
   */
  override def run(): Unit = {

    val actorSink = ActorSink.actorRefWithBackpressure(
      ref = worker,
      messageAdapter = (responseActorRef: ActorRef[PersistentWorker.Ack], data: MqttMessage) => {
        PersistentWorker.Consume(data.topic, data.payload.utf8String, responseActorRef)
      },
      onInitMessage = (responseActorRef: ActorRef[PersistentWorker.Ack]) => PersistentWorker.Init(responseActorRef),
      ackMessage = PersistentWorker.Ack,
      onCompleteMessage = PersistentWorker.Complete,
      onFailureMessage = (ex) => PersistentWorker.Fail(ex)
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