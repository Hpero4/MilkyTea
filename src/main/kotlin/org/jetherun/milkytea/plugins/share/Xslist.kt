package org.jetherun.milkytea.plugins.share

import net.dongliu.requests.Requests
import net.dongliu.requests.body.Part
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetherun.milkytea.MilkyTea
import org.jetherun.milkytea.function.MtUtil
import org.jetherun.milkytea.plugins.SessionMessagePlugin
import org.jetherun.milkytea.plugins.waitParam
import org.jsoup.Jsoup
import kotlin.random.Random


/**
 * 以图搜演员
 *
 * Xslist 人工智能人脸识别搜片中演员.
 * 具体功能见站内.
 */
class Xslist(override val milkyTea: MilkyTea) : SessionMessagePlugin {
    override val ceaseEvent: Boolean
        get() = true
    override val keyWords: MutableList<String>
        get() = mutableListOf(
                "搜演员", "搜主演", "找演员", "找主演"
        )
    override val nameZh: String
        get() = "以图搜演员"
    override val onlyCallMe: Boolean
        get() = true
    override val tag: String
        get() = "Xslist"
    override val timeout: Long
        get() = 300L

    override suspend fun excess(event: MessageEvent): Boolean? {
        val sessionKey = (event.message[0] as MessageSource).fromId.toString()
        event.subject.sendMessage("发送图片进行检索(消息内包含多张图片则只搜索第一张)")
        this@Xslist.waitParam<Image>(event, sessionKey, "需要发送图片才能进行检索喔") { imgs ->
            milkyTea.logger.d(tag, "Picture received successfully")
            event.subject.sendMessage("正在搜索, 该功能响应速度较慢, 请耐心等待...")

            var msg: Message = PlainText("搜索结果:\n")
            val imgData = MtUtil.webImgToBytes(imgs[0].queryUrl())
            try {
                val html: String? = try {
                    Requests.post("https://xslist.org/search/pic")
                            .multiPartBody(
                                    Part.file("pic", "${Random.nextInt()}.png", imgData),
                                    Part.text("lg", "zh")
                            )
                            .timeout(45_000)
                            .send()
                            .readToText()
                } catch (e: Exception){
                    null
                }

                html ?. let {
                    val resultList = Jsoup.parse(html)
                            .getElementsByTag("ul")
                            .first()
                            .getElementsByTag("li")

                    for (item in resultList) {
                        val url = item.getElementsByTag("img").first().attr("src")
                        msg = msg + PlainText(item.getElementsByTag("a").first().text()) + try {
                            event.subject.uploadImage(MtUtil.webImgToBytes(url).toExternalResource())
                        } catch (e: Exception) {
                            PlainText("")
                        } + "\n\n"
                    }

                    event.subject.sendMessage(msg)
                } ?: let {
                    milkyTea.logger.ex(tag, "Request time out.")
                    event.subject.sendMessage("请求超时, 请稍后再试.")
                    return false
                }
            } catch (e: Exception){
                milkyTea.logger.ex(tag, "Request exception: ${e.message}.")
                event.subject.sendMessage("服务异常, 请稍后再试.")
                return false
            }
            return true
        }
        return null
    }
}