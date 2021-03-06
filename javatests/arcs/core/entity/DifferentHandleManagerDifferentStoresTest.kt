package arcs.core.entity

import arcs.core.host.EntityHandleManager
import arcs.core.storage.testutil.testStorageEndpointManager
import org.junit.Before
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("EXPERIMENTAL_API_USAGE")
@RunWith(JUnit4::class)
class DifferentHandleManagerDifferentStoresTest : HandleManagerTestBase() {
  private var i = 0

  @Before
  override fun setUp() {
    super.setUp()
    i++
    val readStores = testStorageEndpointManager()
    monitorStorageEndpointManager = readStores
    readHandleManager = EntityHandleManager(
      arcId = "testArcId",
      hostId = "testHostId",
      time = fakeTime,
      scheduler = schedulerProvider("reader-#$i"),
      storageEndpointManager = readStores,
      foreignReferenceChecker = foreignReferenceChecker
    )
    val writeStores = testStorageEndpointManager()
    writeHandleManager = EntityHandleManager(
      arcId = "testArcId",
      hostId = "testHostId",
      time = fakeTime,
      scheduler = schedulerProvider("writer"),
      storageEndpointManager = writeStores,
      foreignReferenceChecker = foreignReferenceChecker
    )
  }
}
