package org.jetherun.milkytea.plugins.share

import kotlinx.coroutines.runBlocking
import net.dongliu.requests.Requests
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.content
import org.jetherun.milkytea.MilkyTea
import org.jetherun.milkytea.plugins.ShareMessagePlugin
import java.io.ByteArrayInputStream

/**
 * PixivCat 墙内获取Pixiv图像
 */
class PixivCat(override val milkyTea: MilkyTea) : ShareMessagePlugin {
    override val ceaseEvent: Boolean
        get() = true
    override val keyWords: MutableList<String>
        get() = mutableListOf("pid", "Pid", "PID")
    override val onlyCallMe: Boolean
        get() = false
    override val tag: String
        get() = "PixivCat"

    override suspend fun excess(event: MessageEvent): Boolean? {
        // 不响应各种卡片消息
        if("appid" in event.message.content) return null

        Regex("""\d+""").find(event.message.content)?.value?.let { pid ->
            milkyTea.logger.i(tag, "Getting pic, PID=$pid.")
            mutableListOf("png", "jpg", "gif").forEach { suffix ->
                val req = Requests.get("https://pixiv.cat/$pid.$suffix").send().readToBytes()
                val reqText = String(req)
                if ("404 Not Found" !in reqText) {
                    runBlocking {
                        try {
                            event.subject.sendImage(ByteArrayInputStream(req))
                        } catch (e: Exception) {
                            event.subject.sendMessage("加载失败")
                        }
                    }
                    return null
                } else if ("需要指定是第幾張圖片才能正確顯示" in reqText) {
                    var index = 1
                    while (true) {
                        val page = Requests.get("https://pixiv.cat/$pid-$index.$suffix").send().readToBytes()
                        if ("404 Not Found" !in String(page)) {
                            runBlocking { event.subject.sendImage(ByteArrayInputStream(page)) }
                            index++
                            milkyTea.logger.d(tag, "Send pic: ${String(page)}-$suffix")
                        } else break
                    }
                    return null
                }
            }
            milkyTea.logger.w(tag, "Getting pic not found PID")
            event.subject.sendMessage("這個作品可能已被刪除，或無法取得。\n該当作品は削除されたか、存在しない作品IDです。")
        }
        return null
    }
}