package core.services.connectors.mqtt

import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.alpakka.mqtt.*
import akka.stream.alpakka.mqtt.scaladsl.{MqttMessageWithAck, MqttSink, MqttSource}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.typed.scaladsl.ActorSink
import akka.stream.{KillSwitches, Materializer, SharedKillSwitch}
import akka.util.ByteString
import core.services.connectors.{Connectable, ConnectionManagerEntity}
import grpc.entity.DeviceProvisioning.MQTT
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import java.time.{Instant, LocalDateTime}
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}


class MQTTConnector(config: MQTT, connectionManagerRef: ActorRef[ConnectionManagerEntity.Command])(implicit system: ActorSystem[_], ec: ExecutionContext, mat: Materializer) extends Connectable {

  private val subconnectionSettings = MqttConnectionSettings.create(config.server, config.tenantId + config.deviceId, new MemoryPersistence)
    .withCleanSession(config.cleanSession)
    .withAutomaticReconnect(config.autoReconnect)
    .withKeepAliveInterval(1.minute)
    .withMqttVersion(3)

  private val pubconnectionSettings = MqttConnectionSettings.create(config.server, UUID.randomUUID().toString, new MemoryPersistence)
    .withCleanSession(config.cleanSession)
    .withAutomaticReconnect(config.autoReconnect)
    .withKeepAliveInterval(1.minute)
    .withMqttVersion(3)

  private val topics: Map[String, MqttQoS] = config.topics.map(t => t -> MqttQoS.AtLeastOnce).toMap
  private val mqttSubscriptions = MqttSubscriptions(topics)

  private val mqttSource: Source[MqttMessageWithAck, Future[Done]] = MqttSource.atLeastOnce(subconnectionSettings, mqttSubscriptions, config.backpressure)
  private val mqttSink: Sink[MqttMessage, Future[Done]] = MqttSink(pubconnectionSettings, MqttQoS.AtLeastOnce) // QoS can be adjusted

  val killSwitch: SharedKillSwitch = KillSwitches.shared(s"mqtt-kill-switch-${config.tenantId}" + ":" + "${config.deviceId}")

  /** Creates a Flow an processes incoming messages from the MQTT source. Each
   * message payload is extracted and sent to the MqttConnectionManager actor.
   */
  override def subscribe(): Unit = {

    val actorSink = ActorSink.actorRefWithBackpressure(
      ref = connectionManagerRef,
      messageAdapter = (responseActorRef: ActorRef[ConnectionManagerEntity.Ack], data: MqttMessage) => {
        val timestamp_start = System.nanoTime()
        ConnectionManagerEntity.ProcessRecord(config.deviceId, config.tenantId, config.name, data.payload.utf8String, data.topic, timestamp_start , responseActorRef)
      },
      onInitMessage = (responseActorRef: ActorRef[ConnectionManagerEntity.Ack]) => ConnectionManagerEntity.Init(responseActorRef),
      ackMessage = ConnectionManagerEntity.Ack,
      onCompleteMessage = ConnectionManagerEntity.Complete,
      onFailureMessage = (ex) => ConnectionManagerEntity.Fail(ex)
    )

    mqttSource
      .via(killSwitch.flow)
      .mapAsync(16) { messageWithAck =>
        messageWithAck.ack().map(_ => messageWithAck.message)
      }
      .runWith(actorSink)
  }

  def publish(command: String): Unit = {
    val message = MqttMessage("shellyplusht-e86beae8d784/commands/rpc", ByteString(command))

    Source.single(message).runWith(mqttSink)
  }

  override def terminate(): Unit = {
    println(s"Kill - switch: ${killSwitch.name} triggered!") // TODO: proper logging
    killSwitch.shutdown()
  }
}