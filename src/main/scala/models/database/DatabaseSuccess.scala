package models.database

import models.database.DatabaseSuccess

sealed trait DatabaseSuccess

case object ReadSuccess extends DatabaseSuccess

case object CreateSuccess extends DatabaseSuccess

case object UpdateSuccess extends DatabaseSuccess

case object DeleteSuccess extends DatabaseSuccess
