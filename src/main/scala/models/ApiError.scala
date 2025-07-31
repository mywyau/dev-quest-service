package models

import io.circe.generic.semiauto._
import io.circe.Encoder
import io.circe.Json
import models.database.DatabaseErrors

case class ApiError(code: String, message: String, column: Option[String])

object ApiError {
    
  def from(error: DatabaseErrors): ApiError =
    ApiError(error.code, error.message, error.sqlColumn)

  implicit val encoder: Encoder[ApiError] = deriveEncoder
}
