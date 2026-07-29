package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.compat.ConfigurationBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.util.concurrent.CountDownLatch
import kotlin.system.measureTimeMillis

internal class ShutdownTest {

    @Test
    fun `shutdown interrupts blocking upload`() {
        val uploadStarted = CountDownLatch(1)
        val analytics = analyticsWithBlockingUpload(uploadStarted)

        analytics.track("test-event")
        uploadStarted.await()

        val elapsed = measureTimeMillis { analytics.shutdown(waitForTasks = true) }

        assertTrue(elapsed < 2000, "Expected shutdown in <2s, took ${elapsed}ms")
    }

    @Test
    fun `shutdown interrupts multiple blocking uploads`() {
        val uploadStarted = CountDownLatch(1)
        val analytics = analyticsWithBlockingUpload(uploadStarted)

        repeat(3) { analytics.track("event-$it") }
        uploadStarted.await()

        val elapsed = measureTimeMillis { analytics.shutdown(waitForTasks = true) }

        assertTrue(elapsed < 2000, "Expected shutdown in <2s, took ${elapsed}ms")
    }

    @Test
    fun `shutdown without wait returns immediately`() {
        val uploadStarted = CountDownLatch(1)
        val analytics = analyticsWithBlockingUpload(uploadStarted)
        analytics.track("event")
        uploadStarted.await()

        val elapsed = measureTimeMillis { analytics.shutdown(waitForTasks = false) }

        assertTrue(elapsed < 200, "Expected immediate return, took ${elapsed}ms")
    }

    @Test
    fun `shutdown interrupts blocking settings fetch`() {
        val settingsStarted = CountDownLatch(1)
        val factory = object : RequestFactory() {
            override fun upload(apiHost: String): HttpURLConnection =
                throw RuntimeException("not used")

            override fun settings(cdnHost: String, writeKey: String): HttpURLConnection {
                settingsStarted.countDown()
                Thread.sleep(20_000)
                throw RuntimeException("not used")
            }
        }

        val analytics = Analytics(
            ConfigurationBuilder("test-key")
                .setApplication("test-app")
                .setAutoAddSegmentDestination(true)
                .setRequestFactory(factory)
                .build()
        )
        settingsStarted.await()

        val elapsed = measureTimeMillis { analytics.shutdown(waitForTasks = true) }

        assertTrue(elapsed < 2000, "Expected shutdown in <2s, took ${elapsed}ms")
    }

    private fun analyticsWithBlockingUpload(uploadStarted: CountDownLatch): Analytics {
        val factory = object : RequestFactory() {
            override fun upload(apiHost: String): HttpURLConnection {
                uploadStarted.countDown()
                Thread.sleep(20_000)
                throw RuntimeException("not used")
            }

            override fun settings(cdnHost: String, writeKey: String): HttpURLConnection =
                throw RuntimeException("not used")
        }

        return Analytics(
            ConfigurationBuilder("test-key")
                .setApplication("test-app")
                .setFlushAt(1)
                .setAutoAddSegmentDestination(true)
                .setRequestFactory(factory)
                .build()
        )
    }
}
