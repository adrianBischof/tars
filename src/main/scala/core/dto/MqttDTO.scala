package core.dto

case class MqttDTO(device_id: String, tenant_id: String, name: String, location:String, server:String, cleanSession: Boolean, autoReconnect: Boolean, topics: Seq[String], backpressure: Int)


object MqttDTO {
  
  def toDTO(grpcMqtt: grpc.entity.DeviceProvisioning.MQTT): MqttDTO =
    MqttDTO(
      device_id = grpcMqtt.deviceId,
      tenant_id = grpcMqtt.tenantId,
      name = grpcMqtt.name,
      location = grpcMqtt.location,
      server = grpcMqtt.server,
      cleanSession = grpcMqtt.cleanSession,
      autoReconnect = grpcMqtt.autoReconnect,
      topics = grpcMqtt.topics,
      backpressure = grpcMqtt.backpressure
    )
    
  def toProto(dtoMqtt: MqttDTO): grpc.entity.DeviceProvisioning.MQTT =
    grpc.entity.DeviceProvisioning.MQTT(
      deviceId = dtoMqtt.device_id,
      name = dtoMqtt.name,
      location = dtoMqtt.location,
      server = dtoMqtt.server,
      cleanSession = dtoMqtt.cleanSession,
      autoReconnect = dtoMqtt.autoReconnect,
      topics = dtoMqtt.topics,
      backpressure = dtoMqtt.backpressure,
      tenantId = dtoMqtt.tenant_id,
    )
    
}

