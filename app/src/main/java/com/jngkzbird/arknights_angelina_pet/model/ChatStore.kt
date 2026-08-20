package com.jngkzbird.arknights_angelina_pet.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 会话数据层 — 鸿蒙版 ChatStore 移植。
 * chat_history.json 格式直接复用（可互通）；数据安全三道闸（2026-08-19 事故教训）：
 * ① 保存原子写（tmp+rename）② 每次保存前自动备份上一版到 .bak ③ 加载失败先备份原文件再返回空结构。
 */

class Agent(
    var aid: String,
    var name: String,
    var prompt: String,
    var created_at: Long
)

class RoundNode(
    var nid: String,
    var parent: String, // 父节点 nid；根为 ''
    var children: MutableList<String>, // 子节点 nid（≥2 = 对话树锚点）
    var user: String,
    var assistant: String,
    var time: Long,
    var reasoning: String = "" // 思考过程（思考模型流式；鸿蒙版无此字段，加字段兼容互通）
)

class Session(
    var sid: String,
    var title: String,
    var created_at: Long,
    var pid: String = "", // 所属项目（'' = 无项目）
    var context_size: Int = 0, // 每对话上下文条数（0 = 用全局设置）
    var agent_aid: String = "", // 绑定智能体（创建时烘焙；''=安洁莉娜，'~raw'=陌生的友人）
    var rounds: MutableList<RoundNode> = mutableListOf(),
    var path: MutableList<String> = mutableListOf()
)

class Project(
    var pid: String,
    var name: String,
    var created_at: Long
)

class HistoryMessage(
    var time: Long,
    var role: String,
    var content: String
)

class ChatHistory {
    var active_session: String = ""
    var sessions: MutableList<Session> = mutableListOf()
    var projects: MutableList<Project> = mutableListOf()
    var agents: MutableList<Agent> = mutableListOf()
}

class ChatSettings(
    var chat_base_url: String = "https://api.deepseek.com",
    var chat_api_key: String = "",
    var chat_model: String = "deepseek-v4-flash",
    var player_name: String = "博士",
    var context_window_size: Int = 20,
    var time_awareness: Boolean = false,
    var extra_prompt: String = "",
    var chatter_interval: String = "中频",
    var voice_enabled: Boolean = false,
    var voice_language: String = "中文"
)

class ChatMsg(val role: String, val content: String)

/** 会话内搜索命中（kind 1=用户输入 2=助手回复） */
class SearchHit(
    var nid: String,
    var snippet: String,
    var kind: Int
)

object ChatStore {
    fun historyPath(context: Context): File = File(context.filesDir, "chat_history.json")

    // ── 三道闸之三：加载失败先备份原文件再返回空结构 ──
    fun loadHistory(context: Context): ChatHistory {
        val path = historyPath(context)
        val h = ChatHistory()
        try {
            if (!path.exists()) {
                return h
            }
            val text = path.readText()
            val root = JSONObject(text)
            h.active_session = root.optString("active_session", "")
            val sessions = root.optJSONArray("sessions")
            if (sessions != null) {
                for (i in 0 until sessions.length()) {
                    h.sessions.add(parseSession(sessions.getJSONObject(i)))
                }
            }
            val projects = root.optJSONArray("projects")
            if (projects != null) {
                for (i in 0 until projects.length()) {
                    val o = projects.getJSONObject(i)
                    h.projects.add(Project(o.getString("pid"), o.getString("name"), o.optLong("created_at")))
                }
            }
            val agents = root.optJSONArray("agents")
            if (agents != null) {
                for (i in 0 until agents.length()) {
                    val o = agents.getJSONObject(i)
                    h.agents.add(Agent(o.getString("aid"), o.getString("name"), o.getString("prompt"), o.optLong("created_at")))
                }
            }
            // 旧版线性消息迁移（单链树）
            for (s in h.sessions) {
                migrateSession(s)
            }
            return h
        } catch (e: Exception) {
            // 解析失败：备份原文件后再返回空结构（防止空结构被保存覆写真实数据）
            try {
                if (path.exists() && path.readText().isNotEmpty()) {
                    path.copyTo(File(path.path + ".bak"), overwrite = true)
                }
            } catch (_: Exception) {
            }
            return ChatHistory()
        }
    }

