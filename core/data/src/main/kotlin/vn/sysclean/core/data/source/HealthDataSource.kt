package vn.sysclean.core.data.source

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import vn.sysclean.core.data.repository.BatteryRepository
import vn.sysclean.core.data.repository.DeviceInfoRepository
import vn.sysclean.core.data.repository.HealthRepository
import vn.sysclean.core.data.repository.HealthSnapshot
import vn.sysclean.core.data.repository.StorageRepository
import vn.sysclean.core.data.repository.UserPreferencesRepository
import vn.sysclean.core.domain.HealthScoreCalculator
import vn.sysclean.core.model.BatteryInfo
import vn.sysclean.core.model.MemoryInfo
import vn.sysclean.core.model.StorageInfo
import vn.sysclean.core.privilege.AccessRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class HealthDataSource @Inject constructor(
    private val storageRepository: StorageRepository,
    private val deviceInfoRepository: DeviceInfoRepository,
    private val batteryRepository: BatteryRepository,
    private val preferences: UserPreferencesRepository,
    private val accessRepository: AccessRepository,
) : HealthRepository {

    private val invalidations = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(memoryIntervalMillis: Long): Flow<HealthSnapshot> {
        // Storage is expensive to query: re-read when junk changes (scan, clean), when usage
        // access flips (the breakdown appears) and once a minute for files added meanwhile.
        val storage = combine(
            preferences.lastSafeJunkBytes,
            accessRepository.state.map { it.usageAccess }.distinctUntilChanged(),
            ticker(STORAGE_REFRESH_MILLIS),
            invalidations,
        ) { _, _, tick, _ -> tick }
            .mapLatest { storageRepository.storageInfo() }
            .orNullFirst()

        return combine(
            storage,
            // Restarting the sampler reads RAM right away and keeps the interval from there.
            invalidations.flatMapLatest { deviceInfoRepository.memory(memoryIntervalMillis) }.orNullFirst(),
            batteryRepository.battery().orNullFirst(),
            preferences.lastSafeJunkBytes,
        ) { storageInfo, memory, battery, junk -> snapshot(storageInfo, memory, battery, junk) }
    }

    override suspend fun current(): HealthSnapshot = snapshot(
        storage = runCatching { storageRepository.storageInfo() }.getOrNull(),
        memory = withTimeoutOrNull(SOURCE_TIMEOUT) { deviceInfoRepository.memory().first() },
        battery = withTimeoutOrNull(SOURCE_TIMEOUT) { batteryRepository.battery().first() },
        junk = preferences.lastSafeJunkBytes.first(),
    )

    override fun invalidate() = invalidations.update { it + 1 }

    private fun snapshot(storage: StorageInfo?, memory: MemoryInfo?, battery: BatteryInfo?, junk: Long?) = HealthSnapshot(
        // Storage is the heaviest factor; without it the number would jump once it arrives.
        score = storage?.let { HealthScoreCalculator.calculate(it, memory, battery, junk) },
        storage = storage,
        memory = memory,
        battery = battery,
        safeJunkBytes = junk,
    )

    private fun ticker(periodMillis: Long): Flow<Long> = flow {
        var tick = 0L
        while (true) {
            emit(tick++)
            delay(periodMillis)
        }
    }

    private fun <T> Flow<T>.orNullFirst(): Flow<T?> = map<T, T?> { it }.onStart { emit(null) }

    private companion object {
        const val STORAGE_REFRESH_MILLIS = 60_000L
        const val SOURCE_TIMEOUT = 3_000L
    }
}
