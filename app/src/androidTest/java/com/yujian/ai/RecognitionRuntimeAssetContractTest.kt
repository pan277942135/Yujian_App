package com.yujian.ai

import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.RecognitionRuntimeContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionRuntimeAssetContractTest {
    @Test
    fun packagedPresentationTimelineAndStateMachineMatchRuntimeContract() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val timeline = assets.open("identify/animation/identify_timeline.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        val stateMachine = assets.open("identify/state_machine/identify_state_machine.json").bufferedReader().use {
            JSONObject(it.readText())
        }

        assertEquals(RecognitionRuntimeContract.CONTRACT_VERSION, timeline.getString("contract_version"))
        assertEquals(RecognitionRuntimeContract.CONTRACT_VERSION, stateMachine.getString("contract_version"))
        val steps = timeline.getJSONArray("timeline_ms")
        assertEquals(RecognitionRuntimeContract.timeline.size, steps.length())
        RecognitionRuntimeContract.timeline.forEachIndexed { index, step ->
            assertEquals(step.startMs.toInt(), steps.getJSONObject(index).getInt("time"))
            assertEquals(step.state.name, steps.getJSONObject(index).getString("state"))
            assertEquals(step.label, steps.getJSONObject(index).getString("label"))
        }
        val states = stateMachine.getJSONArray("states")
        assertEquals("IMAGE_RECOGNIZING", states.getString(0))
        assertEquals("SPECIES_RECOGNIZING", states.getString(2))
        assertEquals(10, states.length())
        val flow = stateMachine.getJSONObject("flow")
        assertEquals(4, flow.getJSONArray("IMAGE_RECOGNIZING").length())
        assertEquals(3, flow.getJSONArray("RESOLVE").length())
        assertTrue((0 until states.length()).none { states.getString(it) == "DETECTING" })
    }
}
