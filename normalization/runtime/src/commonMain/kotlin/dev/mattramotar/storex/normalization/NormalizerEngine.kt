package dev.mattramotar.storex.normalization

import dev.mattramotar.storex.normalization.format.NormalizedRecord
import dev.mattramotar.storex.normalization.format.NormalizedValue
import dev.mattramotar.storex.normalization.keys.EntityKey
import dev.mattramotar.storex.normalization.schema.DenormalizationContext
import dev.mattramotar.storex.normalization.schema.EntityAdapter
import dev.mattramotar.storex.normalization.schema.NormalizationContext
import dev.mattramotar.storex.normalization.schema.SchemaRegistry
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.normalization.backend.NormalizationBackend
import dev.mattramotar.storex.normalization.backend.RootRef
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


class NormalizerEngine(
    private val registry: SchemaRegistry,
    private val backend: NormalizationBackend,
    private val index: IndexManager
) {
    /**
     * Decomposes [data] (entity or list) into a NormalizedChangeSet and applies it atomically.
     * Also updates the list/index roots for [requestKey].
     */
    suspend fun normalizeAndWrite(requestKey: StoreKey, data: Any): NormalizedChangeSet {
        val upserts = LinkedHashMap<EntityKey, NormalizedRecord>()
        val masks   = LinkedHashMap<EntityKey, Set<String>>()
        val metas   = LinkedHashMap<EntityKey, EntityMeta>()
        val roots   = ArrayList<EntityKey>()

        val ctx = object : NormalizationContext {
            override fun registerNested(entity: Any): EntityKey {
                val adapter: EntityAdapter<Any> =
                    registry.getAdapter(entity::class as kotlin.reflect.KClass<Any>)
                val key = adapter.key(entity)
                if (key !in upserts) {
                    val (rec, mask) = adapter.normalize(entity, this)
                    upserts[key] = rec
                    masks[key] = mask
                    metas[key] = EntityMeta() // default meta; adapter can augment via a decorator
                }
                return key
            }
        }

        when (data) {
            is Iterable<*> -> data.filterNotNull().forEach { roots += ctx.registerNested(it) }
            else -> roots += ctx.registerNested(data)
        }

        val change = NormalizedChangeSet(upserts = upserts, fieldMasks = masks, meta = metas)
        backend.apply(change)
        index.updateIndex(requestKey, roots)
        return change
    }
}

class RecomposerEngine<V: Any>(
    private val registry: SchemaRegistry,
    private val backend: NormalizationBackend,
    private val index: IndexManager,
    private val shape: Shape<V>
) {
    fun stream(requestKey: StoreKey): Flow<List<Any?>> {
        val rootRef = RootRef(requestKey, shape.id)
        val triggers: Flow<Unit> = backend.rootInvalidations
            .map { roots -> if (roots.any { it == rootRef }) Unit else null }
            .filterNotNull()
            .onStart { emit(Unit) } // initial compose

        return index.streamIndex(requestKey).flatMapLatest { roots ->
            if (roots.isNullOrEmpty()) flowOf(emptyList())
            else triggers.transformLatest { emit(recompose(rootRef, roots)) }
        }
    }

    private suspend fun recompose(root: RootRef, rootIds: List<EntityKey>): List<Any?> {
        val seen = LinkedHashSet<EntityKey>()
        val toVisit = ArrayDeque<EntityKey>().apply { addAll(rootIds) }
        val records = LinkedHashMap<EntityKey, NormalizedRecord?>()
        val dependencies = LinkedHashSet<EntityKey>()

        fun collectOutboundRefs(rec: NormalizedRecord?): Set<EntityKey> =
            if (rec == null) emptySet() else shape.outboundRefs(rec)

        while (toVisit.isNotEmpty()) {
            val batch = buildList<EntityKey> {
                while (toVisit.isNotEmpty() && size < 256) add(toVisit.removeFirst())
            }.filterNot(seen::contains)

            if (batch.isEmpty()) continue
            seen += batch
            records += backend.read(batch.toSet())

            batch.forEach { key ->
                val rec = records[key]
                dependencies += key
                collectOutboundRefs(rec).forEach { ref ->
                    if (ref !in seen) toVisit.add(ref)
                }
            }
        }

        backend.updateRootDependencies(root, dependencies)

        val resolver = object : DenormalizationContext {
            override suspend fun resolveReference(key: EntityKey): Any? {
                val rec = records[key] ?: backend.read(setOf(key))[key]
                @Suppress("UNCHECKED_CAST")
                val adapter: EntityAdapter<Any> = registry.getAdapter(key.typeName)
                return rec?.let { adapter.denormalize(it, this) }
            }
        }

        return rootIds.map { k ->
            val rec = records[k]
            val adapter: EntityAdapter<Any> = registry.getAdapter(k.typeName)
            rec?.let { adapter.denormalize(it, resolver) }
        }
    }
}



