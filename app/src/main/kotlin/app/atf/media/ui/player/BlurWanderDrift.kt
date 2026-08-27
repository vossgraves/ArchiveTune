/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package app.atf.media.ui.player

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
 * Drift for the heavily blurred artwork behind lyrics — shared by the
 * Apple-Music-style player's inline lyrics backdrop and the standalone lyrics
 * screen's [app.atf.media.ui.player.LyricsScreen] backdrop, so both
 * surfaces move identically.
 *
 * ### Why a random walk and not a formula
 *
 * The first version was a pair of `RepeatMode.Reverse` tweens: the artwork swept
 * to one extreme and turned around at full speed, which read as the colours
 * whipping back. Replacing it with a Lissajous path (two sines per axis off one
 * ever-advancing phase) removed the hard turnaround but not the underlying
 * problem — a closed periodic path still spends half its cycle travelling back
 * the way it came, so the colours were still seen "suddenly travelling in the
 * opposite direction", just more smoothly.
 *
 * This drives the drift as a walk between random waypoints instead:
 *
 *  * each leg carries the artwork from where it currently rests to a new
 *    waypoint drawn at random inside a disc of [WanderRadiusDp],
 *  * each leg also turns the artwork by a random angle — see [rotationDeg],
 *  * the interpolation is a raised cosine, so speed starts and ends at zero —
 *    the artwork *finishes its path*, settles, and only then sets off again,
 *    which means no reversal ever happens while it is moving,
 *  * the next waypoint is at least [MinTurnRadians] away in angle from the leg
 *    that just finished, so the colours visibly "come again and spread" in a
 *    genuinely different direction rather than retracing the previous path,
 *  * legs are timed off their own length at a constant [WanderSpeedDpPerSecond],
 *    so a long leg doesn't race and a short one doesn't crawl.
 *
 * The walk starts at the centre, so the first frame after the backdrop appears
 * has zero offset and the drift grows out of nothing.
 *
 * ### Why translation alone was not enough
 *
 * Translating the backdrop moves every colour by the *same* vector, so their
 * arrangement is rigid: whatever sits in the top third of the artwork stays in
 * the top third of the screen, free to shuffle by at most [WanderRadiusDp] —
 * under a fifth of a phone's height. That is why some colours "only spread at
 * the top and never reach the bottom" however long you watch. No amount of
 * panning can carry them there; it was never a matter of tuning the path.
 *
 * [rotationDeg] is what breaks the rigidity. Turning the artwork about its centre
 * sweeps a colour from one edge to the opposite one over half a turn, so the
 * whole surface genuinely gets visited — and it costs one more property on a
 * layer that is already composited offscreen for the blur, so no extra pass and
 * no second image. Rotation walks per leg like the offsets do, rather than
 * spinning at a constant rate, which keeps the same "settle, then set off again"
 * character and stops it reading as a turntable.
 *
 * ### Bounds
 *
 * [WanderRadiusDp] is the largest offset this can ever produce, and callers rely
 * on that: the blurred artwork is drawn scaled up so that it still covers the
 * screen at maximum offset.
 *
 * Rotation changes that sum, because a rotated rectangle only reliably covers its
 * own inscribed circle — so the budget must be measured to the container's
 * furthest *corner* rather than its nearest edge. Scaling up is not enough on its
 * own: `Modifier.blur`'s default `BlurredEdgeTreatment.Rectangle` clips the layer
 * it creates to the composable's bounds, so what actually rotates is a rectangle
 * of exactly those bounds — its inscribed circle has the radius of the *short*
 * side, which on a phone is far too small however much the layer is scaled. See
 * [blurBackdropFootprint], which is what callers use to size the layer.
 *
 * ### Threading / recomposition
 *
 * [xDp], [yDp] and [rotationDeg] are [FloatState]s meant to be read **only**
 * from draw-phase lambdas (`Modifier.graphicsLayer { }`). Reading them during
 * composition would invalidate the whole player subtree on every animation
 * frame, which is what the lyrics views' frame budget cannot afford.
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
 *
 * ### Why the layer cannot simply be the container
 *
 * The backdrop is built as `Modifier.graphicsLayer { scale / translate / rotate }.blur(radius)`:
 * the blur is applied to the still, centred artwork and the drift transform is applied to the
 * blurred result. `Modifier.blur`'s default `BlurredEdgeTreatment.Rectangle` sets `clip = true` on
 * the layer it creates, so the blurred result is an opaque rectangle of **exactly the composable's
 * bounds** — not the `max(W, H)` square that `ContentScale.Crop` painted into it, which is clipped
 * away before the transform ever runs.
 *
 * A rectangle rotated by an arbitrary angle only reliably covers its own inscribed circle, whose
 * radius comes from the rectangle's *short* side. With the layer sized to the container, that is
 * `scale * min(W, H) / 2` — 432dp on a 360x800 phone at 2.4x — while the point that has to stay
 * covered is the container's furthest corner plus the drift, `hypot(W, H) / 2 + WanderRadiusDp` =
 * 559dp. The 127dp shortfall is not theoretical: it is a near-black wedge sweeping through a corner
 * of the lyrics backdrop in time with the rotation, and it is there even at zero drift (432 against
 * 439 for the bare corner).
 *
 * ### What this returns
 *
 * A footprint whose *shorter* side is long enough that the inscribed circle reaches that corner.
 * Only the short side grows: `max(width, height)` comes out unchanged in every real window shape,
 * and because `ContentScale.Crop` of a square artwork renders it at side `max(w, h)`, that means
 * the artwork is still rasterised at exactly the same scale and the backdrop looks identical. The
 * extra area exists purely for the rotation to swing into.
 *
 * ### Cost
 *
 * A bigger layer is a bigger offscreen buffer — 1.3x the container's area for the lyrics screen,
 * up to ~2x for the Apple-Music player, whose resting scale is the tighter constraint. Sized
 * against correctness that is the right way round: the exposed corner is on screen for a large
 * share of all rotation angles, on every device, whereas this is one background layer that only
 * exists while the player is.
 *
 * If it ever needs to come down, the lever is resolution rather than footprint: halving the
 * footprint while doubling the caller's scales and halving its blur radius is pixel-identical (the
 * on-screen blur is `radius * scale`, the visible window is `containerSide / scale` of an artwork
 * drawn at `max(footprint)`, and the coverage product `scale * footprint` is unchanged) at a
 * quarter of the pixels.
 *
 * @param restScale the scale the layer sits at while it carries no drift and no rotation (equal to
 *   [driftScale] for surfaces that never ramp).
 * @param driftScale the scale the layer reaches once it carries the full drift and rotation.
 * @param maxDriftDp the largest translation the walk can produce, i.e. [BlurWanderDrift.WanderRadiusDp].
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

/**
 * Remembers a [BlurWanderDrift] and advances it from the frame clock while
 * [active].
 *
 * The loop is gated because it is the only thing keeping the frame clock busy:
 * an `InfiniteTransition` (what this replaced) keeps asking for a frame every
 * ~16ms for as long as it is composed, even when nothing reads its value — so
 * the player kept the whole Compose frame pipeline awake while sitting on the
 * cover with no drift on screen. When [active] goes false the walk freezes where
 * it is and resumes from there, so closing and reopening lyrics doesn't restart
 * the path.
 */
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
