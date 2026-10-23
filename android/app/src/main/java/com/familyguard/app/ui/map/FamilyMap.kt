package com.familyguard.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** One person on the family map. */
data class MapMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,        // "Мама", "Старший брат", "Я"
    val subtitle: String,     // "5 мин назад", "на паузе"
    val isSelf: Boolean = false,
    val paused: Boolean = false
)

/**
 * The family map used by the app. Screens only see this function, so
 * another provider (Yandex MapKit, 2GIS) is plugged in by writing one more
 * implementation like [OsmFamilyMap] and calling it here.
 */
@Composable
fun FamilyMap(markers: List<MapMarker>, modifier: Modifier = Modifier) {
    OsmFamilyMap(markers, modifier)
}
