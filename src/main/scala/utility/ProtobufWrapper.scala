package utility

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeId, JsonTypeInfo}


object ProtobufWrapper {
  
  // https://doc.akka.io/docs/akka/current/serialization-jackson.html

  def toClass(proto: Any): ISourceConfig = {
    proto match {
      case p: grpc.entity.SourceProto.MQTTConfig => MQTTConfig(p.sourceId, p.aggregateId, p.name, p.location, p.broker, p.cleanSession, p.autoReconnect, p.topics, p.ref, p.backpressure)
      case p: grpc.entity.SourceProto.DummyConfig => DummyConfig(p.sourceId, p.aggregateId, p.start, p.end)
    }
  }

}
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[MQTTConfig], name="mqtt-config"),
    new JsonSubTypes.Type(value = classOf[DummyConfig], name = "dummy-config")
  )
)
trait ISourceConfig

final case class MQTTConfig(sourceId: String,
                            aggregateId: String,
                            name: String,
                            location: String,
                            broker: String,
                            cleanSession: Boolean,
                            autoReconnect: Boolean,
                            topics: Seq[String],
                            ref: String,
                            backpressure: Int
                           ) extends ISourceConfig

final case class DummyConfig(sourceId: String,
                             aggregateId: String,
                             start: Int,
                             end: Int
                            ) extends ISourceConfig
