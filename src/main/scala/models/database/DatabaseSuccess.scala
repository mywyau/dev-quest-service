package models.database

import models.database.DatabaseSuccess

sealed trait DatabaseSuccess

case class ReadSuccess[A](value: A) extends DatabaseSuccess

// case class ReadSuccess[A](data: List[A]) extends DatabaseSuccess

case object CreateSuccess extends DatabaseSuccess

case object UpdateSuccess extends DatabaseSuccess

case object DeleteSuccess extends DatabaseSuccess