    fun agentOf(h: ChatHistory, aid: String): Agent? = h.agents.firstOrNull { it.aid == aid }

    // 默认智能体的提示词：rawfile 里的安洁莉娜 Skill 包（启动时加载）
    var defaultAgentPrompt: String = ""
    var priestessPrompt: String = "" // 「你是普瑞赛斯」
    var jieerpeitaPrompt: String = "" // 「你是洁尔佩塔」
    var angelinaPastPrompt: String = "" // 「酸橙味的信」

    /** 所选智能体的系统提示词（含彩蛋 Key 开关；Key 不进上下文） */
    fun activeAgentPrompt(h: ChatHistory, aid: String, extraPrompt: String = ""): String {
        if (aid == "~raw") {
            return ""
        }
        val a = agentOf(h, aid)
        if (a != null) {
            return a.prompt
        }
        if (extraPrompt == "你是普瑞赛斯" && priestessPrompt.isNotEmpty()) {
            return priestessPrompt
        }
        if (extraPrompt == "你是洁尔佩塔" && jieerpeitaPrompt.isNotEmpty()) {
            return jieerpeitaPrompt
        }
        if (extraPrompt == "酸橙味的信" && angelinaPastPrompt.isNotEmpty()) {
            return angelinaPastPrompt
        }
        return defaultAgentPrompt
    }

    // 旧版线性消息 → 单链树（用户+紧随助手成一轮；孤立助手单条成轮）
    private fun migrateSession(s: Session) {
        if (s.rounds.isNotEmpty()) {
            return
        }
        // messages 字段在 parseSession 时未解析（新格式无此字段），旧格式无 rounds → 空列表跳过
        // （当前 Android 版无旧数据，逻辑保留占位——旧鸿蒙文件互通时 rounds 已存在）
    }

    private fun parseSession(o: JSONObject): Session {
        val s = Session(
            o.getString("sid"), o.getString("title"), o.optLong("created_at"),
            o.optString("pid", ""), o.optInt("context_size", 0), o.optString("agent_aid", "")
        )
        val rounds = o.optJSONArray("rounds")
        if (rounds != null) {
            for (i in 0 until rounds.length()) {
                val r = rounds.getJSONObject(i)
                val children = mutableListOf<String>()
                val ch = r.optJSONArray("children")
                if (ch != null) {
                    for (j in 0 until ch.length()) {
                        children.add(ch.getString(j))
                    }
                }
                s.rounds.add(
                    RoundNode(
                        r.getString("nid"), r.optString("parent", ""), children,
                        r.optString("user", ""), r.optString("assistant", ""), r.optLong("time"),
                        r.optString("reasoning", "")
                    )
                )
            }
        }
        val path = o.optJSONArray("path")
        if (path != null) {
            for (i in 0 until path.length()) {
                s.path.add(path.getString(i))
            }
        }
        return s
    }

    // ── 三道闸之一、二：原子写（tmp+rename）+ 保存前备份上一版 ──
    fun saveHistory(context: Context, h: ChatHistory) {
        val path = historyPath(context)
        try {
            if (path.exists() && path.readText().length > 100) {
                path.copyTo(File(path.path + ".bak"), overwrite = true)
            }
        } catch (_: Exception) {
        }
        val tmp = File(path.path + ".tmp")
        tmp.writeText(toJson(h))
        tmp.renameTo(path)
    }

