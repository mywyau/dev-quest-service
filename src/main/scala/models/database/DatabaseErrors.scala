package models.database

sealed trait DatabaseErrors {
  def code: String
  def message: String
  def sqlColumn: Option[String]
}

// --- Common SQL & DB Errors ---

case class DuplicateError(column: Option[String] = None) extends DatabaseErrors {
  val code = "DUPLICATE"
  val message = "A record with this unique value already exists."
  val sqlColumn = column
}

case object ForeignKeyViolationError extends DatabaseErrors {
  val code = "FOREIGN_KEY_VIOLATION"
  val message = "A referenced record does not exist."
  val sqlColumn = None
}

case object DataTooLongError extends DatabaseErrors {
  val code = "DATA_TOO_LONG"
  val message = "One of the fields exceeds the allowed length."
  val sqlColumn = None
}

case object DatabaseConnectionError extends DatabaseErrors {
  val code = "DB_CONNECTION_ERROR"
  val message = "Could not connect to the database."
  val sqlColumn = None
}

case object ConstraintViolation extends DatabaseErrors {
  val code = "CONSTRAINT_VIOLATION"
  val message = "A database constraint was violated."
  val sqlColumn = None
}

case object NotFoundError extends DatabaseErrors {
  val code = "NOT_FOUND"
  val message = "The specified record was not found."
  val sqlColumn = None
}

case object InsertionFailed extends DatabaseErrors {
  val code = "INSERTION_FAILED"
  val message = "The record could not be inserted."
  val sqlColumn = None
}

case object DeleteError extends DatabaseErrors {
  val code = "DELETE_FAILED"
  val message = "Failed to delete the record."
  val sqlColumn = None
}

case object UnexpectedResultError extends DatabaseErrors {
  val code = "UNEXPECTED_RESULT"
  val message = "The database returned an unexpected number of rows."
  val sqlColumn = None
}

case class SqlExecutionError(details: String) extends DatabaseErrors {
  val code = "SQL_EXECUTION_ERROR"
  val message = s"SQL execution failed: $details"
  val sqlColumn = None
}

case class UnknownError(details: String) extends DatabaseErrors {
  val code = "UNKNOWN_ERROR"
  val message = s"An unknown error occurred: $details"
  val sqlColumn = None
}

// --- Application-specific errors ---

case object TooManyActiveQuestsError extends DatabaseErrors {
  val code = "TOO_MANY_ACTIVE_QUESTS"
  val message = "You already have too many active quests."
  val sqlColumn = None
}

case object QuestNotEstimatedError extends DatabaseErrors {
  val code = "QUEST_NOT_ESTIMATED"
  val message = "Quest must be estimated before it can be finalized."
  val sqlColumn = None
}

case object NotEnoughEstimates extends DatabaseErrors {
  val code = "NOT_ENOUGH_ESTIMATES"
  val message = "There are not enough estimates to calculate a result."
  val sqlColumn = None
}

case object UnableToCalculateEstimates extends DatabaseErrors {
  val code = "CANNOT_CALCULATE_ESTIMATES"
  val message = "Failed to calculate estimates."
  val sqlColumn = None
}

case object TooManyEstimatesToday extends DatabaseErrors {
  val code = "TOO_MANY_ESTIMATES"
  val message = "You have reached your estimate limit for today."
  val sqlColumn = None
}

// --- Flexible, contextual errors ---

case class UpdateNotFound(reason: String) extends DatabaseErrors {
  val code = "UPDATE_NOT_FOUND"
  val message = s"Update failed: $reason"
  val sqlColumn = None
}

case class UpdateFailure(reason: String) extends DatabaseErrors {
  val code = "UPDATE_FAILED"
  val message = s"Update operation failed: $reason"
  val sqlColumn = None
}

case class CreateFailure(reason: String) extends DatabaseErrors {
  val code = "CREATE_FAILED"
  val message = s"Failed to create resource: $reason"
  val sqlColumn = None
}

case object UnauthorizedAccess extends DatabaseErrors {
  val code = "UNAUTHORIZED"
  val message = "You are not authorized to perform this operation."
  val sqlColumn = None
}

case object RateLimitExceeded extends DatabaseErrors {
  val code = "RATE_LIMIT"
  val message = "Too many requests. Please try again later."
  val sqlColumn = None
}
