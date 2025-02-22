package core.services

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.util.Timeout
import core.services.connectors.ConnectionManagerEntity
import grpc.entity.State.{DeviceID, StateResponse, StateService, StateUpdate}

import scala.concurrent.{ExecutionContext, Future}

class StateAPI(implicit shardRegion: ClusterSharding) extends StateService {


  import concurrent.duration.DurationInt

  implicit val timeout: Timeout = 5.seconds // timeout after 2 seconds with no response
  implicit val executionContext: ExecutionContext = ExecutionContext.global // adapt threading model -> work stealing thread model is used by default

  shardRegion.init(Entity(ConnectionManagerEntity.TypeKey)(eCtx => ConnectionManagerEntity(eCtx.entityId)))

  override def updateState(in: StateUpdate): Future[StateResponse] = {
    val connectionEntity = shardRegion.entityRefFor(ConnectionManagerEntity.TypeKey, in.tenantId)

    def utilityUpdateState(in: StateUpdate)(replyTo: ActorRef[ConnectionManagerEntity.Ack]) =
      ConnectionManagerEntity.ProcessRecord(in.deviceId, in.tenantId, in.deviceName, in.state, "", System.nanoTime(), replyTo)

    connectionEntity.ask(utilityUpdateState(in)).mapTo[ConnectionManagerEntity.Ack].map {
      case ConnectionManagerEntity.Ack => StateResponse("ok", in.state)
    }
  }

  override def getState(in: DeviceID): Future[StateResponse] = {
    val connectionEntity = shardRegion.entityRefFor(ConnectionManagerEntity.TypeKey, in.tenantId)

    def utilityGetState(in: DeviceID)(replyTo: ActorRef[ConnectionManagerEntity.Response]) =
      ConnectionManagerEntity.GetState(in.deviceId+in.tenantId, replyTo)

    connectionEntity.ask(utilityGetState(in)).mapTo[ConnectionManagerEntity.Response].map {
      case ConnectionManagerEntity.SuccessEvent(data) => StateResponse("ok", data)
      case ConnectionManagerEntity.FailureEvent(data) => StateResponse("failure", data)
    }
  }
}
