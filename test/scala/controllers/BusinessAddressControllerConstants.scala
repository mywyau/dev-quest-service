package controllers

import java.time.LocalDateTime
import models.business.address.BusinessAddressPartial
import models.business.address.BusinessAddressPartial

object BusinessAddressControllerConstants {

  val sampleBusinessAddress1: BusinessAddressPartial =
    BusinessAddressPartial(
      userId = "user_1",
      businessId = "business1",
      buildingName = Some("building 1"),
      floorNumber = Some("floor 1"),
      street = Some("1 Canton Street"),
      city = Some("Cardiff"),
      country = Some("United Kingdom"),
      county = Some("South Glamorgan"),
      postcode = Some("CF3 5DE"),
      latitude = Some(100.1),
      longitude = Some(-100.1)
    )
}
