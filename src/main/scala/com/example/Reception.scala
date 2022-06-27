package com.example

import akka.actor.typed._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.example.Table.StandClient

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
  final case class SitClient(requestId: Long, client: String, mesa: String, replyTo: ActorRef[ClientCommand])
    extends Command
  final case class TableTerminated(table: ActorRef[Table.Command], tableId: String) extends Command
  final case class TableRegistered(table: ActorRef[Table.Command]) extends ClientCommand
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
              replyTo ! TableRegistered(tableActor)
              this
            case None =>
              context.log.info(s"Table created with name $tableName")
              val tableActor = context.spawn(Table(tableName), s"Mesa $tableName")
              context.watchWith(tableActor, TableTerminated(tableActor, tableName))
              tables += tableActor
              replyTo ! TableRegistered(tableActor)
              this
          }
        case MoveClient(id, client, currentTable, newTable, replyTo) =>
            currentTable ! StandClient(id, client, replyTo)
            context.self ! SitClient(id, client, newTable, replyTo)
            this
        case TableTerminated(_, tableName) =>
          context.log.info(s"Table died $tableName")
          tables -= tableName
          this
      }
    }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("Reception actor stopped")
      this
  }
}
