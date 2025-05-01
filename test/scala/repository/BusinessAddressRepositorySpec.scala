package repository

import cats.data.Validated.Valid
import cats.effect.kernel.Ref
import cats.effect.IO
import mocks.MockQuestRepository
import models.database.*
import services.RepositorySpecBase
import testData.TestConstants.*
import weaver.SimpleIOSuite

object BusinessAddressRepositorySpec extends SimpleIOSuite with RepositorySpecBase {

  test(".findByBusinessId() - should return an address if businessId1 exists") {

    val existingAddressForUser = testQuest(userId1, businessId1)

    for {
      mockRepo <- createMockRepo(List(existingAddressForUser))
      result <- mockRepo.findByBusinessId(businessId1)
    } yield expect(result.contains(existingAddressForUser))
  }

  test(".findByBusinessId() - should return None if businessId1 does not exist") {
    for {
      mockRepo <- createMockRepo(List())
      result <- mockRepo.findByBusinessId(businessId1)
    } yield expect(result.isEmpty)
  }

  test(".createBusinessAddress() - when given a valid business address should insert an address into the postgres db") {

    val testBusinessAddressRequest: CreateBusinessAddressRequest = testCreateBusinessAddressRequest(userId2, businessId2)
    val testAddressForUser2 = testAddress(userId2, businessId2)

    for {
      mockRepo <- createMockRepo(List())
      result <- mockRepo.create(testBusinessAddressRequest)
      findInsertedAddress <- mockRepo.findByBusinessId(businessId2)
    } yield expect.all(
      result == Valid(CreateSuccess),
      findInsertedAddress == Some(testAddressForUser2)
    )
  }

  test(".delete() - when given a valid businessId should delete the business address details") {

    val existingAddressForUser = testAddress(userId3, businessId3)

    for {
      mockRepo <- createMockRepo(List(existingAddressForUser))
      findInitiallyCreatedAddress <- mockRepo.findByBusinessId(businessId3)
      result <- mockRepo.delete(businessId3)
      findInsertedAddress <- mockRepo.findByBusinessId(businessId3)
    } yield expect.all(
      findInitiallyCreatedAddress == Some(existingAddressForUser),
      result == Valid(DeleteSuccess),
      findInsertedAddress == None
    )
  }
}
