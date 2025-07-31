package utils

import models.database.*

object DatabaseErrorHandler {

  def fromSQLException(ex: java.sql.SQLException): DatabaseErrors =
    ex.getSQLState match {
      case "23505" => DuplicateError(extractDuplicateColumn(ex.getMessage()))
      case "23503" => ForeignKeyViolationError
      case "08001" => DatabaseConnectionError
      case "22001" => DataTooLongError
      case _ => SqlExecutionError(ex.getMessage)
    }

  def fromThrowable(ex: Throwable): DatabaseErrors = ex match {
    case sqlEx: java.sql.SQLException => fromSQLException(sqlEx)
    case _ => UnknownError(ex.getMessage)
  }

  def extractDuplicateColumn(message: String): Option[String] = {
    val regex = """Key \((.*?)\)=\(""".r
    regex.findFirstMatchIn(message).map(_.group(1))
  }
}
