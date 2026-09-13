package moe.rukamori.archivetune.echo.utils.cipher

class RendererRecoveryPolicy(
    private val maxConsecutiveFailures: Int = DEFAULT_MAX_CONSECUTIVE_FAILURES,
    private val backoffMs: Long = DEFAULT_BACKOFF_MS,
) {
    var consecutiveFailures = 0
        private set

    private var backoffUntilMs = 0L

    fun shouldAttempt(nowMs: Long): Boolean =
        consecutiveFailures < maxConsecutiveFailures || nowMs >= backoffUntilMs

    fun onFailure(nowMs: Long) {
        consecutiveFailures++
        if (consecutiveFailures >= maxConsecutiveFailures) {
            backoffUntilMs = nowMs + backoffMs
        }
    }

    fun onSuccess() {
        consecutiveFailures = 0
        backoffUntilMs = 0L
    }

    companion object {
        const val DEFAULT_MAX_CONSECUTIVE_FAILURES = 3
        const val DEFAULT_BACKOFF_MS = 60_000L
    }
}
