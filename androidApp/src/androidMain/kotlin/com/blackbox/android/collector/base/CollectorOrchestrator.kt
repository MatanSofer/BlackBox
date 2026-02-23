package com.blackbox.android.collector.base

import com.blackbox.domain.model.record.CollectorType
import com.blackbox.domain.model.settings.CollectionProfile
import com.blackbox.domain.model.settings.CollectorSetting
import com.blackbox.domain.repository.SettingsRepository
import com.blackbox.domain.util.BlackBoxLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages all registered data collectors.
 *
 * Responsibilities:
 * - Registers and holds references to all [BaseCollector] instances
 * - Starts/stops collectors based on [SettingsRepository] settings
 * - Observes settings changes reactively via [SettingsRepository.observeSettings]
 * - Manages the [RecordBatcher] lifecycle alongside collectors
 * - Propagates the current [CollectionProfile] to all active collectors
 *
 * @property settingsRepository Source of per-collector enable/disable settings.
 * @property recordBatcher Batcher that buffers and flushes records.
 * @property logger Logger for orchestration events.
 */
class CollectorOrchestrator(
    private val settingsRepository: SettingsRepository,
    private val recordBatcher: RecordBatcher,
    private val logger: BlackBoxLogger,
) {

    private val collectors = mutableMapOf<CollectorType, BaseCollector>()
    private var orchestratorScope: CoroutineScope? = null
    private var settingsObserverJob: Job? = null

    private val _collectionProfile = MutableStateFlow(CollectionProfile.NORMAL)

    /** Current battery-adaptive collection profile. */
    val collectionProfile: StateFlow<CollectionProfile> = _collectionProfile.asStateFlow()

    private val _isRunning = MutableStateFlow(false)

    /** Whether the orchestrator is active and managing collectors. */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /**
     * Registers a collector to be managed by this orchestrator.
     * Must be called before [startAll]. Typically called during DI setup.
     *
     * @param collector The collector to register.
     */
    fun register(collector: BaseCollector) {
        collector.setRecordCallback { records ->
            recordBatcher.add(records)
        }
        collectors[collector.collectorType] = collector
        logger.d(TAG, "Registered collector: ${collector.collectorType}")
    }

    /**
     * Starts the orchestrator: begins observing settings and starts
     * all enabled collectors along with the [RecordBatcher].
     */
    suspend fun startAll() {
        if (_isRunning.value) {
            logger.d(TAG, "Already running, ignoring startAll()")
            return
        }

        logger.i(TAG, "Starting orchestrator with ${collectors.size} registered collectors")

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        orchestratorScope = scope

        recordBatcher.start()

        val settings = settingsRepository.getAllSettings()
        applySettings(settings)

        settingsObserverJob = scope.launch {
            settingsRepository.observeSettings().collect { updatedSettings ->
                logger.d(TAG, "Settings changed, re-evaluating collectors")
                applySettings(updatedSettings)
            }
        }

        _isRunning.value = true
        logger.i(TAG, "Orchestrator started")
    }

    /**
     * Stops all active collectors and the record batcher.
     */
    suspend fun stopAll() {
        if (!_isRunning.value) {
            logger.d(TAG, "Already stopped, ignoring stopAll()")
            return
        }

        logger.i(TAG, "Stopping orchestrator")
        settingsObserverJob?.cancel()
        settingsObserverJob = null

        collectors.values.filter { it.isRunning }.forEach { collector ->
            try {
                collector.stop()
            } catch (e: Exception) {
                logger.e(TAG, "Error stopping ${collector.collectorType}", e)
            }
        }

        recordBatcher.stop()
        orchestratorScope?.cancel()
        orchestratorScope = null
        _isRunning.value = false
        logger.i(TAG, "Orchestrator stopped")
    }

    /**
     * Updates the battery-adaptive collection profile.
     * Propagates the new profile to all registered collectors.
     *
     * @param profile The new collection profile to apply.
     */
    fun updateCollectionProfile(profile: CollectionProfile) {
        logger.i(TAG, "Collection profile changed to: ${profile.name}")
        _collectionProfile.value = profile
        collectors.values.forEach { collector ->
            collector.collectionProfile = profile
        }
    }

    /**
     * Returns the running state of a specific collector type.
     */
    fun isCollectorRunning(type: CollectorType): Boolean {
        return collectors[type]?.isRunning == true
    }

    private suspend fun applySettings(settings: List<CollectorSetting>) {
        for (setting in settings) {
            val collector = collectors[setting.collectorType] ?: continue

            if (setting.isEnabled && !collector.isRunning) {
                logger.i(TAG, "Starting ${setting.collectorType} (enabled in settings)")
                try {
                    collector.start()
                } catch (e: Exception) {
                    logger.e(TAG, "Failed to start ${setting.collectorType}", e)
                }
            } else if (!setting.isEnabled && collector.isRunning) {
                logger.i(TAG, "Stopping ${setting.collectorType} (disabled in settings)")
                try {
                    collector.stop()
                } catch (e: Exception) {
                    logger.e(TAG, "Failed to stop ${setting.collectorType}", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "CollectorOrchestrator"
    }
}
