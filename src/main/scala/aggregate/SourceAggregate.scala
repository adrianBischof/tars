package aggregate

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import akka.stream.{Materializer, SystemMaterializer}
import factory.source.from.ISourceConnector
import factory.source.from.dummy.{DummySource, DummySourceFactory}
import factory.source.from.mqtt.{MQTTSource, MQTTSourceFactory}
import grpc.entity.SourceProto.{AggregateState, GenericResponse}
import serializer.CborSerializable
import utility.{DummyConfig, MQTTConfig, ISourceConfig}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

// Persistent Actor
object SourceAggregate {

  // https://doc.akka.io/docs/akka/current/typed/persistence.html -> handle commands based on state

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("sensor-type-key") // TODO: fix tag
  private var streamObjects: Map[String, ISourceConnector] = Map.empty

  def apply(entityID: String): Behavior[Command] = {
    Behaviors.setup { ctx =>
      EventSourcedBehavior[Command, Event, State](
        PersistenceId(TypeKey.name, entityID),
        State.emptyState,
        commandHandler = (state, command) => commandHandler(entityID, state, command)(ctx),
        eventHandler = (state, event) => eventHandler(state, event)
      )
        .withTagger(_ => Set(calculateTag(entityID, tags)))
        .withRetention(
          RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2)
        )
        .onPersistFailure(
          SupervisorStrategy.restartWithBackoff(
            minBackoff = 10.seconds,
            maxBackoff = 60.seconds,
            randomFactor = 0.1)
        )
        .receiveSignal {
          case (state, RecoveryCompleted) =>

            // TODO: Add logging
            // TODO: improve implicit call of ActorSystem, ExecutionContext & Materialize

            implicit val system: ActorSystem[_] = ctx.system
            implicit val ec: ExecutionContext = ctx.executionContext
            implicit val mat: Materializer = SystemMaterializer(system).materializer

            state.srcConfig.foreach { case (id, config) =>
              println("Replaying:" + config)
              config match {
                case mqtt: utility.MQTTConfig =>
                  val mqtt = MQTTSourceFactory.newSource(config.asInstanceOf[utility.MQTTConfig], ctx.self)
                  streamObjects = streamObjects ++ Map(id -> mqtt)
                  mqtt.run()
                case dummy: utility.DummyConfig =>
                  val dummy = DummySourceFactory.newSource(config.asInstanceOf[utility.DummyConfig], ctx.self)
                  streamObjects = streamObjects ++ Map(id -> dummy)
                  dummy.run()
              }
              println(streamObjects)
            }
        }
    }
  }


  private def initStream(replyTo: ActorRef[Ack]): Effect[Event, State] = {
    replyTo ! Ack
    Effect.none
  }
  private def streamComplete()(implicit ctx: ActorContext[Command]):  Effect[Event, State] = {
    //TODO: Add proper logging
    ctx.log.info("source done")
    Effect.none
  }
  private def streamFailed(throwable: Throwable)(implicit ctx: ActorContext[Command]):  Effect[Event, State] = {
    //TODO: Add proper logging
    ctx.log.info("Received Fail message", throwable)
    Effect.none
  }
  private def addMQTTSource(state: State, source: MQTTConfig, replyTo: ActorRef[GenericResponse])(implicit ctx: ActorContext[Command]): Effect[Event, State] = {

    implicit val system: ActorSystem[_] = ctx.system
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val mat: Materializer = SystemMaterializer(system).materializer

    val sourceId = source.sourceId

    if !state.configExists(sourceId) then {
      val mqtt = MQTTSourceFactory.newSource(source, ctx.self) // TODO: Carefully: when NothingSource is returned this returns a NotImplementedError!
      streamObjects = streamObjects ++ Map(sourceId -> mqtt)
      mqtt.run()
      println(streamObjects)
      Effect.persist(SourceAdded(sourceId, source)).thenReply(replyTo)(_ => GenericResponse(s"Successful - source: ${source.name} was created"))
    } else {
      Effect.none.thenReply(replyTo)(_ => GenericResponse(s"Source with this ID already exists: ${sourceId}"))
    }
  }
  private def sourceAdded(state: State, event: Event, sourceId: String, source: ISourceConfig): State = {
    state.updateConfig(Map(sourceId -> source))
  }
  private def addDummySource(state: State, source: DummyConfig, replyTo: ActorRef[GenericResponse])(implicit ctx: ActorContext[Command]): Effect[Event, State] = {

    implicit val system: ActorSystem[_] = ctx.system
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val mat: Materializer = SystemMaterializer(system).materializer
  
    val sourceId = source.sourceId

    if !state.configExists(sourceId) then {
      val dummy = DummySourceFactory.newSource(source, ctx.self)
      streamObjects = streamObjects ++ Map(sourceId -> dummy)
      dummy.run()
      println(streamObjects)
      Effect.persist(SourceAdded(sourceId, source)).thenReply(replyTo)(_ => GenericResponse(s"Successful - source: ${sourceId} was created"))
    } else {
      Effect.none.thenReply(replyTo)(_ => GenericResponse(s"Source with this ID already exists: ${sourceId}"))
    }

  }
  private def removeSource(state: State, sourceId: String, replyTo: ActorRef[GenericResponse]): Effect[Event, State] = {
    println("SourceId: " + sourceId)
    streamObjects(sourceId).stop()
    streamObjects = streamObjects - sourceId
    println(streamObjects)
    Effect.persist(RemovedSource(sourceId)).thenReply(replyTo)(_ => GenericResponse(s"Removed source: $sourceId")) // TODO: Maybe remove MQTTStream object within a .thenRun
  }
  private def showSourceAggregateSources(state: State, replyTo: ActorRef[GenericResponse]): Effect[Event, State] = {
    // TODO: This output does not make much sense so far
    println("In method: showSourceAggregateSources: " + state.state)
    Effect.none.thenReply(replyTo)(_ => GenericResponse(streamObjects.keys.toString()))
  }
  private def showSourceAggregateState(state: State, replyTo: ActorRef[AggregateState]): Effect[Event, State] = {
    Effect.none.thenReply(replyTo)(_ => AggregateState(state.state.toSeq.map { case (key, value) => s"$key: $value"}))
  }
  private def processData(sourceId: String, aggregateId: String, data: String, replyTo: ActorRef[Ack]): Effect[Event, State] = {
    replyTo ! Ack
    Effect.persist(DataProcessed(sourceId, aggregateId, data))
  }
  private def passivateEntity(entityId: String, replyTo: ActorRef[GenericResponse]): Effect[Event, State] = {
    Effect.stop().thenReply(replyTo)(_ => GenericResponse(s"Passivate this SourceAggregate-$entityId"))
  }
  private def commandHandler(entityId: String, state: State, command: Command)(implicit ctx: ActorContext[Command]): Effect[Event, State] = {

    command match {

      case Init(replyTo) =>
        initStream(replyTo)
      case Complete =>
        streamComplete()
      case Fail(ex) =>
        streamFailed(ex)
      case AddMQTTSource(source, replyTo) =>
        addMQTTSource(state, source, replyTo)
      case AddDummySource(source, replyTo) =>
        addDummySource(state, source, replyTo)
      case RemoveSource(sourceId, replyTo) =>
        removeSource(state, sourceId, replyTo)
      case ShowSourceAggregateSources(replyTo) =>
        showSourceAggregateSources(state, replyTo)
      case ShowSourceAggregateState(replyTo) =>
        showSourceAggregateState(state, replyTo)
      case ProcessData(sourceId, data, replyTo) =>
        processData(sourceId, entityId, data, replyTo)
      case Passivate(replyTo) =>
        passivateEntity(entityId, replyTo)
    }
  }

  private def sourceAdded(state: State, sourceId: String, sourceConfig: ISourceConfig): State = {
    state.updateConfig(Map(sourceId -> sourceConfig))
  }
  private def deleteSource(state: State, sourceId: String): State = {
    state.deleteSrcConfig(sourceId)
  }
  private def dataProcessed(state: State, sourceId: String, aggregateId: String, data: String): State = {
    println(s"Data Processed $data")
    state.updateState(Map(sourceId -> data))
  }
  private def eventHandler(state: State, event: Event): State = {

    event match {
      case SourceAdded(sourceId, sourceConfig) =>
        sourceAdded(state, sourceId, sourceConfig)
      case RemovedSource(sourceId) =>
        deleteSource(state, sourceId)
      case DataProcessed(sourceId, aggregateId, data) =>
        dataProcessed(state, sourceId, aggregateId, data)
    }
  }

  val tags: Seq[String] = Vector.tabulate(3)(i => s"sensor-tag-$i") //TODO: fix tag

  private def calculateTag(entityId: String, tags: Seq[String] = tags): String = {
    val tagIndex = math.abs(entityId.hashCode & tags.size)
    tags(tagIndex)
  }


  trait Command

  final case class AddMQTTSource(source: MQTTConfig, replyTo: ActorRef[GenericResponse]) extends Command with CborSerializable // Add MQTT Source

  final case class RemoveSource(sourceId: String, replyTo: ActorRef[GenericResponse]) extends Command with CborSerializable // Remove MQTT Source

  final case class AddDummySource(source: DummyConfig, replyTo: ActorRef[GenericResponse]) extends Command with CborSerializable

  final case class ShowSourceAggregateState(replyTo: ActorRef[AggregateState]) extends Command with CborSerializable // Show State of Aggregate

  final case class ShowSourceAggregateSources(replyTo: ActorRef[GenericResponse]) extends Command with CborSerializable // Show data sources of Aggregate


  final case class Passivate(replyTo: ActorRef[GenericResponse]) extends Command with CborSerializable


  final case class ProcessData(sourceId: String, data: String, replyTo: ActorRef[Ack]) extends Command with CborSerializable // TODO: change variables inside case class

  case class Init(ackTo: ActorRef[Ack]) extends Command

  case object Complete extends Command

  case class Fail(ex: Throwable) extends Command

  trait Ack

  object Ack extends Ack

  trait Event

  private final case class SourceAdded(sourceId: String, sourceConfig: ISourceConfig) extends Event with CborSerializable

  final case class DataProcessed(sourceId: String, aggregate_id: String, data: String) extends Event with CborSerializable

  private final case class RemovedSource(sourceId: String) extends Event with CborSerializable


  final case class State(state: Map[String, String], srcConfig: Map[String, ISourceConfig]) extends CborSerializable {

    def configExists(key: String): Boolean = {
      srcConfig.contains(key)
    }

    def updateState(newState: Map[String, String]): State = {
      copy(state = state ++ newState, srcConfig = srcConfig)
    }

    def updateConfig(newSrcConfig: Map[String, ISourceConfig]): State = {
      copy(state = state, srcConfig = srcConfig ++ newSrcConfig)
    }

    def deleteSrcConfig(key: String): State = {
      copy(state, srcConfig = srcConfig - key)
    }
  }

  private object State {
    val emptyState: State = State(Map.empty, Map.empty)
  }

}
