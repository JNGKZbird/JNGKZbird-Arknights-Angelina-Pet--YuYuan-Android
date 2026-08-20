package com.jngkzbird.arknights_angelina_pet.ui.terminal

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jngkzbird.arknights_angelina_pet.R
import com.jngkzbird.arknights_angelina_pet.model.Agent
import com.jngkzbird.arknights_angelina_pet.model.ChatHistory
import com.jngkzbird.arknights_angelina_pet.model.ChatApi
import com.jngkzbird.arknights_angelina_pet.model.ChatSettings
import com.jngkzbird.arknights_angelina_pet.model.ChatStore
import com.jngkzbird.arknights_angelina_pet.model.Project
import com.jngkzbird.arknights_angelina_pet.model.RoundNode
import com.jngkzbird.arknights_angelina_pet.model.Session
import com.jngkzbird.arknights_angelina_pet.model.SettingsStore
import com.jngkzbird.arknights_angelina_pet.ui.theme.ThemeTokens

/**
 * 聊天终端 — 鸿蒙版 ChatSidebar/ChatMain 结构复刻（Kimi K3 极简纪律）。
 * 数据层：chat_history.json（复用鸿蒙格式）+ 三道闸见 ChatStore。
 */
@Composable
fun TerminalScreen(
    theme: ThemeTokens,
    history: ChatHistory,
    historyVersion: Int,
    settings: ChatSettings,
    agentPrompt: String,
    streamVersion: Int,
    api: ChatApi,
    isPhone: Boolean,
    isLandscape: Boolean,
    onBubble: (String, Long) -> Unit,
    onBubbleStream: (String) -> Unit,
    onMutate: () -> Unit,
    onToggleTheme: () -> Unit,
    onSaveSettings: (ChatSettings) -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var showAgents by remember { mutableStateOf(false) }
    var renameSid by remember { mutableStateOf<String?>(null) }
    var editingAgent by remember { mutableStateOf<Agent?>(null) }
    var creatingAgent by remember { mutableStateOf(false) }
    var panelVersion by remember { mutableStateOf(0) }
    var sideCollapsed by remember { mutableStateOf(false) }
    var creatingProject by remember { mutableStateOf(false) }
    var renamingProject by remember { mutableStateOf<Project?>(null) }
    var moveProjectSid by remember { mutableStateOf<String?>(null) }
    var showConvParams by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var currentProjectPid by remember { mutableStateOf(SettingsStore.loadProjectCurrent(context)) }
    var expandedPids by remember { mutableStateOf(SettingsStore.loadExpandedPids(context)) }
    var agentCurrent by remember { mutableStateOf(SettingsStore.loadAgentCurrent(context)) }
    // 竖屏自动收起侧边栏（用户仍可手动展开）；横屏自动展开
    LaunchedEffect(isLandscape) {
        sideCollapsed = !isLandscape
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize().background(theme.bgMain)) {
            ChatSidebar(
                theme, history, historyVersion, onMutate, onToggleTheme,
                onOpenSettings = { showSettings = true },
                onOpenAgents = { showAgents = true },
                onRename = { sid -> renameSid = sid },
                onDelete = { sid ->
                    history.sessions.removeAll { it.sid == sid }
                    if (history.active_session == sid) {
                        history.active_session = history.sessions.firstOrNull()?.sid ?: ""
                    }
                    onMutate()
                },
                isLandscape = isLandscape,
                collapsed = sideCollapsed,
                onToggleCollapse = { sideCollapsed = !sideCollapsed },
                currentProjectPid = currentProjectPid,
                expandedPids = expandedPids,
                onToggleProject = { pid ->
                    expandedPids = if (("," + expandedPids + ",").contains("," + pid + ",")) {
                        expandedPids.split(",").filter { it.isNotEmpty() && it != pid }.joinToString(",")
                    } else {
                        if (expandedPids.isEmpty()) pid else expandedPids + "," + pid
                    }
                    SettingsStore.saveExpandedPids(context, expandedPids)
                },
                onNewProject = { creatingProject = true },
                onLongPressProject = { pid ->
                    renamingProject = history.projects.firstOrNull { it.pid == pid }
                },
                agentCurrent = agentCurrent
            )
            ChatMain(theme, history, historyVersion, settings, agentPrompt, streamVersion, api, onBubble, onBubbleStream, onMutate, isPhone, isLandscape, sideCollapsed,
                currentProjectPid,
                onSelectProject = { pid ->
                    currentProjectPid = pid
                    SettingsStore.saveProjectCurrent(context, pid)
                },
                onRenameTopic = { renameSid = history.active_session },
                agentCurrent = agentCurrent,
                onSelectAgent = { aid ->
                    agentCurrent = aid
                    SettingsStore.saveAgentCurrent(context, aid)
                },
                onOpenConvParams = { showConvParams = true },
                onOpenAgents = { showAgents = true })
        }
        if (showSettings) {
            SettingsPanel(theme, settings, api, onSave = { s ->
                onSaveSettings(s)
                showSettings = false
            }, onClose = { showSettings = false })
        }
        if (showAgents) {
            AgentPanel(
                theme, history.agents, history.sessions.firstOrNull { it.sid == history.active_session }?.agent_aid ?: "",
                onNew = {
                    creatingAgent = true
                    editingAgent = null
                },
                onEdit = { aid ->
                    editingAgent = history.agents.firstOrNull { it.aid == aid }
                },
                onSelect = { aid ->
                    history.sessions.firstOrNull { it.sid == history.active_session }?.agent_aid = aid
                    onMutate()
                },
                onClose = { showAgents = false }
            )
        }
        renameSid?.let { sid ->
            val session = ChatStore.findSession(history, sid)
            if (session != null) {
                RenameDialog(
                    theme, session.title,
                    onDone = { newTitle ->
                        if (newTitle.isNotBlank()) {
                            session.title = newTitle.trim()
                            onMutate()
                        }
                        renameSid = null
                    },
                    onDelete = {
                        history.sessions.removeAll { it.sid == sid }
                        if (history.active_session == sid) {
                            history.active_session = history.sessions.firstOrNull()?.sid ?: ""
                        }
                        renameSid = null
                        onMutate()
                    },
                    onMoveToProject = {
                        renameSid = null
                        moveProjectSid = sid
                    }
                )
            } else {
                renameSid = null
            }
        }
        // 项目名称对话框（新建/重命名共用；重命名模式带删除）
        if (creatingProject || renamingProject != null) {
            ProjectNameDialog(
                theme,
                title = if (creatingProject) "新建项目" else "重命名项目",
                draft = renamingProject?.name ?: "",
                onConfirm = { name ->
                    if (creatingProject) {
                        val pid = ChatStore.newProject(history, name)
                        if (pid.isNotEmpty()) {
                            expandedPids = if (expandedPids.isEmpty()) pid else expandedPids + "," + pid
                            SettingsStore.saveExpandedPids(context, expandedPids)
                            onMutate()
                        }
                    } else {
                        renamingProject?.let { p ->
                            if (ChatStore.renameProject(history, p.pid, name)) {
                                onMutate()
                            }
                        }
                    }
                    creatingProject = false
                    renamingProject = null
                },
                onDelete = if (creatingProject) null else {
                    {
                        renamingProject?.let { p ->
                            if (ChatStore.deleteProject(history, p.pid)) {
                                onMutate()
                            }
                        }
                        renamingProject = null
                    }
                },
                onDismiss = {
                    creatingProject = false
                    renamingProject = null
                }
            )
        }
        // 对话参数（每对话上下文条数 + 人设查看/修改；安洁莉娜锁定只读）
        if (showConvParams) {
            val sess = ChatStore.findSession(history, history.active_session)
            val aid = sess?.agent_aid ?: agentCurrent
            ConvParamDialog(
                theme,
                ctxDraft = if ((sess?.context_size ?: 0) > 0) (sess?.context_size ?: 0).toString() else "",
                agentName = agentNameOf(history, aid),
                // 彩蛋隐秘性：前端始终显示安洁莉娜本体的 Skill（实际对话用彩蛋人设，此处不透露）
                agentPrompt = if (aid.isEmpty()) ChatStore.defaultAgentPrompt else ChatStore.activeAgentPrompt(history, aid, ""),
                locked = aid.isEmpty(),
                onSave = { ctxText, promptText ->
                    sess?.let { s ->
                        val n = ctxText.trim().toIntOrNull() ?: 0
                        s.context_size = when {
                            n < 0 -> 0
                            n > 100 -> 100
                            else -> n
                        }
                    }
                    if (aid.isNotEmpty() && promptText.isNotEmpty()) {
                        ChatStore.agentOf(history, aid)?.let { a ->
                            a.prompt = promptText
                        }
                    }
                    showConvParams = false
                    onMutate()
                },
                onDismiss = { showConvParams = false }
            )
        }
        // 移动到项目
        moveProjectSid?.let { sid ->
            val sess = history.sessions.firstOrNull { it.sid == sid }
            if (sess != null) {
                MoveToProjectDialog(
                    theme,
                    current = sess.pid,
                    projects = history.projects,
                    onPick = { pid ->
                        if (ChatStore.moveSession(history, sid, pid)) {
                            // 移入项目后自动展开目标文件夹（收起时移入会"消失"，用户会以为没生效）
                            if (pid.isNotEmpty() && !("," + expandedPids + ",").contains("," + pid + ",")) {
                                expandedPids = if (expandedPids.isEmpty()) pid else expandedPids + "," + pid
                                SettingsStore.saveExpandedPids(context, expandedPids)
                            }
                            onMutate()
                        }
                        moveProjectSid = null
                    },
                    onDismiss = { moveProjectSid = null }
                )
            } else {
                moveProjectSid = null
            }
        }
        if (creatingAgent || editingAgent != null) {
            @Suppress("UNUSED_EXPRESSION")
            panelVersion
            AgentEditDialog(
                theme, if (creatingAgent) null else editingAgent,
                onSave = { name, prompt ->
                    if (creatingAgent) {
                        history.agents.add(
                            Agent("a" + System.currentTimeMillis() + "-" + (Math.random() * 100000).toInt(), name, prompt, System.currentTimeMillis())
                        )
                    } else {
                        editingAgent?.name = name
                        editingAgent?.prompt = prompt
                    }
                    creatingAgent = false
                    editingAgent = null
                    panelVersion++
                    onMutate()
                },
                onDelete = if (creatingAgent) null else {
                    {
                        editingAgent?.let { a -> history.agents.remove(a) }
                        editingAgent = null
                        panelVersion++
                        onMutate()
                    }
                },
                onClose = {
                    creatingAgent = false
                    editingAgent = null
                }
            )
        }
    }
}

