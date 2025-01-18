package core.services

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.util.Timeout
import core.services.connectors.{ConfigurationEntity, ConnectionManagerEntity, GrpcConfig, MqttConfig}
import grpc.entity.DeviceProvisioning.{DeviceProvisioningService, ID, MQTT, MQTTConfigResponse, Response, gRPC, gRPCConfigResponse}

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}



class IoTProvisioningAPI(implicit shardRegion: ClusterSharding) extends DeviceProvisioningService {

  
  import concurrent.duration.DurationInt
  
  implicit val timeout: Timeout = 5.seconds // timeout after 2 seconds with no response
  implicit val executionContext: ExecutionContext = ExecutionContext.global // adapt threading model -> work stealing thread model is used by default

  shardRegion.init(Entity(ConnectionManagerEntity.TypeKey)(eCtx => ConnectionManagerEntity(eCtx.entityId)))
  shardRegion.init(Entity(ConfigurationEntity.TypeKey)(eCtx => ConfigurationEntity(eCtx.entityId)))


  override def addBrokerConfig(in: MQTT): Future[Response] = {

    val connectionEntity = shardRegion.entityRefFor(ConnectionManagerEntity.TypeKey, in.tenantId)
    val configEntity = shardRegion.entityRefFor(ConfigurationEntity.TypeKey, in.tenantId)


    def utilitySetConfig(in: MQTT)(replyTo: ActorRef[ConfigurationEntity.Response]) =
      ConfigurationEntity.SetConfig(in.deviceId, MqttConfig(in), replyTo)

    def utilitySetConnector(in: MQTT)(replyTo: ActorRef[ConnectionManagerEntity.Response]) =
      ConnectionManagerEntity.InstantiateMqttConnector(MqttConfig(in), replyTo)

    // Ensure that the entities for the given tenantId are available before proceeding
    configEntity.ask(utilitySetConfig(in)).flatMap {
      case ConfigurationEntity.SuccessEvent(_) => connectionEntity.ask(utilitySetConnector(in)).mapTo[ConnectionManagerEntity.Response].map(_ => Response("ok", in.toProtoString))
      case ConfigurationEntity.FailureEvent(r) => Future { Response("error", r) }
    }
  }

  override def getBrokerConfig(in: ID): Future[MQTTConfigResponse] = {

    val configEntity = shardRegion.entityRefFor(ConfigurationEntity.TypeKey, in.tenantId)

    def utilityGetConfig(in: ID)(replyTo: ActorRef[ConfigurationEntity.Response]) =
      ConfigurationEntity.GetConfig(in.deviceId, replyTo)

    configEntity.ask(utilityGetConfig(in)).mapTo[ConfigurationEntity.Response].map {
      case ConfigurationEntity.MqttConfigurationResponse(r) => MQTTConfigResponse("ok", Option(r))
    }
  }

  override def removeBrokerConfig(in: ID): Future[Response] = {
    
    val configEntity = shardRegion.entityRefFor(ConfigurationEntity.TypeKey, in.tenantId)
    val connectionEntity = shardRegion.entityRefFor(ConnectionManagerEntity.TypeKey, in.tenantId)

    def utilityRemoveConfig(in: ID)(replyTo: ActorRef[ConfigurationEntity.Response]) =
      ConfigurationEntity.RemoveConfig(in.deviceId, replyTo)

    def utilityDeleteConnection(in: ID)(replyTo: ActorRef[ConnectionManagerEntity.Response]) =
      ConnectionManagerEntity.DeleteMqttConnector(in.deviceId, replyTo)

    configEntity.ask(utilityRemoveConfig(in)).flatMap {
      case ConfigurationEntity.SuccessEvent(response) => connectionEntity.ask(utilityDeleteConnection(in)).mapTo[ConnectionManagerEntity.Response].map(response => Response(response.toString, in.toProtoString))
    }
  }


  override def addGRPCConfig(in: gRPC): Future[Response] = {
    // TODO: ensure one does not exist without the other
    val configEntity = shardRegion.entityRefFor(ConfigurationEntity.TypeKey, in.tenantId)
    val connectionEntity = shardRegion.entityRefFor(ConnectionManagerEntity.TypeKey, in.tenantId)


    def utilityAddGrpcStream(in: gRPC)(replyTo: ActorRef[ConfigurationEntity.Response]) =
      ConfigurationEntity.SetConfig(in.deviceId, GrpcConfig(in), replyTo)

    configEntity.ask(utilityAddGrpcStream(in)).mapTo[ConfigurationEntity.Response].map(e => Response("ok", e.toString))
  }
  
  override def removeGRPCConfig(in: ID): Future[Response] = ???


  override def getGrpcConfig(in: ID): Future[gRPCConfigResponse] = {
    // TODO: Error handling when config does not exist
    val configEntity = shardRegion.entityRefFor(ConfigurationEntity.TypeKey, in.tenantId)

    def utilityGetConfig(in: ID)(replyTo: ActorRef[ConfigurationEntity.Response]) =
      ConfigurationEntity.GetConfig(in.deviceId, replyTo)

    configEntity.ask(utilityGetConfig(in)).mapTo[ConfigurationEntity.Response].map {
      case ConfigurationEntity.gRPCConfigurationResponse(r) => gRPCConfigResponse("ok", Some(r))
    }
  }
}
