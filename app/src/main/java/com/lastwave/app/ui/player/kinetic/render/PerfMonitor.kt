package com.lastwave.app.ui.player.kinetic.render

/**
 * Development-only frame statistics (shown only when the "Performance overlay"
 * option is on). Allocation-free: numbers are smoothed with exponential
 * averages and the overlay text is rebuilt at most twice per second.
 */
class PerfMonitor {
    var fps = 0f; private set
    var frameMs = 0f; private set
    var renderMs = 0f; private set
    var activeWords = 0
    var particles = 0
    var stars = 0

    private var lastNanos = 0L
    private var textAt = 0L
    var text: String = ""; private set

    fun onFrame(nowNanos: Long) {
        if (lastNanos != 0L) {
            val ms = (nowNanos - lastNanos) / 1_000_000f
            if (ms in 0.5f..250f) {
                frameMs += (ms - frameMs) * 0.1f
                fps = if (frameMs > 0.01f) 1000f / frameMs else 0f
            }
        }
        lastNanos = nowNanos
    }

    fun onRender(ns: Long) {
        val ms = ns / 1_000_000f
        renderMs += (ms - renderMs) * 0.1f
    }

    fun refreshText(nowNanos: Long) {
        if (nowNanos - textAt < 500_000_000L) return
        textAt = nowNanos
        text = "%.0f fps  frame %.1f ms  render %.1f ms  words %d  particles %d  stars %d"
            .format(fps, frameMs, renderMs, activeWords, particles, stars)
    }
}
