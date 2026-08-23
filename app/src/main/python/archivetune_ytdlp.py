# ArchiveTune (2026)
# © Rukamori — github.com/rukamori
# GPL-3.0 License | Contributors: see git history

import base64
import binascii
import importlib
import json
import os
import sys
import tempfile
import threading
import urllib.parse


_runtime_path = None
_runtime_version = None
_archive_loaded = False
_runtime_lock = threading.Lock()
_DEFAULT_HTTP_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept": "*/*",
    "Accept-Language": "en-US,en;q=0.9",
    "Referer": "https://www.youtube.com/",
    "Origin": "https://www.youtube.com",
}


def _ensure_runtime(runtime_path):
    global _runtime_path, _runtime_version, _archive_loaded
    with _runtime_lock:
        if _runtime_version is not None:
            return
        normalized_path = runtime_path if runtime_path and os.path.isfile(runtime_path) else None
        try:
            version = _load_runtime(normalized_path)
        except Exception:
            if normalized_path is None:
                raise
            _purge_runtime(normalized_path)
            version = _load_runtime(None)
            normalized_path = None
        _runtime_path = normalized_path
        _runtime_version = version
        _archive_loaded = normalized_path is not None


def _load_runtime(runtime_path):
    if runtime_path:
        sys.path.insert(0, runtime_path)
    from yt_dlp.version import __version__
    _register_android_jsc_provider()
    return __version__


def _purge_runtime(runtime_path):
    sys.path = [entry for entry in sys.path if entry != runtime_path]
    for module_name in tuple(sys.modules):
        if (
            module_name == "yt_dlp"
            or module_name.startswith("yt_dlp.")
            or module_name == "yt_dlp_ejs"
            or module_name.startswith("yt_dlp_ejs.")
        ):
            sys.modules.pop(module_name, None)
    importlib.invalidate_caches()


def _register_android_jsc_provider():
    from java import jclass
    from yt_dlp.extractor.youtube.jsc._builtin.ejs import EJSBaseJCP
    from yt_dlp.extractor.youtube.jsc.provider import register_preference, register_provider
    from yt_dlp.extractor.youtube.pot._provider import BuiltinIEContentProvider

    java_runtime = jclass(
        "moe.rukamori.archivetune.morideobfuscator.ytdlp.YtDlpJavaScriptRuntime"
    )

    @register_provider
    class ArchiveTuneJCP(EJSBaseJCP, BuiltinIEContentProvider):
        PROVIDER_NAME = "archivetune"
        JS_RUNTIME_NAME = "archivetune"

        def is_available(self):
            return True

        def _run_js_runtime(self, source):
            return str(java_runtime.evaluate(source))

    @register_preference(ArchiveTuneJCP)
    def archive_tune_preference(provider, requests):
        return 2000


def is_runtime_archive_loaded():
    return _archive_loaded


def _write_cookie_file(cookie_header, directory):
    if not cookie_header:
        return None
    os.makedirs(directory, exist_ok=True)
    handle = tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        prefix="auth_",
        suffix=".cookies",
        dir=directory,
        delete=False,
    )
    try:
        os.chmod(handle.name, 0o600)
        handle.write("# Netscape HTTP Cookie File\n")
        for raw_cookie in cookie_header.split(";"):
            name, separator, value = raw_cookie.strip().partition("=")
            if (
                not separator
                or not name
                or any(character in name or character in value for character in "\r\n\t")
            ):
                continue
            handle.write(
                ".youtube.com\tTRUE\t/\tTRUE\t0\t{}\t{}\n".format(name, value)
            )
        return handle.name
    finally:
        handle.close()


def _extract_expiry(url):
    try:
        values = urllib.parse.parse_qs(urllib.parse.urlparse(url).query).get("expire")
        return int(values[0]) * 1000 if values else 0
    except (TypeError, ValueError, IndexError):
        return 0


def _mime_type(format_info):
    mime_type = format_info.get("mime_type")
    if mime_type:
        return mime_type.split(";", 1)[0]
    extension = (format_info.get("audio_ext") or format_info.get("ext") or "").lower()
    if extension in ("m4a", "mp4"):
        return "audio/mp4"
    if extension in ("webm", "weba"):
        return "audio/webm"
    if extension == "ogg" or extension == "opus":
        return "audio/ogg"
    return "audio/" + extension if extension else "audio/webm"


def _bitrate(format_info):
    value = format_info.get("abr") or format_info.get("tbr") or 0
    try:
        return int(float(value) * 1000)
    except (TypeError, ValueError):
        return 0


def _choose_format(formats, quality, network_metered, pinned_format_id):
    candidates = []
    for format_info in formats or ():
        url = format_info.get("url")
        audio_codec = format_info.get("acodec")
        video_codec = format_info.get("vcodec")
        protocol = (format_info.get("protocol") or "").lower()
        if not url or not audio_codec or audio_codec == "none":
            continue
        if video_codec not in (None, "none"):
            continue
        if protocol and protocol not in ("http", "https"):
            continue
        if format_info.get("has_drm"):
            continue
        candidates.append(format_info)

    if not candidates:
        raise RuntimeError("yt-dlp returned no direct, audio-only format")

    if pinned_format_id:
        pinned = next(
            (
                item
                for item in candidates
                if str(item.get("format_id")) == str(pinned_format_id)
            ),
            None,
        )
        if pinned is not None:
            return pinned

    effective_quality = quality
    if quality == "AUTO":
        effective_quality = "HIGH" if network_metered else "HIGHEST"
    target = {"LOW": 70000, "HIGH": 160000}.get(effective_quality)

    candidates.sort(
        key=lambda item: (
            _bitrate(item),
            int(item.get("asr") or 0),
            str(item.get("format_id") or ""),
        )
    )
    if target is None:
        return candidates[-1]
    not_above_target = [item for item in candidates if _bitrate(item) <= target]
    return not_above_target[-1] if not_above_target else candidates[0]


class _QuietYtDlpLogger:
    def debug(self, message):
        pass

    def info(self, message):
        pass

    def warning(self, message):
        pass

    def error(self, message):
        pass


def _extract_info(youtube_dl, url, youtube_args, cookie_file=None):
    options = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "skip_download": True,
        "socket_timeout": 15,
        "retries": 1,
        "extractor_retries": 1,
        "fragment_retries": 1,
        "extractor_args": {"youtube": youtube_args},
        "js_runtimes": {},
        "remote_components": set(),
        "logger": _QuietYtDlpLogger(),
        "http_headers": dict(_DEFAULT_HTTP_HEADERS),
    }
    if cookie_file:
        options["cookiefile"] = cookie_file
    with youtube_dl(options) as downloader:
        return downloader.extract_info(url, download=False)


def _is_age_verification_required(error):
    return "sign in to confirm your age" in str(error).lower()


def _normalize_po_token(value):
    unpadded = (
        urllib.parse.unquote(value.strip())
        .replace("+", "-")
        .replace("/", "_")
        .rstrip("=")
    )
    padded = unpadded + "=" * ((4 - len(unpadded) % 4) % 4)
    try:
        decoded = base64.b64decode(padded, altchars=b"-_", validate=True)
    except (binascii.Error, ValueError) as error:
        raise ValueError("PO Token must be a base64url-encoded string") from error
    if not decoded:
        raise ValueError("PO Token must not be empty")
    return base64.urlsafe_b64encode(decoded).decode("ascii")


def resolve_audio(request_json, runtime_path, cookie_directory):
    _ensure_runtime(runtime_path)
    from yt_dlp import YoutubeDL
    from yt_dlp.utils import DownloadError

    request = json.loads(request_json)
    cookie_file = _write_cookie_file(request.get("cookie"), cookie_directory)
    try:
        base_youtube_args = {
            "skip": ["hls", "dash", "translated_subs"],
        }
        youtube_args = dict(base_youtube_args)
        visitor_data = request.get("visitor_data")
        if visitor_data:
            youtube_args["visitor_data"] = [visitor_data]
        data_sync_id = request.get("data_sync_id")
        if data_sync_id:
            youtube_args["data_sync_id"] = [data_sync_id]

        po_tokens = []
        gvs_token = request.get("po_token_gvs")
        if gvs_token:
            normalized_gvs_token = _normalize_po_token(gvs_token)
            po_tokens.extend(
                (
                    "web.gvs+" + normalized_gvs_token,
                    "web_music.gvs+" + normalized_gvs_token,
                    "web_creator.gvs+" + normalized_gvs_token,
                )
            )
        player_token = request.get("po_token_player")
        if player_token:
            normalized_player_token = _normalize_po_token(player_token)
            po_tokens.extend(
                (
                    "web.player+" + normalized_player_token,
                    "web_music.player+" + normalized_player_token,
                )
            )
        subs_token = request.get("po_token_subs")
        if subs_token:
            normalized_subs_token = _normalize_po_token(subs_token)
            po_tokens.extend(
                (
                    "web.subs+" + normalized_subs_token,
                    "web_music.subs+" + normalized_subs_token,
                )
            )
        if po_tokens:
            youtube_args["po_token"] = list(dict.fromkeys(po_tokens))
            youtube_args["player_client"] = ["web"]

        cookie_youtube_args = dict(base_youtube_args)
        if po_tokens:
            cookie_youtube_args["po_token"] = list(dict.fromkeys(po_tokens))
            cookie_youtube_args["player_client"] = ["web"]

        url = "https://www.youtube.com/watch?v=" + request["media_id"]
        try:
            info = _extract_info(
                YoutubeDL,
                url,
                youtube_args,
                cookie_file,
            )
        except DownloadError as primary_error:
            if cookie_file is None:
                raise
            try:
                info = _extract_info(
                    YoutubeDL,
                    url,
                    cookie_youtube_args,
                    cookie_file,
                )
            except DownloadError as cookie_context_error:
                if _is_age_verification_required(cookie_context_error):
                    raise cookie_context_error from primary_error
                if _is_age_verification_required(primary_error):
                    raise primary_error from None
                try:
                    info = _extract_info(
                        YoutubeDL,
                        url,
                        dict(base_youtube_args),
                    )
                except DownloadError as anonymous_error:
                    raise anonymous_error from cookie_context_error

        selected = _choose_format(
            info.get("formats"),
            request["quality"],
            bool(request.get("network_metered")),
            request.get("pinned_format_id"),
        )
        stream_url = selected["url"]
        content_length = selected.get("filesize") or 0
        stream_headers = dict(_DEFAULT_HTTP_HEADERS)
        for source_headers in (
            info.get("http_headers") or {},
            selected.get("http_headers") or {},
        ):
            for header_name, header_value in source_headers.items():
                if header_value is not None:
                    stream_headers[str(header_name)] = str(header_value)
        stream_headers.pop("Accept-Encoding", None)
        result = {
            "url": stream_url,
            "headers": stream_headers,
            "format_id": selected.get("format_id") or "",
            "mime_type": _mime_type(selected),
            "codecs": selected.get("acodec") or "",
            "bitrate": _bitrate(selected),
            "sample_rate": int(selected.get("asr") or 0),
            "content_length": int(content_length or 0),
            "expires_at_ms": _extract_expiry(stream_url),
            "title": info.get("title"),
            "duration_seconds": int(info.get("duration") or 0),
            "thumbnail_url": info.get("thumbnail"),
            "runtime_version": _runtime_version,
            "archive_loaded": _archive_loaded,
        }
        return json.dumps(result, separators=(",", ":"))
    finally:
        if cookie_file:
            try:
                os.remove(cookie_file)
            except FileNotFoundError:
                pass
