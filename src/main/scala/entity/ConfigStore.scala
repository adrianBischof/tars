package entity

import akka.actor.typed.{Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}

import concurrent.duration.DurationInt

object ConfigStore {

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("config-type-key")

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


  trait Command
  trait Event


  final case class State(state: Map[String, String]) {

  }

  private object State {
    val emptyState: State = State(Map.empty)
  }

}
