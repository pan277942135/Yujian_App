package com.yujian.ai

import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.RecognitionRuntimeContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionRuntimeAssetContractTest {
    @Test
    fun packagedV31PresentationAndStateMachineMatchRuntimeContract() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val timeline = assets.open("identify/animation/identify_timeline.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        val stateMachine = assets.open("identify/state_machine/identify_state_machine.json").bufferedReader().use {
            JSONObject(it.readText())
        }

        assertEquals(RecognitionRuntimeContract.CONTRACT_VERSION, timeline.getString("contract_version"))
        assertEquals(RecognitionRuntimeContract.CONTRACT_VERSION, stateMachine.getString("contract_version"))
        val sequence = timeline.getJSONArray("presentation_sequence")
        assertEquals(RecognitionRuntimeContract.presentationSequence.size, sequence.length())
        RecognitionRuntimeContract.presentationSequence.forEachIndexed { index, state ->
            assertEquals(state.name, sequence.getString(index))
        }

        val rows = timeline.getJSONArray("states")
        assertEquals(RecognitionRuntimeContract.states.size, rows.length())
        RecognitionRuntimeContract.states.forEachIndexed { index, state ->
            val row = rows.getJSONObject(index)
            assertEquals(state.state.name, row.getString("state"))
            assertEquals(state.minimumDurationMs.toInt(), row.getInt("minimum_duration_ms"))
            assertEquals(state.title, row.getString("title"))
            assertEquals(state.subtitle, row.getString("subtitle"))
        }

        val states = stateMachine.getJSONArray("states")
        assertEquals(RecognitionRuntimeContract.presentationSequence.size, states.length())
        RecognitionRuntimeContract.presentationSequence.forEachIndexed { index, state ->
            assertEquals(state.name, states.getString(index))
        }
        assertEquals(RecognitionRuntimeContract.productionPipeline.size, stateMachine.getJSONArray("production_pipeline").length())
        assertEquals(220, stateMachine.getJSONObject("minimum_duration_ms").getInt("CAPTURED"))
        assertEquals(320, stateMachine.getJSONObject("minimum_duration_ms").getInt("DETECTING"))
        assertEquals(350, stateMachine.getJSONObject("minimum_duration_ms").getInt("OUTLINE"))
        assertEquals(250, stateMachine.getJSONObject("minimum_duration_ms").getInt("CLASSIFYING"))
        assertEquals(0, stateMachine.getJSONObject("minimum_duration_ms").getInt("RESULT_READY"))
        assertTrue(stateMachine.getString("result_ready_rule").contains("Immediate handoff"))
        assertFalse((0 until states.length()).any { states.getString(it) == "IMAGE_RECOGNIZING" })
        assertFalse((0 until states.length()).any { states.getString(it) == "RESOLVE" })
    }
}
