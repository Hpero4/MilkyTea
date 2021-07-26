package org.jetherun.milkytea.plugins.share

import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.ids
import org.jetherun.milkytea.MilkyTea
import org.jetherun.milkytea.function.TPMsgPool
import org.jetherun.milkytea.plugins.ShareMessagePlugin
import org.ktorm.dsl.*
import java.sql.SQLException
import java.time.LocalDateTime

class MsgPool(override val milkyTea: MilkyTea): ShareMessagePlugin {
    override val tag: String = "MsgPool"
    override val onlyCallMe: Boolean = false
    override val keyWords: MutableList<String>? = null
    override val ceaseEvent: Boolean = false

    override suspend fun excess(event: MessageEvent): Boolean? {
        // 只有群消息有subordinate
        val subordinate = if (event is GroupMessageEvent) {
            event.group.id.toString()
        } else {
            null
        }

        var lastIds: Int? = null
        try {
            if (event is GroupMessageEvent) {
                milkyTea.db
                    .from(TPMsgPool)
                    .select()
                    .groupBy(TPMsgPool.msg_ids)
                    .having {
                        (TPMsgPool.subordinate eq event.group.id.toString()) and (TPMsgPool.upd eq max(TPMsgPool.upd))
                    }.forEach {
                        lastIds = it[TPMsgPool.msg_ids]
                    }
            } else {
                milkyTea.db
                    .from(TPMsgPool)
                    .select()
                    .groupBy(TPMsgPool.msg_ids)
                    .having {
                        (TPMsgPool.subordinate.isNull()) and (TPMsgPool.upd eq max(TPMsgPool.upd))
                    }.forEach {
                        lastIds = it[TPMsgPool.msg_ids]
                    }
            }
        } catch (e: SQLException) {
            lastIds = null
        }

        milkyTea.db.insert(TPMsgPool) {
            set(it.sender, event.sender.id.toString())
            set(it.subordinate, subordinate)
            set(it.mirai_code_str, event.message.serializeToMiraiCode())
            set(it.upd, LocalDateTime.now())
            set(it.mirai_ids, event.message.ids.contentToString())
            set(it.last_msg_ids, lastIds)
        }

        return null
    }
}