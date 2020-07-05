package org.jetherun.milkytea.plugins

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.nextMessage
import org.jetherun.milkytea.MilkyTea

interface SessionMessagePlugin: ShareMessagePlugin {
    val timeout: Long
    val nameZh: String

    override fun filter(event: Event): MessageEvent? {
        super.filter(event) ?. let {msgEvent ->
            val key = (msgEvent.message[0] as MessageSource).fromId.toString()
            milkyTea.sessionPool.checkPool(key)?.let {
                GlobalScope.launch { msgEvent.reply("您有正在挂起的会话 ${it.name}(${it.nameZh}), 请先结束.") }
                milkyTea.logger.d(tag, "User($key) existence session ${it.name}, ignore.")
                return null
            } ?: let {
                return msgEvent
            }
        } ?: return null
    }

    /**
     * 在会话池中添加一个会话
     */
    fun addSession(sessionKey: String){
        milkyTea.sessionPool.addObj(
                sessionKey,
                MilkyTea.Session(tag, System.currentTimeMillis() + timeout, nameZh, this)
        )
    }

    /**
     * 捕获用户终止会话的意图
     *
     * 成功捕获则返回 true 并将会话 [MilkyTea.Session] 从会话池 [MilkyTea.sessionPool] 中删除
     * 否则返回 false
     */
    fun cancelSession(event: MessageContent, sessionKey: String): Boolean{
        arrayListOf("算了", "取消", "cancel").forEach { keyWord ->
            if (keyWord in event.content) {
                milkyTea.sessionPool.removeObj(sessionKey)
                return true
            }
        }
        return false
    }
}

/**
 * 等待次轮会话中指定类型 <T> 的参数
 * 若未成功捕获则会一直挂起线程直到会话超时
 *
 * @param tips: 对于所需要参数的描述, 如 "需要发送图片才能进行检索喔"
 * @return:
 *      成功捕获参数 -> MutableList<T>
 *      用户取消 -> null
 */
suspend inline fun <reified T: Message> SessionMessagePlugin.waitParam(
        event: MessageEvent, sessionKey: String, tips: String): MutableList<T>? {
    val params = mutableListOf<T>()
    event.nextMessage(timeoutMillis=timeout) { paramMsg ->
        paramMsg.message.forEachContent {
            if (it is T){
                params.add(it)
            } else if (cancelSession(it, sessionKey)) return@nextMessage true
        }
        if (params.size > 0)
            return@nextMessage true
        else {
            val waitMinute = timeout / 60000
            GlobalScope.launch {
                event.reply(
                        "$tips\n该会话将一直挂起直到最多${waitMinute}分钟, " +
                                "发送 \"取消\" \"算了\" \"cancel\" 可以立即结束会话"
                )
            }
            return@nextMessage false
        }
    }

    return if (params.size > 0)
        params
    else
        null
}
