package com.example.lab6.data

import org.osmdroid.util.GeoPoint

data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    init {
        require(south < north) { "south must be < north" }
        require(west < east) { "west must be < east" }
    }

    val centerLat: Double get() = (south + north) / 2.0
    val centerLon: Double get() = (west + east) / 2.0

    fun contains(lat: Double, lon: Double): Boolean =
        lat in south..north && lon in west..east

    companion object {
        fun fromPoints(a: GeoPoint, b: GeoPoint): GeoBounds {
            val south = minOf(a.latitude, b.latitude)
            val north = maxOf(a.latitude, b.latitude)
            val west = minOf(a.longitude, b.longitude)
            val east = maxOf(a.longitude, b.longitude)
            return GeoBounds(south, west, north, east)
        }
    }
}
