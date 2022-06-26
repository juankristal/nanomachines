package actors.customer

import actors.AggregateException

object Exceptions {

  case class CustomerCreationException(message: String) extends AggregateException {

    override def getMessage: String =
      s"[${this.getClass.getSimpleName}] Cannot create Customer: $message"
  }

  case class CustomerNotFoundException() extends AggregateException {

    override def getMessage: String =
      s"[${this.getClass.getSimpleName}] Customer not found"
  }
}
