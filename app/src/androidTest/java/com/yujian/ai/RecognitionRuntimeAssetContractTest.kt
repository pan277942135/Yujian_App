package com.yujian.ai

import androidx.test.platform.app.InstrumentationRegistry
import com.yujian.ai.ai.RecognitionRuntimeContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionRuntimeAssetContractTest {
    @Test
    fun packagedV31AssetsAreTheRuntimeTimingAuthority() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val timeline = assets.open("identify/animation/identify_timeline.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        val stateMachine = assets.open("identify/state_machine/identify_state_machine.json").bufferedReader().use {
            JSONObject(it.readText())
        }

        val authorityVersion = "recognition_processing_state_machine_v3_1"
        assertEquals(authorityVersion, timeline.getString("contract_version"))
        assertEquals(authorityVersion, stateMachine.getString("contract_version"))
        assertEquals(authorityVersion, RecognitionRuntimeContract.CONTRACT_VERSION)
        val sequence = timeline.getJSONArray("presentation_sequence")
        assertEquals(RecognitionRuntimeContract.presentationSequence.size, sequence.length())
        RecognitionRuntimeContract.presentationSequence.forEachIndexed { index, state ->
            assertEquals(state.name, sequence.getString(index))
        }

        val rows = timeline.getJSONArray("states")
        assertEquals(RecognitionRuntimeContract.states.size, rows.length())
        val authorityDurations = listOf(220, 320, 350, 250, 0)
        val historicalCopy = listOf(
            "正在准备识别" to "AI 已获取这张照片",
            "正在理解这张照片" to "寻找这次鱼获的线索",
            "已定位到鱼体" to "正在分析这次鱼获",
            "正在认识这条鱼" to "分析鱼体特征",
            "" to "",
        )
        RecognitionRuntimeContract.presentationSequence.forEachIndexed { index, state ->
            assertEquals(state.name, rows.getJSONObject(index).getString("state"))
            assertEquals(authorityDurations[index], rows.getJSONObject(index).getInt("minimum_duration_ms"))
            assertEquals(historicalCopy[index].first, rows.getJSONObject(index).getString("title"))
            assertEquals(historicalCopy[index].second, rows.getJSONObject(index).getString("subtitle"))
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
        assertTrue((0 until states.length()).none { states.getString(it) == "IMAGE_RECOGNIZING" })
        assertTrue((0 until states.length()).none { states.getString(it) == "RESOLVE" })
    }
}
