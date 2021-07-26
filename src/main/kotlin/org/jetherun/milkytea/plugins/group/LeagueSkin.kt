package org.jetherun.milkytea.plugins.group

import kotlinx.coroutines.*
import net.dongliu.requests.Requests
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.RemoteFile.Companion.uploadFile
import org.jetherun.milkytea.MilkyTea
import org.jetherun.milkytea.function.TGroups
import org.jetherun.milkytea.function.SkinCrawler
import org.jetherun.milkytea.plugins.GroupMessagePlugin
import org.jetherun.milkytea.plugins.base.ClockPlugin
import org.jsoup.Jsoup
import org.ktorm.dsl.*
import java.io.File
import java.time.LocalDateTime

/**
 * 自动检测LeagueSkin更新并上传到群
 */
class LeagueSkin(override val milkyTea: MilkyTea): ClockPlugin, GroupMessagePlugin {
    override val tag = "LeagueSkin"
    override val interval: Long = 1800000
    override var lastRuntime: Long? = null

    override val onlyCallMe: Boolean = true
    override val keyWords: MutableList<String> = mutableListOf("Skin", "skin", "SKIN", "皮肤", "换肤")
    override val ceaseEvent: Boolean = true

    override suspend fun foo(): Boolean {
        return try {
            inspect()
        } catch (e: Exception) {
            milkyTea.logger.ex(tag, e.message?:e.toString())
            false
        }
    }

    override suspend fun excess(event: GroupMessageEvent): Boolean {
        return inspect(event)
    }

    private suspend fun inspect(event: GroupMessageEvent? = null): Boolean {
        // 爬取数据
        val doc = Jsoup.connect("http://leagueskin.net/p/download-mod-skin-2020-chn").get()
        val url = doc.getElementById("link_download3").attr("href")
        val ver = Regex("""\d+\.\d+(\.\d+)?""").find(url)?.value
        val filename = "MODSKIN_$ver.zip"

        // 写SkinCrawler表
        val newId = milkyTea.db.insertAndGenerateKey(SkinCrawler) {
            set(it.upd, LocalDateTime.now())
            set(it.file_name, filename)
            set(it.version, ver)
            set(it.url, url)
        } as Int

        // 遍历所有需要更新的群
        milkyTea.db.from(TGroups).select().where(TGroups.is_skin_enable eq true).forEach f@ { groupRow ->
            // continue掉不需要更新的
            groupRow[TGroups.skin_id]?.let {
                if (SkinCrawler.getVer(milkyTea.db, it) == ver) {
                    event?.let { event ->
                        if (groupRow[TGroups.group_id]!! == event.group.id.toString()) {
                            milkyTea.bot.getGroup(groupRow[TGroups.group_id]!!.toLong())?.sendMessage("未检测到更新")
                        }
                    }
                    return@f
                }
            }

            // 检查文件是否存在, 不存在要下载
            val file = File(filename)
            if(!file.exists()) {
                milkyTea.logger.i(tag, "Download $filename")
                Requests.get(url).send().writeToFile(filename)
            }

            // 上传到目标群
            milkyTea.bot.getGroup(groupRow[TGroups.group_id]!!.toLong())
                ?.let { group ->
                    // 上传并发消息告知
                    group.sendMessage(group.uploadFile("/$filename", file.toExternalResource()))

                    // 只有成功上传了才写日志和数据库
                    milkyTea.logger.i(tag, "Upload skin to group: ${group.id}")
                    milkyTea.db.update(TGroups) { row ->
                        set(row.upd, LocalDateTime.now())
                        set(row.skin_id, newId)
                        where { row.group_id eq group.id.toString() }
                    }
                } ?: let {
                    // 找不到可能是bot退群了.
                    milkyTea.logger.ex(tag, "Group ${groupRow[TGroups.group_id]} is not found.")
                }
        }
        return true
    }
}