    private fun toJson(h: ChatHistory): String {
        val root = JSONObject()
        root.put("active_session", h.active_session)
        val sessions = JSONArray()
        for (s in h.sessions) {
            val o = JSONObject()
            o.put("sid", s.sid)
            o.put("title", s.title)
            o.put("created_at", s.created_at)
            o.put("pid", s.pid)
            o.put("context_size", s.context_size)
            o.put("agent_aid", s.agent_aid)
            val rounds = JSONArray()
            for (r in s.rounds) {
                val ro = JSONObject()
                ro.put("nid", r.nid)
                ro.put("parent", r.parent)
                ro.put("children", JSONArray(r.children))
                ro.put("user", r.user)
                ro.put("assistant", r.assistant)
                ro.put("time", r.time)
                if (r.reasoning.isNotEmpty()) {
                    ro.put("reasoning", r.reasoning)
                }
                rounds.put(ro)
            }
            o.put("rounds", rounds)
            o.put("path", JSONArray(s.path))
            sessions.put(o)
        }
        root.put("sessions", sessions)
        val projects = JSONArray()
        for (p in h.projects) {
            val o = JSONObject()
            o.put("pid", p.pid)
            o.put("name", p.name)
            o.put("created_at", p.created_at)
            projects.put(o)
        }
        root.put("projects", projects)
        val agents = JSONArray()
        for (a in h.agents) {
            val o = JSONObject()
            o.put("aid", a.aid)
            o.put("name", a.name)
            o.put("prompt", a.prompt)
            o.put("created_at", a.created_at)
            agents.put(o)
        }
        root.put("agents", agents)
        return root.toString()
    }

    // ── 会话操作 ──
    fun newSession(h: ChatHistory, firstTitle: String = "新对话", pid: String = "", aid: String = ""): String {
        val sid = "s" + System.currentTimeMillis().toString() + "-" + (Math.random() * 100000).toInt().toString()
        val s = Session(sid, firstTitle, System.currentTimeMillis())
        s.pid = pid // 新建会话归属（输入卡片项目胶囊选择）
        s.agent_aid = aid // 会话绑定智能体（创建时烘焙）
        h.sessions.add(0, s)
        h.active_session = sid
        return sid
    }

    // ── 项目操作（文件夹语义：删除项目不删会话，会话变无项目） ──

    fun newProject(h: ChatHistory, name: String): String {
        val t = name.trim()
        if (t.isEmpty()) {
            return ""
        }
        val pid = "p" + System.currentTimeMillis().toString() + "-" + (Math.random() * 10000).toInt().toString()
        h.projects.add(Project(pid, t, System.currentTimeMillis()))
        return pid
    }

    fun renameProject(h: ChatHistory, pid: String, name: String): Boolean {
        val t = name.trim()
        if (t.isEmpty()) {
            return false
        }
        val p = h.projects.firstOrNull { it.pid == pid } ?: return false
        p.name = t
        return true
    }

    /** 删除项目：其会话全部变无项目（不删除会话） */
    fun deleteProject(h: ChatHistory, pid: String): Boolean {
        if (!h.projects.removeAll { it.pid == pid }) {
            return false
        }
        for (s in h.sessions) {
            if (s.pid == pid) {
                s.pid = ""
            }
        }
        return true
    }

    /** 移动会话到项目（pid='' = 移出到无项目）；每条对话最多属于一个项目 */
    fun moveSession(h: ChatHistory, sid: String, pid: String): Boolean {
        val session = findSession(h, sid) ?: return false
        session.pid = pid
        return true
    }

    fun findSession(h: ChatHistory, sid: String): Session? = h.sessions.firstOrNull { it.sid == sid }

    fun findRound(s: Session, nid: String): RoundNode? = s.rounds.firstOrNull { it.nid == nid }

    fun setReason(h: ChatHistory, sid: String, nid: String, reason: String) {
        findSession(h, sid)?.let { s -> findRound(s, nid)?.reasoning = reason }
    }

    /** 开分支：同一条用户输入重新生成（Chatbox 语义）——目标轮的父节点下建兄弟节点 */
    private fun forkBranch(h: ChatHistory, sid: String, nid: String, newUserText: String): String {
        val session = findSession(h, sid) ?: return ""
        val node = findRound(session, nid) ?: return ""
        val newNid = "r" + System.currentTimeMillis().toString() + "-" + (Math.random() * 10000).toInt().toString()
        session.rounds.add(RoundNode(newNid, node.parent, mutableListOf(), newUserText, "", System.currentTimeMillis()))
        if (node.parent.isNotEmpty()) {
            findRound(session, node.parent)?.children?.add(newNid)
        }
        val p2 = mutableListOf<String>()
        for (n in session.path) {
            p2.add(n)
            if (n == node.parent) {
                break
            }
        }
        p2.add(newNid)
        session.path = p2
        return newNid
    }

