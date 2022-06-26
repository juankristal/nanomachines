package actors.customer

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import models._

trait Commands {
  _: Replies =>
  type ReplyTo[T] = ActorRef[StatusReply[T]]

  sealed trait CustomerCommand

  sealed trait WithReply[T <: CustomerReply] {
    val replyTo: ActorRef[StatusReply[T]]
  }

  case class CreateCustomer(customer: Customer, override val replyTo: ReplyTo[CustomerInformation])
    extends CustomerCommand
      with WithReply[CustomerInformation]

  case class UpdateCustomer(customer: Customer, override val replyTo: ReplyTo[CustomerInformation])
    extends CustomerCommand
      with WithReply[CustomerInformation]

  case class GetCustomer(override val replyTo: ReplyTo[CustomerInformation])
    extends CustomerCommand
      with WithReply[CustomerInformation]
}
