package core.services.connectors.grpcs

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.Materializer
import core.services.connectors.{Connectable, ConnectionManagerEntity}
import grpc.entity.DeviceProvisioning.gRPC

import scala.concurrent.ExecutionContext

class gRPCConnector(conf: gRPC,connectionManagerRef: ActorRef[ConnectionManagerEntity.Command])(implicit system: ActorSystem[_], ec: ExecutionContext, mat: Materializer) extends Connectable {

  override def subscribe(): Unit = ???

  override def terminate(): Unit = ???

  override def publish(command: String): Unit = ???
}
