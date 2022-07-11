package com.example

import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import com.example.Customer.{EatCommand, EatConfirmed, ProcessCommand}
import com.example.part_3.Reception
import com.example.part_3.Reception.{MoveClient, SitClient, TableRegistered, UnavailableTable}

import scala.concurrent.{Await, TimeoutException}
import scala.concurrent.duration.DurationInt
import scala.io.StdIn
import scala.language.postfixOps
import scala.util.control.Breaks.{break, breakable}

object Customer {

  final case class ProcessCommand(requestId: Int, msg: String, table: String) extends Reception.ClientCommand
  final case class EatCommand(requestId: Int, replyTo: ActorRef[Reception.ClientCommand]) extends Reception.ClientCommand
  final case class EatConfirmed(requestId: Int) extends Reception.ClientCommand

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
      case EatCommand(requestId, replyTo) =>
        context.log.info("Estoy comiendo y voy a tardar un rato...")
        Thread.sleep(4000)
        replyTo ! EatConfirmed(requestId)
        this
      case ProcessCommand(requestId, msg, table) =>
        this.processCommand(requestId, msg, table)
        this
      case UnavailableTable(id) =>
        context.log.info("Che, la mesa no esta disponible\n")
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
        context.log.info("No entiendo este comando")
    }
  }
}

object Main extends App {
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
        case Array(name, command, param) =>
          customers.get(name) match {
            case Some(customer) =>
              command match {
                case "eat" =>
                  println("Esperamos a que coma el cliente...")
                  implicit val timeout: Timeout = param.toInt.seconds
                  val future = customer ? ((ref: ActorRef[Reception.ClientCommand]) => EatCommand(requestNumber, ref))
                  try {
                    Await.result(future, timeout.duration) match {
                      case EatConfirmed(id) =>
                        println(s"${id}: El cliente finalizo de comer a tiempo")
                    }
                  } catch {
                    case e: TimeoutException => println("El cliente no comio a tiempo!")
                  }
                case _ =>
                  customer ! ProcessCommand(requestNumber, command, param)
              }
            case None =>
              val customer: ActorSystem[Reception.ClientCommand] = ActorSystem(Customer(name, reception), "Console")
              customers += name -> customer
              customer ! ProcessCommand(requestNumber, command, param)
          }
        case _ =>
          println("Unkown Command")
      }
    }
  }
  scala.sys.exit()
}

