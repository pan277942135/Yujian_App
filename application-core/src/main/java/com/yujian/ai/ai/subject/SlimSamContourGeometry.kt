package com.yujian.ai.ai.subject

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class PixelBox(val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
  /** Reference-domain boxes use coordinate differences; mask pixel extents stay inclusive. */
  val width: Int get() = x2 - x1
  val height: Int get() = y2 - y1
  val area: Int get() = width.coerceAtLeast(0) * height.coerceAtLeast(0)
}

data class ContourPoint(val x: Float, val y: Float)

data class CubicBezierSegment(
  val control1X: Float,
  val control1Y: Float,
  val control2X: Float,
  val control2Y: Float,
  val endX: Float,
  val endY: Float,
)

enum class BoundaryTerminationReason {
  INVALID_INPUT,
  ISOLATED_PIXEL,
  NO_NEIGHBOR,
  REPEATED_STATE,
  RETURNED_TO_START,
  MAX_STEPS,
}

data class ContourData(
  val rawPoints: List<ContourPoint>,
  val simplifiedPoints: List<ContourPoint>,
  val startPoint: ContourPoint,
  val bezierSegments: List<CubicBezierSegment>,
  val totalLength: Float,
  val rawPointCount: Int,
  val simplifiedPointCount: Int,
  val extractionTimeMs: Long,
  val traceClosed: Boolean,
  val terminationReason: BoundaryTerminationReason,
)

data class ConnectedComponentResult(
  val cleanMask: ByteArray,
  val pixelCount: Int,
  val bbox: PixelBox,
  val startX: Int,
  val startY: Int,
)

data class BoundaryTraceResult(
  val points: List<ContourPoint>,
  val traceClosed: Boolean,
  val terminationReason: BoundaryTerminationReason,
  val steps: Int,
)

/**
 * Pure Kotlin geometry algorithms ported directly from `src/utils/contourExtraction.ts`.
 */
object SlimSamContourGeometry {

  private inline fun isForeground(b: Byte): Boolean = (b.toInt() and 0xFF) > 127

  /**
   * Extracts the Largest Connected Component (LCC) from a binary mask using 4-connected BFS.
   */
  fun getLargestConnectedComponent(
    mask: ByteArray,
    width: Int,
    height: Int,
  ): ConnectedComponentResult {
    val total = width * height
    if (total <= 0 || mask.size < total) {
      return ConnectedComponentResult(ByteArray(0), 0, PixelBox(0, 0, 0, 0), -1, -1)
    }

    val visited = ByteArray(total)
    val queue = IntArray(total)
    val cleanMask = ByteArray(total)

    var maxCount = 0
    var bestMinX = width
    var bestMinY = height
    var bestMaxX = 0
    var bestMaxY = 0
    var bestStartX = -1
    var bestStartY = -1

    for (y in 0 until height) {
      val rowOffset = y * width
      for (x in 0 until width) {
        val idx = rowOffset + x
        if (!isForeground(mask[idx]) || visited[idx].toInt() != 0) continue

        var head = 0
        var tail = 0
        var compSize = 0
        queue[tail++] = idx
        visited[idx] = 1

        var minX = x
        var maxX = x
        var minY = y
        var maxY = y

        while (head < tail) {
          val curr = queue[head++]
          compSize++
          val cx = curr % width
          val cy = curr / width

          if (cx < minX) minX = cx
          if (cx > maxX) maxX = cx
          if (cy < minY) minY = cy
          if (cy > maxY) maxY = cy

          if (cx > 0) {
            val n = curr - 1
            if (isForeground(mask[n]) && visited[n].toInt() == 0) {
              visited[n] = 1
              queue[tail++] = n
            }
          }
          if (cx < width - 1) {
            val n = curr + 1
            if (isForeground(mask[n]) && visited[n].toInt() == 0) {
              visited[n] = 1
              queue[tail++] = n
            }
          }
          if (cy > 0) {
            val n = curr - width
            if (isForeground(mask[n]) && visited[n].toInt() == 0) {
              visited[n] = 1
              queue[tail++] = n
            }
          }
          if (cy < height - 1) {
            val n = curr + width
            if (isForeground(mask[n]) && visited[n].toInt() == 0) {
              visited[n] = 1
              queue[tail++] = n
            }
          }
        }

        if (compSize > maxCount) {
          maxCount = compSize
          cleanMask.fill(0)
          for (i in 0 until tail) cleanMask[queue[i]] = 0xFF.toByte()
          bestMinX = minX
          bestMinY = minY
          bestMaxX = maxX
          bestMaxY = maxY
          bestStartX = x
          bestStartY = y
        }
      }
    }

    return ConnectedComponentResult(
      cleanMask = cleanMask,
      pixelCount = maxCount,
      bbox = if (maxCount > 0) PixelBox(bestMinX, bestMinY, bestMaxX, bestMaxY) else PixelBox(0, 0, 0, 0),
      startX = bestStartX,
      startY = bestStartY,
    )
  }

