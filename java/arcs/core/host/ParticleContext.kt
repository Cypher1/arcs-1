/*
 * Copyright 2020 Google LLC.
 *
 * This code may only be used under the BSD style license found at
 * http://polymer.github.io/LICENSE.txt
 *
 * Code distributed by Google as part of this project is also subject to an additional IP rights
 * grant found at
 * http://polymer.github.io/PATENTS.txt
 */
package arcs.core.host

import arcs.core.data.Plan
import arcs.core.entity.Handle
import arcs.core.host.api.Particle
import arcs.core.storage.StorageProxy.StorageEvent
import arcs.core.util.Scheduler
import arcs.core.util.TaggedLog
import kotlinx.coroutines.withContext

/** Maximum number of times a particle may fail to be started before giving up. */
const val MAX_CONSECUTIVE_FAILURES = 5

/**
 * Holds per-[Particle] context state needed by [ArcHost] to implement [Particle] lifecycle.
 *
 * @property particle currently instantiated [Particle] class
 * @property planParticle the [Plan.Particle] used to instantiate [particle]
 * @property dispatcher used when invoking the particle lifecycle methods
 * @property particleState the current state the particle lifecycle is in
 * @property consecutiveFailureCount how many times this particle failed to start in a row; used
 *           to detect infinite-crash loop particles
 */
