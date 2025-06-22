package models

import cats.effect.IO
import cats.implicits.*
import io.circe.Json
import io.circe.Printer
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait ModelsBaseSpec {

  implicit val testLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  // Pretty-print JSON for debugging
  val printer: Printer = Printer.spaces2SortKeys

  // Function to find JSON differences
  def jsonDiff(actual: Json, expected: Json, expectedResultPretty: String, jsonResultPretty: String): List[String] =
    (actual.asObject, expected.asObject) match {
      case (Some(actualObj), Some(expectedObj)) =>
        val actualKeys = actualObj.keys.toSet
        val expectedKeys = expectedObj.keys.toSet

        val missingKeys = expectedKeys.diff(actualKeys).map(k => s"Missing key: '$k'")
        val extraKeys = actualKeys.diff(expectedKeys).map(k => s"Unexpected key: '$k'")

        val differingValues = (actualObj.toMap.keys ++ expectedObj.toMap.keys).toSet.toList
          .flatMap { key =>
            (actualObj(key), expectedObj(key)) match {
              case (Some(a), Some(e)) if a != e =>
                Some(s"Value mismatch for key '$key':\n  Expected: ${printer.print(e)}\n  Actual: ${printer.print(a)}")
              case _ => None
            }
          }

        (missingKeys ++ extraKeys ++ differingValues).toList

      case _ =>
        if (actual != expected) List(s"Entire JSON mismatch:\nExpected:\n$expectedResultPretty\nActual:\n$jsonResultPretty")
        else List.empty
    }

  def diffPrinter(differences: List[String], jsonResultPretty: String, expectedResultPretty: String) = {
    println("=== JSON Difference Detected! ===")
    differences.foreach(diff => println(s"- $diff"))
    println("Generated JSON:\n" + jsonResultPretty)
    println("Expected JSON:\n" + expectedResultPretty)
  }

}
