package actors.customer

import models.{Customer}

trait Replies {
  sealed trait CustomerReply

  case class CustomerInformation(customer: Customer) extends CustomerReply
}