class InMemoryNormalizationBackend : NormalizationBackend {
    private val lock = Mutex()

    private val records = LinkedHashMap<EntityKey, MutableMap<String, NormalizedValue>>()
    private val metas   = LinkedHashMap<EntityKey, EntityMeta>()

    private val rootToDeps = LinkedHashMap<RootRef, MutableSet<EntityKey>>()
    private val depToRoots = LinkedHashMap<EntityKey, MutableSet<RootRef>>()

    private val entitySignal = MutableSharedFlow<Set<EntityKey>>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val rootSignal = MutableSharedFlow<Set<RootRef>>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val entityInvalidations: Flow<Set<EntityKey>> get() = entitySignal
    override val rootInvalidations: Flow<Set<RootRef>> get() = rootSignal

    override suspend fun read(keys: Set<EntityKey>): Map<EntityKey, NormalizedRecord?> =
        lock.withLock { keys.associateWith { k -> records[k]?.toMap() } }

    override suspend fun readMeta(keys: Set<EntityKey>): Map<EntityKey, EntityMeta?> =
        lock.withLock { keys.associateWith { k -> metas[k] } }

    override suspend fun apply(changeSet: NormalizedChangeSet) {
        val changed = LinkedHashSet<EntityKey>()
        val impactedRoots = LinkedHashSet<RootRef>()

        lock.withLock {
            // 1) Rekey first (provisional → canonical)
            for ((from, to) in changeSet.rekeys) {
                val rec = records.remove(from)
                val meta = metas.remove(from)
                if (rec != null) records[to] = rec
                if (meta != null) metas[to] = meta

                // Rewrite references across all stored records
                records.forEach { (_, r) ->
                    r.forEach { (field, v) ->
                        when (v) {
                            is NormalizedValue.Ref ->
                                if (v.key == from) r[field] = NormalizedValue.Ref(to)
                            is NormalizedValue.RefList -> {
                                val newKeys = v.keys.map { if (it == from) to else it }
                                r[field] = NormalizedValue.RefList(newKeys)
                            }
                            else -> Unit
                        }
                    }
                }
                // Update dependency index
                depToRoots[from]?.toList()?.forEach { root ->
                    depToRoots.getOrPut(to) { LinkedHashSet() }.add(root)
                }
                depToRoots.remove(from)
                rootToDeps.forEach { (_, deps) ->
                    if (deps.remove(from)) deps.add(to)
                }

                changed += listOf(from, to)
            }

            // 2) Upserts with PATCH-safe field masks
            for ((key, rec) in changeSet.upserts) {
                val mask = changeSet.fieldMasks[key]
                val dst = records.getOrPut(key) { LinkedHashMap() }
                if (mask == null || mask.isEmpty()) {
                    dst.clear(); dst.putAll(rec)
                } else {
                    mask.forEach { field -> rec[field]?.let { dst[field] = it } }
                }
                changeSet.meta[key]?.let { metas[key] = it }
                changed += key
            }

            // 3) Deletes (tombstone meta retained)
            for (key in changeSet.deletes) {
                records.remove(key)
                metas[key] = (metas[key] ?: EntityMeta()).copy(tombstone = true)

                depToRoots[key]?.let { impactedRoots.addAll(it) }
                val roots = depToRoots.remove(key).orEmpty()
                roots.forEach { root -> rootToDeps[root]?.remove(key) }

                changed += key
            }

            // Compute impacted roots from entity changes
            for (k in changed) depToRoots[k]?.let { impactedRoots.addAll(it) }
        }

        if (changed.isNotEmpty()) entitySignal.tryEmit(changed)
        if (impactedRoots.isNotEmpty()) rootSignal.tryEmit(impactedRoots)
    }

    override suspend fun updateRootDependencies(root: RootRef, dependsOn: Set<EntityKey>) {
        val maybeImpacted = LinkedHashSet<RootRef>()
        lock.withLock {
            val old = rootToDeps[root] ?: emptySet()
            // remove old deps
            old.forEach { dep -> depToRoots[dep]?.remove(root) }
            // set new deps
            rootToDeps[root] = dependsOn.toMutableSet()
            dependsOn.forEach { dep -> depToRoots.getOrPut(dep) { LinkedHashSet() }.add(root) }
            if (old != dependsOn) maybeImpacted += root
        }
        if (maybeImpacted.isNotEmpty()) rootSignal.tryEmit(maybeImpacted)
    }

    override suspend fun clear() {
        TODO("Not yet implemented")
    }
}
