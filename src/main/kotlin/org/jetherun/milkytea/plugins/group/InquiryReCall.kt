package org.jetherun.milkytea.plugins.group

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.PlainText
import org.jetherun.milkytea.MilkyTea
import org.jetherun.milkytea.function.TPMsgPool
import org.jetherun.milkytea.plugins.GroupMessagePlugin
import org.jetherun.milkytea.plugins.share.ReCallRecorder
import org.ktorm.dsl.*
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * 查询该群在奶茶运行时间内撤回过的消息
 *
 * 撤回消息池: MilkyTea.kwargs["ReCallMessage"]
 *
 * 若前置插件未运行, 该插件将异常. (查询总是返回无撤回消息)
 *
 * 前置插件需求:
 * @see ReCallRecorder 通过消息撤回事件将原消息加入撤回消息池
 */
class InquiryReCall(override val milkyTea: MilkyTea) : GroupMessagePlugin {
    override val ceaseEvent: Boolean = true
    override val tag: String = "InquiryReCall"
    override val keyWords: MutableList<String> = mutableListOf("查询撤回", "撤回查询", "查撤回")
    override val onlyCallMe: Boolean = true

    override suspend fun excess(event: GroupMessageEvent): Boolean? {
        var isFirstRun = true

        milkyTea.db.from(TPMsgPool).select().where {
            (TPMsgPool.subordinate eq event.group.id.toString()) and (TPMsgPool.is_recall eq true)
        }.forEach {
            if (isFirstRun) {
                event.subject.sendMessage("从奶茶开始运行时间 ${milkyTea.startTimeFormat} 至今, 该群共撤回过以下消息:")
                isFirstRun = false
            }

            val id = it[TPMsgPool.sender]!!
            val name = event.group[id.toLong()]?.nameCard ?: "成员已退出该群($id)"
            val time = it[TPMsgPool.upd]!!.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            event.subject.sendMessage(PlainText("$time $name($id)\n原消息内容:\n") + deserializeMiraiCode(it[TPMsgPool.mirai_code_str]?:""))
        }

        if (isFirstRun) {
            event.subject.sendMessage("该群从奶茶开始运行时间 ${milkyTea.startTimeFormat} 至今没有撤回过消息.")
        }

        return null
    }
}