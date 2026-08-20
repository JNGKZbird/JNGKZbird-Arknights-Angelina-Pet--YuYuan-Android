package com.jngkzbird.arknights_angelina_pet.spine38

import kotlin.math.abs

/**
 * 官方 spine-ts 3.8 SkeletonClipping 逐行移植（Windows clipping.py → Kotlin）。
 * 裁剪附件世界顶点 → makeClockwise → ear-clipping 三角化 → 凸分解 →
 * Sutherland-Hodgman 逐三角形裁剪。自交多边形下 S-H 结果 = 所有边半平面交，
 * 与 even-odd 掩码语义完全不同——坐/睡眼白泄漏的根治（2026-08-20）。
 */

class ClippedTri {
    var pts: DoubleArray = DoubleArray(0) // 骨架坐标平铺（未闭合）
    var uvs: DoubleArray = DoubleArray(0)
}

private fun positiveArea(
    p1x: Double, p1y: Double, p2x: Double, p2y: Double, p3x: Double, p3y: Double
): Boolean = p1x * (p3y - p2y) + p2x * (p1y - p3y) + p3x * (p2y - p1y) >= 0

// spine-ts SkeletonClipping.makeClockwise（原地；平铺数组）
private fun makeClockwise(verts: DoubleArray) {
    val n = verts.size
    var area = verts[n - 2] * verts[1] - verts[0] * verts[n - 1]
    var i = 0
    while (i < n - 3) {
        area += verts[i] * verts[i + 3] - verts[i + 2] * verts[i + 1]
        i += 2
    }
    if (area < 0) {
        return
    }
    val last = n - 2
    var j = 0
    while (j < n / 2) {
        val x = verts[j]
        val y = verts[j + 1]
        val o = last - j
        verts[j] = verts[o]
        verts[j + 1] = verts[o + 1]
        verts[o] = x
        verts[o + 1] = y
        j += 2
    }
}

private fun isConcave(index: Int, vertexCount: Int, verts: DoubleArray, indices: List<Int>): Boolean {
    val prev = indices[(vertexCount + index - 1) % vertexCount]
    val cur = indices[index]
    val nxt = indices[(index + 1) % vertexCount]
    return !positiveArea(
        verts[prev * 2], verts[prev * 2 + 1],
        verts[cur * 2], verts[cur * 2 + 1],
        verts[nxt * 2], verts[nxt * 2 + 1]
    )
}

// spine-ts Triangulator.triangulate（ear-clipping）→ 三角形顶点索引
private fun triangulatePolygon(verts: DoubleArray): List<Int> {
    var vertexCount = verts.size / 2
    val indices = ArrayList<Int>(vertexCount)
    val concave = ArrayList<Boolean>(vertexCount)
    for (i in 0 until vertexCount) {
        indices.add(i)
        concave.add(false)
    }
    for (i in 0 until vertexCount) {
        concave[i] = isConcave(i, vertexCount, verts, indices)
    }
    val triangles = ArrayList<Int>()
    while (vertexCount > 3) {
        var previous = vertexCount - 1
        var i = 0
        var nxt = 1
        while (true) {
            var earOk = false
            if (!concave[i]) {
                val p1 = indices[previous]
                val p2 = indices[i]
                val p3 = indices[nxt]
                val p1x = verts[p1 * 2]
                val p1y = verts[p1 * 2 + 1]
                val p2x = verts[p2 * 2]
                val p2y = verts[p2 * 2 + 1]
                val p3x = verts[p3 * 2]
                val p3y = verts[p3 * 2 + 1]
                var blocked = false
                var ii = (nxt + 1) % vertexCount
                while (ii != previous) {
                    if (concave[ii]) {
                        val v = indices[ii]
                        val vx = verts[v * 2]
                        val vy = verts[v * 2 + 1]
                        if (positiveArea(p3x, p3y, p1x, p1y, vx, vy) &&
                            positiveArea(p1x, p1y, p2x, p2y, vx, vy) &&
                            positiveArea(p2x, p2y, p3x, p3y, vx, vy)
                        ) {
                            blocked = true
                            break
                        }
                    }
                    ii = (ii + 1) % vertexCount
                }
                if (!blocked) {
                    earOk = true
                }
            }
            if (earOk) {
                break
            }
            if (nxt == 0) {
                while (true) {
                    if (!concave[i]) {
                        break
                    }
                    i -= 1
                    if (i <= 0) {
                        break
                    }
                }
                break
            }
            previous = i
            i = nxt
            nxt = (nxt + 1) % vertexCount
        }
        triangles.add(indices[(vertexCount + i - 1) % vertexCount])
        triangles.add(indices[i])
        triangles.add(indices[(i + 1) % vertexCount])
        indices.removeAt(i)
        concave.removeAt(i)
        vertexCount -= 1
        val prevIndex = (vertexCount + i - 1) % vertexCount
        val nextIndex = if (i != vertexCount) i else 0
        concave[prevIndex] = isConcave(prevIndex, vertexCount, verts, indices)
        concave[nextIndex] = isConcave(nextIndex, vertexCount, verts, indices)
    }
    if (vertexCount == 3) {
        triangles.add(indices[2])
        triangles.add(indices[0])
        triangles.add(indices[1])
    }
    return triangles
}

