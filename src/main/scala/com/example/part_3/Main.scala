package com.example

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import com.example.Customer.ProcessCommand
import com.example.part_3.Reception
import com.example.part_3.Reception.{MoveClient, SitClient, TableRegistered, UnavailableTable}

import scala.concurrent.duration.DurationInt
import scala.io.StdIn
import scala.language.postfixOps
import scala.util.control.Breaks.{break, breakable}

object Customer {

  final case class ProcessCommand(requestId: Int, msg: String, table: String) extends Reception.ClientCommand

  def apply(name: String, reception: ActorSystem[Reception.Command]): Behavior[Reception.ClientCommand] =
    Behaviors.setup(context => new Customer(context, name, reception))
}

class Customer(context: ActorContext[Reception.ClientCommand],
               name: String,
               reception: ActorSystem[Reception.Command]) extends
  AbstractBehavior[Reception.ClientCommand](context) {
  var currentTable: Option[ActorRef[com.example.part_3.Table.Command]] = None

  override def onMessage(msg: Reception.ClientCommand): Behavior[Reception.ClientCommand] = {
    msg match {
      case ProcessCommand(requestId, msg, table) =>
        this.processCommand(requestId, msg, table)
        this
      case UnavailableTable(id) =>
        context.log.info("Consola recibio mensaje\n")
        this
      case TableRegistered(table) =>
        context.log.info(s"Me asigaron a la mesa ${table}\n")
        currentTable = Some(table)
        this
    }
  }

  def processCommand(requestNumber: Int, msg: String, table: String): Unit ={
    msg match {
      case "sit" =>
        reception ! SitClient(requestNumber, name, table, context.self)
      case "move" =>
        currentTable match {
          case Some(aTable) =>
            reception ! MoveClient(requestNumber, name, aTable, table, context.self)
          case None =>
            context.log.info("No estoy asignado a una mesa")
        }
      case _ =>
    }
  }
}

object Main extends App {
  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val reception: ActorSystem[Reception.Command] = ActorSystem(Reception(), "Reception")
  var customers: Map[String, ActorSystem[Reception.ClientCommand]] =
    Map.empty[String, ActorSystem[Reception.ClientCommand]]
  var requestNumber: Int = 0
  breakable {
    while(true) {
      val msg = StdIn.readLine()
      requestNumber += 1
      msg.split(" ") match {
        case Array("q") => break
        case Array(name, command, table) =>
          customers.get(name) match {
            case Some(customer) =>
              customer ! ProcessCommand(requestNumber, command, table)
              //val future = customer ? ((ref: ActorRef[Reception.ClientCommand]) => ProcessCommand(requestNumber, command, table))
              //Await.result(future, timeout.duration) match {
              //  case ProcessCommand(_, _, _) =>
              //    println("Garantizamos que se espera la resolucion del futuro!")
              //}
            case None =>
              val customer: ActorSystem[Reception.ClientCommand] = ActorSystem(Customer(name, reception), "Console")
              customers += name -> customer
              customer ! ProcessCommand(requestNumber, command, table)
          }
      }
    }
  }
  scala.sys.exit()
}

