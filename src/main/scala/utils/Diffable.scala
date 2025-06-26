package utils

trait Diffable[T] {
  def diff(a: T, b: T): Map[String, (Any, Any)]
}

object Diffable {

  def apply[T](implicit instance: Diffable[T]): Diffable[T] = instance

  // Generic Implementation for Any Case Class
  implicit def caseClassDiffable[T <: Product]: Diffable[T] = (a: T, b: T) => {
    val fields = a.getClass.getDeclaredFields.map(_.getName)
    val valuesA = a.productIterator.toList
    val valuesB = b.productIterator.toList

    fields
      .zip(valuesA.zip(valuesB))
      .collect {
        case (field, (valA, valB)) if valA != valB => field -> (valA, valB)
      }
      .toMap
  }

  // Helper method to compare two instances
  def diff[T](a: T, b: T)(implicit diffInstance: Diffable[T]): Map[String, (Any, Any)] =
    diffInstance.diff(a, b)

  //  Fix `logDifferences` to explicitly use `Diffable`
  def logDifferences[T](expected: T, actual: T)(implicit diffInstance: Diffable[T]): Unit = {
    val differences = diffInstance.diff(expected, actual)
    if (differences.nonEmpty) {
      println("\n" + " [ Expected ] | [ Actual ] " + ("***************") * 3 + "\n")
      differences.foreach { case (field, (expectedValue, actualValue)) =>
        println(s"❌ Difference Found - $field: $expectedValue | $actualValue \n")
      }
    } else {
      println(" No differences found!")
    }
  }

  //  Fix `DiffableOps` to correctly reference `Diffable.logDifferences`
  implicit class DiffableOps[T](val expected: T) extends AnyVal {
    def logDifferences(actual: T)(implicit diffInstance: Diffable[T]): Unit = {
      Diffable.logDifferences(expected, actual) //  Explicitly reference `Diffable.logDifferences`
    }
  }

}
