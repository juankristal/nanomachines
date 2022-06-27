package com.example

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, LoggerOps}
import akka.actor.typed._

object Table {
  def apply(tableId: String): Behavior[Command] =
    Behaviors.setup(context => new Table(context, tableId))

  sealed trait Command
  trait ClientCommand

  final case class GetClients(requestId: Long, replyTo: ActorRef[ClientCommand]) extends Command
  final case class RespondClients(requestId: Long, value: List[String]) extends ClientCommand

  final case class LocateClient(requestId: Long,
                                client: String,
                                replyTo: ActorRef[Reception.ClientCommand],
                                replyToReception: ActorRef[Reception.Command])
    extends Command
  final case class ClientNotFound(requestId: Long) extends Reception.ClientCommand

  final case class StandClient(requestId: Long, value: String, replyTo: ActorRef[Reception.ClientCommand])
    extends Command
  final case class ClientStood(requestId: Long) extends Reception.ClientCommand
}

class Table(context: ActorContext[Table.Command], tableId: String)
  extends AbstractBehavior[Table.Command](context) {
  import Table._
  import Reception.{TableIsFull, ClientSat}

  var clients: List[String] = List[String]()
  val maxSpots = 5

  context.log.info("Table actor {} started", tableId)

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case LocateClient(id, client, replyTo, reception) =>
        if (clients.length == maxSpots) {
          context.log.info("Table is full!")
          reception ! TableIsFull(id, replyTo)
        }
        context.log.info2("Client {} sat at table {}", client, id)
        clients ::= client
        reception ! ClientSat(id, context.self, replyTo)
        this

      case StandClient(id, client, replyTo) =>
        context.log.info2("Client {} left table {}", client, id)
        clients.filter(c => c == client).lastOption match {
          case Some(filteredClient) =>
            clients = clients.filter(c => c != filteredClient)
            replyTo ! ClientStood(id)
          case None =>
            replyTo ! ClientNotFound(id)
        }
        this

      case GetClients(id, replyTo) =>
        replyTo ! RespondClients(id, clients)
        this
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("Table actor {} stopped", tableId)
      this
  }
}
