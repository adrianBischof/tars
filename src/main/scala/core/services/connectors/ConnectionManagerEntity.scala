package core.services.connectors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import akka.stream.{Materializer, SystemMaterializer}
import core.serializer.CborSerializable
import core.services.connectors.mqtt.MQTTConnector

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt


object ConnectionManagerEntity {

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("connection-manager-type-key")

  def apply(tenantId: String): Behavior[Command] = {
    Behaviors.setup { ctx =>
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
    }
  }

  private def commandHandler(tenantId: String, state: State, command: Command, connections: Map[String, Connectable], ctx: ActorContext[Command]): Effect[Event, State] = {
    command match
      case Init(ackTo) => initStream(ackTo)
      case Complete => streamComplete()(ctx)
      case Fail(ex) => streamFailed(ex)(ctx)
      case ProcessRecord(deviceId, tenantId, deviceName, data, info, timestampStart, replyTo) =>
        //ctx.log.info(s"Got sensor_reading from $tenantId, $deviceId with $data and Timestamp Start: $timestampStart")
        persistData(deviceId, tenantId, deviceName, data, info, timestampStart, replyTo)
      case InstantiateMqttConnector(config, replyTo) =>
        //instantiateMqttConnector(config, ctx)
        Effect.persist(PersistedConnection(config)).thenReply(replyTo)(_ => SuccessEvent("Created Connector"))
      case DeleteMqttConnector(deviceId, replyTo) =>
        Effect.persist(DeletedMqttConnection(deviceId)).thenReply(replyTo)(_ => SuccessEvent("Deleted Connector"))
      case SendCommandToDevice(deviceId, message, replyTo) => commandToDevice(deviceId, message, replyTo)
  }

  private def eventHandler(state: State, event: Event, ctx: ActorContext[Command]): State = {
    event match
      case RecordProcessed(deviceId, tenantId, device_name, data, info, timestampStart, timestampEnd) => state.updateData(deviceId + tenantId, data)

      case PersistedConnection(config) =>
        implicit val system: ActorSystem[_] = ctx.system
        implicit val ec: ExecutionContext = ctx.executionContext
        implicit val mat: Materializer = SystemMaterializer(system).materializer
        
        val conn = MQTTConnector(config.value, ctx.self)
        conn.subscribe()
        state.addConnection(config.value.deviceId, conn)

      case DeletedMqttConnection(deviceId) =>
        state.deleteConnection(deviceId)

      case CommandSentToDevice(deviceId, message) => 
        if state.streams.contains(deviceId) then state.streams(deviceId).publish(message)
        state
        
  }
  
  private def commandToDevice(deviceId: String, message: String, replyTo: ActorRef[Response]): Effect[Event, State] = {
    Effect.persist(CommandSentToDevice(deviceId, message)).thenReply(replyTo)(_ => SuccessEvent("ok"))
  }

  private def persistData(deviceId: String, tenantId: String, deviceName: String, data: String, info: String, timestampStart: Long, replyTo: ActorRef[Ack]): Effect[Event, State] = {
    //Effect.persist(RecordProcessed(deviceId, tenantId, deviceName, data, info, timestampStart, System.nanoTime())).thenReply(replyTo)(_ => Ack)
    Effect.none.thenReply(replyTo)(_ => Ack)
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

  private def instantiateMqttConnector(config: MqttConfig, ctx: ActorContext[Command]): Unit = {
    implicit val system: ActorSystem[_] = ctx.system
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val mat: Materializer = SystemMaterializer(system).materializer

    val conn = MQTTConnector(config.value, ctx.self)
    
    conn.terminate()
  }


  sealed trait Command extends CborSerializable
  case class InstantiateMqttConnector(config: MqttConfig, replyTo: ActorRef[Response]) extends Command
  final case class ProcessRecord(deviceId: String, tenantId: String, deviceName: String, data: String, info: String, timestampStart: Long, replyTo: ActorRef[Ack]) extends Command
  case class Init(ackTo: ActorRef[Ack]) extends Command
  case class Fail(ex: Throwable) extends Command
  case object Complete extends Command
  case class DeleteMqttConnector(deviceId: String, replyTo: ActorRef[Response]) extends Command
  case class SendCommandToDevice(deviceId: String, message: String, replyTo: ActorRef[Response]) extends Command


  trait Response
  case class SuccessEvent(response: String) extends Response
  case class FailureEvent(response: String) extends Response

  trait Ack
  object Ack extends Ack

  trait Event extends CborSerializable
  case class RecordProcessed(deviceId: String, tenantId: String, deviceName: String, data: String, info: String, timestampStart: Long, timestampEnd: Long) extends Event
  private case class PersistedConnection(config: MqttConfig) extends Event
  private case class DeletedMqttConnection(deviceId: String) extends Event
  private case class CommandSentToDevice(deviceId: String, message: String) extends Event
  

  final case class State(data: Map[String, String], streams: Map[String, Connectable]) extends CborSerializable {

    // Check if device exists in the data map
    def deviceExists(key: String): Boolean = data.contains(key)

    // Update data map by adding or updating a key-value pair
    def updateData(key: String, value: String): State = copy(data = data + (key -> value))
    
    def addConnection(key: String, connection: Connectable): State = copy(streams = streams + (key -> connection) )

    def deleteConnection(key: String): State =
      streams(key).terminate()
      copy(streams = streams.removed(key))
  }

  private object State {
    val empty: State = State(Map.empty, Map.empty)
  }

  val tags = Vector.tabulate(3)(i => s"connection-manager-tag-$i")

  private def calculateTag(tenantId: String, tags: Vector[String] = tags): String = {
    val tagIndex = math.abs(tenantId.hashCode % tags.size)
    tags(tagIndex)
  }

}
