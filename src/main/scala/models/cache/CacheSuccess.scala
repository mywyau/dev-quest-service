package models.cache

sealed trait CacheSuccess

case object CacheReadSuccess extends CacheSuccess
case object CacheCreateSuccess extends CacheSuccess
case object CacheUpdateSuccess extends CacheSuccess
case object CacheDeleteSuccess extends CacheSuccess
