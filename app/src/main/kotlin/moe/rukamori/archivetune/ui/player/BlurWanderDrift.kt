/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Drift for the heavily blurred artwork behind lyrics — shared by the Apple-Music-style player's
 * inline lyrics backdrop and the standalone lyrics screen's
 * [moe.rukamori.archivetune.ui.player.LyricsScreen] backdrop, so both surfaces move identically.
 */
internal class BlurWanderDrift(
    private val random: Random = Random.Default,
) {
    private val xState = mutableFloatStateOf(0f)
    private val yState = mutableFloatStateOf(0f)
    private val rotationState = mutableFloatStateOf(0f)

    /** Horizontal offset in dp, in `-WanderRadiusDp..WanderRadiusDp`. */
    val xDp: FloatState get() = xState

    /** Vertical offset in dp, in `-WanderRadiusDp..WanderRadiusDp`. */
    val yDp: FloatState get() = yState

    /**
     * Rotation of the backdrop about its own centre, in degrees.
     *
     * Unbounded on purpose. It is an angle, so it wraps for free, and letting it
     * accumulate is what lets a colour keep travelling the same way past a
     * half-turn instead of being tugged back toward a nominal zero.
     */
    val rotationDeg: FloatState get() = rotationState

    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var fromRotation = 0f
    private var toRotation = 0f
    private var legAngle = random.nextFloat() * TwoPi
    private var legDurationMs = 0f
    private var legElapsedMs = 0f

    init {
        startNextLeg()
    }

    /**
     * Advances the walk by [deltaMs] of wall time. Legs roll over inside the
     * same call, so a dropped frame is caught up rather than skipped.
     */
    fun advance(deltaMs: Float) {
        if (deltaMs <= 0f) return
        legElapsedMs += deltaMs
        while (legElapsedMs >= legDurationMs) {
            legElapsedMs -= legDurationMs
            startNextLeg()
        }
        // Raised cosine: zero velocity at both ends of the leg.
        val t = legElapsedMs / legDurationMs
        val eased = 0.5f - 0.5f * cos(PI.toFloat() * t)
        xState.floatValue = fromX + (toX - fromX) * eased
        yState.floatValue = fromY + (toY - fromY) * eased
        rotationState.floatValue = fromRotation + (toRotation - fromRotation) * eased
    }

    private fun startNextLeg() {
        fromX = toX
        fromY = toY
        fromRotation = toRotation
        // Turn by at least MinTurnRadians so the new leg is never a retread of
        // the one that just ended.
        val turn = MinTurnRadians + random.nextFloat() * (TwoPi - 2f * MinTurnRadians)
        legAngle = (legAngle + turn) % TwoPi
        // sqrt-free radius spread biased outwards: the backdrop reads as more
        // alive when the colours actually reach the edges, so waypoints sit in
        // the outer half of the disc.
        val radius = WanderRadiusDp * (MinRadiusFraction + random.nextFloat() * (1f - MinRadiusFraction))
        toX = cos(legAngle) * radius
        // No vertical squash. It used to shave the vertical amplitude to 0.8 on
        // the grounds that the covering budget was tighter horizontally, which
        // was true while panning was the only motion and is not once the budget
        // is measured to a corner. It also worked directly against the symptom
        // rotation is here to fix, by making the axis that already struggled to
        // reach the bottom of the screen the shorter of the two.
        toY = sin(legAngle) * radius
        // Rotation direction is drawn per leg rather than taken from the leg's
        // own angle: tying the two together would make the backdrop appear to
        // roll along its path, which reads as a mechanism instead of as drifting
        // colour.
        val rotationSign = if (random.nextBoolean()) 1f else -1f
        val rotationSpan =
            MinLegRotationDegrees + random.nextFloat() * (MaxLegRotationDegrees - MinLegRotationDegrees)
        toRotation = fromRotation + rotationSign * rotationSpan
        val distance = hypot(toX - fromX, toY - fromY)
        legDurationMs =
            (distance / WanderSpeedDpPerSecond * 1000f)
                .coerceIn(MinLegDurationMs, MaxLegDurationMs)
    }

    internal companion object {
        /**
         * Largest offset the walk can ever produce, in dp. See the class docs.
         *
         * Was 150 while translation was the only motion. Rotation supplies far
         * more travel than those 30dp ever did, and measures its covering budget
         * to the container's corner rather than its edge, so trading a little pan
         * for the headroom is the better deal.
         */
        const val WanderRadiusDp = 120f

        /** Average travel speed. Deliberately slow — this sits behind lyrics. */
        private const val WanderSpeedDpPerSecond = 26f

        private const val MinLegDurationMs = 6_000f
        private const val MaxLegDurationMs = 18_000f

        /**
         * Degrees of rotation one leg may add. Against the ~12s median leg that is
         * roughly 3°/s, so a colour crosses the screen — half a turn — in about a
         * minute: ambient, rather than something you notice while reading lyrics.
         */
        private const val MinLegRotationDegrees = 18f
        private const val MaxLegRotationDegrees = 55f

        /** Waypoints are never closer to the centre than this fraction of the radius. */
        private const val MinRadiusFraction = 0.5f

        /** ~72°: enough that a new leg is unmistakably a new direction. */
        private const val MinTurnRadians = 1.25f

        private const val TwoPi = (2.0 * PI).toFloat()
    }
}

/**
 * Footprint the heavily blurred backdrop layer has to occupy so that [BlurWanderDrift]'s rotation
 * can never swing one of the layer's own corners into view.
 */
internal fun blurBackdropFootprint(
    width: Dp,
    height: Dp,
    restScale: Float,
    driftScale: Float,
    maxDriftDp: Float = BlurWanderDrift.WanderRadiusDp,
): DpSize {
    val w = width.value
    val h = height.value
    if (w <= 0f || h <= 0f || restScale <= 0f || driftScale <= 0f) return DpSize(width, height)

    val corner = hypot(w, h) / 2f
    // Callers ramp scale, translation and rotation off one progress value, so the requirement along
    // the ramp is `2 * (corner + drift * p) / (restScale + (driftScale - restScale) * p)`. That is
    // a Mobius function of p, so it has no interior extremum and the worst case is an endpoint:
    // either resting (no drift, but the smallest scale) or fully drifting (largest scale, but the
    // corner has moved out by the whole wander radius).
    val requiredAtRest = 2f * corner / restScale
    val requiredAtFullDrift = 2f * (corner + maxDriftDp) / driftScale
    val required = max(requiredAtRest, requiredAtFullDrift) * BlurBackdropCoverSafety

    return DpSize(max(w, required).dp, max(h, required).dp)
}

/**
 * A little headroom on [blurBackdropFootprint]'s result. The derivation is exact, so this only
 * absorbs rounding between the dp maths here and the pixel maths the layer is actually rasterised
 * with — 2% is a couple of dp on a phone.
 */
private const val BlurBackdropCoverSafety = 1.02f

/** Remembers a [BlurWanderDrift] and advances it from the frame clock while [active]. */
@Composable
internal fun rememberBlurWanderDrift(active: Boolean): BlurWanderDrift {
    val drift = remember { BlurWanderDrift() }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameNanos != 0L) {
                    drift.advance((frameTimeNanos - lastFrameNanos) / 1_000_000f)
                }
                lastFrameNanos = frameTimeNanos
            }
        }
    }
    return drift
}