    /** 重新生成回复（同一条用户输入） */
    fun regenBranch(h: ChatHistory, sid: String, nid: String): String {
        val session = findSession(h, sid) ?: return ""
        val node = findRound(session, nid) ?: return ""
        return forkBranch(h, sid, nid, node.user)
    }

    /** 编辑并重发（修改用户输入后开分支） */
    fun editResendBranch(h: ChatHistory, sid: String, nid: String, newUserText: String): String =
        forkBranch(h, sid, nid, newUserText)

    /** 原地编辑（不开分支——编辑即改上下文） */
    fun editRoundContent(h: ChatHistory, sid: String, nid: String, isUser: Boolean, content: String) {
        val session = findSession(h, sid) ?: return
        val node = findRound(session, nid) ?: return
        if (isUser) {
            node.user = content
        } else {
            node.assistant = content
        }
    }

    /** 该轮是否为对话树锚点（下方有 ≥2 条分支） */
    fun isAnchor(h: ChatHistory, sid: String, nid: String): Boolean {
        val session = findSession(h, sid) ?: return false
        val node = findRound(session, nid)
        return node != null && node.children.size > 1
    }

    /** 删除单条消息：只清该侧内容；两侧都空时整节点删除 */
    fun deleteMessageSide(h: ChatHistory, sid: String, nid: String, isUser: Boolean): String {
        val session = findSession(h, sid) ?: return "miss"
        val node = findRound(session, nid) ?: return "miss"
        if (isUser) {
            node.user = ""
        } else {
            node.assistant = ""
            node.reasoning = "" // 思考过程属于助手回复，随其一并删除
        }
        if (node.user.isEmpty() && node.assistant.isEmpty()) {
            return deleteRound(h, sid, nid)
        }
        return "ok"
    }

    /** 删除轮次。锚点：连同全部后续分支一并删除（'anchor'）；单链/叶：重连父子（'ok'） */
    fun deleteRound(h: ChatHistory, sid: String, nid: String): String {
        val session = findSession(h, sid) ?: return "miss"
        val node = findRound(session, nid) ?: return "miss"
        if (node.children.size > 1) {
            // 锚点：整棵子树一并删除
            val kill = mutableListOf<String>()
            val queue = ArrayDeque(node.children)
            while (queue.isNotEmpty()) {
                val c = queue.removeFirst()
                kill.add(c)
                findRound(session, c)?.children?.forEach { queue.addLast(it) }
            }
            session.rounds.removeAll { it.nid in kill }
            if (node.parent.isNotEmpty()) {
                findRound(session, node.parent)?.children?.remove(nid)
            }
            session.rounds.removeAll { it.nid == nid }
            val pIdx = session.path.indexOf(nid)
            if (pIdx >= 0) {
                session.path.subList(pIdx, session.path.size).clear()
            }
            return "anchor"
        }
        // 单链/叶：重连父子
        if (node.parent.isNotEmpty()) {
            val parent = findRound(session, node.parent)
            parent?.children?.remove(nid)
            if (node.children.size == 1) {
                parent?.children?.add(node.children[0])
                findRound(session, node.children[0])?.parent = node.parent
            }
        }
        session.rounds.removeAll { it.nid == nid }
        val pIdx = session.path.indexOf(nid)
        if (pIdx >= 0) {
            session.path.removeAt(pIdx)
        }
        return "ok"
    }

    /** 切换分支：路径前缀到锚点，接选定子节点并沿其首子链走到叶 */
    fun switchBranch(h: ChatHistory, sid: String, anchorNid: String, childNid: String): Boolean {
        val session = findSession(h, sid) ?: return false
        val p = mutableListOf<String>()
        for (n in session.path) {
            p.add(n)
            if (n == anchorNid) {
                break
            }
        }
        if (p.isEmpty() || p.last() != anchorNid) {
            return false
        }
        var cur = childNid
        var guard = 0
        while (cur.isNotEmpty() && guard < 1000) {
            guard++
            p.add(cur)
            val node = findRound(session, cur)
            if (node != null && node.children.isNotEmpty()) {
                cur = node.children[0]
            } else {
                break
            }
        }
        session.path = p
        return true
    }

