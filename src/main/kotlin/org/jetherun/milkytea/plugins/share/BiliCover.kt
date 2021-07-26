package org.jetherun.milkytea.plugins.share

import com.alibaba.fastjson.JSONObject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dongliu.requests.Requests
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetherun.milkytea.MilkyTea
import org.jetherun.milkytea.function.urlGetImage
import org.jetherun.milkytea.plugins.ShareMessagePlugin
import java.net.URL

/**
 * B站封面提取
 */
class BiliCover(override val milkyTea: MilkyTea) : ShareMessagePlugin {
    override val ceaseEvent: Boolean = true
    override val keyWords: MutableList<String> = mutableListOf("av", "AV", "bv", "BV", "Av", "Bv")
    override val onlyCallMe: Boolean = false
    override val tag: String = "BiliCover"

    var type: BiliType? = null
    var id: String? = null

    override fun filter(event: Event): MessageEvent? {
        super.filter(event) ?. let {
            Regex("""(Av|AV|av)(\d+)""").find(it.message.content) ?.let { matchResult ->
                type = BiliType.AV
                id = matchResult.groups[2]!!.value
                return it
            }
            Regex("""(Bv|BV|bv)([0-9a-zA-Z]{10})""").find(it.message.content) ?. let { matchResult ->
                type = BiliType.BV
                id = matchResult.groups[2]!!.value
                return it
            }
            Regex("""(Cv|CV|cv)(\d+)""").find(it.message.content) ?. let { matchResult ->
                type = BiliType.CV
                id = matchResult.groups[2]!!.value
                return it
            }
        }
        return null
    }

    override suspend fun excess(event: MessageEvent): Boolean? {
        if (type != null && id != null) {
            val res = Requests.get("https://api.magecorn.com/bilicover/get-cover.php")
                .params(mapOf(
                    "type" to type!!.name.toLowerCase(),
                    "id" to id!!,
                    "client" to "2.2.0"
                ))
                .headers(mapOf(
                    "referer" to "https://bilicover.magecorn.com/"
                ))
                .send()
            val json = res.readToJson(JSONObject::class.java)
            if (json.getInteger("code") == 0) {
                val head = "${type!!.name}$id\n"
                val title = json.getString("title")
                val up = json.getString("up")
                val url = json.getString("url")

                event.subject.sendMessage(
                    PlainText(
                                head
                                + "标题: 《$title》\n"
                                + "UP: $up\n"
                                + "封面:"
                    ) + event.subject.uploadImage(url.urlGetImage().toExternalResource())
                )
            }
        }
        type = null
        id = null
        return null
    }

    enum class BiliType {
        AV,
        BV,
        CV
    }
}