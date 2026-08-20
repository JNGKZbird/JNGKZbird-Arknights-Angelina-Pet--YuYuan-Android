package com.jngkzbird.arknights_angelina_pet.spine38

import kotlin.math.PI

/**
 * Spine 3.8 runtime for Kotlin — ported from spine38/utils.ets / spine38/utils.py（spine-ts 3.8）。
 * 移植期保持与 ArkTS 同名（snake_case），便于与参考实现 diff 比对。
 */
class Color(
    var r: Double = 1.0,
    var g: Double = 1.0,
    var b: Double = 1.0,
    var a: Double = 1.0
) {
    fun set(r: Double, g: Double, b: Double, a: Double) {
        this.r = r
        this.g = g
        this.b = b
        this.a = a
    }

    companion object {
        fun rgba8888(color: Color, value: Int) {
            color.r = (value ushr 24) / 255.0
            color.g = ((value ushr 16) and 0xFF) / 255.0
            color.b = ((value ushr 8) and 0xFF) / 255.0
            color.a = (value and 0xFF) / 255.0
        }

        fun rgb888(color: Color, value: Int) {
            color.r = ((value ushr 16) and 0xFF) / 255.0
            color.g = ((value ushr 8) and 0xFF) / 255.0
            color.b = (value and 0xFF) / 255.0
        }
    }
}

fun deg_to_rad(deg: Double): Double = deg * PI / 180.0

fun rad_to_deg(rad: Double): Double = rad * 180.0 / PI
