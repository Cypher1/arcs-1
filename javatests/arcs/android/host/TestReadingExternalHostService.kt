package arcs.android.host

import arcs.core.host.ReadPerson
import arcs.core.host.toRegistration
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class TestReadingExternalHostService : TestExternalArcHostService() {
  override val arcHost = object : TestingAndroidHost(
    this@TestReadingExternalHostService,
    scope,
    schedulerProvider,
    ::ReadPerson.toRegistration()
  ) {}
}
