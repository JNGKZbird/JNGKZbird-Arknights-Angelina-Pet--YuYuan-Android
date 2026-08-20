package com.jngkzbird.arknights_angelina_pet.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jngkzbird.arknights_angelina_pet.model.Agent
import com.jngkzbird.arknights_angelina_pet.model.ChatApi
import com.jngkzbird.arknights_angelina_pet.model.ChatSettings
import com.jngkzbird.arknights_angelina_pet.ui.theme.ThemeTokens

/** 弹层卡片通用骨架：暗遮罩 + 居中白卡（圆角 20，阴影） */
@Composable
fun PanelScaffold(
    theme: ThemeTokens,
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
    footer: @Composable () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable { onClose() }
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.7f)
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(20.dp), spotColor = Color(0x33000000))
                .background(Color.White, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text("✕", color = theme.textSecondary, fontSize = 16.sp, modifier = Modifier.padding(6.dp).clickable { onClose() })
            }
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
            footer()
        }
    }
}

@Composable
private fun FieldLabel(theme: ThemeTokens, label: String) {
    Text(label, color = theme.textTertiary, fontSize = 12.sp)
}

@Composable
private fun PanelInput(
    theme: ThemeTokens,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    password: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.bgHover, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        textStyle = TextStyle(color = theme.textBody, fontSize = 14.sp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, color = theme.textQuaternary, fontSize = 14.sp)
                }
                inner()
            }
        }
    )
}

// ── 聊天设置面板（鸿蒙版 SettingsPanel 复刻） ──
@Composable
fun SettingsPanel(
    theme: ThemeTokens,
    settings: ChatSettings,
    api: ChatApi,
    onSave: (ChatSettings) -> Unit,
    onClose: () -> Unit
) {
    var key by remember { mutableStateOf(settings.chat_api_key) }
    var url by remember { mutableStateOf(settings.chat_base_url) }
    var model by remember { mutableStateOf(settings.chat_model) }
    var name by remember { mutableStateOf(settings.player_name) }
    var ctx by remember { mutableStateOf(settings.context_window_size.toString()) }
    var timeAware by remember { mutableStateOf(settings.time_awareness) }
    var extra by remember { mutableStateOf(settings.extra_prompt) }
    var chatter by remember { mutableStateOf(settings.chatter_interval) }
    var voice by remember { mutableStateOf(settings.voice_enabled) }
    var voiceLang by remember { mutableStateOf(settings.voice_language) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    var testOk by remember { mutableStateOf(false) }

    PanelScaffold(
        theme, "聊天设置", onClose,
        content = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    FieldLabel(theme, "API Key")
                    PanelInput(theme, key, { key = it }, "sk-…（未配置时为演示模式）", password = true)
                }
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    FieldLabel(theme, "接口地址")
                    PanelInput(theme, url, { url = it }, "https://api.deepseek.com")
                }
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    FieldLabel(theme, "模型")
                    PanelInput(theme, model, { model = it }, "deepseek-v4-flash")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(theme.accent, RoundedCornerShape(8.dp))
                            .clickable(enabled = !testing) {
                                if (key.isBlank()) {
                                    testOk = false
                                    testResult = "请先填写 API Key"
                                } else if (url.isBlank()) {
                                    testOk = false
                                    testResult = "请先填写接口地址"
                                } else if (model.isBlank()) {
                                    testOk = false
                                    testResult = "请先填写模型名"
                                } else {
                                    testing = true
                                    testResult = ""
                                    api.test(url.trim(), key.trim(), model.trim(), object : ChatApi.Callbacks {
                                        override fun onDelta(delta: String) {}
                                        override fun onReasoning(delta: String) {}
                                        override fun onDone(full: String) {
                                            testing = false
                                            testOk = true
                                            testResult = full
                                        }
                                        override fun onError(err: String) {
                                            testing = false
                                            testOk = false
                                            testResult = err
                                        }
                                    })
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (testing) "测试中…" else "测试连接", color = Color.White, fontSize = 13.sp)
                    }
                    if (testResult.isNotEmpty()) {
                        Text(
                            testResult,
                            color = if (testOk) Color(0xFF2E8B57) else Color(0xFFE5484D),
                            fontSize = 12.sp,
                            maxLines = 2,
                            modifier = Modifier.padding(start = 8.dp).weight(1f)
                        )
                    }
                }
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    FieldLabel(theme, "玩家称呼")
                    PanelInput(theme, name, { name = it }, "博士")
                }
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    FieldLabel(theme, "上下文条数（1-100）")
                    PanelInput(theme, ctx, { ctx = it.filter { c -> c.isDigit() } }, "20")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("时间感知", color = theme.textPrimary, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = timeAware,
                        onCheckedChange = { timeAware = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = theme.accent)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("主动性发言频率", color = theme.textPrimary, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    for (opt in listOf("低频", "中频", "高频")) {
                        Text(
                            opt,
                            color = if (chatter == opt) theme.accent else theme.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).clickable { chatter = opt }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("语音", color = theme.textPrimary, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = voice,
                        onCheckedChange = { voice = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = theme.accent)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("语音语言", color = theme.textPrimary, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    for (opt in listOf("中文", "日文")) {
                        Text(
                            opt,
                            color = if (voiceLang == opt) theme.accent else theme.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).clickable { voiceLang = opt }
                        )
                    }
                }
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    FieldLabel(theme, "额外提示词")
                    BasicTextField(
                        value = extra,
                        onValueChange = { extra = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(theme.bgHover, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        textStyle = TextStyle(color = theme.textBody, fontSize = 14.sp)
                    )
                }
            }
        },
        footer = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.accent, RoundedCornerShape(10.dp))
                    .clickable {
                        onSave(
                            ChatSettings(
                                url.trim(), key.trim(), model.trim(), name.trim(),
                                ctx.toIntOrNull()?.coerceIn(1, 100) ?: 20,
                                timeAware, extra, chatter, voice, voiceLang
                            )
                        )
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("保存", color = Color.White, fontSize = 14.sp)
            }
        }
    )
}

