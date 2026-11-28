package com.yujian.ai.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ModelSnapshotDtypeContractTest {
    private val assets = File("src/main/assets")
    private fun json(name: String) = JSONObject(File(assets, name).readText())

    @Test fun pythonGeneratedMainSnapshotMatchesRuntimeTensorContract() {
        val snapshot = json("model_release_contract.json")
        val tensor = json("tensor_contract.json")
        assertEquals(2, snapshot.getInt("schema_version"))
        val expected = tensor.getJSONArray("inputs").getJSONObject(0).getString("dtype") to
            tensor.getJSONArray("outputs").getJSONObject(0).getString("dtype")
        assertEquals(expected, ModelSnapshotDtypeContract.validate(snapshot, tensor))
        for (field in listOf("input_dtype", "output_dtype")) {
            for (change in listOf("REMOVE", "float16", "")) {
                val broken = JSONObject(snapshot.toString())
                if (change == "REMOVE") broken.remove(field) else broken.put(field, change)
                try {
                    ModelSnapshotDtypeContract.validate(broken, tensor)
                    fail("$field $change must reject")
                } catch (_: org.json.JSONException) {
                    // Missing field: the production parser must fail closed.
                } catch (_: IllegalArgumentException) {
                    // Mismatch: the production parser must fail closed.
                }
            }
        }
    }
}
