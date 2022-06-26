package actors

import akka.persistence.typed.PersistenceId

trait PersistenceEntity {
  val SnapshotAtNumberOfEvents: Int = 15
  val KeepNSnapshots: Int = 10
  val AggregateSuffix: String
  final val PersistenceSeparator = "-"
  type Id = String

  def persistenceId(entityId: String): PersistenceId =
    PersistenceId(AggregateSuffix, entityId, PersistenceSeparator)

  def entityId(id: String) = s"$id"

  def extractIdsFromEntityId(entityId: String): Id = entityId

  def extractIdsFromPersistenceId(persistenceId: String): Id =
    persistenceId.split(PersistenceSeparator).toList match {
      case _ :: entityId :: Nil => extractIdsFromEntityId(entityId)
      case _                    => throw new Exception(s"Cannot extract entityId from persistenceId: $persistenceId")
    }

}
