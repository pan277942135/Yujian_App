package com.yujian.ai.ui.recognition

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Marching Squares contour extraction from the actual SlimSAM alpha mask. */
internal object RecognitionMaskContourExtractor {
    private const val DEFAULT_THRESHOLD = 36
    private const val KEY_SCALE = 1024f

    private data class Point(val x: Float, val y: Float)
    private data class Key(val x: Int, val y: Int)
    private data class Edge(val start: Point, val end: Point)

    fun extract(width: Int, height: Int, alpha: ByteArray, threshold: Int = DEFAULT_THRESHOLD): List<RecognitionContourSegment> {
        if (width <= 1 || height <= 1 || alpha.size != width * height) return emptyList()
        val cut = threshold.coerceIn(1, 254)
        val labels = labelForeground(width, height, alpha, cut)
        val largest = labels.sizes.maxOrNull() ?: return emptyList()
        val minComponent = max(4, largest / 200)
        val keep = BooleanArray(labels.sizes.size) { labels.sizes[it] >= minComponent }

        fun sample(vertexX: Int, vertexY: Int): Int {
            val x = vertexX - 1
            val y = vertexY - 1
            if (x !in 0 until width || y !in 0 until height) return 0
            val index = y * width + x
            val label = labels.ids[index]
            if (label < 0 || !keep[label]) return 0
            return alpha[index].toInt() and 0xff
        }

        val edges = ArrayList<Edge>()
        for (cellY in 0..height) {
            for (cellX in 0..width) {
                val tl = sample(cellX, cellY)
                val tr = sample(cellX + 1, cellY)
                val br = sample(cellX + 1, cellY + 1)
                val bl = sample(cellX, cellY + 1)
                val code = (if (tl >= cut) 1 else 0) or
                    (if (tr >= cut) 2 else 0) or
                    (if (br >= cut) 4 else 0) or
                    (if (bl >= cut) 8 else 0)
                if (code == 0 || code == 15) continue
                val top = crossing(Point(cellX - .5f, cellY - .5f), tl, Point(cellX + .5f, cellY - .5f), tr, cut)
                val right = crossing(Point(cellX + .5f, cellY - .5f), tr, Point(cellX + .5f, cellY + .5f), br, cut)
                val bottom = crossing(Point(cellX - .5f, cellY + .5f), bl, Point(cellX + .5f, cellY + .5f), br, cut)
                val left = crossing(Point(cellX - .5f, cellY - .5f), tl, Point(cellX - .5f, cellY + .5f), bl, cut)
                val crossings = buildList {
                    if (top != null) add(0 to top)
                    if (right != null) add(1 to right)
                    if (bottom != null) add(2 to bottom)
                    if (left != null) add(3 to left)
                }.toMap()
                val pairings = when {
                    crossings.size == 2 -> listOf(crossings.keys.toList())
                    crossings.size == 4 -> {
                        val centerInside = (tl + tr + br + bl) * .25f >= cut
                        when (code) {
                            5 -> if (centerInside) listOf(listOf(0, 1), listOf(2, 3))
                                else listOf(listOf(0, 3), listOf(1, 2))
                            10 -> if (centerInside) listOf(listOf(0, 3), listOf(1, 2))
                                else listOf(listOf(0, 1), listOf(2, 3))
                            else -> emptyList()
                        }
                    }
                    else -> emptyList()
                }
                pairings.forEach { pair ->
                    val start = crossings[pair[0]] ?: return@forEach
                    val end = crossings[pair[1]] ?: return@forEach
                    if (distance(start, end) > .001f) edges += Edge(start, end)
                }
            }
        }
        if (edges.isEmpty()) return emptyList()

        val adjacency = HashMap<Key, MutableList<Int>>(edges.size * 2)
        edges.forEachIndexed { index, edge ->
            adjacency.getOrPut(key(edge.start)) { ArrayList(2) }.add(index)
            adjacency.getOrPut(key(edge.end)) { ArrayList(2) }.add(index)
        }
        val used = BooleanArray(edges.size)
        val loops = ArrayList<List<Point>>()
        edges.indices.forEach { firstIndex ->
            if (used[firstIndex]) return@forEach
            val first = edges[firstIndex]
            val startKey = key(first.start)
            var currentKey = key(first.end)
            val points = ArrayList<Point>()
            points += first.start
            points += first.end
            used[firstIndex] = true
            var closed = false
            var remaining = edges.size
            while (remaining-- > 0) {
                if (currentKey == startKey) {
                    closed = true
                    break
                }
                val nextIndex = adjacency[currentKey]?.firstOrNull { !used[it] } ?: break
                used[nextIndex] = true
                val next = edges[nextIndex]
                val nextPoint = if (key(next.start) == currentKey) next.end else next.start
                points += nextPoint
                currentKey = key(nextPoint)
            }
            if (closed && points.size >= 5) {
                val loop = simplify(points.dropLast(1))
                if (loop.size >= 4 && abs(signedArea(loop)) > .0001f) loops += loop
            }
        }
        return loops.flatMap { loop -> toSegments(loop, width, height) }
    }

