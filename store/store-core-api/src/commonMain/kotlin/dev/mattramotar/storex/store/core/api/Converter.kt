package dev.mattramotar.storex.store.core.api

interface Converter<Network, Local, Domain> {
    fun fromNetworkToLocal(network: Network): Local
    fun fromLocalToDomain(local: Local): Domain
    fun fromDomainToLocal(domain: Domain): Local
}