// ── 智能体面板（鸿蒙版 AgentPanel 复刻）+ 编辑对话框 ──
@Composable
fun AgentPanel(
    theme: ThemeTokens,
    agents: List<Agent>,
    activeAid: String,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onSelect: (String) -> Unit,
    onClose: () -> Unit
) {
    PanelScaffold(
        theme, "智能体", onClose,
        content = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // 默认智能体：安洁莉娜（锁定）
                AgentRow(theme, "安洁莉娜", activeAid.isEmpty(), true) { onSelect("") }
                for (a in agents) {
                    AgentRow(theme, a.name, activeAid == a.aid, false, {
                        if (activeAid == a.aid) onEdit(a.aid) else onSelect(a.aid)
                    })
                }
            }
        },
        footer = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNew() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("＋", color = theme.accent, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text("新建智能体", color = theme.textPrimary, fontSize = 14.sp)
            }
        }
    )
}

@Composable
private fun AgentRow(
    theme: ThemeTokens,
    name: String,
    active: Boolean,
    locked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(theme.accent, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            name + if (locked) "（默认，锁定）" else "",
            color = if (active) theme.accent else theme.textPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (active) "使用中" else "启用",
            color = theme.accent,
            fontSize = 11.sp,
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .clickable { onClick() }
        )
    }
}

/** 智能体编辑/新建对话框：名称 + 提示词 */
@Composable
fun AgentEditDialog(
    theme: ThemeTokens,
    agent: Agent?, // null = 新建
    onSave: (name: String, prompt: String) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit
) {
    var name by remember { mutableStateOf(agent?.name ?: "") }
    var prompt by remember { mutableStateOf(agent?.prompt ?: "") }
    PanelScaffold(
        theme, if (agent == null) "新建智能体" else "编辑智能体", onClose,
        content = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    FieldLabel(theme, "名称")
                    PanelInput(theme, name, { name = it }, "给智能体起个名字")
                }
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    FieldLabel(theme, "提示词")
                    BasicTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(theme.bgHover, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        textStyle = TextStyle(color = theme.textBody, fontSize = 14.sp)
                    )
                }
            }
        },
        footer = {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (onDelete != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Color(0xFFE5484D), RoundedCornerShape(10.dp))
                            .clickable { onDelete() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("删除", color = Color(0xFFE5484D), fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(theme.accent, RoundedCornerShape(10.dp))
                        .clickable {
                            if (name.isNotBlank()) {
                                onSave(name.trim(), prompt)
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("保存", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    )
}
