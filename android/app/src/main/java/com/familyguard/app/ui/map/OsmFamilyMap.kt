package com.familyguard.app.ui.map

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** OpenStreetMap through osmdroid: free, no API key. */
@Composable
fun OsmFamilyMap(markers: List<MapMarker>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember { createMapView(context) }
    // Zoom to everyone once, then leave the user's pan/zoom alone
    val fitted = remember { booleanArrayOf(false) }

    AndroidView(factory = { mapView }, modifier = modifier) { view ->
        view.overlays.removeAll { it is Marker }
        markers.forEach { m ->
            view.overlays.add(Marker(view).apply {
                position = GeoPoint(m.latitude, m.longitude)
                title = m.title
                snippet = m.subtitle
                icon = dot(context, when {
                    m.isSelf -> Color.rgb(47, 111, 235)   // accent blue
                    m.paused -> Color.GRAY
                    else -> Color.rgb(22, 163, 74)
                })
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            })
        }
        if (!fitted[0] && markers.isNotEmpty()) {
            fitted[0] = true
            val points = markers.map { GeoPoint(it.latitude, it.longitude) }
            view.post {
                if (points.size == 1) {
                    view.controller.setZoom(16.0)
                    view.controller.setCenter(points[0])
                } else {
                    view.zoomToBoundingBox(BoundingBox.fromGeoPoints(points).increaseByScale(1.4f), false)
                }
            }
        }
        view.invalidate()
    }
}

private fun createMapView(context: Context): MapView {
    // The OSM tile servers require an identifying user agent
    Configuration.getInstance().userAgentValue = context.packageName
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        controller.setZoom(12.0)
        controller.setCenter(GeoPoint(41.311, 69.279))  // Tashkent until positions arrive
    }
}

private fun dot(context: Context, color: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    val px = (18 * context.resources.displayMetrics.density).toInt()
    setSize(px, px)
    setColor(color)
    setStroke((3 * context.resources.displayMetrics.density).toInt(), Color.WHITE)
}
