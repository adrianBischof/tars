package core.services.connectors

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo, JsonTypeName}
import akka.serialization.jackson.JsonSerializable
import grpc.entity.DeviceProvisioning.{MQTT, gRPC}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(Array(
  new JsonSubTypes.Type(value = classOf[GrpcConfig], name = "gRPCConfig"),
  new JsonSubTypes.Type(value = classOf[MqttConfig], name = "MQTTConfig"),
))
sealed trait ConfigurationValue extends JsonSerializable

@JsonTypeName("gRPCConfig")
case class GrpcConfig(value: gRPC) extends ConfigurationValue

@JsonTypeName("MQTTConfig")
case class MqttConfig(value: MQTT) extends ConfigurationValue