    private data class Components(val ids: IntArray, val sizes: List<Int>)

    private fun labelForeground(width: Int, height: Int, alpha: ByteArray, threshold: Int): Components {
        val ids = IntArray(width * height) { -1 }
        val queue = IntArray(width * height)
        val sizes = ArrayList<Int>()
        var nextLabel = 0
        for (start in ids.indices) {
            if ((alpha[start].toInt() and 0xff) < threshold || ids[start] >= 0) continue
            var head = 0
            var tail = 0
            ids[start] = nextLabel
            queue[tail++] = start
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val neighbor = ny * width + nx
                    if (ids[neighbor] < 0 && (alpha[neighbor].toInt() and 0xff) >= threshold) {
                        ids[neighbor] = nextLabel
                        queue[tail++] = neighbor
                    }
                }
            }
            sizes += tail
            nextLabel += 1
        }
        return Components(ids, sizes)
    }

    private fun crossing(a: Point, alphaA: Int, b: Point, alphaB: Int, threshold: Int): Point? {
        if ((alphaA >= threshold) == (alphaB >= threshold)) return null
        val denominator = (alphaB - alphaA).toFloat()
        val t = if (abs(denominator) < .001f) .5f else ((threshold - alphaA) / denominator).coerceIn(0f, 1f)
        return Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    private fun key(point: Point) = Key((point.x * KEY_SCALE).roundToInt(), (point.y * KEY_SCALE).roundToInt())
    private fun distance(a: Point, b: Point) = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()

    /** Removes only near-collinear subpixel samples; sharp fins and tails stay in the path. */
    private fun simplify(points: List<Point>): List<Point> {
        if (points.size < 5) return points
        val simplified = points.filterIndexed { index, current ->
            val previous = points[(index - 1 + points.size) % points.size]
            val next = points[(index + 1) % points.size]
            val dx = next.x - previous.x
            val dy = next.y - previous.y
            val length = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(.001f)
            val lineDistance = abs(dy * current.x - dx * current.y + next.x * previous.y - next.y * previous.x) / length
            lineDistance > .22f || distance(previous, next) < .5f
        }
        return if (simplified.size >= 4) simplified else points
    }

    private fun signedArea(points: List<Point>): Float {
        var area = 0f
        points.indices.forEach { index ->
            val a = points[index]
            val b = points[(index + 1) % points.size]
            area += a.x * b.y - b.x * a.y
        }
        return area * .5f
    }

    private fun toSegments(points: List<Point>, width: Int, height: Int): List<RecognitionContourSegment> {
        val result = ArrayList<RecognitionContourSegment>(points.size)
        points.indices.forEach { index ->
            val start = points[index]
            val end = points[(index + 1) % points.size]
            val previous = points[(index - 1 + points.size) % points.size]
            val following = points[(index + 2) % points.size]
            val segmentLength = distance(start, end)
            val c1 = limitedTangent(start, Point(end.x - previous.x, end.y - previous.y), segmentLength)
            val c2 = limitedTangent(end, Point(start.x - following.x, start.y - following.y), segmentLength)
            result += RecognitionContourSegment(
                startX = (start.x / width).coerceIn(0f, 1f),
                startY = (start.y / height).coerceIn(0f, 1f),
                endX = (end.x / width).coerceIn(0f, 1f),
                endY = (end.y / height).coerceIn(0f, 1f),
                control1X = (c1.x / width).coerceIn(0f, 1f),
                control1Y = (c1.y / height).coerceIn(0f, 1f),
                control2X = (c2.x / width).coerceIn(0f, 1f),
                control2Y = (c2.y / height).coerceIn(0f, 1f),
                beginsContour = index == 0,
                closesContour = index == points.lastIndex,
            )
        }
        return result
    }

    private fun limitedTangent(origin: Point, delta: Point, segmentLength: Float): Point {
        var x = delta.x * .10f
        var y = delta.y * .10f
        val length = hypot(x.toDouble(), y.toDouble()).toFloat()
        val cap = segmentLength * .24f
        if (length > cap && length > .001f) {
            val scale = cap / length
            x *= scale
            y *= scale
        }
        return Point(origin.x + x, origin.y + y)
    }
}
