package dev.mattramotar.storex.normalization.internal

import dev.mattramotar.storex.core.Converter
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.normalization.GraphProjection
import dev.mattramotar.storex.normalization.NormalizedWrite


/**
 * Converter used when Store runs against a normalized SOT.
 * - ReadDb is GraphProjection<V> so we can surface freshness to the validator.
 * - WriteDb is NormalizedWrite<K> so SOT can apply change-sets and optional index updates.
 * - NetOut to WriteDb mapping is supplied by the caller (often via codegen/adapter).
 */
class NormalizationConverter<K : StoreKey, V, NetOut>(
    private val toWrite: suspend (key: K, net: NetOut) -> NormalizedWrite<K>
) : Converter<K, V, GraphProjection<V>, NetOut, NormalizedWrite<K>> {

    override suspend fun netToDbWrite(key: K, net: NetOut): NormalizedWrite<K> =
        toWrite(key, net)

    override suspend fun dbReadToDomain(key: K, db: GraphProjection<V>): V =
        db.value

    override suspend fun dbMetaFromProjection(db: GraphProjection<V>): Any? =
        // Your FreshnessValidator can treat this as DefaultDbMeta(updatedAt, etag)
        object {
            val updatedAt = db.meta.updatedAt
            val etag = db.meta.etagFingerprint
        }

    override suspend fun netMeta(net: NetOut) = Converter.NetMeta()
}
