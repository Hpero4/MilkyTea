package org.jetherun.milkytea.plugins.share

import kotlinx.coroutines.coroutineScope
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.content
import org.jetherun.milkytea.MilkyTea
import org.jetherun.milkytea.function.TGroups
import org.jetherun.milkytea.function.TPBan
import org.jetherun.milkytea.function.TPInfo
import org.jetherun.milkytea.function.TPKeywords
import org.jetherun.milkytea.plugins.ShareMessagePlugin
import org.ktorm.dsl.*
import org.ktorm.schema.Column
import java.time.LocalDateTime

class Setting(override val milkyTea: MilkyTea) : ShareMessagePlugin {
    override val tag: String = "Setting"
    override val onlyCallMe: Boolean = true
    override val keyWords: MutableList<String> = mutableListOf("功能")
    override val ceaseEvent: Boolean = false

    @Suppress("unchecked")
    override suspend fun excess(event: MessageEvent): Boolean {
        TPKeywords.checkHit(milkyTea.db, event.message.content)?.let { pId ->

            // 查询后使用rowSet离线不可用? Ktorm Bug?
            var field = ""
            var pluginName = ""

            milkyTea.db.from(TPInfo).select().where(TPInfo.plugin_id eq pId).forEach {
                field = it[TPInfo.plugin_field]!!
                pluginName = it[TPInfo.plugin_name]!!
                return@forEach
            }

            when (event) {
                is GroupMessageEvent -> {
                    if (
                        milkyTea.db.from(TPBan).select().where(
                            (TPBan.plugin_id eq pId) and (TPBan.ban_object eq event.group.id.toString())
                        ).rowSet.size() != 0
                    ) {
                        milkyTea.logger.w(
                            tag,
                            "Blacklist member attempt opening function: ${event.group.id}[$pluginName]"
                        )
                    } else {
                        milkyTea.db.update(TGroups) {
                            set(TGroups[field] as Column<Boolean>, obtainIntention(event))
                            set(it.upd, LocalDateTime.now())
                            where { TGroups.group_id eq event.group.id.toString() }
                        }
                        milkyTea.logger.i(tag, "Change setting of plugin: ${event.sender.id}[$pluginName]")
                    }
                }
                is FriendMessageEvent -> {
                    milkyTea.logger.w(tag, "Function not implemented. Request change friend setting.")

                    coroutineScope {
                        event.sender.sendMessage("该功能未实现.")
                    }
                    return false
                }
                else -> {
                    milkyTea.logger.ex(tag, "Unexpected event: $event")
                    return false
                }
            }
        } ?: let {
            milkyTea.logger.w(tag, "Can't get the plugin keyword. Msg: ${event.message.content}")
            return false  // 找不到关键字, 无法定位需要操作的插件
        }
        return true
    }

    /**
     * 获取用户的意图
     *
     * @return:
     *  true -> 开启该功能
     *  false -> 关闭该功能
     *
     * @throws IllegalArgumentException: 当无法获取意图时.
     */
    private fun obtainIntention(event: MessageEvent): Boolean {
        mutableListOf("开", "启").forEach {
            if (it in event.message.content) {
                return true
            }
        }

        mutableListOf("关", "停").forEach {
            if (it in event.message.content) {
                return false
            }
        }

        throw IllegalArgumentException("Can't obtain user intention: ${event.message.content}")
    }
}