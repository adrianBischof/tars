package core.services.connectors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, DispatcherSelector, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import akka.stream.{Materializer, SystemMaterializer}
import akka.util.Timeout
import core.serializer.CborSerializable
import core.services.connectors.ConfigurationEntity.ConfigurationResponse
import core.services.connectors.mqtt.MQTTConnector
import grpc.entity.DeviceProvisioning.{ID, MQTT, MQTTConfigResponse}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt


object ConnectionManagerEntity {
  
  implicit val timeout: Timeout = 5.seconds // timeout after 2 seconds with no response
  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("connection-manager-type-key")
  private val streams: collection.mutable.Map[String, MQTTConnector] = collection.mutable.Map.empty // since this is probably global the key is the tenant id and the value the object.

  def apply(tenantId: String): Behavior[Command] = {

    Behaviors.setup { ctx =>
      val shardRegion = ClusterSharding(ctx.system).entityRefFor(ConfigurationEntity.TypeKey, tenantId)
      ctx.log.info(s"Inside: ${TypeKey}" + s" ${tenantId}")
      EventSourcedBehavior[Command, Event, State](
        PersistenceId(TypeKey.name, tenantId),
        State.empty,
        commandHandler = (state, command) => commandHandler(tenantId, state, command, Map.empty, ctx),
        eventHandler = (state, event) => eventHandler(state, event, ctx)
      )
        .withTagger(_ => Set(calculateTag(tenantId, tags)))
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 5))
        .onPersistFailure(
          SupervisorStrategy.restartWithBackoff(
            minBackoff = 10.seconds,
            maxBackoff = 60.seconds,
            randomFactor = 0.1
          )
        )
        .receiveSignal {
          case (state, RecoveryCompleted) =>
            def utilityGetConfigs()(replyTo: ActorRef[ConfigurationEntity.Response]) =
              ConfigurationEntity.GetAllConfigs(replyTo)

            shardRegion.ask(utilityGetConfigs()).mapTo[ConfigurationResponse].map {
              case ConfigurationEntity.ConfigurationResponse(r) => 
                if r.data.nonEmpty then r.data.foreach { (k,v) => instantiateMqttConnector(k,v, ctx)}
            }(ctx.executionContext)
          ctx.log.info("Recovery completed, restoring MQTT connections...")
        }
    }
  }

  private def commandHandler(tenantId: String, state: State, command: Command, connections: Map[String, Connectable], ctx: ActorContext[Command]): Effect[Event, State] = {
    command match
      case Init(ackTo) => initStream(ackTo)
      case Complete => streamComplete()(ctx)
      case Fail(ex) => streamFailed(ex)(ctx)
      case ProcessRecord(deviceId, tenantId, deviceName, data, info, timestampStart, replyTo) =>
        ctx.log.info(s"Got sensor_reading from $tenantId, $deviceId with $data and Timestamp Start: $timestampStart")
        persistData(deviceId, tenantId, deviceName, data, info, timestampStart, replyTo)
      case InstantiateMqttConnector(config, replyTo) =>
        Effect.persist(PersistedConnection(config)).thenReply(replyTo)(_ => SuccessEvent("Created Connector"))
      case DeleteMqttConnector(deviceId, replyTo) =>
        Effect.persist(DeletedMqttConnection(deviceId, tenantId)).thenReply(replyTo)(_ => SuccessEvent("Deleted Connector"))
      case SendCommandToDevice(deviceId, message, replyTo) => commandToDevice(deviceId, message, replyTo)
      case GetState(deviceId, replyTo) => getState(deviceId, state, replyTo)
  }

  private def eventHandler(state: State, event: Event, ctx: ActorContext[Command]): State = {

    event match
      case RecordProcessed(deviceId, tenantId, device_name, data, info, timestampStart, timestampEnd) => state.updateData(deviceId + tenantId, data)

      case PersistedConnection(config) =>
        instantiateMqttConnector(config.deviceId, config, ctx)
        state

      case DeletedMqttConnection(deviceId, tenantId) =>
        if streams.contains(deviceId + "|" + tenantId) then streams(deviceId + "|" + tenantId).terminate()
        state

      case CommandSentToDevice(deviceId, message) =>
        if streams.contains(deviceId) then streams(deviceId).publish(message)
        state
  }

  private def instantiateMqttConnector(deviceId: String, config: MQTT, ctx: ActorContext[Command]): Unit = {
    if (!streams.contains(deviceId)) {
      ctx.log.info(s"Instantiating MQTT connection for device: $deviceId")
      implicit val system: ActorSystem[_] = ctx.system
      implicit val ec: ExecutionContext = ctx.executionContext
      implicit val mat: Materializer = SystemMaterializer(system).materializer

      val conn = MQTTConnector(config, ctx.self)
      conn.subscribe()
      streams.put(config.deviceId + "|" + config.tenantId, conn)
    }
  }

  private def getState(deviceId: String, state: State, replyTo: ActorRef[Response]): Effect[Event, State] = {
    Effect.none.thenReply(replyTo)(_ => if state.data.contains(deviceId) then SuccessEvent(state.data(deviceId)) else FailureEvent("no such device"))

  }

  private def commandToDevice(deviceId: String, message: String, replyTo: ActorRef[Response]): Effect[Event, State] = {
    Effect.persist(CommandSentToDevice(deviceId, message)).thenReply(replyTo)(_ => SuccessEvent("ok"))
  }

  private def persistData(deviceId: String, tenantId: String, deviceName: String, data: String, info: String, timestampStart: Long, replyTo: ActorRef[Ack]): Effect[Event, State] = {
    Effect.persist(RecordProcessed(deviceId, tenantId, deviceName, data, info, timestampStart, System.nanoTime())).thenReply(replyTo)(_ => Ack)
  }

  private def initStream(replyTo: ActorRef[Ack]): Effect[Event, State] = {
    replyTo ! Ack
    Effect.none
  }

  private def streamComplete()(implicit ctx: ActorContext[Command]): Effect[Event, State] = {
    ctx.log.info("MQTT session has been terminated!")
    Effect.none
  }

  private def streamFailed(throwable: Throwable)(implicit ctx: ActorContext[Command]): Effect[Event, State] = {
    ctx.log.info(s"Received Fail Message from MQTTStream:[$throwable]", throwable)
    Effect.none
  }


  sealed trait Command extends CborSerializable

  case class InstantiateMqttConnector(config: MQTT, replyTo: ActorRef[Response]) extends Command

  final case class ProcessRecord(deviceId: String, tenantId: String, deviceName: String, data: String, info: String, timestampStart: Long, replyTo: ActorRef[Ack]) extends Command

  case class Init(ackTo: ActorRef[Ack]) extends Command

  case class Fail(ex: Throwable) extends Command

  case object Complete extends Command

  case class DeleteMqttConnector(deviceId: String, replyTo: ActorRef[Response]) extends Command

  case class SendCommandToDevice(deviceId: String, message: String, replyTo: ActorRef[Response]) extends Command

  case class GetState(deviceId: String, replyTo: ActorRef[Response]) extends Command

  trait Response extends CborSerializable

  case class SuccessEvent(response: String) extends Response

  case class FailureEvent(response: String) extends Response


  trait Ack

  trait Event extends CborSerializable

  object Ack extends Ack with Event

  case class RecordProcessed(deviceId: String, tenantId: String, deviceName: String, data: String, info: String, timestampStart: Long, timestampEnd: Long) extends Event

  private case class PersistedConnection(config: MQTT) extends Event

  private case class DeletedMqttConnection(deviceId: String, tenantId: String) extends Event

  private case class CommandSentToDevice(deviceId: String, message: String) extends Event


  final case class State(data: Map[String, String]) extends CborSerializable {
    def deviceExists(key: String): Boolean = data.contains(key)

    // Update data map by adding or updating a key-value pair
    def updateData(key: String, value: String): State = copy(data = data + (key -> value))

  }

  private object State {
    val empty: State = State(Map.empty)
  }

  val tags = Vector.tabulate(3)(i => s"connection-manager-tag-$i")

  private def calculateTag(tenantId: String, tags: Vector[String] = tags): String = {
    val tagIndex = math.abs(tenantId.hashCode % tags.size)
    tags(tagIndex)
  }

}
