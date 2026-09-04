/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The pink/red accent used by the iOS-inspired music UI redesign.
 *
 * Matches the vibrant magenta-pink of iOS system pink (#FF2D55) used as the
 * accent color throughout the redesigned History / Liked / Cached / Playlist
 * pages: section labels, primary action button text & icons, active tab indicator.
 *
 * Chosen because it remains highly legible against both the dark-mode surface
 * (deep black/charcoal) AND a light-mode surface (off-white): the saturated
 * pink has enough chroma and luminance contrast to pass WCAG AA against both
 * backgrounds, so the hero's accent text (section label, Play/Shuffle labels,
 * and pill icons) reads cleanly in either theme.
 */
val AppleMusicStyleAccentColor: Color = Color(0xFFFF375C)

/**
 * A compact, iOS-inspired playlist/album hero header that matches the user's
 * reference screenshots:
 *
 *   • Small pink uppercase section label (e.g. "RECENTLY PLAYED")
 *   • Large bold page title (left-aligned, SF Pro-like)
 *   • Subtitle/metadata line in muted gray
 *   • Rounded pill-shaped "Play" and "Shuffle" controls with pink text/icons
 *   • Optional trailing icon action (e.g. Clear/Overflow)
 *
 * Unlike [MediaDetailHero], this header does NOT render a large artwork
 * backdrop — it sits on the page's plain dark background so the visual rhythm
 * matches the reference (large title + clean controls + song list).
 *
 * Existing callers continue to use [MediaDetailHero] unchanged; this is only
 * used by the redesigned History, Liked Songs, Cached Songs and Playlist
 * pages.
 *
 * ## Light-mode contrast
 * The title, subtitle, and pill container colors are derived from
 * [MaterialTheme.colorScheme] so the hero remains legible against the light
 * theme surface as well as the dark theme surface. The pink accent
 * ([AppleMusicStyleAccentColor]) is intentionally NOT theme-derived — it is
 * a saturated brand pink that has acceptable contrast on both light and dark
 * surfaces and matches the iOS Music reference.
 *
 * @param sectionLabel Small uppercase accent label (e.g. "RECENTLY PLAYED").
 *                     Pass null to omit.
 * @param title Large bold page title.
 * @param subtitle Optional metadata line below the title (e.g. "20 songs • 1h 12m").
 * @param onPlay Play action callback. If null, the Play button is hidden.
 * @param onShuffle Shuffle action callback. If null, the Shuffle button is hidden.
 * @param onPrimaryTrailing Optional icon-button action to the right of Play/Shuffle
 *                          (e.g. Clear history, More options). Pass null to omit.
 * @param primaryTrailingIcon The icon drawable for [onPrimaryTrailing].
 * @param primaryTrailingDescription Content description for [onPrimaryTrailing].
 * @param additionalActions Optional extra actions rendered after the trailing icon
 *                          (e.g. download state indicator for playlists).
 * @param modifier Modifier for the container.
 */
