package org.jetherun.milkytea.plugins

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.nextMessage
import org.jetherun.milkytea.MilkyTea
import org.jetherun.milkytea.function.TPInfo
import org.jetherun.milkytea.function.TPSessPool
import org.ktorm.dsl.*
import java.time.LocalDateTime

interface SessionMessagePlugin: ShareMessagePlugin {
    /**
     * 会话超时时间, 单位秒
     */
    val timeout: Long
    val nameZh: String

    override fun filter(event: Event): MessageEvent? {
        super.filter(event) ?. let { msgEvent ->
            milkyTea.db.from(TPSessPool).select().where {
                (TPSessPool.user_id eq msgEvent.sender.id.toString()) and (TPSessPool.plugin_timeout greater LocalDateTime.now())
            }.forEach { sessRow ->
                milkyTea.db.from(TPInfo).select().where(TPInfo.plugin_id eq sessRow[TPSessPool.plugin_id]!!).forEach {
                    runBlocking { msgEvent.subject.sendMessage(
                        "您有正在挂起的会话 ${it[TPInfo.plugin_name]}(${it[TPInfo.plugin_name_zh]}), 请先结束."
                    ) }
                    milkyTea.logger.d(
                        tag, "User(${msgEvent.sender.id}) existence session ${it[TPInfo.plugin_name]}, ignore."
                    )
                }
                return null
            }

            milkyTea.db.from(TPInfo).select().where(TPInfo.plugin_name eq tag).forEach f@ { pInfo ->
                milkyTea.db.insert(TPSessPool) {
                    set(it.plugin_id, pInfo[TPInfo.plugin_id])
                    set(it.user_id, msgEvent.sender.id.toString())
                    set(it.upd, LocalDateTime.now())
                    set(it.plugin_timeout, LocalDateTime.now().plusSeconds(timeout))
                }
                return@f  // 其实理论上不return也只有一个
            }

            return msgEvent
        } ?: return null
    }

    /**
     * 捕获用户终止会话的意图
     *
     * 成功捕获则返回 true
     * 否则返回 false
     */
    fun cancelSession(event: SingleMessage, user: String): Boolean{
        arrayListOf("算了", "取消", "cancel").forEach { keyWord ->
            if (keyWord in event.content) {
                milkyTea.db.delete(TPSessPool) {
                    it.user_id eq user
                }
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
 * @param func: 得到参数后需要执行的逻辑
 * @return:
 *  成功捕获参数 -> [func]执行结果
 *  用户取消 -> null
 */
suspend inline fun <reified T: Message> SessionMessagePlugin.waitParam (
    event: MessageEvent, user: String, tips: String, func: (params: MutableList<T>) -> Boolean): Boolean? {
    val params = mutableListOf<T>()
    event.nextMessage(timeoutMillis=timeout * 1000) { paramMsg ->
        paramMsg.message.forEach {
            if (it is T){
                params.add(it)
            } else if (cancelSession(it, user)) return@nextMessage true
        }
        if (params.size > 0) {
            return@nextMessage true
        } else {
            val waitMinute = timeout / 60000
            event.subject.sendMessage(
                    "$tips\n该会话将一直挂起直到最多${waitMinute}分钟, " +
                            "发送 \"取消\" \"算了\" \"cancel\" 可以立即结束会话"
            )
            return@nextMessage false
        }
    }

    return if (params.size > 0) {
        val result = func(params)
        milkyTea.db.delete(TPSessPool) {
            it.user_id eq user
        }
        result
    } else {
        null  // 用户取消
    }
}
