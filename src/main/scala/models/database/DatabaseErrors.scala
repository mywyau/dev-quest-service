package models.database

import models.database.DatabaseErrors

trait DatabaseErrors

case object TooManyActiveQuestsError extends DatabaseErrors

case class UpdateNotFound(message: String) extends DatabaseErrors

case class UpdateFailure(reason: String) extends DatabaseErrors

case class CreateFailure(reason: String) extends DatabaseErrors

case class UnknownError(message: String) extends DatabaseErrors

case class SqlExecutionError(message: String) extends DatabaseErrors

case object InsertionFailed extends DatabaseErrors

case object ForeignKeyViolationError extends DatabaseErrors

case object DatabaseConnectionError extends DatabaseErrors

case object ConstraintViolation extends DatabaseErrors

case object DatabaseError extends DatabaseErrors

case object DeleteError extends DatabaseErrors

case object NotFoundError extends DatabaseErrors

case object DataTooLongError extends DatabaseErrors

case object UnexpectedResultError extends DatabaseErrors