@Composable
fun AppleMusicPlaylistHero(
    sectionLabel: String?,
    title: String,
    subtitle: String?,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    onPrimaryTrailing: (() -> Unit)? = null,
    @DrawableRes primaryTrailingIcon: Int? = null,
    @StringRes primaryTrailingDescription: Int? = null,
    additionalActions: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val accent = AppleMusicStyleAccentColor
    // Theme-aware foreground colors so the hero remains legible against both
    // the dark theme (Color.Black-ish surface) and the light theme
    // (off-white surface). Using `onBackground` keeps the title at full
    // contrast against the page surface, while `onSurfaceVariant` gives the
    // metadata line the muted-gray look the reference screenshots have.
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        sectionLabel?.let { label ->
            Text(
                text = label.uppercase(),
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.08.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Text(
            text = title,
            color = onBackgroundColor,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            lineHeight = 38.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        subtitle?.let {
            Text(
                text = it,
                color = subtitleColor,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        val hasActions = onPlay != null || onShuffle != null ||
            onPrimaryTrailing != null || additionalActions != null
        if (hasActions) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Play and Shuffle pills share the available horizontal space
                // evenly (each gets weight 1f), matching the reference layout
                // where Play and Shuffle sit side-by-side as roughly
                // equal-width pills, and any trailing circular action
                // (download / clear / overflow) sits as a fixed-size circle
                // on the right end.
                //
                // ── Why no Spacer before the trailing icon ──
                //
                // The previous implementation inserted
                // `Spacer(Modifier.weight(1f))` between Shuffle and the
                // trailing icon to push the trailing icon all the way to the
                // right edge of the screen. That meant Play + Shuffle + Spacer
                // each got 1/3 of the remaining width (after subtracting the
                // trailing icon's 46.dp and the gaps), so each pill ended up
                // only ~80dp wide — not enough for the icon (20dp) + 8dp gap +
                // label ("Play"/"Shuffle" at 16sp bold ≈ 35-50dp) + the 22dp
                // horizontal padding on each side (= 44dp). The label hit
                // `maxLines = 1` + `TextOverflow.Ellipsis` and truncated to
                // "P..." and "S...".
                //
                // Dropping the Spacer lets Play + Shuffle each take 1/2 of
                // the remaining width (~127dp on a 320dp screen), which is
                // plenty for the full label. The trailing icon naturally sits
                // at the right edge because the parent Row has
                // `Arrangement.spacedBy(10.dp)` and Play/Shuffle take weight
                // 1f — the trailing icon's fixed 46dp naturally lands at the
                // right end with no extra push needed.
                onPlay?.let { play ->
                    PillActionButton(
                        text = stringResource(moe.rukamori.archivetune.R.string.play),
                        icon = moe.rukamori.archivetune.R.drawable.play,
                        accent = accent,
                        primary = true,
                        onClick = play,
                        modifier = Modifier.weight(1f),
                    )
                }
                onShuffle?.let { shuffle ->
                    PillActionButton(
                        text = stringResource(moe.rukamori.archivetune.R.string.shuffle),
                        icon = moe.rukamori.archivetune.R.drawable.shuffle,
                        accent = accent,
                        primary = false,
                        onClick = shuffle,
                        modifier = Modifier.weight(1f),
                    )
                }
                additionalActions?.invoke()
                if (onPrimaryTrailing != null && primaryTrailingIcon != null) {
                    TrailingIconButton(
                        icon = primaryTrailingIcon,
                        description = primaryTrailingDescription,
                        onClick = onPrimaryTrailing,
                        accent = accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun PillActionButton(
    text: String,
    @DrawableRes icon: Int,
    accent: Color,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The pill container color is a low-alpha tint of `onBackground` so it
    // reads as a subtle frosted surface against both the dark and light page
    // surface. Previously this was a `Color.White.copy(alpha = ...)` tint
    // which on a light-mode page made the pill almost invisible (white on
    // off-white) and on a dark-mode page made it look like a solid white
    // slab — neither matched the reference's subtle translucent pill.
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val containerColor = onBackgroundColor.copy(alpha = if (primary) 0.10f else 0.06f)
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .height(46.dp),
        shape = RoundedCornerShape(percent = 50),
        color = containerColor,
    ) {
        // Per user request (2026-08-28): "the play and shuffle icon and
        // text are not centred in their pills. Fix it. not just in history
        // page but everywhere. Fix it."
        //
        // Surface defaults its content slot to TopStart alignment, so
        // without `fillMaxHeight()` the Row below sits at the top of the
        // 46dp pill (taking only ~22dp of intrinsic height) and leaves a
        // visible gap at the bottom — the icon and text read as
        // top-aligned rather than vertically centered within the pill.
        // Forcing the Row to fill the Surface's height lets the existing
        // `verticalAlignment = Alignment.CenterVertically` actually
        // center the icon + text within the full pill height.
        Row(
            modifier =
                Modifier
                    .fillMaxHeight()
                    // Reduced from 22.dp to 16.dp so the pill label has more
                    // horizontal breathing room. The previous 22.dp padding
                    // on each side (= 44.dp total) plus the icon (20.dp) plus
                    // the 8.dp gap left only ~9.dp for the label on a 1/3-width
                    // pill allocation, which truncated "Play" → "P..." and
                    // "Shuffle" → "S..." (see the parent Row comment for the
                    // full-width breakdown). 16.dp is still generous enough
                    // to read as a pill-shaped capsule, and matches the
                    // reference's slightly tighter pill proportions.
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TrailingIconButton(
    @DrawableRes icon: Int,
    @StringRes description: Int?,
    onClick: () -> Unit,
    accent: Color,
) {
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val containerColor = onBackgroundColor.copy(alpha = 0.06f)
    Surface(
        modifier =
            Modifier
                .size(46.dp)
                .clip(CircleShape),
        shape = CircleShape,
        color = containerColor,
    ) {
        // fillMaxHeight() so the IconButton is vertically centered within
        // the 46dp pill, matching the centered look of the PillActionButton
        // next to it (per user request 2026-08-28: "the play and shuffle
        // icon and text are not centred in their pills. Fix it. not just
        // in history page but everywhere. Fix it.").
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.IconButton(onClick = onClick) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = description?.let { stringResource(it) },
                    tint = onBackgroundColor.copy(alpha = 0.78f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
