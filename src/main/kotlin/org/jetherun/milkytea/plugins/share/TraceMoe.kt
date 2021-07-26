package org.jetherun.milkytea.plugins.share

import com.alibaba.fastjson.JSONObject
import net.dongliu.requests.Requests
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetherun.milkytea.MilkyTea
import org.jetherun.milkytea.function.urlGetImage
import org.jetherun.milkytea.plugins.SessionMessagePlugin
import org.jetherun.milkytea.plugins.waitParam
import java.net.URL

/**
 * 以图搜番
 * 核心算法源自 trace.moe
 *
 * """
 * Life is too short to answer all the "What is the anime?" questions. Let computers do that for you.
 * trace.moe is a test-of-concept prototype search engine that helps users trace back the original anime
 * by screenshot. It searches over 22300 hours of anime and find the best matching scene. It tells you what
 * anime it is, from which episode and the time that scene appears. Since the search result may not be
 * accurate, it provides a few seconds of preview for verification.
 *
 * There has been a lot of anime screencaps and GIFs spreading around the internet, but very few of them
 * mention the source. While those online platforms are gaining popularity, trace.moe respects the original
 * producers and staffs by showing interested anime fans what the original source is. This search engine
 * encourages users to give credits to the original creators / owners before they share stuff online.
 *
 * This website is non-profit making. There is no pro/premium features at all. This website is not intended for
 * watching anime. The server has effective measures to forbid users to access the original video beyond the
 * preview limit. I would like to redirect users to somewhere they can watch that anime legally, if possible.
 *
 * Most Anime since 2000 are indexed, but some are excluded (see FAQ). No Doujin work, no derived art work are
 * indexed. The system only analyzes officially published anime. If you wish to search artwork / wallpapers, try
 * to use SauceNAO and iqdb.org
 * """
 *
 * Site: https://trace.moe/
 */
class TraceMoe(override val milkyTea: MilkyTea): SessionMessagePlugin {
    override val keyWords: MutableList<String> = mutableListOf("以图搜番", "什么番", "找番", "查番", "搜番", "搜动漫")
    override val onlyCallMe: Boolean = true
    override val tag: String = "TraceMoe"
    override val nameZh: String = "以图搜番"
    override val timeout: Long = 300
    override val ceaseEvent: Boolean = true

    override suspend fun excess(event: MessageEvent): Boolean? {
        val user = event.sender.id.toString()

        event.subject.sendMessage("发送一张或以上的图片进行检索")
        waitParam<Image>(event, user, "需要发送图片才能进行检索喔") { imgs ->
            milkyTea.logger.d(tag, "Picture received successfully")
            if (imgs.size > 1)
                event.subject.sendMessage("超过1张图片的搜索需要最少8秒/张并且结果将分批返回")
            event.subject.sendMessage("正在搜索...")

            imgs.forEach {
                try {
                    search(it.queryUrl()) ?. let { trace ->
                        val msg = PlainText(
                                "作品名称: 《${trace.title}》\n" +
                                        "分集: ${trace.episode}\n" +
                                        "帧位时间: ${trace.from.toInt() / 60}:${(trace.from.toInt()) % 60}\n" +
                                        "相似度: ${trace.similarity * 100}%\n" +
                                        "数据库匹配帧: "
                        ) + try {
                            event.subject.uploadImage(URL(trace.imageUrl).urlGetImage().toExternalResource())
                        } catch (e: Exception){
                            PlainText("加载失败...")
                        }
                        event.subject.sendMessage(msg)
                    } ?: event.subject.sendMessage("没有找到符合条件的番剧, " +
                            "请检查: \n1.数据库中几乎只有日本番\n2.画面是否完整\n3.画面是否被遮挡\n4.只有极少数里番在数据库中")

                } catch (e: Exception){
                    event.subject.sendMessage("服务异常, 请稍后再试: \nrequesting exception: ${e.message}")
                    milkyTea.logger.ex(tag, "requesting exception: ${e.message}")
                    return false
                }
                Thread.sleep(8000)  // 调用速度有限制, 10次/分钟, 所以有多张图要分开返回
            }
            return true
        } ?: milkyTea.logger.i(tag, "User cancellation")
        return null
    }

    /**
     * 搜索结果数据类
     *
     * @param title: 匹配到的作品标题, 匹配到的文件名
     * @param episode: 匹配到的作品分集, 可能是 集数(number) OVA/OAD 空字符串
     * @param from: 匹配到的片段起始时间(单位秒)
     * @param to: 匹配到的片段结束时间(单位秒)
     * @param similarity: 相似度, 0~1
     * @param videoUrl: 预览片段URL
     * @param imageUrl: 预览图片URL
     */
    data class Trace(
            val title: String,
            val episode: String,
            val from: Double,
            val to: Double,
            val similarity: Float,
            val videoUrl: String,
            val imageUrl: String
    )

    /**
     * 发送搜索请求并解析结果
     *
     * @return:
     *  解析成功 -> [Trace]实例
     *  解析失败 -> null 或 throw
     *
     */
    @Suppress("RedundantSuspendModifier")
    private suspend fun search (imgUrl: String): Trace? {
        val res = Requests.get("https://api.trace.moe/search?url=$imgUrl")
            .timeout(20000)
            .send()

        if (res.statusCode() == 200) {
            val resJson = res.readToJson(JSONObject::class.java)

            var obj: JSONObject? = null
            for (it in resJson.getJSONArray("result")) {
                val node = JSONObject.parseObject(it.toString())
                if (node.getString("episode") != "") {
                    obj = node
                    break
                }
            }

            obj ?. let {trace ->
                if (trace.getFloatValue("similarity") > 0.85) {
                    /*
                    * Search results with similarity lower than 87%
                    * are probably incorrect result (just similar, not a match).
                    * It's up to you to decide the cut-off value.
                    */
                    return Trace(
                        title = trace.getString("filename"),
                        episode = trace.getString("episode"),
                        from = trace.getDouble("from"),
                        to = trace.getDouble("to"),
                        similarity = trace.getFloat("similarity"),
                        imageUrl = trace.getString("image"),
                        videoUrl = trace.getString("video")
                    )
                } else milkyTea.logger.w(tag, "similarity too low: ${trace.getFloatValue("similarity")}")
            }
        } else {
            milkyTea.logger.ex(tag, when (res.statusCode()) {
                400 -> "request image is empty"
                403 -> "using an invalid token"
                429 -> "using requesting too fast"
                else -> "unknown exception, code=${res.statusCode()}, msg=${res.readToText()}"
            })
        }
        return null
    }
}
