package com.yujian.ai.ai.subject

/** Conservative v0.1 checks. A bad subject preview is discarded, never shown as a result. */
object FishSubjectQualityGate {
    const val VERSION = "FISH_SUBJECT_QUALITY_GATE_v0.1"
    const val MIN_AREA_RATIO = 0.03f
    const val LARGE_AREA_RATIO = 0.92f
    const val EDGE_MASK_RATIO_WARNING = 0.35f
    const val MASK_THRESHOLD = 0.5f

    fun assess(mask: FloatArray, width: Int, height: Int): FishSubjectQuality {
        if (width <= 0 || height <= 0 || mask.size != width * height) return FishSubjectQuality.INVALID
        val foreground = mask.count { it >= MASK_THRESHOLD }
        if (foreground == 0) return FishSubjectQuality.INVALID
        val areaRatio = foreground.toFloat() / mask.size
        if (areaRatio < MIN_AREA_RATIO) return FishSubjectQuality.INVALID

        // A mask made of many tiny islands is not safe to present as a fish subject.
        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        var components = 0
        var largestComponent = 0
        for (start in mask.indices) {
            if (visited[start] || mask[start] < MASK_THRESHOLD) continue
            components++
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var size = 0
            while (head < tail) {
                val index = queue[head++]
                size++
                val x = index % width
                val y = index / width
                if (x > 0) tail = visit(index - 1, mask, visited, queue, tail)
                if (x + 1 < width) tail = visit(index + 1, mask, visited, queue, tail)
                if (y > 0) tail = visit(index - width, mask, visited, queue, tail)
                if (y + 1 < height) tail = visit(index + width, mask, visited, queue, tail)
            }
            largestComponent = maxOf(largestComponent, size)
        }
        if (components > 12 && largestComponent.toFloat() / foreground < 0.70f) return FishSubjectQuality.WARNING

        var edge = 0
        for (y in 0 until height) for (x in 0 until width) {
            if (mask[y * width + x] >= MASK_THRESHOLD && (x == 0 || y == 0 || x == width - 1 || y == height - 1)) edge++
        }
        val edgeRatio = edge.toFloat() / foreground
        return if (areaRatio > LARGE_AREA_RATIO || edgeRatio > EDGE_MASK_RATIO_WARNING) {
            FishSubjectQuality.WARNING
        } else {
            FishSubjectQuality.GOOD
        }
    }

    private fun visit(index: Int, mask: FloatArray, visited: BooleanArray, queue: IntArray, tail: Int): Int {
        if (!visited[index] && mask[index] >= MASK_THRESHOLD) {
            visited[index] = true
            queue[tail] = index
            return tail + 1
        }
        return tail
    }
}
