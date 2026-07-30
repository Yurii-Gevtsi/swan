package com.gysignalstudio.blackswan

import com.gysignalstudio.blackswan.data.local.AssetDataLoader
import com.gysignalstudio.blackswan.data.remote.RemoteDataManifest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteDataSyncTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `manifest json parses into RemoteDataManifest`() {
        val json = """
            {
              "schemaVersion": 1,
              "dataVersion": "20260717T120000Z",
              "generatedAt": "2026-07-17T12:00:00.000Z",
              "files": ["osint_events.json", "map_event_groups.json"]
            }
        """.trimIndent()

        val manifest = moshi.adapter(RemoteDataManifest::class.java).fromJson(json)!!

        assertEquals("20260717T120000Z", manifest.dataVersion)
        assertEquals(listOf("osint_events.json", "map_event_groups.json"), manifest.files)
    }

    @Test
    fun `parseEvents reads a full event snapshot`() {
        val json = """
            {
              "schemaVersion": 1,
              "recordCount": 1,
              "events": [{
                "id": "event_20260715_wk_test",
                "status": "PUBLISHED",
                "titleEn": "Test refinery event",
                "titleUk": "Тестова подія НПЗ",
                "date": "2026-07-15",
                "datePrecision": "DAY",
                "category": "INFRASTRUCTURE_DISRUPTION",
                "eventScope": "TERRITORIAL_RUSSIA",
                "theater": "RUSSIA_INTERNAL",
                "regionId": null,
                "federalDistrictId": null,
                "maritimeAreaId": null,
                "sanctionsJurisdictionId": null,
                "approximateLocationLabelEn": "Ryazan Oblast (generalized)",
                "approximateLocationLabelUk": "Рязанська область (узагальнено)",
                "lat": 54.6,
                "lng": 39.7,
                "radiusKm": 60,
                "precision": "REGION_LEVEL",
                "assetId": null,
                "actor": "UNATTRIBUTED",
                "actorConfidence": "MEDIA_REPORTED",
                "actorNote": "",
                "verificationStatus": "MEDIA_REPORTED_WITH_OFFICIAL_REFERENCE",
                "severity": "HIGH",
                "summaryEn": "Test summary.",
                "summaryUk": "Тестовий опис.",
                "impactTags": "Refinery, Test",
                "sources": "source_test_2026_07_15",
                "safetyNotes": "Location generalized.",
                "createdAt": "2026-07-17T12:00:00Z",
                "updatedAt": "2026-07-17T12:00:00Z"
              }]
            }
        """.trimIndent()

        val events = AssetDataLoader.parseEvents(json)

        assertEquals(1, events.size)
        assertEquals("event_20260715_wk_test", events[0].id)
        assertTrue(events[0].titleUk.contains("НПЗ"))
    }

    @Test
    fun `parseEvents returns empty list for empty snapshot`() {
        val events = AssetDataLoader.parseEvents("""{"schemaVersion":1,"recordCount":0,"events":[]}""")
        assertTrue(events.isEmpty())
    }
}
