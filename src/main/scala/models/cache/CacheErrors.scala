package models.cache

trait CacheErrors

case object CacheUpdateFailure extends CacheErrors
case object CacheCreateFailure extends CacheErrors
case object CacheUnknownError extends CacheErrors
case object CacheDeleteError extends CacheErrors
case object CacheNotFoundError extends CacheErrors
case object CacheUnexpectedResultError extends CacheErrors
case object CacheCacheError extends CacheErrors
