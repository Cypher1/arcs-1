package arcs.core.entity

import arcs.core.host.EntityHandleManager
import arcs.core.storage.testutil.testStorageEndpointManager
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("EXPERIMENTAL_API_USAGE")
@RunWith(JUnit4::class)
class DifferentHandleManagerTest : HandleManagerTestBase() {
  private var i = 0

  @Before
  override fun setUp() {
    super.setUp()
    val storageEndpointManager = testStorageEndpointManager()
    i++
    monitorStorageEndpointManager = storageEndpointManager
    readHandleManager = EntityHandleManager(
      arcId = "testArc",
      hostId = "testHost",
      time = fakeTime,
      scheduler = schedulerProvider("reader-#$i"),
      storageEndpointManager = storageEndpointManager,
      foreignReferenceChecker = foreignReferenceChecker
    )
    writeHandleManager = EntityHandleManager(
      arcId = "testArc",
      hostId = "testHost",
      time = fakeTime,
      scheduler = schedulerProvider("writer-#$i"),
      storageEndpointManager = storageEndpointManager,
      foreignReferenceChecker = foreignReferenceChecker
    )
  }

  @After
  override fun tearDown() = super.tearDown()
}
