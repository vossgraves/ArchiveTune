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
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Drift for the heavily blurred artwork behind lyrics — shared by the
 * Apple-Music-style player's inline lyrics backdrop and the standalone lyrics
 * screen's [moe.rukamori.archivetune.ui.player.LyricsScreen] backdrop, so both
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
 * ### Bounds
 *
 * [WanderRadiusDp] is the largest offset this can ever produce, and callers rely
 * on that: the blurred artwork is drawn scaled up so that it still covers the
 * screen at maximum offset, and the blur (64dp) has to be covered too. At scale
 * 2.4 on a 360dp-wide screen the image overhangs 252dp per edge, against the
 * 150 + 64 = 214dp asked for here.
 *
 * ### Threading / recomposition
 *
 * [xDp] and [yDp] are [FloatState]s meant to be read **only** from draw-phase
 * lambdas (`Modifier.graphicsLayer { }`). Reading them during composition would
 * invalidate the whole player subtree on every animation frame, which is what
 * the lyrics views' frame budget cannot afford.
 */
internal class BlurWanderDrift(
    private val random: Random = Random.Default,
) {
    private val xState = mutableFloatStateOf(0f)
    private val yState = mutableFloatStateOf(0f)

    /** Horizontal offset in dp, in `-WanderRadiusDp..WanderRadiusDp`. */
    val xDp: FloatState get() = xState

    /** Vertical offset in dp, in `-WanderRadiusDp..WanderRadiusDp`. */
    val yDp: FloatState get() = yState

    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
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
    }

    private fun startNextLeg() {
        fromX = toX
        fromY = toY
        // Turn by at least MinTurnRadians so the new leg is never a retread of
        // the one that just ended.
        val turn = MinTurnRadians + random.nextFloat() * (TwoPi - 2f * MinTurnRadians)
        legAngle = (legAngle + turn) % TwoPi
        // sqrt-free radius spread biased outwards: the backdrop reads as more
        // alive when the colours actually reach the edges, so waypoints sit in
        // the outer half of the disc.
        val radius = WanderRadiusDp * (MinRadiusFraction + random.nextFloat() * (1f - MinRadiusFraction))
        toX = cos(legAngle) * radius
        toY = sin(legAngle) * radius * VerticalSquash
        val distance = hypot(toX - fromX, toY - fromY)
        legDurationMs =
            (distance / WanderSpeedDpPerSecond * 1000f)
                .coerceIn(MinLegDurationMs, MaxLegDurationMs)
    }

    internal companion object {
        /** Largest offset the walk can reach, in dp. See the class docs. */
        const val WanderRadiusDp = 150f

        /**
         * Vertical amplitude is squashed a little: phones are taller than they
         * are wide, so the same absolute offset reads as less movement
         * vertically, and the covering budget is tighter horizontally.
         */
        private const val VerticalSquash = 0.8f

        /** Average travel speed. Deliberately slow — this sits behind lyrics. */
        private const val WanderSpeedDpPerSecond = 26f

        private const val MinLegDurationMs = 6_000f
        private const val MaxLegDurationMs = 18_000f

        /** Waypoints are never closer to the centre than this fraction of the radius. */
        private const val MinRadiusFraction = 0.5f

        /** ~72°: enough that a new leg is unmistakably a new direction. */
        private const val MinTurnRadians = 1.25f

        private const val TwoPi = (2.0 * PI).toFloat()
    }
}

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
