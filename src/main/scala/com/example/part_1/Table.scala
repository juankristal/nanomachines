package com.example.part_1

import akka.actor.typed._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

object Table {
  def apply(tableId: String): Behavior[Command] =
    Behaviors.setup(context => new Table(context, tableId))

  sealed trait Command

  final case class GetClients(requestId: Long, replyTo: ActorRef[Answer]) extends Command

  final case class SitClient(requestId: Long, client: String, replyTo: ActorRef[Answer]) extends Command

  final case class RemoveClient(requestId: Long, client: String, replyTo: ActorRef[Answer]) extends Command

  final case class Answer(msg: String)
}

class Table(context: ActorContext[Table.Command], tableId: String)
  extends AbstractBehavior[Table.Command](context) {
  import Table._

  var clients: List[String] = List[String]()
  val maxSpots = 6

  context.log.info("Table actor {} started\n", tableId)

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case SitClient(id, client, replyTo) =>
        if (clients.length == maxSpots) {
          replyTo ! Answer(s"Request id: ${id}\nTable is full!")
        } else {
          replyTo ! Answer(s"Request id: ${id}\nClient ${client} sat\n")
          clients ::= client
        }
        this

      case RemoveClient(id, client, replyTo) =>
        clients.filter(c => c == client).lastOption match {
          case Some(filteredClient) =>
            replyTo ! Answer(s"Request id: ${id}\nClients ${client} left the table.")
            clients = clients.filter(c => c != filteredClient)
          case None =>
            replyTo ! Answer(s"Request id: ${id}\nClients ${client} is not sat in this table.")
        }
        this

      case GetClients(id, replyTo) =>
        replyTo ! Answer(s"Request id: ${id}\nClients: ${clients}")
        this
      case _ =>
        Behaviors.unhandled
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("Table actor {} stopped", tableId)
      this
  }
}
