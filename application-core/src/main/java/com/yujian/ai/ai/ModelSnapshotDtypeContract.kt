package com.yujian.ai.ai

import org.json.JSONObject

/** Shared by production model loading and the cross-language contract regression. */
object ModelSnapshotDtypeContract {
    fun validate(snapshot: JSONObject, tensorContract: JSONObject): Pair<String, String> {
        require(snapshot.getInt("schema_version") == 1 || snapshot.getInt("schema_version") == 2)
        val input = tensorContract.getJSONArray("inputs").getJSONObject(0).getString("dtype")
        val output = tensorContract.getJSONArray("outputs").getJSONObject(0).getString("dtype")
        val snapshotInput = snapshot.getString("input_dtype")
        val snapshotOutput = snapshot.getString("output_dtype")
        require(snapshotInput.isNotBlank() && snapshotInput == input) { "model snapshot input_dtype mismatch" }
        require(snapshotOutput.isNotBlank() && snapshotOutput == output) { "model snapshot output_dtype mismatch" }
        return snapshotInput to snapshotOutput
    }
}
