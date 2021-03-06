package arcs.android.host

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import arcs.android.sdk.host.ArcHostHelper
import arcs.android.sdk.host.ResurrectableHost
import arcs.core.data.Capabilities
import arcs.core.data.Capability.Shareable
import arcs.core.host.ParticleRegistration
import arcs.core.host.SchedulerProvider
import arcs.core.host.SimpleSchedulerProvider
import arcs.core.host.TestingHost
import arcs.sdk.android.storage.AndroidStorageServiceEndpointManager
import arcs.sdk.android.storage.ResurrectionHelper
import arcs.sdk.android.storage.service.BindHelper
import arcs.sdk.android.storage.service.DefaultBindHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

@ExperimentalCoroutinesApi
abstract class TestExternalArcHostService : Service() {
  protected val scope: CoroutineScope = MainScope()

  abstract val arcHost: TestingAndroidHost

  val schedulerProvider = SimpleSchedulerProvider(Dispatchers.Default)

  private val arcHostHelper: ArcHostHelper by lazy {
    ArcHostHelper(this, arcHost)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val result = super.onStartCommand(intent, flags, startId)
    arcHostHelper.onStartCommand(intent)
    return result
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    super.onDestroy()
    scope.cancel()
  }

  @ExperimentalCoroutinesApi
  abstract class TestingAndroidHost(
    context: Context,
    scope: CoroutineScope,
    schedulerProvider: SchedulerProvider,
    vararg particles: ParticleRegistration
  ) : TestingHost(
    schedulerProvider,
    AndroidStorageServiceEndpointManager(
      scope,
      testBindHelper ?: DefaultBindHelper(context)
    ),
    *particles
  ),
    ResurrectableHost {
    override val resurrectionHelper: ResurrectionHelper =
      ResurrectionHelper(context, ::onResurrected)

    override val arcHostContextCapability = testingCapability
  }

  companion object {
    var testBindHelper: BindHelper? = null
    var testingCapability = Capabilities(Shareable(true))
  }
}
