package com.theveloper.pixelplay.data.network.musicbrainz

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Enforces MusicBrainz's 1 request/second rate limit.
 * Blocks the calling thread if requests arrive too quickly.
 */
class MusicBrainzThrottleInterceptor : Interceptor {

    private val minIntervalMs = 1100L

    @Volatile
    private var lastRequestTime = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRequestTime
        if (elapsed < minIntervalMs) {
            Thread.sleep(minIntervalMs - elapsed)
        }
        lastRequestTime = System.currentTimeMillis()
        return chain.proceed(chain.request())
    }
}