private fun winding(
    p1x: Double, p1y: Double, p2x: Double, p2y: Double, p3x: Double, p3y: Double
): Int {
    val px = p2x - p1x
    val py = p2y - p1y
    return if (p3x * py - p3y * px + px * p1y - p1x * py >= 0) 1 else -1
}

// spine-ts Triangulator.decompose：三角形合并为凸多边形
private fun decompose(verts: DoubleArray, triangles: List<Int>): List<DoubleArray> {
    val convexPolys = ArrayList<DoubleArray>()
    val convexIndices = ArrayList<MutableList<Int>>()
    var polyIndices: MutableList<Int> = ArrayList()
    var polygon: MutableList<Double> = ArrayList()
    var fanBaseIndex = -1
    var lastWinding = 0
    var i = 0
    while (i < triangles.size) {
        val t1 = triangles[i] * 2
        val t2 = triangles[i + 1] * 2
        val t3 = triangles[i + 2] * 2
        val x1 = verts[t1]
        val y1 = verts[t1 + 1]
        val x2 = verts[t2]
        val y2 = verts[t2 + 1]
        val x3 = verts[t3]
        val y3 = verts[t3 + 1]
        var merged = false
        if (fanBaseIndex == t1) {
            val o = polygon.size - 4
            val w1 = winding(polygon[o], polygon[o + 1], polygon[o + 2], polygon[o + 3], x3, y3)
            val w2 = winding(x3, y3, polygon[0], polygon[1], polygon[2], polygon[3])
            if (w1 == lastWinding && w2 == lastWinding) {
                polygon.add(x3)
                polygon.add(y3)
                polyIndices.add(t3)
                merged = true
            }
        }
        if (!merged) {
            if (polygon.isNotEmpty()) {
                convexPolys.add(polygon.toDoubleArray())
                convexIndices.add(polyIndices)
            }
            polygon = arrayListOf(x1, y1, x2, y2, x3, y3)
            polyIndices = arrayListOf(t1, t2, t3)
            lastWinding = winding(x1, y1, x2, y2, x3, y3)
            fanBaseIndex = t1
        }
        i += 3
    }
    if (polygon.isNotEmpty()) {
        convexPolys.add(polygon.toDoubleArray())
        convexIndices.add(polyIndices)
    }
    // 第二循环：合并共享 (firstIndex, lastIndex) 边的三角形
    for (pi in convexPolys.indices) {
        polyIndices = convexIndices[pi]
        if (polyIndices.isEmpty()) {
            continue
        }
        val firstIndex = polyIndices[0]
        val lastIndex = polyIndices[polyIndices.size - 1]
        polygon = convexPolys[pi].toMutableList()
        val o = polygon.size - 4
        var prevPrevX = polygon[o]
        var prevPrevY = polygon[o + 1]
        var prevX = polygon[o + 2]
        var prevY = polygon[o + 3]
        val firstX = polygon[0]
        val firstY = polygon[1]
        val secondX = polygon[2]
        val secondY = polygon[3]
        val w = winding(prevPrevX, prevPrevY, prevX, prevY, firstX, firstY)
        var ii = 0
        while (ii < convexPolys.size) {
            if (ii == pi) {
                ii += 1
                continue
            }
            val otherIndices = convexIndices[ii]
            if (otherIndices.size != 3) {
                ii += 1
                continue
            }
            if (otherIndices[0] != firstIndex || otherIndices[1] != lastIndex) {
                ii += 1
                continue
            }
            val otherPoly = convexPolys[ii]
            val x3 = otherPoly[otherPoly.size - 2]
            val y3 = otherPoly[otherPoly.size - 1]
            val w1 = winding(prevPrevX, prevPrevY, prevX, prevY, x3, y3)
            val w2 = winding(x3, y3, firstX, firstY, secondX, secondY)
            if (w1 == w && w2 == w) {
                convexPolys[ii] = DoubleArray(0)
                convexIndices[ii] = ArrayList()
                polygon.add(x3)
                polygon.add(y3)
                polyIndices.add(otherIndices[2])
                prevPrevX = prevX
                prevPrevY = prevY
                prevX = x3
                prevY = y3
                ii = 0
            } else {
                ii += 1
            }
        }
        convexPolys[pi] = polygon.toDoubleArray()
    }
    val out = ArrayList<DoubleArray>()
    for (p in convexPolys) {
        if (p.isNotEmpty()) {
            out.add(p)
        }
    }
    return out
}

// 裁剪附件世界顶点 → 官方语义的裁剪多边形列表（闭合 CW，骨架坐标）
fun clipAttachmentPolys(verts: DoubleArray): List<DoubleArray> {
    val v = verts.copyOf()
    makeClockwise(v)
    val tris = triangulatePolygon(v)
    val polys = decompose(v, tris)
    val out = ArrayList<DoubleArray>()
    for (p in polys) {
        makeClockwise(p)
        val closed = p.copyOf(p.size + 2)
        closed[p.size] = p[0]
        closed[p.size + 1] = p[1]
        out.add(closed)
    }
    return out
}

