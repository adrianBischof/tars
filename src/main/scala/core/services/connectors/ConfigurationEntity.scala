package core.services.connectors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import core.serializer.CborSerializable
import grpc.entity.DeviceProvisioning.{MQTT, gRPC}

import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

object ConfigurationEntity {

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("config-store-type-key")
  type State = Map[String, ConfigurationValue]

  def apply(tenantId: String): Behavior[Command] = {
    Behaviors.setup { ctx =>
      ctx.log.info(s"Inside: ${TypeKey}" + s" ${tenantId}")
      EventSourcedBehavior[Command, Event, State](
        PersistenceId(TypeKey.name, tenantId),
        Map.empty,
        commandHandler = (state, command) => commandHandler(state, command),
        eventHandler = (state, event) => eventHandler(state, event)
      )
        .withTagger(_ => Set(calculateTag(tenantId, tags)))
        //.withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 5))
        .onPersistFailure(
          SupervisorStrategy.restartWithBackoff(
            minBackoff = 10.seconds,
            maxBackoff = 60.seconds,
            randomFactor = 0.1
          )
        )
    }
  }

  private val commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
    command match {
      case SetConfig(key, value, replyTo) =>
        if state.contains(key) then Effect.none.thenReply(replyTo)(_ => FailureEvent("Could not provision device: device_id already exists!"))
        else Effect.persist(ConfigUpdatedEvent(key, value)).thenReply(replyTo)(_ => SuccessEvent(s"Device $key provisioned!"))

      case RemoveConfig(key, replyTo) =>
        if state.contains(key) then
          Effect.persist(ConfigRemovedEvent(key)).thenReply(replyTo)(_ => SuccessEvent(s"Device `$key` removed!"))
        else Effect.none.thenReply(replyTo)(_ => FailureEvent("Could not delete configuration: device_id does not exists!"))

      case GetConfig(key, replyTo) =>
        if state.contains(key) then Effect.none.thenReply(replyTo)(e => 
          state(key) match
            case MqttConfig(value) => MqttConfigurationResponse(value)
            case GrpcConfig(value) => gRPCConfigurationResponse(value)
        )
        else Effect.none.thenReply(replyTo)(_ => FailureEvent("Could not retrieve configuration for this device_id"))

      case GetAllConfigs(replyTo) => Effect.none.thenReply(replyTo)(_ => ConfigurationResponse(state))
    }
  }

  private val eventHandler: (State, Event) => State = { (state, event) =>
    event match
      case ConfigUpdatedEvent(key, value) => state + (key -> value)
      case ConfigRemovedEvent(key) => state - key
  }

  trait Command
  case class SetConfig(key: String, value: ConfigurationValue, replyTo: ActorRef[Response]) extends Command with CborSerializable
  case class GetConfig(key: String, replyTo: ActorRef[Response]) extends Command with CborSerializable
  case class RemoveConfig(key: String, replyTo: ActorRef[Response]) extends Command with CborSerializable
  case class GetAllConfigs(replyTo: ActorRef[Response]) extends Command with CborSerializable


  trait Event
  private case class ConfigUpdatedEvent(key: String, value: ConfigurationValue) extends Event with CborSerializable
  private case class ConfigRemovedEvent(key: String) extends Event with CborSerializable

  trait Response
  case class SuccessEvent(response: String) extends Response
  case class FailureEvent(response: String) extends Response
  case class ConfigurationResponse(state: State) extends Response
  case class MqttConfigurationResponse(config: MQTT) extends Response
  case class gRPCConfigurationResponse(config: gRPC) extends Response

  val tags = Vector.tabulate(3)(i => s"device-registry-tag-$i")

  private def calculateTag(tenantId: String, tags: Vector[String] = tags): String = {
    val tagIndex = math.abs(tenantId.hashCode % tags.size)
    tags(tagIndex)
  }

}
