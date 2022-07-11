package com.example.part_3

import akka.actor.typed._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

object Reception {
  def apply(): Behavior[Command] =
    Behaviors.setup(context => new Reception(context))

  sealed trait Command
  trait ClientCommand

  final case class MoveClient(requestId: Long,
                              client: String,
                              currentTable: ActorRef[Table.Command],
                              newTable: String,
                              replyTo: ActorRef[ClientCommand]) extends Command
  final case class SitClient(requestId: Long,
                             client: String,
                             mesa: String,
                             replyTo: ActorRef[ClientCommand])
    extends Command
  final case class TableTerminated(table: ActorRef[Table.Command], tableId: String) extends Command
  final case class TableRegistered(table: ActorRef[Table.Command]) extends ClientCommand
  final case class TableIsFull(requestId: Long, replyTo: ActorRef[ClientCommand]) extends Command
  final case class ClientNotFound(requestId: Long, replyTo: ActorRef[ClientCommand]) extends Command
  final case class UnavailableTable(requestId: Long) extends ClientCommand
  final case class ClientSat(requestId: Long,
                             table: ActorRef[Table.Command],
                             replyTo: ActorRef[ClientCommand]) extends Command
  final case class ClientStood(requestId: Long,
                               client: String,
                               newTable: String,
                               replyTo: ActorRef[ClientCommand]) extends Command
}

class Reception(context: ActorContext[Reception.Command])
  extends AbstractBehavior[Reception.Command](context) {
  import Reception._

  var tables: Map[String, ActorRef[Table.Command]] = Map.empty[String, ActorRef[Table.Command]]
  val maxSeats = 100
  var currentSeats = 0

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case SitClient(id, client, tableName, replyTo) =>
        tables.get(tableName) match {
          case Some(tableActor) =>
            println("Some tableActor\n")
            tableActor ! Table.LocateClient(id, client, replyTo, context.self)
            this
          case None =>
            context.log.info(s"Table created with name $tableName")
            val tableActor = context.spawn(Table(tableName), s"$tableName")
            context.watchWith(tableActor, TableTerminated(tableActor, tableName))
            tables += tableName -> tableActor
            tableActor ! Table.LocateClient(id, client, replyTo, context.self)
            this
        }
      case MoveClient(id, client, currentTable, newTable, replyTo) =>
        currentTable ! Table.RemoveClient(id, client, newTable, replyTo, context.self)
        this
      case ClientNotFound(id, replyTo) =>
        replyTo ! UnavailableTable(id)
        this
      case ClientStood(id, client, newTable, replyTo) =>
        context.self ! SitClient(id, client, newTable, replyTo)
        this
      case ClientSat(id, table, replyTo) =>
        replyTo ! TableRegistered(table)
        this
      case TableIsFull(id, replyTo) =>
        replyTo ! UnavailableTable(id)
        this
      case TableTerminated(_, tableName) =>
        context.log.info(s"Table died $tableName")
        tables -= tableName
        this
      case _ =>
        Behaviors.unhandled
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("Reception actor stopped")
      this
  }
}
