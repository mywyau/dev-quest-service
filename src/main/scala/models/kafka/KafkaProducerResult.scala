package models.kafka

sealed trait KafkaProducerResult

case object SuccessfulWrite extends KafkaProducerResult

sealed trait Failure extends KafkaProducerResult {
  def message: String
  def cause: Option[Throwable] = None
}

case class SerializationError(message: String, override val cause: Option[Throwable] = None) extends Failure
case class FailedWrite(message: String, override val cause: Option[Throwable] = None) extends Failure
case class KafkaSendError(message: String, override val cause: Option[Throwable] = None) extends Failure
case class UnknownError(message: String, override val cause: Option[Throwable] = None) extends Failure
case class DatabaseFailure(message: String) extends Failure