  /**
   * Moore-Neighbor Tracing algorithm to trace the exterior perimeter of the primary mask component.
   */
  fun extractBoundaryTrace(
    mask: ByteArray,
    width: Int,
    height: Int,
    startHintX: Int = -1,
    startHintY: Int = -1,
    maxStepsOverride: Int? = null,
  ): BoundaryTraceResult {
    if (width <= 0 || height <= 0 || mask.size < width * height) {
      return BoundaryTraceResult(
        points = emptyList(),
        traceClosed = false,
        terminationReason = BoundaryTerminationReason.INVALID_INPUT,
        steps = 0,
      )
    }

    var startX = startHintX
    var startY = startHintY

    if (
      startX < 0 ||
      startY < 0 ||
      startX >= width ||
      startY >= height ||
      !isForeground(mask[startY * width + startX])
    ) {
      startX = -1
      startY = -1
      outer@ for (y in 0 until height) {
        val rowOff = y * width
        for (x in 0 until width) {
          if (isForeground(mask[rowOff + x])) {
            startX = x
            startY = y
            break@outer
          }
        }
      }
    }

    if (startX == -1) {
      return BoundaryTraceResult(
        points = emptyList(),
        traceClosed = false,
        terminationReason = BoundaryTerminationReason.INVALID_INPUT,
        steps = 0,
      )
    }

    val dx = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
    val dy = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)

    var backtrackDir = 6
    val westX = startX + dx[6]
    val westY = startY + dy[6]
    if (westX in 0 until width && westY in 0 until height && isForeground(mask[westY * width + westX])) {
      for (dir in 0 until 8) {
        val bx = startX + dx[dir]
        val by = startY + dy[dir]
        if (bx !in 0 until width || by !in 0 until height || !isForeground(mask[by * width + bx])) {
          backtrackDir = dir
          break
        }
      }
    }

    val boundary = ArrayList<ContourPoint>(512)
    var currX = startX
    var currY = startY
    boundary.add(ContourPoint(currX.toFloat(), currY.toFloat()))

    val maxSteps = maxStepsOverride ?: max(width * height * 2, 16)
    val visitedDirectedEdges = HashSet<Long>(min(maxSteps, 2048))
    var firstNextX = -1
    var firstNextY = -1
    var firstNextDir = -1
    var steps = 0

