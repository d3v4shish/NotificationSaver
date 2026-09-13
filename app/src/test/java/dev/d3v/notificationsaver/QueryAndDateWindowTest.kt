package dev.d3v.notificationsaver

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueryAndDateWindowTest {
    @Test
    fun toFtsQuery_normalizesTermsWithoutLeavingOperators() {
        assertEquals(
            "\"order\"* AND \"ID-42\"*",
            "  order?!   ID-42  ".toFtsQuery(),
        )
        assertNull("?!  …".toFtsQuery())
    }

    @Test
    fun todayWindow_usesTheProvidedInstantAndZone() {
        val now = Instant.parse("2026-09-13T18:45:00Z").toEpochMilli()

        assertEquals(
            Instant.parse("2026-09-13T00:00:00Z").toEpochMilli(),
            DateWindow.Today.fromTimestamp(now, ZoneOffset.UTC),
        )
        assertEquals(
            Instant.parse("2026-09-13T18:30:00Z").toEpochMilli(),
            DateWindow.Today.fromTimestamp(now, ZoneOffset.ofHoursMinutes(5, 30)),
        )
    }

    @Test
    fun rollingWindow_subtractsTheConfiguredNumberOfDays() {
        val now = Instant.parse("2026-09-13T18:45:00Z").toEpochMilli()

        assertEquals(now - 7L * 24L * 60L * 60L * 1000L, DateWindow.Last7Days.fromTimestamp(now, ZoneOffset.UTC))
        assertNull(DateWindow.AllTime.fromTimestamp(now, ZoneOffset.UTC))
    }
}
