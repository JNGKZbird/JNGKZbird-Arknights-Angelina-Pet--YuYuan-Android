package com.jngkzbird.arknights_angelina_pet.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MarkdownView — 轻量 Markdown 渲染（鸿蒙版 components/MarkdownView.ets 移植；LLM 回复常用子集）。
 * 行级解析：代码块 ```、标题 #~###、无序列表 "-"/"*"、有序列表 1.、引用 >、段落；
 * 行内：粗体 **x**、行内代码 `x`。
 */

class MdSeg(val text: String, val bold: Boolean = false, val code: Boolean = false)

class MdBlock(val kind: String, val segs: List<MdSeg>, val num: Int = 0)

/** 行内解析：**粗体** 与 `行内代码`（找最早出现的开标记，配对关闭） */
private fun parseInline(line: String): List<MdSeg> {
    val out = mutableListOf<MdSeg>()
    var rest = line
    while (rest.isNotEmpty()) {
        val bOpen = rest.indexOf("**")
        val cOpen = rest.indexOf('`')
        val open: Int
        val closer: String
        if (bOpen >= 0 && (cOpen < 0 || bOpen <= cOpen)) {
            open = bOpen
            closer = "**"
        } else if (cOpen >= 0) {
            open = cOpen
            closer = "`"
        } else {
            out.add(MdSeg(rest))
            break
        }
        if (open > 0) {
            out.add(MdSeg(rest.substring(0, open)))
        }
        val closeIdx = rest.indexOf(closer, open + closer.length)
        if (closeIdx < 0) {
            out.add(MdSeg(rest.substring(open)))
            break
        }
        val inner = rest.substring(open + closer.length, closeIdx)
        if (inner.isNotEmpty()) {
            out.add(MdSeg(inner, bold = closer == "**", code = closer == "`"))
        }
        rest = rest.substring(closeIdx + closer.length)
    }
    return out
}

private fun isOlStart(t: String): Boolean {
    val dot = t.indexOf(". ")
    if (dot <= 0) {
        return false
    }
    for (i in 0 until dot) {
        if (t[i] < '0' || t[i] > '9') {
            return false
        }
    }
    return true
}

private fun olNum(t: String): Int = t.substring(0, t.indexOf(". ")).toIntOrNull() ?: 1

/** 块级解析 */
fun mdParse(text: String): List<MdBlock> {
    val lines = text.split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val t = lines[i].trim()
        when {
            t.startsWith("```") -> {
                val buf = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    buf.add(lines[i])
                    i++
                }
                i++ // 跳过结束 ```
                blocks.add(MdBlock("code", listOf(MdSeg(buf.joinToString("\n")))))
            }
            t.startsWith("### ") -> {
                blocks.add(MdBlock("h", parseInline(t.substring(4))))
                i++
            }
            t.startsWith("## ") -> {
                blocks.add(MdBlock("h", parseInline(t.substring(3))))
                i++
            }
            t.startsWith("# ") -> {
                blocks.add(MdBlock("h", parseInline(t.substring(2))))
                i++
            }
            t.startsWith("- ") || t.startsWith("* ") -> {
                blocks.add(MdBlock("li", parseInline(t.substring(2))))
                i++
            }
            isOlStart(t) -> {
                blocks.add(MdBlock("ol", parseInline(t.substring(t.indexOf(". ") + 2)), olNum(t)))
                i++
            }
            t.startsWith(">") -> {
                blocks.add(MdBlock("quote", parseInline(t.substring(1).trim())))
                i++
            }
            t.isEmpty() -> {
                i++
            }
            else -> {
                // 段落：连续非块起始行合并
                val buf = mutableListOf<String>()
                while (i < lines.size) {
                    val t2 = lines[i].trim()
                    if (t2.isEmpty() || t2.startsWith("```") || t2.startsWith("# ") || t2.startsWith("## ") ||
                        t2.startsWith("### ") || t2.startsWith("- ") || t2.startsWith("* ") || t2.startsWith(">") ||
                        isOlStart(t2)
                    ) {
                        break
                    }
                    buf.add(lines[i])
                    i++
                }
                blocks.add(MdBlock("p", parseInline(buf.joinToString("\n"))))
            }
        }
    }
    return blocks
}

@Composable
fun MarkdownView(
    text: String,
    fontColor: Color = Color(0xFF1A1A1A),
    baseSize: Int = 15,
    modifier: Modifier = Modifier
) {
    fun annotated(segs: List<MdSeg>, weight: FontWeight, color: Color) = buildAnnotatedString {
        for (s in segs) {
            withStyle(
                SpanStyle(
                    fontWeight = if (s.bold) FontWeight.Bold else weight,
                    color = if (s.code) Color(0xFFC7254E) else color
                )
            ) {
                append(s.text)
            }
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (b in mdParse(text)) {
            when (b.kind) {
                "code" -> Text(
                    if (b.segs.isNotEmpty()) b.segs[0].text else "",
                    color = Color(0xFF333333),
                    fontSize = (baseSize - 2).sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
                "h" -> Text(
                    annotated(b.segs, FontWeight.Bold, fontColor),
                    fontSize = (baseSize + 2).sp,
                    lineHeight = 26.sp
                )
                "li" -> Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text("•  ", color = fontColor, fontSize = baseSize.sp)
                    Text(
                        annotated(b.segs, FontWeight.Normal, fontColor),
                        fontSize = baseSize.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                "ol" -> Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(b.num.toString() + ".  ", color = fontColor, fontSize = baseSize.sp)
                    Text(
                        annotated(b.segs, FontWeight.Normal, fontColor),
                        fontSize = baseSize.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                "quote" -> Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(30.dp)
                            .background(Color(0xFFDDDDDD), RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        annotated(b.segs, FontWeight.Normal, Color(0xFF777777)),
                        fontSize = (baseSize - 1).sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> Text(
                    annotated(b.segs, FontWeight.Normal, fontColor),
                    fontSize = baseSize.sp,
                    lineHeight = 24.sp
                )
            }
        }
    }
}
