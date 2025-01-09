
/*
package core.services.connectors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import akka.persistence.typed.PersistenceId
import akka.stream.{Materializer, SystemMaterializer}
import core.serializer.CborSerializable
import core.services.connectors.mqtt.MQTTConnector

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt



object ConnectionManagerEntity1 {


  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("connection-manager-type-key")
  private var connectionRef: collection.immutable.Map[String, MQTTConnector] = Map.empty

  def apply(tenantId: String): Behavior[Command] = {
    Behaviors.withTimers { timers =>
      Behaviors.setup { ctx =>
        timers.startTimerWithFixedDelay(Tick, 5.seconds) // Sends every 5 seconds a message to self to refresh state
        val adapter: ActorRef[ConfigurationEntity.Response] = ctx.messageAdapter(response => RefreshConfig(response))

        EventSourcedBehavior[Command, Event, Record](
          PersistenceId(TypeKey.name, tenantId),
          Record.empty,
          commandHandler = (state, command) => commandHandler(adapter, tenantId, ctx, state, command),
          eventHandler = (state, event) => eventHandler(state, event)
        )
          .withTagger(_ => Set("connection-manager-tag"))
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
  }

  private def commandHandler(adapter: ActorRef[ConfigurationEntity.Response], uniqueEntityID: String, ctx: ActorContext[Command], state: Record, command: Command): Effect[Event, Record] = {

    implicit val system: ActorSystem[_] = ctx.system
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val mat: Materializer = SystemMaterializer(system).materializer

    val configStore = ClusterSharding(ctx.system).entityRefFor(ConfigurationEntity.TypeKey, "tempor nisi quis") //TODO: config-store needs to have a unique fixed name

    command match
      case Init(ackTo) => initStream(ackTo)
      case Complete => streamComplete()(ctx)
      case Fail(ex) => streamFailed(ex)(ctx)

      case Tick =>
        configStore ! ConfigurationEntity.GetBrokerConfigs(adapter)
        ctx.log.info("refresh myself") // TODO: Remove logging
        Effect.none
      case RefreshConfig(ConfigurationEntity.ConfigsShow(configStore)) =>
        ctx.log.info("1. Config Store: " + configStore.toString)
        ctx.log.info("2. Connection Ref: " + connectionRef.toString)
        val d = mapDelta(configStore.configs, connectionRef)
        ctx.log.info(s"3. Delta: ${d.mkString(", ")}") // More readable logging of delta
        updateConnectionRef(configStore, d)(using ctx)

        ctx.log.info("4. Connection Ref: " + connectionRef.toString)
        Effect.none

      case ProcessRecord(deviceId, deviceName, data, info, timestamp, replyTo) =>
        processData(deviceId, deviceName, data, info, timestamp, replyTo)
        ctx.log.info(deviceId + " " + data)
        Effect.persist(RecordProcessed(deviceId, deviceName, data, info, timestamp))
  }

  private def updateConnectionRef(configStore: ConfigurationEntity.Config, d: Set[String])(implicit ctx: ActorContext[Command]): Unit = {
    //TODO: what happens if the config is updated? there will be not delta but the connection must be terminated and a new one with the dedicated setting must be set.
    implicit val system: ActorSystem[_] = ctx.system
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val mat: Materializer = SystemMaterializer(system).materializer
    connectionRef = connectionRef ++ d.collect {
      // If key is in configStore but not in connectionRef, create and run a new connector
      case k if configStore.configs.contains(k) && !connectionRef.contains(k) =>
        val temp = new MQTTConnector(configStore.configs(k), ctx.self)
        temp.run()
        k -> temp

      // If key is in connectionRef but not in configStore, stop the connector
      case k if !configStore.configs.contains(k) && connectionRef.contains(k) =>
        connectionRef(k).stop() // Stop the existing connector
        k -> null // Mark for removal
    }
    connectionRef = connectionRef.filter(_._2 != null) // Remove null values (stopped connectors)

    // Optional: Log the updated connectionRef
    ctx.log.info(s"Updated connectionRef: ${connectionRef.toString}")
  }


  private def mapDelta(a1: Map[String, Any], a2: Map[String, Any]) = (a1.keySet -- a2.keySet) ++ (a2.keySet -- a1.keySet)

  private def eventHandler(state: Record, event: Event): Record = {
    event match
      case RecordProcessed(device_id, device_name, data, info, timestamp) => state.updateData(device_id, data)
  }

  private def processData(device_id: String, device_name: String, data: String, info: String, timestamp: LocalDateTime, replyTo: ActorRef[Ack]): Effect[Event, Record] = {
    replyTo ! Ack
    Effect.persist(RecordProcessed(device_id, device_name, data, info, timestamp))
  }

  private def initStream(replyTo: ActorRef[Ack]): Effect[Event, Record] = {
    replyTo ! Ack
    Effect.none
  }

  private def streamComplete()(implicit ctx: ActorContext[Command]): Effect[Event, Record] = {
    //TODO: Add proper logging
    ctx.log.info("Mqtt connection terminated!")
    Effect.none
  }

  private def streamFailed(throwable: Throwable)(implicit ctx: ActorContext[Command]): Effect[Event, Record] = {
    //TODO: Add proper logging
    ctx.log.info("Received Fail message", throwable)
    Effect.none
  }

  trait Command

  case object Tick extends Command with CborSerializable

  private case class RefreshConfig(response: ConfigurationEntity.Response) extends Command with CborSerializable

  final case class ProcessRecord(deviceId: String, deviceName: String, data: String, info: String, timestamp: LocalDateTime, replyTo: ActorRef[Ack]) extends Command with CborSerializable // TODO: change variables inside case class

  case class Init(ackTo: ActorRef[Ack]) extends Command with CborSerializable

  case class Fail(ex: Throwable) extends Command with CborSerializable

  case object Complete extends Command with CborSerializable

  trait Ack

  object Ack extends Ack

  trait Event

  case class RecordProcessed(device_id: String, device_name: String, data: String, info: String, timestamp: LocalDateTime) extends Event with CborSerializable

  final case class Record(data: Map[String, String]) {
    def deviceExists(key: String): Boolean = data.contains(key)

    def updateData(key: String, value: String): Record = copy(data = data + (key -> value))

    def removeConfig(key: String): Record = copy(data = data - key)
  }

  private object Record {
    val empty: Record = Record(Map.empty)
  }


}


 */
