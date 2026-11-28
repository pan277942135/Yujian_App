package com.yujian.ai.ui.recognition

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionStage10FailureFixtureTest {
    @Test
    fun fixturePreservesDeviceFactsWithoutInventingSessionStoreOrNavigationEvidence() {
        val fixture = JSONObject(
            requireNotNull(javaClass.getResourceAsStream("/recognition/stage10_failure_5344335d6a51.json"))
                .bufferedReader().use { it.readText() },
        )
        assertEquals("DETERMINISTIC_LOGIC_FIXTURE_DERIVED_FROM_DEVICE_JSONL", fixture.getString("fixtureKind"))
        assertFalse(fixture.getBoolean("fullModelInferenceReplay"))
        assertEquals("REAL_DEVICE_EVIDENCE_UNAVAILABLE", fixture.getString("videoStatus"))
        assertEquals("NOT_OBSERVABLE", fixture.getJSONObject("path").getString("sessionStoreBinding"))
        assertEquals("NOT_OBSERVABLE", fixture.getJSONObject("path").getString("navigation"))
        assertEquals(22_444, fixture.getJSONObject("timeline").getInt("stage10EnteredSceneElapsedMs"))
        assertEquals(60_006, fixture.getJSONObject("timeline").getInt("resultFirstPresentedSceneElapsedMs"))
        assertEquals(3, fixture.getJSONObject("path").getInt("returnPathSegmentCount"))
        assertEquals(0, fixture.getJSONObject("returnEvent").getInt("pathSegmentCount"))
        assertTrue(fixture.getJSONObject("returnEvent").getBoolean("geometryMismatchRejected"))
        assertEquals(1, fixture.getJSONObject("capture").getInt("captured"))
    }
}