    while (steps < maxSteps) {
      steps++
      var nextX = -1
      var nextY = -1
      var nextDir = -1

      for (i in 0 until 8) {
        val dir = (backtrackDir + 1 + i) % 8
        val nx = currX + dx[dir]
        val ny = currY + dy[dir]
        if (nx in 0 until width && ny in 0 until height) {
          if (isForeground(mask[ny * width + nx])) {
            nextX = nx
            nextY = ny
            nextDir = dir
            break
          }
        }
      }

      if (nextX == -1) {
        val reason = if (steps == 1) {
          BoundaryTerminationReason.ISOLATED_PIXEL
        } else {
          BoundaryTerminationReason.NO_NEIGHBOR
        }
        return BoundaryTraceResult(
          points = boundary,
          traceClosed = false,
          terminationReason = reason,
          steps = steps,
        )
      }

      val edgeKey = (((currY * width + currX).toLong()) shl 3) or nextDir.toLong()
      if (steps == 1) {
        firstNextX = nextX
        firstNextY = nextY
        firstNextDir = nextDir
        visitedDirectedEdges.add(edgeKey)
      } else {
        if (currX == startX && currY == startY && nextX == firstNextX && nextY == firstNextY && nextDir == firstNextDir) {
          if (boundary.size > 1) {
            val last = boundary.last()
            if (last.x.toInt() == startX && last.y.toInt() == startY) {
              boundary.removeAt(boundary.lastIndex)
            }
          }
          val isClosedLoop = boundary.size >= 3
          return BoundaryTraceResult(
            points = boundary,
            traceClosed = isClosedLoop,
            terminationReason = if (isClosedLoop) {
              BoundaryTerminationReason.RETURNED_TO_START
            } else {
              BoundaryTerminationReason.REPEATED_STATE
            },
            steps = steps,
          )
        }
        if (!visitedDirectedEdges.add(edgeKey)) {
          return BoundaryTraceResult(
            points = boundary,
            traceClosed = false,
            terminationReason = BoundaryTerminationReason.REPEATED_STATE,
            steps = steps,
          )
        }
      }

      boundary.add(ContourPoint(nextX.toFloat(), nextY.toFloat()))
      currX = nextX
      currY = nextY
      backtrackDir = (nextDir + 4) % 8
    }

