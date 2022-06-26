package actors.customer

import actors.{AggregateState, DomainEvent}
import models.Customer

object Events {

  sealed trait CustomerEvent extends DomainEvent {
    def applyTo(customer: Customer): AggregateState
  }

  case class CustomerCreated(customer: Customer) extends CustomerEvent {
    override def applyTo(customer: Customer): AggregateState = customer
  }

  case class CustomerUpdated(customer: Customer) extends CustomerEvent {
    override def applyTo(customer: Customer): AggregateState = customer
  }
}
