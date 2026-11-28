package com.yujian.ai.catches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatchSaveMetadataParserTest {
    @Test
    fun parsesTheRequiredCatchMeasurementsAndUnicodeText() {
        val metadata = CatchSaveMetadataParser.parse("32", "3,6", "上海市青浦区", "清晨湖边的一次收获")

        assertEquals(32.0, metadata.lengthCm!!, 0.0)
        assertEquals(3.6, metadata.weightKg!!, 0.000001)
        assertEquals("上海市青浦区", metadata.location)
        assertEquals("清晨湖边的一次收获", metadata.story)
    }

    @Test
    fun blankValuesRemainNullInsteadOfBecomingZero() {
        val metadata = CatchSaveMetadataParser.parse("", "   ", "", " ")

        assertNull(metadata.lengthCm)
        assertNull(metadata.weightKg)
        assertNull(metadata.location)
        assertNull(metadata.story)
    }

    @Test
    fun rejectsInvalidAndOutOfRangeMeasurementsBeforeUpload() {
        listOf("0", "-2", "NaN", "Infinity", "1000.01", "bad", "1,2,3").forEach { value ->
            runCatching { CatchSaveMetadataParser.parse(value, "", "", "") }
                .onSuccess { throw AssertionError("Accepted invalid length '$value'") }
        }
        listOf("0", "-1", "NaN", "Infinity", "1001", "bad").forEach { value ->
            runCatching { CatchSaveMetadataParser.parse("", value, "", "") }
                .onSuccess { throw AssertionError("Accepted invalid weight '$value'") }
        }
    }
}