// Sutherland-Hodgman：三角形（平铺 6 元素）与闭合裁剪多边形求交 → 未闭合平铺交点
private fun shClip(
    x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double, clipArea: DoubleArray
): DoubleArray {
    var inp = doubleArrayOf(x1, y1, x2, y2, x3, y3, x1, y1)
    var inpLen = 8
    var out = DoubleArray(0)
    val last = clipArea.size - 4
    var i = 0
    while (true) {
        val edgeX = clipArea[i]
        val edgeY = clipArea[i + 1]
        val edgeX2 = clipArea[i + 2]
        val edgeY2 = clipArea[i + 3]
        val deltaX = edgeX - edgeX2
        val deltaY = edgeY - edgeY2
        // 每轮裁剪输出 ≤ 输入点数 + 1（一条边被切成两段），闭合点已含在 outLen 写入里
        out = DoubleArray(inpLen + 8)
        var outLen = 0
        val inputLen = inpLen - 2
        var ii = 0
        while (ii < inputLen) {
            val inX = inp[ii]
            val inY = inp[ii + 1]
            val inX2 = inp[ii + 2]
            val inY2 = inp[ii + 3]
            val side2 = deltaX * (inY2 - edgeY2) - deltaY * (inX2 - edgeX2) > 0.0
            if (deltaX * (inY - edgeY2) - deltaY * (inX - edgeX2) > 0.0) {
                if (side2) {
                    out[outLen] = inX2
                    out[outLen + 1] = inY2
                    outLen += 2
                } else {
                    val c0 = inY2 - inY
                    val c2 = inX2 - inX
                    val s = c0 * (edgeX2 - edgeX) - c2 * (edgeY2 - edgeY)
                    if (abs(s) > 0.000001) {
                        val ua = (c2 * (edgeY - inY) - c0 * (edgeX - inX)) / s
                        out[outLen] = edgeX + (edgeX2 - edgeX) * ua
                        out[outLen + 1] = edgeY + (edgeY2 - edgeY) * ua
                    } else {
                        out[outLen] = edgeX
                        out[outLen + 1] = edgeY
                    }
                    outLen += 2
                }
            } else if (side2) {
                val c0 = inY2 - inY
                val c2 = inX2 - inX
                val s = c0 * (edgeX2 - edgeX) - c2 * (edgeY2 - edgeY)
                if (abs(s) > 0.000001) {
                    val ua = (c2 * (edgeY - inY) - c0 * (edgeX - inX)) / s
                    out[outLen] = edgeX + (edgeX2 - edgeX) * ua
                    out[outLen + 1] = edgeY + (edgeY2 - edgeY) * ua
                } else {
                    out[outLen] = edgeX
                    out[outLen + 1] = edgeY
                }
                outLen += 2
                out[outLen] = inX2
                out[outLen + 1] = inY2
                outLen += 2
            }
            ii += 2
        }
        if (outLen == 0) {
            return DoubleArray(0)
        }
        out[outLen] = out[0]
        out[outLen + 1] = out[1]
        outLen += 2
        if (i == last) {
            break
        }
        inp = out.copyOf(outLen)
        inpLen = outLen
        i += 2
    }
    return out.copyOf(out.size - 2)
}

// 三角形（平铺 6）与全部裁剪多边形求交 → 交集多边形列表（pts + 重心插值 UV）
fun clipTriangleToPolys(tri: DoubleArray, uv: DoubleArray, polys: List<DoubleArray>): List<ClippedTri> {
    val x1 = tri[0]
    val y1 = tri[1]
    val x2 = tri[2]
    val y2 = tri[3]
    val x3 = tri[4]
    val y3 = tri[5]
    val u1 = uv[0]
    val v1 = uv[1]
    val u2 = uv[2]
    val v2 = uv[3]
    val u3 = uv[4]
    val v3 = uv[5]
    val d0 = y2 - y3
    val d1 = x3 - x2
    val d2 = x1 - x3
    val d4 = y3 - y1
    val denom = d0 * d2 + d1 * (y1 - y3)
    if (abs(denom) < 1e-12) {
        return emptyList()
    }
    val inv = 1.0 / denom
    val results = ArrayList<ClippedTri>()
    for (poly in polys) {
        val pts = shClip(x1, y1, x2, y2, x3, y3, poly)
        if (pts.size < 6) {
            continue
        }
        val uvs = DoubleArray(pts.size)
        var k = 0
        while (k < pts.size) {
            val px = pts[k]
            val py = pts[k + 1]
            val c0 = px - x3
            val c1 = py - y3
            val a = (d0 * c0 + d1 * c1) * inv
            val b = (d4 * c0 + d2 * c1) * inv
            val c = 1.0 - a - b
            uvs[k] = u1 * a + u2 * b + u3 * c
            uvs[k + 1] = v1 * a + v2 * b + v3 * c
            k += 2
        }
        val r = ClippedTri()
        r.pts = pts
        r.uvs = uvs
        results.add(r)
    }
    return results
}
