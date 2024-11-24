package entity

import aggregate.SourceAggregate.Ack

import scala.concurrent.duration.*
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.serialization.jackson.CborSerializable
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}


object PersistentWorker {

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("worker-type-key")

  def apply(uniqueEntityID: String): Behavior[Command] = {
    Behaviors.setup { ctx =>
      given ActorContext[Command] = ctx

      EventSourcedBehavior[Command, Event, State](
        PersistenceId(TypeKey.name, uniqueEntityID),
        State.emptyState,
        commandHandler = (state, command) => commandHandler(uniqueEntityID, state, command),
        eventHandler = (state, event) => eventHandler(state, event)
      )
        // TODO: Add .withTagger
        .onPersistFailure(
          SupervisorStrategy.restartWithBackoff(
            minBackoff = 10.seconds,
            maxBackoff = 60.seconds,
            randomFactor = 0.1
          )
        )
    }
  }

  private def commandHandler(uniqueEntityID: String, state: State, command: Command)(using ActorContext[Command]): Effect[Event, State] = {
    ???
  }

  private def eventHandler(state: State, event: Event): State = {
    ???
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
  

  trait Command

  final case class RegisterMQTT() extends Command with CborSerializable

  final case class RegisterGRPC() extends Command with CborSerializable

  final case class Remove() extends Command with CborSerializable

  final case class Status() extends Command with CborSerializable

  final case class Consume(adapterId: String, payload: String, replyTo: ActorRef[Ack]) extends Command with CborSerializable

  case class Init(ackTo: ActorRef[Ack]) extends Command

  case object Complete extends Command

  case class Fail(ex: Throwable) extends Command

  trait Ack

  object Ack extends Ack

  trait Event

  final case class State(state: Map[String, String]) {

  }

  private object State {
    val emptyState: State = State(Map.empty)
  }

}
