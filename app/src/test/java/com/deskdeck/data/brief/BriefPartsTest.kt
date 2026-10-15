package com.deskdeck.data.brief

import com.deskdeck.data.settings.NightSettings
import com.deskdeck.domain.brief.Watchlist
import com.deskdeck.domain.brief.WeatherCondition
import com.deskdeck.domain.night.NightScheduleCalculator
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BriefPartsTest {

    @Test
    fun `rss items with cdata, entities and dates`() {
        val xml = """
            <rss><channel><title>Feed</title>
            <item><title><![CDATA[Stocks rally &amp; bonds slip]]></title><link>https://example.com/a</link>
              <pubDate>Tue, 13 Oct 2026 21:05:00 GMT</pubDate></item>
            <item><title>Fed&apos;s <b>next</b> move</title><link> https://example.com/b </link></item>
            <item><title>No link</title></item>
            <item><title>Bad link</title><link>javascript:alert(1)</link></item>
            </channel></rss>
        """.trimIndent()
        val items = RssParser.parse(xml, "CNBC")
        assertEquals(listOf("Stocks rally & bonds slip", "Fed's next move"), items.map { it.title })
        assertEquals("https://example.com/b", items[1].url)
        assertEquals(Instant.parse("2026-10-13T21:05:00Z").toEpochMilli(), items[0].publishedAtMs)
        assertNull(items[1].publishedAtMs)
        assertEquals("CNBC", items[0].source)
    }

    @Test
    fun `wmo codes map to plain conditions`() {
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromWmo(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.fromWmo(2))
        assertEquals(WeatherCondition.RAIN, WeatherCondition.fromWmo(63))
        assertEquals(WeatherCondition.SHOWERS, WeatherCondition.fromWmo(81))
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherCondition.fromWmo(95))
        assertEquals(WeatherCondition.CLOUDY, WeatherCondition.fromWmo(1234))
    }

    @Test
    fun `indices use their etf on the free plan, other names are symbols`() {
        assertEquals("SPY", Watchlist.symbolFor("S&P 500"))
        assertEquals("QQQ", Watchlist.symbolFor("nasdaq 100"))
        assertEquals("AAPL", Watchlist.symbolFor(" aapl "))
        assertEquals("BTC/USD", Watchlist.symbolFor("Bitcoin"))
        assertEquals("ETH/USD", Watchlist.symbolFor("eth"))
        assertEquals(true, Watchlist.isCrypto(Watchlist.symbolFor("BTC")))
        assertEquals(false, Watchlist.isCrypto(Watchlist.symbolFor("S&P 500")))
    }

    @Test
    fun `intraday chart uses only the latest session, oldest first`() {
        val bars = listOf(
            BarJson("2026-10-13 15:55:00", "571.5"),
            BarJson("2026-10-13 15:50:00", "571.0"),
            BarJson("2026-10-12 15:55:00", "560.0"),
        )
        assertEquals(listOf(571.0, 571.5), lastSession(bars))
        assertEquals(emptyList<Double>(), lastSession(emptyList()))
    }

    @Test
    fun `prefetch runs 15 minutes before wake-up, or now if that's past`() {
        val wake = Instant.parse("2026-10-14T23:00:00Z")
        assertEquals(Duration.ofMinutes(45), prefetchDelay(wake.minus(Duration.ofHours(1)), wake))
        assertEquals(Duration.ZERO, prefetchDelay(wake.minus(Duration.ofMinutes(5)), wake))
    }

    @Test
    fun `next wake-up for the prefetch`() {
        val kl = ZoneId.of("Asia/Kuala_Lumpur")
        val calc = NightScheduleCalculator(kl)
        val now = LocalDateTime.parse("2026-10-13T22:00").atZone(kl).toInstant()
        assertEquals(LocalDateTime.parse("2026-10-14T07:00").atZone(kl).toInstant(), calc.nextWakeAfter(now, NightSettings(enabled = true)))
        assertNull(calc.nextWakeAfter(now, NightSettings(enabled = false)))
    }

    @Test
    fun `after the prefetch has run, the next one is the following morning, not right away`() {
        val kl = ZoneId.of("Asia/Kuala_Lumpur")
        val calc = NightScheduleCalculator(kl)
        val night = NightSettings(enabled = true)
        // The job runs at 06:45 for the 07:00 wake-up.
        val ranAt = LocalDateTime.parse("2026-10-14T06:45").atZone(kl).toInstant()
        val sameMorning = calc.nextWakeAfter(prefetchSearchFrom(ranAt, afterRun = false), night)!!
        // The bug: looking from "now" finds this morning's wake-up again, so it'd re-run at once.
        assertEquals(Duration.ZERO, prefetchDelay(ranAt, sameMorning))
        val next = calc.nextWakeAfter(prefetchSearchFrom(ranAt, afterRun = true), night)!!
        assertEquals(LocalDateTime.parse("2026-10-15T07:00").atZone(kl).toInstant(), next)
        assertEquals(Duration.ofHours(24), prefetchDelay(ranAt, next))
    }
}

class AirQualityLevelTest {
    @Test
    fun `US AQI bands`() {
        assertEquals(com.deskdeck.domain.brief.AirQualityLevel.GOOD, com.deskdeck.domain.brief.AirQualityLevel.of(0))
        assertEquals(com.deskdeck.domain.brief.AirQualityLevel.GOOD, com.deskdeck.domain.brief.AirQualityLevel.of(50))
        assertEquals(com.deskdeck.domain.brief.AirQualityLevel.MODERATE, com.deskdeck.domain.brief.AirQualityLevel.of(51))
        assertEquals(com.deskdeck.domain.brief.AirQualityLevel.SENSITIVE, com.deskdeck.domain.brief.AirQualityLevel.of(150))
        assertEquals(com.deskdeck.domain.brief.AirQualityLevel.UNHEALTHY, com.deskdeck.domain.brief.AirQualityLevel.of(174))
        assertEquals(com.deskdeck.domain.brief.AirQualityLevel.VERY_UNHEALTHY, com.deskdeck.domain.brief.AirQualityLevel.of(300))
        assertEquals(com.deskdeck.domain.brief.AirQualityLevel.HAZARDOUS, com.deskdeck.domain.brief.AirQualityLevel.of(301))
    }
}
