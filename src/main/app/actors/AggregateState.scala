package actors

trait AggregateState

case class UninitializedState() extends AggregateState
