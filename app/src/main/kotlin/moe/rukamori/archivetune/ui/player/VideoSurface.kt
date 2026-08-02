/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import moe.rukamori.archivetune.R

/**
 * Inline YouTube IFrame player that plays the music video for [videoId] in
 * place of the player's album artwork — mirroring the "Video" toggle in
 * YouTube Music. The surface fills its parent and is intended to be dropped
 * into the artwork slot of any player style.
 *
 * The original full-screen VideoPlayerScreen showed "Couldn't find a video
 * version of this song" when the IFrame API rejected the embed (error codes
 * 101 / 150 / 2 / 100). That message was misleading: the video exists on
 * YouTube, it just refuses to play embedded. So instead of showing an error,
 * we show a small inline message + an "Open in YouTube" button that hands
 * off to the system YouTube app / browser.
 *
 * State is surfaced via [onEmbeddingBlocked] so the caller can reset the
 * Song/Video pill back to "Song" if desired. We deliberately do NOT auto-open
 * YouTube — that would be jarring.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoSurface(
    videoId: String,
    modifier: Modifier = Modifier,
    onEmbeddingBlocked: () -> Unit = {},
) {
    val context = LocalContext.current
    var isLoading by remember(videoId) { mutableStateOf(true) }
    var embeddingBlocked by remember(videoId) { mutableStateOf(false) }

    val bridge =
        remember(videoId) {
            object {
                @JavascriptInterface
                fun onReady() {
                    isLoading = false
                }

                @JavascriptInterface
                fun onError(errorCode: Int) {
                    // 101 / 150 = embedding not allowed. 100 = video not found.
                    // 2 = invalid videoId parameter. In all error cases the
                    // video may still exist on YouTube — the IFrame player
                    // simply can't show it here.
                    embeddingBlocked = true
                    isLoading = false
                }
            }
        }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    addJavascriptInterface(bridge, "AndroidBridge")
                    webChromeClient =
                        object : WebChromeClient() {
                            override fun onProgressChanged(
                                view: WebView?,
                                newProgress: Int,
                            ) {
                                if (newProgress >= 80) isLoading = false
                            }
                        }
                    webViewClient =
                        object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean = true
                        }
                    tag = videoId
                    loadDataWithBaseURL(
                        "https://www.youtube.com",
                        buildPlayerHtml(videoId),
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            },
            update = { webView ->
                // Reload only if the videoId has actually changed since the
                // last factory pass. This keeps the video from restarting on
                // every recomposition.
                if (webView.tag != videoId) {
                    webView.tag = videoId
                    isLoading = true
                    embeddingBlocked = false
                    webView.loadDataWithBaseURL(
                        "https://www.youtube.com",
                        buildPlayerHtml(videoId),
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            },
            onRelease = { webView ->
                webView.removeJavascriptInterface("AndroidBridge")
                webView.stopLoading()
                webView.onPause()
                webView.destroy()
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(36.dp),
                color = Color.White,
            )
        }

        if (embeddingBlocked) {
            // Inline fallback UI — replaces the video with a clear message
            // and a button. No "couldn't find a video version" lie.
            Column(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.video_open_in_youtube_fallback),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = {
                        runCatching {
                            val intent =
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.youtube.com/watch?v=$videoId"),
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            context.startActivity(intent)
                        }
                        // Tell the host to flip back to Song mode — the user
                        // is going to watch the video in the YouTube app, so
                        // leaving the player stuck on a dead video surface
                        // would be confusing.
                        onEmbeddingBlocked()
                    },
                ) {
                    Text(text = stringResource(R.string.video_open_in_youtube), color = Color.White)
                }
            }
        }
    }
}

private fun buildPlayerHtml(videoId: String): String =
    """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
        <style>
            html, body { margin: 0; padding: 0; height: 100%; width: 100%; background: #000; overflow: hidden; }
            #player { width: 100vw; height: 100vh; }
        </style>
    </head>
    <body>
        <div id="player"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
            (function() {
                var apiReady = false;
                var domReady = false;
                var attemptedStart = false;

                function tryStart() {
                    if (!apiReady || !domReady || attemptedStart) return;
                    attemptedStart = true;
                    new YT.Player('player', {
                        videoId: '$videoId',
                        playerVars: {
                            'autoplay': 1,
                            'controls': 1,
                            'rel': 0,
                            'modestbranding': 1,
                            'playsinline': 1,
                            'fs': 1,
                            'origin': 'https://www.youtube.com'
                        },
                        events: {
                            'onReady': function(e) {
                                e.target.playVideo();
                                if (window.AndroidBridge) { window.AndroidBridge.onReady(); }
                            },
                            'onError': function(e) {
                                if (window.AndroidBridge) { window.AndroidBridge.onError(e.data); }
                            }
                        }
                    });
                }

                window.onYouTubeIframeAPIReady = function() {
                    apiReady = true;
                    tryStart();
                };

                document.addEventListener('DOMContentLoaded', function() {
                    domReady = true;
                    tryStart();
                });
            })();
        </script>
    </body>
    </html>
    """.trimIndent()
