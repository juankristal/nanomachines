package models

import actors.AggregateState

case class Customer(name: String, gender: String) extends AggregateState
