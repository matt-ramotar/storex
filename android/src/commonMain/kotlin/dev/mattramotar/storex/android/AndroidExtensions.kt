package dev.mattramotar.storex.android

import dev.mattramotar.storex.core.StoreKey

/**
 * Android-specific extensions and integrations for StoreX.
 *
 * Provides seamless integration with Android platform APIs including Room,
 * DataStore, WorkManager, and AndroidX Lifecycle.
 *
 * **Planned Features** (to be implemented):
 * - Room database SourceOfTruth adapter
 * - DataStore integration for preferences
 * - WorkManager background sync
 * - AndroidX Lifecycle awareness
 * - ViewModelStore integration
 * - LiveData/Flow bridges
 *
 * Example usage (future):
 * ```kotlin
 * // Room integration
 * val store = store<UserKey, User> {
 *     sourceOfTruth = roomSourceOfTruth(userDao)
 *     // ... other configuration
 * }
 *
 * // DataStore integration
 * val prefsStore = store<PrefKey, Preferences> {
 *     sourceOfTruth = dataStoreSourceOfTruth(context.dataStore)
 *     // ... other configuration
 * }
 *
 * // WorkManager background sync
 * store.scheduleSync(
 *     interval = 1.hours,
 *     constraints = Constraints.Builder()
 *         .setRequiredNetworkType(NetworkType.CONNECTED)
 *         .build()
 * )
 * ```
 */
interface RoomSourceOfTruthAdapter<K : StoreKey, Entity> {
    /**
     * Creates a SourceOfTruth backed by a Room DAO.
     */
    // suspend fun read(key: K): Entity?
    // suspend fun write(key: K, value: Entity)
    // suspend fun delete(key: K)
}

/**
 * Configuration for WorkManager background sync.
 */
data class SyncConfig(
    val interval: kotlin.time.Duration,
    val requiresNetwork: Boolean = true,
    val requiresCharging: Boolean = false
)

// TODO: Implement the following in future phases:
// - roomSourceOfTruth(): Factory for Room-backed SourceOfTruth
// - dataStoreSourceOfTruth(): Factory for DataStore-backed SourceOfTruth
// - lifecycleAwareStore(): Store that respects Android Lifecycle
// - Store.asLiveData(): Extension to convert Store Flow to LiveData
// - Store.collectInLifecycle(): Extension for lifecycle-aware collection
// - WorkManagerSync: Background sync using WorkManager
// - ViewModelStoreExtensions: Integration with ViewModel and SavedStateHandle