    return BoundaryTraceResult(
      points = boundary,
      traceClosed = false,
      terminationReason = BoundaryTerminationReason.MAX_STEPS,
      steps = steps,
    )
  }

  /**
   * Moore-Neighbor Tracing algorithm to trace the exterior perimeter of the primary mask component.
   */
  fun extractBoundaryPoints(
    mask: ByteArray,
    width: Int,
    height: Int,
    startHintX: Int = -1,
    startHintY: Int = -1,
  ): List<ContourPoint> =
    extractBoundaryTrace(mask, width, height, startHintX, startHintY).points

  private fun perpendicularDistance(p: ContourPoint, a: ContourPoint, b: ContourPoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val mag = hypot(dx, dy)
    if (mag == 0f) {
      return hypot(p.x - a.x, p.y - a.y)
    }
    val u = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (mag * mag)
    val ix = a.x + u * dx
    val iy = a.y + u * dy
    return hypot(p.x - ix, p.y - iy)
  }

  /**
   * Iterative Ramer-Douglas-Peucker simplification (safe against deep recursion on high-res contours).
   */
  fun simplifyContourRDP(points: List<ContourPoint>, epsilon: Float = 2.2f): List<ContourPoint> {
    if (points.size <= 3) return points

    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true

    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to points.lastIndex)

    while (stack.isNotEmpty()) {
      val (start, end) = stack.removeLast()
      if (end - start <= 1) continue

      var maxDist = 0f
      var index = start
      val a = points[start]
      val b = points[end]
      for (i in (start + 1) until end) {
        val d = perpendicularDistance(points[i], a, b)
        if (d > maxDist) {
          maxDist = d
          index = i
        }
      }

      if (maxDist > epsilon) {
        keep[index] = true
        stack.addLast(start to index)
        stack.addLast(index to end)
      }
    }

    val result = ArrayList<ContourPoint>(points.size / 4)
    for (i in points.indices) {
      if (keep[i]) result.add(points[i])
    }
    return result
  }

  /**
   * Closed cyclic weighted Laplacian smoothing filter:
   * P'_i = 0.20 * P_{i-1} + 0.60 * P_i + 0.20 * P_{i+1}
   */
  fun smoothClosedContour(points: List<ContourPoint>, passes: Int = 3): List<ContourPoint> {
    if (points.size < 4 || passes <= 0) return points
    var curr = points
    val n = curr.size
    for (p in 0 until passes) {
      val next = ArrayList<ContourPoint>(n)
      for (i in 0 until n) {
        val prev = curr[(i - 1 + n) % n]
        val self = curr[i]
        val nxt = curr[(i + 1) % n]
        next.add(
          ContourPoint(
            x = 0.20f * prev.x + 0.60f * self.x + 0.20f * nxt.x,
            y = 0.20f * prev.y + 0.60f * self.y + 0.20f * nxt.y,
          ),
        )
      }
      curr = next
    }
    return curr
  }

  /**
   * Converts closed contour vertices to smooth Catmull-Rom Cubic Bézier segments.
   */
  fun pointsToCatmullRomBeziers(
    points: List<ContourPoint>,
  ): Pair<List<CubicBezierSegment>, Float> {
    if (points.size < 3) return emptyList<CubicBezierSegment>() to 0f

    val n = points.size
    val segments = ArrayList<CubicBezierSegment>(n)
    var approxLength = 0f

    for (i in 0 until n) {
      val p0 = points[(i - 1 + n) % n]
      val p1 = points[i]
      val p2 = points[(i + 1) % n]
      val p3 = points[(i + 2) % n]

      val cp1x = p1.x + (p2.x - p0.x) / 6f
      val cp1y = p1.y + (p2.y - p0.y) / 6f
      val cp2x = p2.x - (p3.x - p1.x) / 6f
      val cp2y = p2.y - (p3.y - p1.y) / 6f

      segments.add(CubicBezierSegment(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y))
      approxLength += hypot(p2.x - p1.x, p2.y - p1.y)
    }

    return segments to max(1f, approxLength)
  }

  /**
   * Full contour extraction pipeline matching `extractContourFromMask` in the Web codebase.
   */
  fun extractContourFromMask(
    mask: ByteArray,
    width: Int,
    height: Int,
    simplificationTolerance: Float = 2.2f,
    smoothingPasses: Int = 3,
    startTimeNs: Long = System.nanoTime(),
  ): ContourData? {
    val lcc = getLargestConnectedComponent(mask, width, height)
    return extractContourFromComponent(
      lcc = lcc,
      width = width,
      height = height,
      simplificationTolerance = simplificationTolerance,
      smoothingPasses = smoothingPasses,
      startTimeNs = startTimeNs,
    )
  }

  /** Runs the reference contour stages on an already computed reference 4-connected LCC. */
  fun extractContourFromComponent(
    lcc: ConnectedComponentResult,
    width: Int,
    height: Int,
    simplificationTolerance: Float = 2.2f,
    smoothingPasses: Int = 3,
    startTimeNs: Long = System.nanoTime(),
  ): ContourData? {
    if (lcc.pixelCount < 60) return null

    val trace = extractBoundaryTrace(lcc.cleanMask, width, height, lcc.startX, lcc.startY)
    return contourFromBoundaryTrace(trace, simplificationTolerance, smoothingPasses, startTimeNs)
  }

  fun contourFromBoundaryTrace(
    trace: BoundaryTraceResult,
    simplificationTolerance: Float = 2.2f,
    smoothingPasses: Int = 3,
    startTimeNs: Long = System.nanoTime(),
  ): ContourData? {
    val rawPoints = trace.points
    if (rawPoints.size < 3 || !trace.traceClosed) return null

    val clampedPasses = min(5, max(1, smoothingPasses))
    val smoothedRaw = smoothClosedContour(rawPoints, clampedPasses)
    val simplified = simplifyContourRDP(smoothedRaw, simplificationTolerance)
    val secondPassCount = max(1, min(3, ceil(clampedPasses / 2.0).toInt()))
    val finalSmoothed = smoothClosedContour(simplified, secondPassCount)

    if (finalSmoothed.size < 3) return null

    val (segments, totalLength) = pointsToCatmullRomBeziers(finalSmoothed)
    val elapsed = ((System.nanoTime() - startTimeNs) / 1_000_000L).coerceAtLeast(0L)

    return ContourData(
      rawPoints = rawPoints,
      simplifiedPoints = finalSmoothed,
      startPoint = finalSmoothed.first(),
      bezierSegments = segments,
      totalLength = totalLength,
      rawPointCount = rawPoints.size,
      simplifiedPointCount = finalSmoothed.size,
      extractionTimeMs = elapsed,
      traceClosed = trace.traceClosed,
      terminationReason = trace.terminationReason,
    )
  }
}