/** 无项目分类的列表占位标记 */
private const val NONE_PROJECT_MARK = "@none-project"

/** 侧边栏会话行（展开：圆点+标题；收起：竖条+两步气泡） */
@Composable
private fun SidebarSessionRow(
    theme: ThemeTokens,
    s: Session,
    active: Boolean,
    collapsed: Boolean,
    peekSid: String?,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    if (collapsed) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .alpha(0.5f)
                .pointerInput(s.sid) {
                    detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(if (active) theme.accent else theme.accentBorder, RoundedCornerShape(2.dp))
            )
            if (peekSid == s.sid) {
                // 气泡锚点：行右缘的零宽盒——气泡贴在侧边栏右侧（鸿蒙 Placement.Right 同款），不遮挡竖条可再次点击关闭
                Box(modifier = Modifier.align(Alignment.CenterEnd).width(0.dp)) {
                    Popup(
                        alignment = Alignment.CenterStart,
                        offset = androidx.compose.ui.unit.IntOffset((10f * density).toInt(), 0),
                        properties = PopupProperties(focusable = false)
                    ) {
                        Text(
                            s.title,
                            color = Color(0xFF1A1A1A),
                            fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(s.sid) {
                    detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
                }
                .height(32.dp)
                .padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 圆点：选中=强调色、未选中=淡色，均 100% 不透明
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (active) theme.accent else theme.accentBorder, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                s.title,
                color = if (active) theme.textPrimary else theme.textBody,
                fontSize = 14.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChatSidebar(
    theme: ThemeTokens,
    history: ChatHistory,
    historyVersion: Int,
    onMutate: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAgents: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    isLandscape: Boolean,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    currentProjectPid: String,
    expandedPids: String,
    onToggleProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onLongPressProject: (String) -> Unit,
    agentCurrent: String
) {
    // 收起态两步交互（鸿蒙同款）：第一步点击竖条仅弹标题气泡，再点同一条才切换会话
    var peekSid by remember { mutableStateOf<String?>(null) }
    // 三态宽度（鸿蒙矩阵）：收起 80 / 横屏展开 220 / 竖屏展开 140
    val sideW by animateDpAsState(
        targetValue = if (collapsed) 56.dp else if (isLandscape) 220.dp else 140.dp,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "sidebarW"
    )
    Column(
        modifier = Modifier
            .width(sideW)
            .fillMaxHeight()
            .background(theme.bgSidebar)
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = if (collapsed) 2.dp else 10.dp)
    ) {
        // 顶行：真 logo（avatar.png，两态保留）+ 应用名；整行点击收起/展开（收起只留 logo；箭头仅横屏可见）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clickable { onToggleCollapse() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) androidx.compose.foundation.layout.Arrangement.Center else androidx.compose.foundation.layout.Arrangement.Start
        ) {
            Image(
                painter = painterResource(R.drawable.avatar),
                contentDescription = "头像",
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            if (!collapsed) {
                Spacer(Modifier.width(8.dp))
                Text("酸橙味的信", color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
            }
            if (isLandscape) {
                Spacer(Modifier.width(4.dp))
                Text(if (collapsed) "›" else "‹", color = theme.textSecondary, fontSize = 22.sp)
            }
        }
        // 新对话（收起态只剩 +；+ 与 logo 同轴：外层 10 + 行内 10 → 中心 28）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(if (collapsed) Color.Transparent else Color.White, RoundedCornerShape(10.dp))
                .border(width = 1.dp, color = if (collapsed) Color.Transparent else theme.borderSubtle, shape = RoundedCornerShape(10.dp))
                .clickable {
                    ChatStore.newSession(history, "新对话", currentProjectPid, agentCurrent)
                    onMutate()
                }
                .padding(start = if (collapsed) 0.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) androidx.compose.foundation.layout.Arrangement.Center else androidx.compose.foundation.layout.Arrangement.Start
        ) {
            Text("＋", color = theme.textSecondary, fontSize = 15.sp)
            if (!collapsed) {
                Spacer(Modifier.width(8.dp))
                Text("新对话", color = theme.textBody, fontSize = 14.sp)
            }
        }
        // 新建项目（展开="+"紫色符号；收起=紫色竖条；紫色=项目域颜色）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(start = if (collapsed) 0.dp else 4.dp, end = if (collapsed) 0.dp else 12.dp)
                .clickable { if (!collapsed) onNewProject() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) androidx.compose.foundation.layout.Arrangement.Center else androidx.compose.foundation.layout.Arrangement.Start
        ) {
            if (collapsed) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .alpha(0.5f)
                        .background(Color(0xFF8B5CF6), RoundedCornerShape(2.dp))
                )
            } else {
                Text("＋", color = Color(0xFF8B5CF6), fontSize = 16.sp, modifier = Modifier.width(8.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.width(8.dp))
                Text("新建项目", color = theme.textBody, fontSize = 14.sp)
            }
        }
        // 话题列表：项目文件夹（箭头 + 展开时其下会话缩进）→ 无项目固定分类（灰点，不可收起）
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // historyVersion 用于强制重建（原地改列表不触发重组）
            @Suppress("UNUSED_EXPRESSION")
            historyVersion
            fun isExpandedPid(pid: String): Boolean =
                ("," + expandedPids + ",").contains("," + pid + ",")
            // 扁平列表：Project=文件夹项、String(无项目标记)、Session=会话项
            val items = mutableListOf<Any>()
            for (p in history.projects) {
                items.add(p)
                if (isExpandedPid(p.pid)) {
                    for (s in history.sessions) {
                        if (s.pid == p.pid) {
                            items.add(s)
                        }
                    }
                }
            }
            if (history.sessions.any { it.pid.isEmpty() }) {
                items.add(NONE_PROJECT_MARK)
                for (s in history.sessions) {
                    if (s.pid.isEmpty()) {
                        items.add(s)
                    }
                }
            }
            for (item in items) {
                when {
                    item is Project -> {
                        // 项目文件夹：双线箭头（展开向下/收起向右）+ 名称；点击展开收起（收起态也可交互）；长按重命名/删除
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .padding(start = if (collapsed) 0.dp else 4.dp, end = if (collapsed) 0.dp else 12.dp)
                                .pointerInput(item.pid) {
                                    detectTapGestures(
                                        onTap = { onToggleProject(item.pid) },
                                        onLongPress = { if (!collapsed) onLongPressProject(item.pid) }
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (collapsed) androidx.compose.foundation.layout.Arrangement.Center else androidx.compose.foundation.layout.Arrangement.Start
                        ) {
                            ProjectArrowGlyph(open = isExpandedPid(item.pid), color = theme.accent)
                            if (!collapsed) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    item.name,
                                    color = theme.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    item == NONE_PROJECT_MARK -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .padding(start = if (collapsed) 0.dp else 4.dp, end = if (collapsed) 0.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (collapsed) androidx.compose.foundation.layout.Arrangement.Center else androidx.compose.foundation.layout.Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(if (collapsed) 4.dp else 8.dp)
                                    .height(if (collapsed) 24.dp else 8.dp)
                                    .alpha(if (collapsed) 0.5f else 1f)
                                    .background(Color(0xFF999999), RoundedCornerShape(if (collapsed) 2.dp else 4.dp))
                            )
                            if (!collapsed) {
                                Spacer(Modifier.width(8.dp))
                                Text("无项目", color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                        }
                    }
                    item is Session -> {
                        SidebarSessionRow(
                            theme, item, item.sid == history.active_session, collapsed,
                            peekSid = peekSid,
                            onTap = {
                                if (collapsed) {
                                    // 收起态两步交互：第一步点击竖条仅弹标题气泡，再点同一条才切换会话
                                    if (peekSid == item.sid) {
                                        peekSid = null
                                        history.active_session = item.sid
                                        onMutate()
                                    } else {
                                        peekSid = item.sid
                                    }
                                } else {
                                    history.active_session = item.sid
                                    onMutate()
                                }
                            },
                            onLongPress = { onRename(item.sid) }
                        )
                    }
                }
            }
        }
        // 底行（照鸿蒙版）：主题点（左下，收起只剩点）+ 智能体四角星（收起隐藏）+ 设置齿轮
        Row(modifier = Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.height(28.dp).clickable { onToggleTheme() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(theme.accent, CircleShape)
                )
                if (!collapsed) {
                    Spacer(Modifier.width(6.dp))
                    Text(theme.name, color = theme.textSecondary, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            // 智能体入口（四角星 18vp：细十字胶囊 + 中心空心 + 卫星小十字；收起时隐藏）
            if (!collapsed) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(28.dp)
                        .clickable { onOpenAgents() },
                    contentAlignment = Alignment.Center
                ) {
                    StarGlyph(color = theme.textSecondary, hole = theme.bgSidebar, size = 18.dp)
                }
            }
            // 设置入口（齿轮 14vp：三条滑块线 + 错落圆钮）
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(28.dp)
                    .clickable { onOpenSettings() },
                contentAlignment = Alignment.Center
            ) {
                GearGlyph(color = theme.textSecondary, size = 14.dp)
            }
        }
    }
}

@Composable
private fun ChatMain(
    theme: ThemeTokens,
    history: ChatHistory,
    historyVersion: Int,
    settings: ChatSettings,
    agentPrompt: String,
    streamVersion: Int,
    api: ChatApi,
    onBubble: (String, Long) -> Unit,
    onBubbleStream: (String) -> Unit,
    onMutate: () -> Unit,
    isPhone: Boolean,
    isLandscape: Boolean,
    sidebarCollapsed: Boolean,
    currentProjectPid: String,
    onSelectProject: (String) -> Unit,
    onRenameTopic: () -> Unit,
    agentCurrent: String,
    onSelectAgent: (String) -> Unit,
    onOpenConvParams: () -> Unit,
    onOpenAgents: () -> Unit
) {
    // 768 定宽仅限平板横屏收起（鸿蒙矩阵）；其余情况自适应填满主区
    Box(
        modifier = Modifier.fillMaxSize().background(theme.bgMain),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = (if (!isPhone && sidebarCollapsed && isLandscape) {
                Modifier.width(768.dp).fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            }).padding(
                // 主区外边距（鸿蒙矩阵）：横屏 24/20，竖屏收紧 10/16
                start = if (isLandscape) 24.dp else 10.dp,
                top = if (isLandscape) 20.dp else 16.dp,
                end = if (isLandscape) 24.dp else 10.dp,
                bottom = if (isLandscape) 20.dp else 16.dp
            )
        ) {
        val session = ChatStore.findSession(history, history.active_session)
        // 话题标题（点击直接在对话界面重命名）
        if (session != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRenameTopic() }
                    .padding(top = 2.dp, bottom = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text(session.title, color = theme.textTertiary, fontSize = 13.sp, maxLines = 1)
            }
        }
        val rounds = session?.path?.mapNotNull { nid ->
            session.rounds.firstOrNull { it.nid == nid }
        } ?: emptyList()
        // 消息操作对话框状态
        var actionTarget by remember { mutableStateOf<RoundNode?>(null) }
        var editingTarget by remember { mutableStateOf<RoundNode?>(null) }
        var editingIsUser by remember { mutableStateOf(true) }
        var editResendMode by remember { mutableStateOf(false) }
        var deleteConfirm by remember { mutableStateOf<RoundNode?>(null) }
        var deleteIsUser by remember { mutableStateOf(true) }
        var jumpNid by remember { mutableStateOf("") }
        var jumpNonce by remember { mutableStateOf(0) }
        val clipboardForDialogs = LocalClipboardManager.current
        val hasMessages = rounds.any { it.user.isNotEmpty() || it.assistant.isNotEmpty() }
        if (!hasMessages) {
            Hero(theme, Modifier.weight(1f))
        } else {
            var visMin by remember { mutableStateOf(0) }
            var visMax by remember { mutableStateOf(0) }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                MessageList(
                    theme, rounds, historyVersion, streamVersion,
                    onLongPress = { node, isUser -> actionTarget = node; deleteIsUser = isUser },
                    onVisibleRange = { mn, mx -> visMin = mn; visMax = mx },
                    jumpNid = jumpNid,
                    jumpNonce = jumpNonce,
                    Modifier.weight(1f)
                )
                // 导航轨仅横屏显示（鸿蒙矩阵：竖屏隐藏）
                if (isLandscape) {
                    ConversationRail(
                        theme, history, rounds, visMin, visMax,
                        onJumpRound = { nid -> jumpNid = nid },
                        onSwitchBranch = { anchorNid, childNid ->
                            ChatStore.switchBranch(history, history.active_session, anchorNid, childNid)
                            onMutate()
                        }
                    )
                    // 右侧留白：锚点圆点/数字溢出轨道时落在屏内（轨道不再贴死屏幕右缘）
                    Spacer(Modifier.width(32.dp))
                }
            }
        }
        // 消息操作对话框
        actionTarget?.let { node ->
            val isUser = deleteIsUser
            MessageActionDialog(
                theme,
                preview = if (isUser) node.user else node.assistant,
                role = if (isUser) "user" else "assistant",
                onCopy = {
                    clipboardForDialogs.setText(AnnotatedString(if (isUser) node.user else node.assistant))
                    actionTarget = null
                },
                onEdit = {
                    editingTarget = node
                    editingIsUser = isUser
                    editResendMode = false
                    actionTarget = null
                },
                onEditResend = {
                    editingTarget = node
                    editingIsUser = true
                    editResendMode = true
                    actionTarget = null
                },
                onRegen = {
                    actionTarget = null
                    if (api.running) return@MessageActionDialog
                    val sess = ChatStore.findSession(history, history.active_session)
                    val aid = sess?.agent_aid ?: ""
                    val newNid = ChatStore.regenBranch(history, history.active_session, node.nid)
                    if (newNid.isNotEmpty()) {
                        onMutate()
                        // 复用发送逻辑：把回调传给 ComposerCard 的 sendRound——通过共享顶层函数
                        sendRoundTop(history, settings, agentPrompt, api, onBubble, onBubbleStream, onMutate,
                            history.active_session, newNid, aid.isEmpty(),
                            if (aid.isEmpty()) "安洁莉娜" else (ChatStore.agentOf(history, aid)?.name ?: "陌生的友人"), aid)
                    }
                },
                onDelete = {
                    actionTarget = null
                    val sess = ChatStore.findSession(history, history.active_session)
                    val willRemove = sess != null && (if (isUser) ChatStore.findRound(sess, node.nid)?.assistant.isNullOrEmpty() else ChatStore.findRound(sess, node.nid)?.user.isNullOrEmpty())
                    val anchor = willRemove && ChatStore.isAnchor(history, history.active_session, node.nid)
                    if (anchor) {
                        deleteConfirm = node
                    } else {
                        ChatStore.deleteMessageSide(history, history.active_session, node.nid, isUser)
                        onMutate()
                    }
                },
                onDismiss = { actionTarget = null }
            )
        }
        // 编辑对话框
        editingTarget?.let { node ->
            val isUser = editingIsUser
            MessageEditDialog(
                theme,
                draft = if (isUser) node.user else node.assistant,
                onConfirm = { text ->
                    val t = text.trim()
                    if (t.isEmpty()) {
                        editingTarget = null
                        return@MessageEditDialog
                    }
                    if (editResendMode) {
                        // 编辑并重发：改用户输入 → 开新分支 → 重新生成
                        if (api.running) {
                            editingTarget = null
                            return@MessageEditDialog
                        }
                        val sess = ChatStore.findSession(history, history.active_session)
                        val aid = sess?.agent_aid ?: ""
                        val newNid = ChatStore.editResendBranch(history, history.active_session, node.nid, t)
                        if (newNid.isNotEmpty()) {
                            onMutate()
                            sendRoundTop(history, settings, agentPrompt, api, onBubble, onBubbleStream, onMutate,
                                history.active_session, newNid, aid.isEmpty(),
                                if (aid.isEmpty()) "安洁莉娜" else (ChatStore.agentOf(history, aid)?.name ?: "陌生的友人"), aid)
                        }
                    } else {
                        ChatStore.editRoundContent(history, history.active_session, node.nid, isUser, t)
                        onMutate()
                    }
                    editingTarget = null
                },
                onDismiss = { editingTarget = null }
            )
        }
        // 删除确认（锚点警告）
        deleteConfirm?.let { node ->
            ConfirmDeleteDialog(
                theme,
                title = "该轮是对话树锚点，删除将连同其全部后续分支一并删除，确定？",
                onConfirm = {
                    ChatStore.deleteMessageSide(history, history.active_session, node.nid, deleteIsUser)
                    deleteConfirm = null
                    onMutate()
                },
                onDismiss = { deleteConfirm = null }
            )
        }
            ComposerCard(theme, history, settings, agentPrompt, api, onBubble, onBubbleStream, onMutate,
                onPickResult = { nid ->
                    jumpNid = nid
                    jumpNonce++
                },
                currentProjectPid = currentProjectPid,
                onSelectProject = onSelectProject,
                agentCurrent = agentCurrent,
                onSelectAgent = onSelectAgent,
                onOpenConvParams = onOpenConvParams,
                onOpenAgents = onOpenAgents,
                isPhone, isLandscape)
        }
    }
}