    /** 锚点下的全部分支（子节点列表，顺序=创建顺序） */
    fun branchChildren(h: ChatHistory, sid: String, nid: String): List<RoundNode> {
        val session = findSession(h, sid) ?: return emptyList()
        val node = findRound(session, nid) ?: return emptyList()
        return node.children.mapNotNull { findRound(session, it) }
    }


    /** 追加用户轮次（树尾接新节点），标题取首条消息 */
    fun appendUserRound(h: ChatHistory, sid: String, content: String): String? {
        val s = findSession(h, sid) ?: return null
        if (s.title == "新对话") {
            s.title = if (content.length > 20) content.take(20) else content
        }
        val nid = "m" + System.currentTimeMillis().toString() + "-" + (Math.random() * 100000).toInt().toString()
        val parent = s.path.lastOrNull() ?: ""
        s.rounds.add(RoundNode(nid, parent, mutableListOf(), content, "", System.currentTimeMillis()))
        if (parent.isNotEmpty()) {
            s.rounds.firstOrNull { it.nid == parent }?.children?.add(nid)
        }
        s.path.add(nid)
        return nid
    }

    fun setAssistant(h: ChatHistory, sid: String, nid: String, content: String) {
        findSession(h, sid)?.rounds?.firstOrNull { it.nid == nid }?.assistant = content
    }

    /** 会话内搜索（用户定案：仅当前话题；有对话树则仅搜活跃分支）。大小写不敏感子串匹配，命中用户输入/助手回复 */
    fun searchInSession(h: ChatHistory, sid: String, query: String): List<SearchHit> {
        val q = query.trim().lowercase()
        val out = mutableListOf<SearchHit>()
        if (q.isEmpty()) {
            return out
        }
        val sess = findSession(h, sid) ?: return out
        for (nid in sess.path) {
            val node = findRound(sess, nid) ?: continue
            val uHit = node.user.lowercase().contains(q)
            val aHit = node.assistant.lowercase().contains(q)
            if (!uHit && !aHit) {
                continue
            }
            val text = if (uHit) node.user else node.assistant
            val pos = text.lowercase().indexOf(q)
            val from = maxOf(0, pos - 14)
            val to = minOf(text.length, pos + q.length + 24)
            val snippet = (if (from > 0) "…" else "") + text.substring(from, to) + (if (to < text.length) "…" else "")
            out.add(SearchHit(node.nid, snippet, if (uHit) 1 else 2))
        }
        return out
    }

    /** 活跃分支路径 → 上下文消息列表（截 context_window_size 条；时间前缀注入最后一条用户消息） */
    fun buildPathMessages(h: ChatHistory, s: ChatSettings, agentPrompt: String, sessionCtx: Int = 0): List<ChatMsg> {
        val msgs = mutableListOf<ChatMsg>()
        if (agentPrompt.isNotEmpty()) {
            msgs.add(ChatMsg("system", agentPrompt))
        }
        val session = findSession(h, h.active_session) ?: return msgs
        val ctxSize = if (sessionCtx > 0) sessionCtx else s.context_window_size
        var userIdx = -1
        for (nid in session.path) {
            val node = session.rounds.firstOrNull { it.nid == nid } ?: continue
            if (node.user.isNotEmpty()) {
                msgs.add(ChatMsg("user", node.user))
                userIdx = msgs.size - 1
            }
            if (node.assistant.isNotEmpty()) {
                msgs.add(ChatMsg("assistant", node.assistant))
            }
        }
        if (msgs.size > ctxSize + 1) {
            val keep = ctxSize + 1
            msgs.subList(1, msgs.size - keep).clear()
        }
        // 时间感知：最后一条用户消息注入时间前缀
        if (s.time_awareness && userIdx >= 0 && userIdx < msgs.size) {
            val now = java.util.Calendar.getInstance()
            val prefix = "[%02d:%02d]".format(now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE))
            msgs[userIdx] = ChatMsg("user", prefix + msgs[userIdx].content)
        }
        return msgs
    }
}