class ParticleContext(
  val particle: Particle,
  val planParticle: Plan.Particle,
  val scheduler: Scheduler,
  var particleState: ParticleState = ParticleState.Instantiated,
  var consecutiveFailureCount: Int = 0
) {
  private val log = TaggedLog {
    "ParticleContext(${planParticle.particleName}, state=$particleState)"
  }

  // Indicates whether the particle has any readable handles or not.
  private var isWriteOnly = true

  // The set of handles that expect to receive StorageEvent.READY.
  private val awaitingReady: MutableSet<Handle> = mutableSetOf()

  // The set of handles that are currently desynchronized from storage.
  private val desyncedHandles: MutableSet<Handle> = mutableSetOf()

  // One-shot callback used to notify the arc host that the particle is in the Running state.
  private var notifyReady: ((Particle) -> Unit)? = null

  private val dispatcher = scheduler.asCoroutineDispatcher()

  override fun toString() = "ParticleContext(particle=$particle, particleState=$particleState, " +
    "consecutiveFailureCount=$consecutiveFailureCount, isWriteOnly=$isWriteOnly, " +
    "awaitingReady=$awaitingReady, desyncedHandles=$desyncedHandles)"

  /** Create a copy of [ParticleContext] with a new [particle]. */
  fun copyWith(newParticle: Particle) = ParticleContext(
    newParticle,
    planParticle,
    scheduler,
    particleState,
    consecutiveFailureCount
  )

  /**
   * Sets up [StorageEvent] handling for [particle].
   */
  fun registerHandle(handle: Handle) {
    // TODO(b/159257058): write-only handles still need to sync
    val canRead = handle.mode.canRead // left here to preserve mock ordering in tests
    isWriteOnly = false

    // Track the StorageEvent.READY notifications for readable handles
    // so we can invoke Particle.onReady once they have all fired.
    awaitingReady.add(handle)

    // Particles with readable handles need to be notified for storage events
    // against those handles, but a direct connection from StorageProxy to Particle
    // is difficult in the current architecture. Instead, we'll thread events from
    // the proxy to here via a callback.
    handle.registerForStorageEvents {
      // TODO(b/159257058): for write-only handles, only allow 'ready' events
      if (canRead || it == StorageEvent.READY) {
        notify(it, handle)
      }
    }
  }

  /**
   * Performs the startup lifecycle on [particle].
   */
  suspend fun initParticle() {
    withContext(requireNotNull(dispatcher)) {
      check(
        particleState in arrayOf(
          ParticleState.Instantiated, ParticleState.Stopped,
          ParticleState.Failed, ParticleState.Failed_NeverStarted
        )
      ) {
        "${planParticle.particleName}: initParticle should not be called on a particle " +
          "in state $particleState"
      }
      if (!particleState.hasBeenStarted) {
        try {
          particle.onFirstStart()
          particleState = ParticleState.FirstStart
        } catch (e: Exception) {
          throw markParticleAsFailed(e, "onFirstStart")
        }
      }
      try {
        particle.onStart()
        particleState = ParticleState.Waiting
      } catch (e: Exception) {
        throw markParticleAsFailed(e, "onStart")
      }
    }
  }

  /**
   * For write-only particles, immediately calls `onReady`, moves [particle] to
   * [ParticleState.Running] and fires the [notifyReady] callback.
   *
   * For particles with readable handles, triggers their underlying [StorageProxy] sync requests.
   * As each proxy is synced, [notify] will receive a [StorageEvent.READY] event; once all have
   * been received the particle is made ready as above.
   */
  suspend fun runParticle(notifyReady: (Particle) -> Unit) {
    withContext(requireNotNull(dispatcher)) {
      when (particleState) {
        ParticleState.Running -> {
          // If multiple particles read from the same StorageProxy, it is possible that
          // the proxy syncs and notifies READY before all the calls to runParticle are
          // invoked. In this case, the remaining particles may already be running.
          check(awaitingReady.isEmpty()) {
            "${planParticle.particleName}: runParticle called on an already running " +
              "particle; awaitingReady should be empty but still has " +
              "${awaitingReady.size} handles"
          }
          notifyReady(particle)
          return@withContext
        }
        ParticleState.Waiting -> Unit
        else -> throw IllegalStateException(
          "${planParticle.particleName}: runParticle " +
            "should not be called on a particle in state $particleState"
        )
      }

      this@ParticleContext.notifyReady = notifyReady

      if (isWriteOnly) {
        // Particles with only write-only handles won't receive any storage
        // events and thus need to have onReady invoked directly.
        try {
          moveToReady()
        } catch (e: Exception) {
          throw markParticleAsFailed(e, "onReady")
        }
      } else {
        // Trigger the StorageProxy sync request for each readable handle. Once
        // the StorageEvent.READY notifications have all, been received, we can
        // call particle.onReady (handled by notify below).
        awaitingReady.forEach { it.maybeInitiateSync() }
      }
    }
  }

  /**
   * Performs the shutdown lifecycle on [particle] and resets its handles.
   * This records exceptions from `onShutdown` but does not re-throw them.
   */
  suspend fun stopParticle() {
    // Detach handle callbacks.
    // We want onShutdown to have access to the handles,
    // But this particle should never receive another `onUpdate`
    // callback from this point on.
    withContext(requireNotNull(dispatcher)) {
      particle.handles.detach()
    }

    // Execute the [onShutdown] method for each particle.
    //
    // We submit this next block as a separate scheduler task, so that
    // any storage events that got scheduled while the detach task above was queued
    // get processed. This guarantees that it will be safe to call handles.reset()
    // in this block without worrying about any latent storage events attempting to run
    // and access the now-null handle.
    withContext(requireNotNull(dispatcher)) {
      try {
        particle.onShutdown()
        particleState = ParticleState.Stopped
      } catch (e: Exception) {
        markParticleAsFailed(e, "onShutdown")
      }
      particle.handles.reset()
    }
  }

  /**
   * Called by [StorageProxy] (via the callback in [registerHandle]) when it receives storage
   * events. This is responsible for driving the particle lifecycle API after startup and
   * managing the running particle state.
   *
   * Write-only particles should not receive any of these events.
   *
   * This will be executed in the context of the StorageProxy's scheduler.
   * TODO(b/158790341): error handling in event methods
   */
  fun notify(event: StorageEvent, handle: Handle) {
    check(
      particleState in arrayOf(
        ParticleState.Waiting, ParticleState.Running, ParticleState.Desynced
      )
    ) {
      "${planParticle.particleName}: storage events should not be received " +
        "in state $particleState"
    }

    when (event) {
      StorageEvent.READY -> {
        if (awaitingReady.remove(handle) && awaitingReady.isEmpty()) {
          moveToReady()
        }
      }
      StorageEvent.UPDATE -> {
        if (awaitingReady.isEmpty()) {
          particle.onUpdate()
        }
      }
      StorageEvent.DESYNC -> {
        if (desyncedHandles.isEmpty()) {
          particleState = ParticleState.Desynced
          particle.onDesync()
        }
        desyncedHandles.add(handle)
      }
      StorageEvent.RESYNC -> {
        desyncedHandles.remove(handle)
        if (desyncedHandles.isEmpty()) {
          particle.onResync()
          particleState = ParticleState.Running
        }
      }
    }
  }

  private fun moveToReady() {
    particle.onReady()
    particleState = ParticleState.Running
    notifyReady?.invoke(particle)
    notifyReady = null
  }

  private fun markParticleAsFailed(error: Exception, eventName: String): Exception {
    // TODO(b/160251910): Make logging detail more cleanly conditional.
    log.debug(error) { "Failure in particle ${planParticle.particleName}.$eventName()" }
    log.info { "Failure in particle." }

    if (particleState != ParticleState.MaxFailed) {
      consecutiveFailureCount++
      particleState = when {
        consecutiveFailureCount > MAX_CONSECUTIVE_FAILURES ->
          ParticleState.maxFailedWith(error)
        particleState.hasBeenStarted -> ParticleState.failedWith(error)
        else -> ParticleState.failedNeverStartedWith(error)
      }
    }
    return error
  }
}