@Composable
private fun Hero(theme: ThemeTokens, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(0.72f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(theme.accent, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("予", color = Color.White, fontSize = 17.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text("安洁莉娜的来信", color = theme.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(24.dp))
            Text("给芋圆写一封信吧，她会认真读完的", color = theme.textSecondary, fontSize = 14.sp)
        }
        Spacer(Modifier.weight(1.28f))
    }
}

@Composable
private fun MessageList(
    theme: ThemeTokens,
    rounds: List<RoundNode>,
    historyVersion: Int,
    streamVersion: Int,
    onLongPress: (RoundNode, Boolean) -> Unit,
    onVisibleRange: (Int, Int) -> Unit = { _, _ -> },
    jumpNid: String = "",
    jumpNonce: Int = 0,
    modifier: Modifier = Modifier
) {
    var expandedReasoning by remember { mutableStateOf(setOf<String>()) }
    val scrollState = rememberScrollState()
    val clipboard = LocalClipboardManager.current
    // 导航轨支撑：行位置/高度、视口高度、可见轮次汇报
    val rowPositions = remember { mutableMapOf<String, Float>() }
    val rowHeights = remember { mutableMapOf<String, Float>() }
    val lastReported = remember { intArrayOf(-1, -1) }
    var viewportH by remember { mutableStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    // 自动跟随滚动（Chatbox 式）：仅在本来就贴底时跟随——上滑或导航轨跳转即断开
    val atBottom = remember { derivedStateOf { scrollState.value >= scrollState.maxValue - 200 } }
    LaunchedEffect(historyVersion, streamVersion) {
        if (atBottom.value) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    // 导航轨/搜索跳转：滚动到目标轮（顶部留 24dp 余量）；nonce 支持重复跳转同一轮
    LaunchedEffect(jumpNid, jumpNonce) {
        if (jumpNid.isNotEmpty()) {
            rowPositions[jumpNid]?.let { y ->
                scrollState.animateScrollTo(maxOf(0, y.toInt() - (24f * density).toInt()))
            }
        }
    }
    // 可见轮次汇报（effect 在布局后执行，位置数据最新）
    LaunchedEffect(scrollState.value, historyVersion, viewportH, rounds.size) {
        val scrollPos = scrollState.value.toFloat()
        var firstVis = -1
        var lastVis = -1
        for (i in rounds.indices) {
            val y = rowPositions[rounds[i].nid] ?: continue
            val h = rowHeights[rounds[i].nid] ?: 60f
            if (y + h > scrollPos && y < scrollPos + viewportH) {
                if (firstVis < 0) firstVis = i
                lastVis = i
            }
        }
        if (firstVis >= 0 && (firstVis != lastReported[0] || lastVis != lastReported[1])) {
            lastReported[0] = firstVis
            lastReported[1] = lastVis
            onVisibleRange(firstVis, lastVis)
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .onGloballyPositioned { viewportH = it.size.height.toFloat() }
            .verticalScroll(scrollState)
    ) {
        @Suppress("UNUSED_EXPRESSION")
        historyVersion
        @Suppress("UNUSED_EXPRESSION")
        streamVersion
        for (node in rounds) {
            Column(
                modifier = Modifier.onGloballyPositioned {
                    rowPositions[node.nid] = it.positionInParent().y
                    rowHeights[node.nid] = it.size.height.toFloat()
                }
            ) {
                if (node.user.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                        // 用户气泡最大宽 75%（鸿蒙同款）
                        BoxWithConstraints {
                            Text(
                                node.user,
                                color = theme.textBody,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                modifier = Modifier
                                    .widthIn(max = maxWidth * 0.75f)
                                    .background(theme.bubbleUser, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 6.dp))
                                    .pointerInput(node.nid) {
                                        detectTapGestures(onLongPress = {
                                            onLongPress(node, true)
                                        })
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
                if (node.reasoning.isNotEmpty()) {
                    val expanded = node.nid in expandedReasoning
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.bgHover, RoundedCornerShape(10.dp))
                            .clickable {
                                expandedReasoning = if (expanded) {
                                    expandedReasoning - node.nid
                                } else {
                                    expandedReasoning + node.nid
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .padding(start = 24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (expanded) "▾ 思考过程" else "▸ 思考过程",
                                color = theme.textTertiary,
                                fontSize = 13.sp
                            )
                        }
                        if (expanded) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                node.reasoning,
                                color = theme.textTertiary,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
                if (node.assistant.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(theme.accentSoft, RoundedCornerShape(4.dp))
                                .padding(top = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("予", color = theme.accent, fontSize = 9.sp, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.width(8.dp))
                        MarkdownView(
                            text = node.assistant,
                            fontColor = theme.textBody,
                            baseSize = 15,
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(node.nid) {
                                    detectTapGestures(onLongPress = {
                                        onLongPress(node, false)
                                    })
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerCard(
    theme: ThemeTokens,
    history: ChatHistory,
    settings: ChatSettings,
    agentPrompt: String,
    api: ChatApi,
    onBubble: (String, Long) -> Unit,
    onBubbleStream: (String) -> Unit,
    onMutate: () -> Unit,
    onPickResult: (String) -> Unit,
    currentProjectPid: String,
    onSelectProject: (String) -> Unit,
    agentCurrent: String,
    onSelectAgent: (String) -> Unit,
    onOpenConvParams: () -> Unit,
    onOpenAgents: () -> Unit,
    isPhone: Boolean,
    isLandscape: Boolean
) {
    var text by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(false) }
    var projectPickerShow by remember { mutableStateOf(false) }
    var agentPickerShow by remember { mutableStateOf(false) }
    val streaming = api.running
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val sessNow = ChatStore.findSession(history, history.active_session)
    val displayPid = sessNow?.pid ?: currentProjectPid
    // 有效智能体：有活跃会话=该会话绑定；无=当前选取（新会话烘焙用）
    val agentAid = sessNow?.agent_aid ?: agentCurrent

    // 输入框占位符：按当前会话绑定的智能体
    fun placeholderText(): String = when {
        agentAid.isEmpty() -> "与安洁莉娜对话…"
        agentAid == "~raw" -> "直接与大模型对话…"
        else -> "给「" + agentNameOf(history, agentAid) + "」写信…"
    }

    // 命中内容的发送者：用户消息=自定义称呼，LLM 消息=会话智能体名
    fun senderOf(kind: Int): String {
        if (kind == 1) {
            return settings.player_name
        }
        val sess = ChatStore.findSession(history, history.active_session)
        val aid = sess?.agent_aid ?: ""
        return if (aid.isEmpty()) "安洁莉娜" else (ChatStore.agentOf(history, aid)?.name ?: "陌生的友人")
    }

    fun finishRound(full: String, sid: String, nid: String, agentName: String, direct: Boolean) {
        ChatStore.setAssistant(history, sid, nid, full)
        if (direct) {
            onBubble(full, 12000L)
        } else {
            onBubble("收到「" + agentName + "」的来信", 4000L)
        }
        onMutate()
    }

    fun sendRound(sid: String, nid: String, direct: Boolean, agentName: String, aid: String) {
        if (settings.chat_api_key.isEmpty()) {
            // 假打字演示
            val fake = "博士，先帮我在设置里填上 API Key 吧～"
            val h = android.os.Handler(android.os.Looper.getMainLooper())
            val state = intArrayOf(0)
            val runnable = object : Runnable {
                override fun run() {
                    state[0]++
                    if (state[0] <= fake.length) {
                        if (direct) {
                            onBubbleStream(fake.substring(state[0] - 1, state[0]))
                        }
                        h.postDelayed(this, 30)
                    } else {
                        finishRound(fake, sid, nid, "安洁莉娜", true)
                    }
                }
            }
            h.postDelayed(runnable, 30)
            return
        }
        val msgs = ChatStore.buildPathMessages(history, settings, ChatStore.activeAgentPrompt(history, aid, settings.extra_prompt))
        api.send(settings.chat_base_url, settings.chat_api_key, settings.chat_model, msgs, object : ChatApi.Callbacks {
            override fun onReasoning(delta: String) {
                val session = ChatStore.findSession(history, sid)
                val node = session?.rounds?.firstOrNull { it.nid == nid }
                node?.reasoning = (node?.reasoning ?: "") + delta
            }

            override fun onDelta(delta: String) {
                val session = ChatStore.findSession(history, sid)
                val node = session?.rounds?.firstOrNull { it.nid == nid }
                node?.assistant = (node?.assistant ?: "") + delta
                if (direct && sid == history.active_session) {
                    onBubbleStream(delta)
                }
                onMutate()
            }

            override fun onDone(full: String) {
                finishRound(full, sid, nid, agentName, direct)
            }

            override fun onError(err: String) {
                finishRound("（寄信失败了：" + err.take(80) + "）", sid, nid, agentName, direct)
            }
        })
    }

    fun doSend() {
        if (text.isEmpty()) return
        if (history.active_session.isEmpty() || ChatStore.findSession(history, history.active_session) == null) {
            ChatStore.newSession(history, "新对话", currentProjectPid, agentCurrent)
        }
        val sid = history.active_session
        val sess = ChatStore.findSession(history, sid)
        val aid = sess?.agent_aid ?: ""
        val nid = ChatStore.appendUserRound(history, sid, text.trim()) ?: return
        text = ""
        onMutate()
        val agentName = if (aid.isEmpty()) "安洁莉娜" else (ChatStore.agentOf(history, aid)?.name ?: "陌生的友人")
        // 安洁莉娜 = 直接对话：气泡随流同步输出；其他智能体 = 信使语义（只提示寄信/收信）
        val direct = aid.isEmpty()
        if (direct) {
            onBubbleStream("")
        } else {
            onBubble("寄信给「" + agentName + "」…", 4000L)
        }
        sendRound(sid, nid, direct, agentName, aid)
    }

    // 输入卡片矩阵（鸿蒙同款）：手机横屏整体收紧；平板横屏正常；竖屏收紧让输入框更宽
    val phoneLand = isPhone && isLandscape
    Box {
        // 智能体圆点（输入卡片左上外侧，32×32 判定区；x=-28 相对卡片白边，鸿蒙同款）
        val dotY = if (phoneLand) (-5).dp else if (isLandscape) 3.dp else 1.dp
        // 弹层锚点：圆点上方的零高度盒（弹层向上展开）
        Box(
            modifier = Modifier
                .offset(x = (-28).dp, y = dotY)
                .height(0.dp)
        ) {
            if (agentPickerShow) {
                Popup(
                    alignment = Alignment.BottomStart,
                    offset = androidx.compose.ui.unit.IntOffset(0, -(4f * density).toInt()),
                    properties = PopupProperties(focusable = false)
                ) {
                    Column(
                        modifier = Modifier
                            .width(180.dp)
                            .shadow(elevation = 16.dp, shape = RoundedCornerShape(10.dp), spotColor = Color(0x33000000))
                            .background(Color.White, RoundedCornerShape(10.dp))
                            .padding(top = 6.dp, bottom = 6.dp)
                    ) {
                        AgentPickerRow(theme, "安洁莉娜", agentAid.isEmpty()) {
                            agentPickerShow = false
                            onSelectAgent("")
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEEEEE)))
                        AgentPickerRow(theme, "陌生的友人", agentAid == "~raw") {
                            agentPickerShow = false
                            onSelectAgent("~raw")
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEEEEE)))
                        Text(
                            "管理智能体",
                            color = Color(0xFF666666),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clickable {
                                    agentPickerShow = false
                                    onOpenAgents()
                                }
                        )
                    }
                }
            }
        }
        // 圆点本体：有活跃会话=打开对话参数；未开对话=弹三选一
        Box(
            modifier = Modifier
                .offset(x = (-28).dp, y = dotY)
                .size(32.dp)
                .clickable {
                    if (sessNow != null) {
                        onOpenConvParams()
                    } else {
                        agentPickerShow = !agentPickerShow
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(12.dp).background(theme.accent, CircleShape))
        }
        Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), spotColor = Color(0x1A1F2329))
            .background(theme.bgMain, RoundedCornerShape(20.dp))
            .border(1.dp, theme.borderSubtle, RoundedCornerShape(20.dp))
            .padding(
                start = if (phoneLand) 10.dp else if (isLandscape) 16.dp else 10.dp,
                top = if (phoneLand) 4.dp else if (isLandscape) 12.dp else 10.dp,
                end = if (phoneLand) 6.dp else if (isLandscape) 10.dp else 6.dp,
                bottom = if (phoneLand) 4.dp else if (isLandscape) 10.dp else 8.dp
            )
    ) {
        // 项目胶囊：显示当前归属（有活跃会话=该会话所属；无=新建会话归属）；手机横屏空间紧张时隐藏
        if (!(isPhone && isLandscape)) {
            fun projectLabel(): String {
                if (displayPid.isEmpty()) {
                    return "无项目"
                }
                return history.projects.firstOrNull { it.pid == displayPid }?.name ?: "无项目"
            }
            // 弹层锚点：胶囊上方的零高度盒（弹层向上展开，贴近胶囊）
            Box(modifier = Modifier.height(0.dp)) {
                if (projectPickerShow) {
                    Popup(
                        alignment = Alignment.BottomStart,
                        offset = androidx.compose.ui.unit.IntOffset(0, -(8f * density).toInt()),
                        properties = PopupProperties(focusable = false)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(180.dp)
                                .shadow(elevation = 16.dp, shape = RoundedCornerShape(10.dp), spotColor = Color(0x33000000))
                                .background(Color.White, RoundedCornerShape(10.dp))
                                .padding(top = 6.dp, bottom = 6.dp)
                        ) {
                            ProjectPickerRow(theme, "无项目", displayPid.isEmpty()) {
                                projectPickerShow = false
                                onSelectProject("")
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEEEEE)))
                            for (p in history.projects) {
                                ProjectPickerRow(theme, p.name, displayPid == p.pid) {
                                    projectPickerShow = false
                                    onSelectProject(p.pid)
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clickable { projectPickerShow = !projectPickerShow },
                verticalAlignment = Alignment.CenterVertically
            ) {
                FolderGlyph(theme.accent, 14.dp)
                Spacer(Modifier.width(4.dp))
                Text(projectLabel(), color = theme.textSecondary, fontSize = 12.sp, maxLines = 1)
            }
        }
        // 搜索结果（搜索模式下从输入框上方拉起）
        if (searchMode) {
            val results = ChatStore.searchInSession(history, history.active_session, text)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(12.dp), spotColor = Color(0x22000000))
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(6.dp)
            ) {
                if (text.trim().isNotEmpty() && results.isEmpty()) {
                    Text(
                        "无匹配结果",
                        color = theme.textTertiary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    for (r in results) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    text = ""
                                    searchMode = false
                                    onPickResult(r.nid)
                                }
                                .padding(top = 8.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                senderOf(r.kind) + "：",
                                color = if (r.kind == 1) theme.accent else theme.textTertiary,
                                fontSize = 11.sp
                            )
                            Text(
                                r.snippet,
                                color = theme.textBody,
                                fontSize = 13.sp,
                                maxLines = 2,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(
                        min = if (phoneLand) 26.dp else if (isLandscape) 52.dp else 40.dp,
                        max = if (phoneLand) 70.dp else if (isLandscape) 140.dp else 105.dp
                    ),
                textStyle = TextStyle(color = theme.textBody, fontSize = 15.sp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                if (searchMode) "搜索对话…" else placeholderText(),
                                color = theme.textTertiary,
                                fontSize = 15.sp
                            )
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            // 放大镜（搜索模式开关；内联绘制：圆环 + 斜柄，鸿蒙同款几何）
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable {
                        searchMode = !searchMode
                        if (!searchMode) {
                            text = ""
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    val c = if (searchMode) theme.accent else theme.textSecondary
                    drawCircle(
                        color = c,
                        radius = 5.dp.toPx(),
                        center = Offset(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                    drawLine(
                        color = c,
                        start = Offset(7.85.dp.toPx(), 9.8.dp.toPx()),
                        end = Offset(11.75.dp.toPx(), 13.7.dp.toPx()),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            // 动作键（鸿蒙同款）：搜索=✕退出；流式=■停止（红底）；否则=发送 ↑（32px 圆形）
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        when {
                            searchMode -> theme.bgHover
                            streaming -> Color(0xFFE5484D)
                            text.isNotEmpty() -> theme.accent
                            else -> theme.sendIdle
                        },
                        CircleShape
                    )
                    .clickable {
                        when {
                            searchMode -> {
                                searchMode = false
                                text = ""
                            }
                            streaming -> api.stop()
                            else -> doSend()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    searchMode -> Text("✕", color = theme.textSecondary, fontSize = 13.sp)
                    streaming -> Text("■", color = Color.White, fontSize = 12.sp)
                    else -> Text("↑", color = Color.White, fontSize = 16.sp)
                }
            }
        }
        }
    }
}/** 四角星（鸿蒙 StarGlyph 精确移植：主星细十字胶囊 + 中心空心圆环 + 右上实体小十字星） */
@Composable
private fun StarGlyph(color: Color, hole: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val cr = androidx.compose.ui.geometry.CornerRadius(w * 0.11f)
        // 主星：细竖胶囊 + 细横胶囊
        drawRoundRect(
            color, topLeft = Offset(w * 0.39f, w * 0.14f),
            size = androidx.compose.ui.geometry.Size(w * 0.22f, w * 0.72f), cornerRadius = cr
        )
        drawRoundRect(
            color, topLeft = Offset(w * 0.14f, w * 0.39f),
            size = androidx.compose.ui.geometry.Size(w * 0.72f, w * 0.22f), cornerRadius = cr
        )
        // 中心空心圆（背景色描边圆环 = 视觉挖空）
        drawCircle(
            color = hole, radius = w * 0.15f + w * 0.045f,
            center = Offset(w * 0.5f, w * 0.5f), style = Stroke(width = w * 0.09f)
        )
        // 卫星：实体小十字星（右上角）
        val cr2 = androidx.compose.ui.geometry.CornerRadius(w * 0.06f)
        drawRoundRect(
            color, topLeft = Offset(w * 0.64f, w * 0.07f),
            size = androidx.compose.ui.geometry.Size(w * 0.12f, w * 0.26f), cornerRadius = cr2
        )
        drawRoundRect(
            color, topLeft = Offset(w * 0.57f, w * 0.14f),
            size = androidx.compose.ui.geometry.Size(w * 0.26f, w * 0.12f), cornerRadius = cr2
        )
    }
}

/** 齿轮（鸿蒙 GearGlyph 精确移植：三条滑块线 + 错落圆钮） */
@Composable
private fun GearGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val cd = w * 3f / 14f // 圆钮直径
        val barH = w * 1.5f / 14f
        val gap = w * 3f / 14f
        val barR = androidx.compose.ui.geometry.CornerRadius(barH / 2f)
        val rows = listOf(
            Triple(true, 0.68f, 0f),
            Triple(false, 0.4f, cd + gap),
            Triple(true, 0.56f, 2f * (cd + gap))
        )
        for ((circleFirst, barW, y) in rows) {
            val cy = y + cd / 2f
            if (circleFirst) {
                drawCircle(color, radius = cd / 2f, center = Offset(cd / 2f, cy))
                drawRoundRect(
                    color,
                    topLeft = Offset(cd + gap, y),
                    size = androidx.compose.ui.geometry.Size(w * barW, barH),
                    cornerRadius = barR
                )
            } else {
                drawRoundRect(
                    color,
                    topLeft = Offset(0f, y),
                    size = androidx.compose.ui.geometry.Size(w * barW, barH),
                    cornerRadius = barR
                )
                drawCircle(color, radius = cd / 2f, center = Offset(w * barW + gap + cd / 2f, cy))
            }
        }
    }
}


/** 项目箭头（双线简约，主题色）：展开=向下 ⌄，收起=向右 ›；两条细杆 ±45° 绕交点旋转拼出 */
@Composable
private fun ProjectArrowGlyph(open: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(width = 8.dp, height = 14.dp)) {
        val px = 4.dp.toPx()
        val py = 11.dp.toPx()
        rotate(degrees = if (open) 0f else -90f, pivot = Offset(4.dp.toPx(), 7.dp.toPx())) {
            rotate(degrees = 45f, pivot = Offset(px, py)) {
                drawRoundRect(
                    color,
                    topLeft = Offset(3.dp.toPx(), 4.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(2.dp.toPx(), 7.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
                )
            }
            rotate(degrees = -45f, pivot = Offset(px, py)) {
                drawRoundRect(
                    color,
                    topLeft = Offset(3.dp.toPx(), 4.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(2.dp.toPx(), 7.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
                )
            }
        }
    }
}

/** 文件夹图标（鸿蒙 FolderGlyph 移植：上半翻盖 + 下半盒体，左对齐） */
@Composable
private fun FolderGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        drawRoundRect(
            color,
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(w * 0.45f, w * 0.22f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f)
        )
        drawRoundRect(
            color,
            topLeft = Offset(0f, w * 0.22f),
            size = androidx.compose.ui.geometry.Size(w, w * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f)
        )
    }
}

/** 话题改名对话框 */
@Composable
private fun RenameDialog(
    theme: ThemeTokens,
    current: String,
    onDone: (String) -> Unit,
    onDelete: () -> Unit,
    onMoveToProject: () -> Unit
) {
    var text by remember { mutableStateOf(current) }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable { onDone("") }
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text("话题标题", color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.bgHover, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                textStyle = TextStyle(color = theme.textBody, fontSize = 14.sp)
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(theme.bgHover, RoundedCornerShape(10.dp))
                        .clickable { onDone("") }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("取消", color = theme.textSecondary, fontSize = 14.sp)
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(theme.accent, RoundedCornerShape(10.dp))
                        .clickable { onDone(text) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("保存", color = Color.White, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.borderSubtle, RoundedCornerShape(10.dp))
                    .clickable { onMoveToProject() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("移动到项目", color = theme.textSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE5484D), RoundedCornerShape(10.dp))
                    .clickable { onDelete() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("删除该话题", color = Color(0xFFE5484D), fontSize = 13.sp)
            }
        }
    }
}


/** 共享发送（输入框与重新生成/编辑并重发复用；鸿蒙版 sendChat 的流式部分） */
private fun sendRoundTop(
    history: ChatHistory,
    settings: ChatSettings,
    agentPrompt: String,
    api: ChatApi,
    onBubble: (String, Long) -> Unit,
    onBubbleStream: (String) -> Unit,
    onMutate: () -> Unit,
    sid: String,
    nid: String,
    direct: Boolean,
    agentName: String,
    aid: String
) {
    if (direct) {
        onBubbleStream("")
    } else {
        onBubble("寄信给「" + agentName + "」…", 4000L)
    }
    if (settings.chat_api_key.isEmpty()) {
        val fake = "博士，先帮我在设置里填上 API Key 吧～"
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        val state = intArrayOf(0)
        val runnable = object : Runnable {
            override fun run() {
                state[0]++
                if (state[0] <= fake.length) {
                    if (direct) {
                        onBubbleStream(fake.substring(state[0] - 1, state[0]))
                    }
                    h.postDelayed(this, 30)
                } else {
                    ChatStore.setAssistant(history, sid, nid, fake)
                    if (direct) {
                        onBubble(fake, 12000L)
                    } else {
                        onBubble("收到「" + agentName + "」的来信", 4000L)
                    }
                    onMutate()
                }
            }
        }
        h.postDelayed(runnable, 30)
        return
    }
    val msgs = ChatStore.buildPathMessages(history, settings, ChatStore.activeAgentPrompt(history, aid, settings.extra_prompt))
    api.send(settings.chat_base_url, settings.chat_api_key, settings.chat_model, msgs, object : ChatApi.Callbacks {
        override fun onReasoning(delta: String) {
            val session = ChatStore.findSession(history, sid)
            val node = session?.rounds?.firstOrNull { it.nid == nid }
            node?.reasoning = (node?.reasoning ?: "") + delta
        }

        override fun onDelta(delta: String) {
            val session = ChatStore.findSession(history, sid)
            val node = session?.rounds?.firstOrNull { it.nid == nid }
            node?.assistant = (node?.assistant ?: "") + delta
            if (direct && sid == history.active_session) {
                onBubbleStream(delta)
            }
            onMutate()
        }

        override fun onDone(full: String) {
            ChatStore.setAssistant(history, sid, nid, full)
            if (direct) {
                onBubble(full, 12000L)
            } else {
                onBubble("收到「" + agentName + "」的来信", 4000L)
            }
            onMutate()
        }

        override fun onError(err: String) {
            ChatStore.setAssistant(history, sid, nid, "（寄信失败了：" + err.take(80) + "）")
            onMutate()
        }
    })
}

/** 消息操作对话框（鸿蒙版 MessageActionDialog 复刻） */
@Composable
private fun MessageActionDialog(
    theme: ThemeTokens,
    preview: String,
    role: String,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onEditResend: () -> Unit,
    onRegen: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable { onDismiss() }
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(280.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(top = 20.dp, bottom = 8.dp)
        ) {
            Text(
                preview, color = Color(0xFF666666), fontSize = 13.sp, maxLines = 2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(16.dp))
            DialogRow("复制", 0xFF1A1A1A.toInt()) { onCopy() }
            DialogRow("编辑", 0xFF1A1A1A.toInt()) { onEdit() }
            if (role == "user") {
                DialogRow("编辑并重发", 0xFF1A1A1A.toInt()) { onEditResend() }
            } else {
                DialogRow("重新生成", 0xFF1A1A1A.toInt()) { onRegen() }
            }
            DialogRow("删除", 0xFFE5484D.toInt()) { onDelete() }
            DialogRow("取消", 0xFF666666.toInt()) { onDismiss() }
        }
    }
}

@Composable
private fun DialogRow(label: String, color: Int, onClick: () -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFEEEEEE))
                .background(Color.White)
                .clickable { onClick() }
                .height(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = Color(color), fontSize = 15.sp)
        }
    }
}

/** 消息编辑对话框（多行输入） */
@Composable
private fun MessageEditDialog(
    theme: ThemeTokens,
    draft: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf(draft) }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable { onDismiss() }
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.85f)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text("编辑消息", color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(theme.bgHover, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                textStyle = TextStyle(color = theme.textBody, fontSize = 14.sp)
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(theme.bgHover, RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("取消", color = theme.textSecondary, fontSize = 14.sp)
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(theme.accent, RoundedCornerShape(10.dp))
                        .clickable { onConfirm(input) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("确认", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

/** 删除确认（锚点警告） */
@Composable
private fun ConfirmDeleteDialog(
    theme: ThemeTokens,
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable { onDismiss() }
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text(title, color = theme.textPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(theme.bgHover, RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("取消", color = theme.textSecondary, fontSize = 14.sp)
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFFE5484D), RoundedCornerShape(10.dp))
                        .clickable { onConfirm() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("删除", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}


/** 对话导航轨（鸿蒙版 ConversationRail 复刻：一轮一横条、正态伸缩、分支标记、浮窗预览） */
@Composable
private fun ConversationRail(
    theme: ThemeTokens,
    history: ChatHistory,
    rounds: List<RoundNode>,
    visMin: Int,
    visMax: Int,
    onJumpRound: (String) -> Unit,
    onSwitchBranch: (String, String) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    var focusIdx by remember { mutableStateOf(-1) }
    var railH by remember { mutableStateOf(0f) }
    val pitch = 20f * density

    // 每轮的分支信息（key=rounds 列表实例：数据变更必换新列表，防止同尺寸切路径/会话时用旧映射）
    // 锚点语义（用户定案）：在某轮开了分支 → 该轮是父节点下的兄弟之一，圆点挂在该轮上（兄弟数≥2）
    val branchMap = remember(rounds) {
        val session = ChatStore.findSession(history, history.active_session)
        rounds.mapNotNull { r ->
            if (r.parent.isEmpty()) return@mapNotNull null
            val siblings = session?.rounds?.filter { it.parent == r.parent } ?: emptyList()
            if (siblings.size >= 2) r.nid to siblings else null
        }.toMap()
    }

    fun cap(): Int = minOf(32, maxOf(8, (railH / pitch).toInt() - 2))

    fun windowStart(total: Int): Int {
        val c = cap()
        if (total <= c) return 0
        val center = (visMin + visMax) / 2
        var start = center - c / 2
        if (start < 0) start = 0
        if (start > total - c) start = total - c
        return start
    }

    fun barWidth(g: Int): Float {
        if (focusIdx < 0) return 12f * density
        val d = (g - focusIdx).toDouble()
        return ((12 + 28 * Math.exp(-(d * d) / 12.5)) * density).toFloat()
    }

    // 带分支标记的条长：焦点靠近标记轮时压低整个高斯振幅（上限 30dp），
    // 保证「聚焦条最长、向两侧依次递减」且圆点/数字有位置不溢出屏幕
    fun barMarkWidth(g: Int): Float {
        if (focusIdx < 0) return 12f * density
        val d = (g - focusIdx).toDouble()
        val nearMarked = (maxOf(0, focusIdx - 1)..minOf(rounds.size - 1, focusIdx + 1)).any {
            (branchMap[rounds.getOrNull(it)?.nid]?.size ?: 0) >= 2
        }
        val amp = if (nearMarked) 18.0 else 28.0
        return ((12 + amp * Math.exp(-(d * d) / 12.5)) * density).toFloat()
    }

    // 手势协程里读最新轮次（pointerInput 键为 rounds.size，同尺寸切换会话时闭包不重建）
    val roundsState = rememberUpdatedState(rounds)

    Box(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .onGloballyPositioned { railH = it.size.height.toFloat() }
            .pointerInput(rounds.size) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun idxAt(y: Float): Int {
                        val total = roundsState.value.size
                        if (total == 0 || railH <= 0f) return -1
                        val start = windowStart(total)
                        val shown = minOf(total, cap())
                        val used = minOf(shown * pitch, railH)
                        val topOffset = (railH - used) / 2
                        val yIn = y - topOffset
                        if (yIn < 0 || yIn > used) return -1
                        val idx = start + maxOf(0, minOf(shown - 1, (yIn / pitch).toInt()))
                        return if (idx in roundsState.value.indices) idx else -1
                    }
                    val startIdx = idxAt(down.position.y)
                    // 按下：空白=取消；同条=再点取消（微动不重新聚焦）；其他条=选中（鸿蒙同款）
                    val cancelled = if (startIdx < 0) {
                        focusIdx = -1
                        false
                    } else if (focusIdx == startIdx) {
                        focusIdx = -1
                        true
                    } else {
                        focusIdx = startIdx
                        false
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        if (cancelled) continue
                        if (change.position != change.previousPosition) {
                            val idx = idxAt(change.position.y)
                            // 划出条区取消、划回重新聚焦（鸿蒙同款）
                            focusIdx = if (idx >= 0) idx else -1
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val total = rounds.size
            val start = windowStart(total)
            val shown = minOf(total, cap())
            for (j in 0 until shown) {
                val g = start + j
                if (g !in rounds.indices) continue
                val branches = branchMap[rounds[g].nid] ?: emptyList()
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val barW by animateDpAsState(
                        targetValue = barMarkWidth(g).div(density).dp,
                        animationSpec = tween(120, easing = FastOutSlowInEasing),
                        label = "railBar"
                    )
                    Box(
                        modifier = Modifier
                            .width(barW)
                            .height(8.dp)
                            .background(
                                when {
                                    focusIdx == g -> theme.accent
                                    g in visMin..visMax -> Color(0xFFF0B400)
                                    else -> Color(0x661F2329)
                                },
                                RoundedCornerShape(4.dp)
                            )
                    )
                    if (branches.size >= 2) {
                        Row(
                            modifier = Modifier.offset(x = (barMarkWidth(g).div(density) / 2 + 16).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(theme.accent, CircleShape)
                            )
                            if (branches.size >= 3) {
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    branches.size.toString(),
                                    color = theme.accent,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        // 浮窗（聚焦时）：锚定聚焦横条、贴轨道左侧（Popup 须在 Box 内才锚定轨道坐标）
        if (focusIdx >= 0 && focusIdx < rounds.size) {
            val node = rounds[focusIdx]
            val branches = branchMap[node.nid] ?: emptyList()
            val activeChild = history.sessions
                .firstOrNull { it.sid == history.active_session }
                ?.let { sess -> branches.firstOrNull { sess.path.contains(it.nid) }?.nid ?: "" }
                ?: ""
            val winStart = windowStart(rounds.size)
            val winShown = minOf(rounds.size, cap())
            val usedH = minOf(winShown * pitch, railH)
            val topOff = (railH - usedH) / 2
            val barIdx = focusIdx - winStart
            val barCenterY = topOff + barIdx * pitch + pitch / 2
            val yOff = (barCenterY - railH / 2).toInt()
            // 垂直钳制在屏幕内（估算浮窗半高 140dp）
            val estHalf = 140f * density
            val yClamped = if (railH / 2f <= estHalf) 0 else yOff.coerceIn((estHalf - railH / 2f).toInt(), (railH / 2f - estHalf).toInt())
            if (barIdx in 0 until winShown) {
                Popup(
                    alignment = Alignment.CenterEnd,
                    offset = androidx.compose.ui.unit.IntOffset(-(56f * density).toInt(), yClamped),
                    properties = PopupProperties(focusable = false)
                ) {
                    Column(
                        modifier = Modifier
                            .width(240.dp)
                            .background(Color.White, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        if (node.user.isNotEmpty()) {
                            Text(
                                node.user,
                                color = Color(0xFF1A1A1A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        if (node.assistant.isNotEmpty()) {
                            Text(
                                node.assistant,
                                color = Color(0xFF555555),
                                fontSize = 12.sp,
                                maxLines = 3
                            )
                        }
                        if (branches.size >= 2) {
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEEEEE)))
                            for (b in branches) {
                                Text(
                                    if (b.user.isNotEmpty()) b.user else "（无输入）",
                                    color = if (b.nid == activeChild) theme.accent else Color(0xFF555555),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable {
                                            focusIdx = -1
                                            // 树锚点=该轮的父节点（兄弟分支共同前缀）
                                            onSwitchBranch(node.parent, b.nid)
                                        }
                                )
                            }
                        }
                        Text(
                            "点击跳转至此轮",
                            color = Color(0xFF999999),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clickable {
                                    focusIdx = -1
                                    onJumpRound(node.nid)
                                }
                        )
                    }
                }
            }
        }
    }
}

/** 项目名称对话框（新建/重命名共用；重命名模式带删除） */
@Composable
private fun ProjectNameDialog(
    theme: ThemeTokens,
    title: String,
    draft: String,
    onConfirm: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf(draft) }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable { onDismiss() }
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text(title, color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.bgHover, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                textStyle = TextStyle(color = theme.textBody, fontSize = 14.sp),
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) {
                            Text("项目名称", color = theme.textTertiary, fontSize = 14.sp)
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(theme.bgHover, RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("取消", color = theme.textSecondary, fontSize = 14.sp)
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(theme.accent, RoundedCornerShape(10.dp))
                        .clickable { onConfirm(input) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("保存", color = Color.White, fontSize = 14.sp)
                }
            }
            if (onDelete != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE5484D), RoundedCornerShape(10.dp))
                        .clickable { onDelete() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("删除该项目", color = Color(0xFFE5484D), fontSize = 13.sp)
                }
            }
        }
    }
}

/** 移动到项目对话框（会话操作）：无项目 + 全部项目，当前归属高亮 */
@Composable
private fun MoveToProjectDialog(
    theme: ThemeTokens,
    current: String,
    projects: List<Project>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable { onDismiss() }
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(280.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(top = 20.dp, bottom = 8.dp)
        ) {
            Text(
                "移动到项目",
                color = theme.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEEEEE)))
            MoveProjectRow(theme, "无项目", current.isEmpty()) { onPick("") }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEEEEE)))
            for (p in projects) {
                MoveProjectRow(theme, p.name, current == p.pid) { onPick(p.pid) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEEEEE)))
            }
        }
    }
}

/** 项目选择行（胶囊弹层） */
@Composable
private fun ProjectPickerRow(theme: ThemeTokens, label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) theme.accent else Color(0xFF1A1A1A),
        fontSize = 13.sp,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable { onClick() }
            .padding(horizontal = 10.dp)
    )
}

/** 移动到项目行 */
@Composable
private fun MoveProjectRow(theme: ThemeTokens, label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) theme.accent else Color(0xFF1A1A1A),
        fontSize = 14.sp,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable { onClick() }
    )
}

/** 智能体显示名：''=安洁莉娜，'~raw'=陌生的友人 */
private fun agentNameOf(h: ChatHistory, aid: String): String = when {
    aid.isEmpty() -> "安洁莉娜"
    aid == "~raw" -> "陌生的友人"
    else -> ChatStore.agentOf(h, aid)?.name ?: "安洁莉娜"
}

/** 智能体三选一行 */
@Composable
private fun AgentPickerRow(theme: ThemeTokens, label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) theme.accent else Color(0xFF1A1A1A),
        fontSize = 13.sp,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable { onClick() }
    )
}

/** 对话参数对话框（鸿蒙 ConvParamDialog 移植）：每对话上下文条数 + 人设查看/修改（安洁莉娜锁定只读） */
@Composable
private fun ConvParamDialog(
    theme: ThemeTokens,
    ctxDraft: String,
    agentName: String,
    agentPrompt: String,
    locked: Boolean,
    onSave: (ctx: String, prompt: String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputCtx by remember { mutableStateOf(ctxDraft) }
    var inputPrompt by remember { mutableStateOf(agentPrompt) }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable { onDismiss() }
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
                .widthIn(max = 420.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text("对话参数", color = Color(0xFF1A1A1A), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("上下文条数（0 = 用全局设置）", color = Color(0xFF999999), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = inputCtx,
                    onValueChange = { inputCtx = it },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp),
                    textStyle = TextStyle(color = Color(0xFF1A1A1A), fontSize = 14.sp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (inputCtx.isEmpty()) {
                                Text("0", color = Color(0xFFB0B0B0), fontSize = 14.sp)
                            }
                            inner()
                        }
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(agentName + " · 人设", color = Color(0xFF999999), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    if (locked) {
                        Text("固定不可修改", color = Color(0xFF999999), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (locked) {
                    // 锁定态（安洁莉娜 Skill 包）：只读可滚动完整浏览
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(inputPrompt, color = Color(0xFF1A1A1A), fontSize = 13.sp)
                    }
                } else {
                    BasicTextField(
                        value = inputPrompt,
                        onValueChange = { inputPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 220.dp)
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        textStyle = TextStyle(color = Color(0xFF1A1A1A), fontSize = 13.sp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(theme.bgHover, RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("取消", color = Color(0xFF666666), fontSize = 15.sp)
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(theme.accent, RoundedCornerShape(10.dp))
                        .clickable { onSave(inputCtx, inputPrompt) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("确定", color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}
