package server

import akka.actor.typed.ActorRef
import aggregate.SourceAggregate
import aggregate.SourceAggregate.Ack
import akka.util.Timeout

import scala.concurrent.duration.*
import grpc.entity.SourceProto.{AggregateState, Data, DummyConfig, GenericResponse, MQTTConfig, SourceAggregateId, SourceAggregateService, SourceId}

import scala.concurrent.{ExecutionContext, Future}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import utility.ProtobufWrapper


class SourceAggregateSharding(implicit sharding: ClusterSharding) extends SourceAggregateService {

  implicit val timeout: Timeout = 2.seconds // timeout after 2 seconds with no response
  implicit val executionContext: ExecutionContext = ExecutionContext.global // adapt threading model -> work stealing thread model is used by default


  sharding.init(Entity(SourceAggregate.TypeKey)(eCtx => SourceAggregate(eCtx.entityId))) // Init shard entity

  override def addMQTTSource(in: MQTTConfig): Future[GenericResponse] = {
    val shard = sharding.entityRefFor(SourceAggregate.TypeKey, in.aggregateId)

    def utilityAddMQTTSource(in: MQTTConfig)(replyTo: ActorRef[GenericResponse]) = SourceAggregate.AddMQTTSource(ProtobufWrapper.toClass(in).asInstanceOf[utility.MQTTConfig], replyTo)

    shard.ask(utilityAddMQTTSource(in))

  }

  override def removeSource(in: SourceId): Future[GenericResponse] = {
    val shard = sharding.entityRefFor(SourceAggregate.TypeKey, in.aggregateId)
    def utilityRemoveSource(in: SourceId)(replyTo: ActorRef[GenericResponse]) = SourceAggregate.RemoveSource(in.id, replyTo)
    
    shard.ask(utilityRemoveSource(in))
  }

  override def showSourceAggregateState(in: SourceAggregateId): Future[AggregateState] = {
    val shard = sharding.entityRefFor(SourceAggregate.TypeKey, in.id)
    
    def utilityShowSourceAggregateState(in: SourceAggregateId)(replyTo: ActorRef[AggregateState]) = SourceAggregate.ShowSourceAggregateState(replyTo)
    shard.ask(utilityShowSourceAggregateState(in))
  }

  override def showSourceAggregateSources(in: SourceAggregateId): Future[GenericResponse] = {
    val shard = sharding.entityRefFor(SourceAggregate.TypeKey, in.id)

    def utilityShowSourceAggregateSources(in: SourceAggregateId)(replyTo: ActorRef[GenericResponse]) = SourceAggregate.ShowSourceAggregateSources(replyTo)
    shard.ask(utilityShowSourceAggregateSources(in))
  }

  override def passivate(in: SourceAggregateId): Future[GenericResponse] = {
    val shard = sharding.entityRefFor(SourceAggregate.TypeKey, in.id)

    def utilityPassivate(in: SourceAggregateId)(replyTo: ActorRef[GenericResponse]) = SourceAggregate.Passivate(replyTo)
    shard.ask(utilityPassivate(in))
  }

  override def addDummySource(in: DummyConfig): Future[GenericResponse] = {
    val shard = sharding.entityRefFor(SourceAggregate.TypeKey, in.aggregateId)
    
    def utilityAddDummySource(in: DummyConfig)(replyTo: ActorRef[GenericResponse]) = SourceAggregate.AddDummySource(ProtobufWrapper.toClass(in).asInstanceOf[utility.DummyConfig], replyTo)
    
    shard.ask(utilityAddDummySource(in))
  }

  override def processData(in: Data): Future[GenericResponse] = {
    val shard = sharding.entityRefFor(SourceAggregate.TypeKey, in.aggregateId)
    
    def utilityProcessData(in: Data)(replyTo: ActorRef[Ack]) = SourceAggregate.ProcessData(in.sourceId, in.payload, replyTo)
    
    shard.ask(utilityProcessData(in)).map {
      case Ack => GenericResponse("Updated")
      case _ => GenericResponse("Failure")
    }
    //TODO: Map Ack to GenericResponse

  }
}
