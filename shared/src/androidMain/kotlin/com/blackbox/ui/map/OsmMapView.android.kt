package com.blackbox.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blackbox.domain.model.map.LocationStay
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

// ── Obsidian palette (matches BlackBoxColors) ─────────────────────────────────
private const val COLOR_INDIGO     = 0xFF6366F1.toInt() // regular stay dot
private const val COLOR_ROSE       = 0xFFF472B6.toInt() // selected stay dot
private const val COLOR_DOT_BORDER = 0xFF0A0A0F.toInt() // dot outline
// Teal #14B8A6 at ~65% alpha — route line between dots
private val COLOR_POLYLINE = Color.argb(165, 20, 184, 166)

/**
 * CartoDB Dark Matter tile source.
 *
 * Near-black background with white roads and minimal grey labels — perfectly
 * matched to the Obsidian UI theme. Tiles are downloaded on first use and
 * cached by OSMDroid for subsequent offline access.
 *
 * Attribution: © CartoDB  © OpenStreetMap contributors
 */
private val CARTO_DARK_MATTER = XYTileSource(
    "CartoDB.DarkMatter",
    0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/",
    ),
    "© CartoDB  © OpenStreetMap contributors",
)

/**
 * Android actual for [OsmMapView].
 *
 * Renders an OSMDroid [MapView] inside an [AndroidView]. Each [LocationStay]
 * is drawn as a filled circle whose diameter scales with time spent there,
 * and whose centre contains a sequential visit number:
 *
 * | Duration       | Diameter |
 * |----------------|----------|
 * | < 15 min       | 22 dp    |
 * | 15 min – 1 h   | 30 dp    |
 * | 1 h – 4 h      | 40 dp    |
 * | > 4 h          | 52 dp    |
 *
 * A dashed teal [Polyline] connects all stays in chronological order so the
 * user can see the day's journey at a glance.
 *
 * The selected stay is highlighted in rose; all others are rendered in indigo.
 * Tapping a dot calls [onStayTapped] with that stay; tapping the map background
 * calls [onStayTapped] with null (deselect).
 *
 * Tile source: CartoDB Dark Matter (dark tiles, cached after first load).
 */
