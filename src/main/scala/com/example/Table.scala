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

  final case class RemoveClient(requestId: Long,
                                client: String,
                                newTable: String,
                                replyTo: ActorRef[Reception.ClientCommand],
                                replyToReception: ActorRef[Reception.Command])
    extends Command
}

class Table(context: ActorContext[Table.Command], tableId: String)
  extends AbstractBehavior[Table.Command](context) {
  import Table._
  import Reception.{TableIsFull, ClientSat, ClientStood, ClientNotFound}

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

      case RemoveClient(id, client, newTable, replyTo, reception) =>
        clients.filter(c => c == client).lastOption match {
          case Some(filteredClient) =>
            context.log.info(s"Clients ${client} left the table.")
            clients = clients.filter(c => c != filteredClient)
            reception ! ClientStood(id, client, newTable, replyTo)
          case None =>
            context.log.info(s"Clients ${client} is not sat in this table.")
            reception ! ClientNotFound(id, replyTo)
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
