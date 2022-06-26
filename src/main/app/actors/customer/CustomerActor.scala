package actors.customer

import actors._
import actors.customer.Exceptions.CustomerNotFoundException
import akka.actor.typed.scaladsl._
import akka.actor.typed.{Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import models.Customer

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object CustomerActor
  extends PersistenceEntity
    with Commands
    with Replies {
  import Events._

  override val AggregateSuffix: Id = "Customer"

  def apply(id: Id, tags: Set[String] = Set.empty): Behavior[CustomerCommand] =
    Behaviors.setup { implicit context: ActorContext[CustomerCommand] =>
      implicit val executionContext: ExecutionContextExecutor = context.executionContext
      context.log.info(s"[[Customer Actor]] Starting customer:$id")
      EventSourcedBehavior[CustomerCommand, CustomerEvent, AggregateState](
        persistenceId = persistenceId(entityId(id)),
        emptyState = UninitializedState(),
        commandHandler = commandHandler(id),
        eventHandler = eventHandler(id)
      ).withRetention(
        RetentionCriteria.snapshotEvery(SnapshotAtNumberOfEvents, KeepNSnapshots).withDeleteEventsOnSnapshot
      ).onPersistFailure(
        SupervisorStrategy
          .restartWithBackoff(minBackoff = 1 second, maxBackoff = 60.seconds, randomFactor = 0.1)
      ).withTagger(_ => tags)
    }

  private def commandHandler(id: String)(implicit
                                         actorContext: ActorContext[CustomerCommand]
  ): (AggregateState, CustomerCommand) => Effect[CustomerEvent, AggregateState] = { (state, command) =>
    implicit val executionContext: ExecutionContextExecutor = actorContext.executionContext
    state match {
      case _: UninitializedState =>
        command match {
          case command: CreateCustomer =>
            persist(command)
          case GetCustomer(replyTo) =>
            Effect.none.thenReply(replyTo) { _ => StatusReply.error(CustomerNotFoundException()) }
        }
      case customer: Customer =>
        command match {
          case command: UpdateCustomer =>
            persist(command)
          case GetCustomer(replyTo) =>
            Effect.reply(replyTo)(StatusReply.success(CustomerInformation(customer)))
        }
    }
  }

  private def persist(command: CustomerCommand): Effect[CustomerEvent, AggregateState] = {
    command match {
      case CreateCustomer(customer, replyTo) =>
        Effect
          .persist(CustomerCreated(customer))
          .thenReply(replyTo) { _ => StatusReply.success(CustomerInformation(customer)) }
      case UpdateCustomer(customer, replyTo) =>
        Effect
        .persist(CustomerUpdated(customer))
        .thenReply(replyTo) { _ => StatusReply.success(CustomerInformation(customer)) }
    }
  }

  private def eventHandler(id: String): (AggregateState, CustomerEvent) => AggregateState = {
    case (_, CustomerCreated(customer)) => customer
    case (state: Customer, event) => event.applyTo(state)
    case (state, event) =>
      throw new Exception(
        s"Invalid transition for state:${state.getClass.getSimpleName} and event:${event.getClass.getSimpleName}. id: $id"
      )
  }
}
