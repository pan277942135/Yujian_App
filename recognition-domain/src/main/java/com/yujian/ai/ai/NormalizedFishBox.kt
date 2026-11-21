package com.yujian.ai.ai

data class NormalizedFishBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
) {
    fun normalized(): NormalizedFishBox {
        val left = minOf(x1, x2).coerceIn(0f, 1f)
        val top = minOf(y1, y2).coerceIn(0f, 1f)
        val right = maxOf(x1, x2).coerceIn(0f, 1f)
        val bottom = maxOf(y1, y2).coerceIn(0f, 1f)
        return NormalizedFishBox(left, top, right, bottom)
    }

    val width: Float get() { val b = normalized(); return (b.x2 - b.x1).coerceAtLeast(0f) }
    val height: Float get() { val b = normalized(); return (b.y2 - b.y1).coerceAtLeast(0f) }
    val areaRatio: Float get() = width * height

    fun touchesEdge(margin: Float): Boolean {
        val b = normalized()
        return b.x1 <= margin || b.y1 <= margin || b.x2 >= 1f - margin || b.y2 >= 1f - margin
    }

    fun expand(ratio: Float): NormalizedFishBox {
        val b = normalized()
        val dx = b.width * ratio
        val dy = b.height * ratio
        return NormalizedFishBox(
            (b.x1 - dx).coerceAtLeast(0f),
            (b.y1 - dy).coerceAtLeast(0f),
            (b.x2 + dx).coerceAtMost(1f),
            (b.y2 + dy).coerceAtMost(1f),
        )
    }
}

data class FishDetection(
    val confidence: Float,
    val box: NormalizedFishBox,
    val className: String = "fish",
) {
    val areaRatio: Float get() = box.areaRatio
}