@Composable
actual fun OsmMapView(
    stays: List<LocationStay>,
    selectedStay: LocationStay?,
    onStayTapped: (LocationStay?) -> Unit,
    playbackStay: LocationStay?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialise OSMDroid once per composable instance.
    val mapView = remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = context.packageName
        }
        MapView(context).apply {
            setTileSource(CARTO_DARK_MATTER)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            controller.setZoom(15.0)
            // Obsidian background shown while tiles are loading or not yet cached
            overlayManager.tilesOverlay.loadingBackgroundColor = Color.parseColor("#0A0A0F")
            overlayManager.tilesOverlay.loadingLineColor       = Color.parseColor("#1C1C27")
        }
    }

    // Plain array (not Compose state) — tracks which stays were last fitted so we
    // never call fitBounds just because the selection changed or the Flow re-emitted.
    // Index 0: last fitted list; null means the map has never been positioned.
    val lastFittedStays = remember { arrayOfNulls<List<LocationStay>>(1) }

    // Tracks the last playback stay so we only animate when it actually changes.
    val lastPlaybackStay = remember { arrayOfNulls<LocationStay>(1) }

    // Pause/resume tiles with the screen lifecycle.
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = mapView.onResume()
            override fun onPause(owner: LifecycleOwner) = mapView.onPause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { mv ->
            mv.overlays.clear()

            // Background tap → deselect
            mv.overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        onStayTapped(null)
                        return true
                    }
                    override fun longPressHelper(p: GeoPoint?) = false
                }),
            )

            if (stays.isEmpty()) {
                mv.invalidate()
                return@AndroidView
            }

            val density = mv.resources.displayMetrics.density

            // ── 1. Route polyline (drawn first → dots appear on top) ──────────
            if (stays.size > 1) {
                val polyline = Polyline(mv).apply {
                    setPoints(stays.map { GeoPoint(it.latitude, it.longitude) })
                    infoWindow = null
                    outlinePaint.apply {
                        color = COLOR_POLYLINE
                        strokeWidth = density * 2.5f
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        // Dashed line: 12dp dash, 6dp gap
                        pathEffect = DashPathEffect(
                            floatArrayOf(density * 12f, density * 6f), 0f,
                        )
                    }
                }
                mv.overlays.add(polyline)
            }

            // ── 2. Stay dots with sequential visit numbers ────────────────────
            stays.forEachIndexed { index, stay ->
                val isSelected = stay == selectedStay
                val color = if (isSelected) COLOR_ROSE else COLOR_INDIGO
                val sizeDp = dotSizeDp(stay.durationMs)
                val bitmap = createCircleBitmap(
                    density = density,
                    sizeDp = sizeDp,
                    fillColor = color,
                    orderNumber = index + 1,
                )

                val marker = Marker(mv).apply {
                    position = GeoPoint(stay.latitude, stay.longitude)
                    icon = BitmapDrawable(mv.resources, bitmap)
                    setAnchor(0.5f, 0.5f)
                    setInfoWindow(null)
                    setOnMarkerClickListener { _, _ ->
                        onStayTapped(stay)
                        true
                    }
                    title = stay.knownPlace?.name
                        ?: "(%.4f, %.4f)".format(stay.latitude, stay.longitude)
                }
                mv.overlays.add(marker)
            }

            // ── 3. Animate camera to current playback stay ────────────────────
            if (playbackStay != null && playbackStay != lastPlaybackStay[0]) {
                lastPlaybackStay[0] = playbackStay
                mv.controller.animateTo(GeoPoint(playbackStay.latitude, playbackStay.longitude))
                mv.controller.setZoom(17.0)
            }

            // ── 4. Fit bounds only when the stays data itself changes ─────────
            // On first load use instant positioning; on date changes animate.
            if (stays != lastFittedStays[0]) {
                val animate = lastFittedStays[0] != null
                lastFittedStays[0] = stays
                fitBounds(mv, stays, animate)
            }

            mv.invalidate()
        },
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Maps stay duration to a dot diameter in dp. */
private fun dotSizeDp(durationMs: Long): Int = when {
    durationMs < 15 * 60_000L          -> 22
    durationMs < 60 * 60_000L          -> 30
    durationMs < 4 * 60 * 60_000L      -> 40
    else                               -> 52
}

/**
 * Draws a filled circle with a thin dark border and a bold visit-order number
 * centred inside it.
 *
 * @param density Screen density for px conversion.
 * @param sizeDp  Diameter of the circle in dp.
 * @param fillColor ARGB fill colour.
 * @param orderNumber 1-based visit sequence number drawn in white inside the circle.
 */
private fun createCircleBitmap(
    density: Float,
    sizeDp: Int,
    fillColor: Int,
    orderNumber: Int,
): Bitmap {
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val radius = cx - density // 1dp inset for border

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
        alpha = 230
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_DOT_BORDER
        style = Paint.Style.STROKE
        strokeWidth = density * 1.5f
    }
    canvas.drawCircle(cx, cy, radius, fillPaint)
    canvas.drawCircle(cx, cy, radius, borderPaint)

    // Bold white number centred in the dot.
    // Font size = 45% of dot diameter so "10" still fits in the smallest dot.
    val label = "$orderNumber"
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sizePx * 0.45f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    val textBounds = Rect()
    textPaint.getTextBounds(label, 0, label.length, textBounds)
    canvas.drawText(label, cx, cy + textBounds.height() / 2f, textPaint)

    return bmp
}

/**
 * Zooms the map to show all stays with a comfortable margin.
 *
 * @param animate True for animated pan/zoom (date navigation); false for instant
 *   positioning (first load) — avoids the "ghost zoom from the ocean" artefact.
 */
private fun fitBounds(mapView: MapView, stays: List<LocationStay>, animate: Boolean) {
    if (stays.isEmpty()) return
    val points = stays.map { GeoPoint(it.latitude, it.longitude) }
    if (points.size == 1) {
        if (animate) mapView.controller.animateTo(points.first())
        else mapView.controller.setCenter(points.first())
        mapView.controller.setZoom(16.0)
        return
    }
    val box = BoundingBox.fromGeoPoints(points).increaseByScale(1.3f)
    mapView.post { mapView.zoomToBoundingBox(box, animate, if (animate) 64 else 0) }
}
