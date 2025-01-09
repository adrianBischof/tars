package core.services

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.util.Timeout
import core.services.connectors.ConnectionManagerEntity.SuccessEvent
import core.services.connectors.{ConfigurationEntity, ConnectionManagerEntity}
import grpc.entity.DeviceProvisioning.{CommandRequest, CommandResponse, CommandService}

import scala.concurrent.{ExecutionContext, Future}

class CommandAPI(implicit shardRegion: ClusterSharding) extends CommandService {

  import concurrent.duration.DurationInt

  implicit val timeout: Timeout = 5.seconds // timeout after 2 seconds with no response
  implicit val executionContext: ExecutionContext = ExecutionContext.global // adapt threading model -> work stealing thread model is used by default

  shardRegion.init(Entity(ConnectionManagerEntity.TypeKey)(eCtx => ConnectionManagerEntity(eCtx.entityId)))
  shardRegion.init(Entity(ConfigurationEntity.TypeKey)(eCtx => ConfigurationEntity(eCtx.entityId)))

  override def sendCommandToDevice(in: CommandRequest): Future[CommandResponse] = {
    val connectionEntity = shardRegion.entityRefFor(ConnectionManagerEntity.TypeKey, in.tenantId)
    val configEntity = shardRegion.entityRefFor(ConfigurationEntity.TypeKey, in.tenantId)

    def utilityGetConfig(in: CommandRequest)(replyTo: ActorRef[ConfigurationEntity.Response]) =
      ConfigurationEntity.GetConfig(in.deviceId, replyTo)

    def utilitySendCommand(in: CommandRequest)(replyTo: ActorRef[ConnectionManagerEntity.Response]) = 
      ConnectionManagerEntity.SendCommandToDevice(in.deviceId, in.command, replyTo)

    configEntity.ask(utilityGetConfig(in)).flatMap {
      case ConfigurationEntity.MqttConfigurationResponse(r) => connectionEntity.ask(utilitySendCommand(in)).mapTo[ConnectionManagerEntity.Response].map {
        case SuccessEvent(response) => CommandResponse(r.toProtoString, response)
      }
    }
  }
}
