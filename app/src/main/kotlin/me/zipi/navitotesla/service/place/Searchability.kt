package me.zipi.navitotesla.service.place

sealed class Searchability {
    object Searchable : Searchability()

    object NotSearchable : Searchability()

    object Unknown : Searchability()
}
