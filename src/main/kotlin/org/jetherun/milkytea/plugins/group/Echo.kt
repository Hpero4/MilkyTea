package org.jetherun.milkytea.plugins.group

import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.*
import org.jetherun.milkytea.MilkyTea
import org.jetherun.milkytea.function.TPEchoHistory
import org.jetherun.milkytea.function.TPMsgPool
import org.jetherun.milkytea.plugins.GroupMessagePlugin
import org.ktorm.dsl.*
import java.lang.Exception
import java.util.concurrent.ConcurrentHashMap

class Echo(override val milkyTea: MilkyTea): GroupMessagePlugin {
    override val keyWords: Nothing? = null
    override val tag = "Echo"
    override val onlyCallMe: Boolean = false
    override val ceaseEvent: Boolean = false

    override suspend fun excess(event: GroupMessageEvent): Boolean? {
        val miraiCode = event.message.serializeToMiraiCode()
        var echoIds: Int? = null
        var echoMiraiCode: String? = null
        var lastMsgIds: Int? = null

        milkyTea.db.from(TPMsgPool)
            .select()
            .groupBy(TPMsgPool.msg_ids)
            .having { (TPMsgPool.subordinate eq event.group.id.toString()) and (TPMsgPool.upd eq max(TPMsgPool.upd)) }
            .forEach {
                lastMsgIds = it[TPMsgPool.last_msg_ids]
            }

        if (lastMsgIds != null) {
            milkyTea.db
                .from(TPEchoHistory)
                .select()
                .where(TPEchoHistory.group_id eq event.group.id.toString())
                .forEach {
                    echoIds = it[TPEchoHistory.msg_ids]
                }

            if (echoIds != null) {
                milkyTea.db
                    .from(TPMsgPool)
                    .select()
                    .where(TPMsgPool.msg_ids eq echoIds!!)
                    .forEach {
                        echoMiraiCode = it[TPMsgPool.mirai_code_str]
                    }
            }

            milkyTea.db.from(TPMsgPool).select().where(TPMsgPool.msg_ids eq lastMsgIds!!).forEach {
                if (it[TPMsgPool.mirai_code_str]?:"" == miraiCode && echoMiraiCode != miraiCode) {
                    event.subject.sendMessage(event.message)

                    if (milkyTea.db.from(TPEchoHistory).select().where { TPEchoHistory.group_id eq event.group.id.toString() }.rowSet.size() == 0) {
                        milkyTea.db.insert(TPEchoHistory) { row ->
                            set(row.msg_ids, it[TPMsgPool.msg_ids])
                            set(row.group_id, event.group.id.toString())
                        }
                    } else {
                        milkyTea.db.update(TPEchoHistory) { row ->
                            set(row.msg_ids, it[TPMsgPool.msg_ids])
                            where { row.group_id eq event.group.id.toString() }
                        }
                    }
                }
            }
        }

        return null
    }
}