package com.zs.trainpaymentreminder

import java.lang.Math.*

data class StationData(val name: String, val location: Location) {
    fun distanceTo(lat1: Double, long1: Double): Double {
        val earthRadius = 6371

        val latDistance = toRadians(location.latitude - lat1)
        val lonDistance = toRadians(location.longitude - long1)

        val a = kotlin.math.sin(latDistance / 2) * kotlin.math.sin(latDistance / 2) + kotlin.math.cos(
            toRadians(lat1)
        ) * kotlin.math.cos(
            toRadians(location.latitude)
        ) * kotlin.math.sin(lonDistance / 2) * kotlin.math.sin(lonDistance / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadius * c * 1000
    }
}

data class ClosestStationResult(val station: StationData, val distance: Double) {
    override fun toString(): String {
        return "${station.name} (${distance}M)"
    }
}