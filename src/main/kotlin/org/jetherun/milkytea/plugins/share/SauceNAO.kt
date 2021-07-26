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
 * 以图搜图
 * 核心算法源自 SauceNAO
 *
 * """
 *  SauceNAO is a reverse image search engine. The name 'SauceNAO' is derived from a
 *  slang form of "Need to know the source of this Now!" which has found common usage
 *  on image boards and other similar sites.
 * """
 *
 * Site: https://saucenao.com/
 */
class SauceNAO(override val milkyTea: MilkyTea): SessionMessagePlugin {
    override val keyWords: MutableList<String> = mutableListOf(
        "以图搜图", "查图片", "找图片", "搜图片", "查原图", "找原图", "查原图", "搜原图", "搜图"
    )
    override val onlyCallMe: Boolean = true
    override val tag: String = "SauceNAO"
    override val timeout = 300L
    override val nameZh: String = "以图搜图"
    override val ceaseEvent: Boolean = true

    override suspend fun excess(event: MessageEvent): Boolean? {
        val user = event.sender.id.toString()

        event.subject.sendMessage("发送一张以上的图片进行检索")
        this@SauceNAO.waitParam<Image>(event, user, "需要发送图片才能进行检索喔") { imgs ->
            milkyTea.logger.d(tag, "Picture received successfully")
            event.subject.sendMessage("正在搜索...")

            imgs.forEach {
                var msg: Message = PlainText("")
                try {
                    val results = sauceNAO(it.queryUrl())

                    results ?. sortBy { sauce ->
                        sauce.similarity
                    }

                    results ?. let {
                        val upperLimit = if (results.size > 3) 3 else results.size  // 返回前3的结果
                        for (i in 0 until upperLimit) {
                            msg = msg + PlainText(
                                    "作品名称: 《${results[i].indexName}》\n" +
                                            "相似度:${results[i].similarity}%\n" +
                                            "原图链接: ${results[i].url}\n" +
                                            "数据库匹配图像(缩略图): "
                            ) + try {
                                event.subject.uploadImage(results[i].thumbnail.urlGetImage().toExternalResource())
                            } catch (e: Exception) {
                                PlainText("加载失败")
                            } + "\n"
                        }
                        event.subject.sendMessage(msg)
                    } ?: event.subject.sendMessage("没有找到相似的图片")
                } catch (e: Exception) {
                    event.subject.sendMessage("服务异常, 请稍后再试: \nrequesting exception: ${e.message}")
                    milkyTea.logger.ex(tag, "requesting exception: ${e.message}")
                    return false
                }
            }
            return true
        } ?: milkyTea.logger.i(tag, "User cancellation")
        return null
    }

    /**
     * 每一项搜索结果的数据类
     *
     * @param similarity: 相似度, 0.0 ~ 100.0
     * @param thumbnail: 缩略图 URL
     * @param url: 原图 URL
     * @param indexName: 标签名 (不是作品名)
     */
    data class Sauce(val similarity: Double, val indexName: String, val thumbnail: URL, val url: URL)

    /**
     * 调用 sauceNAO 的 API
     *
     * @param imgUrl: 要搜索的图片的Url字符串
     *
     * @return: 所有结果对象组成的 List , 若无结果或相似度过低则返回 size=0 的 List
     */
    private fun sauceNAO(imgUrl: String): MutableList<Sauce>? {
        val params = mutableMapOf(
            "output_type" to 2,
            "api_key" to "b86496cf8c920b078f0cf6975da338710b4d5e39",
            "db" to 999,
            "url" to imgUrl
        )

        val res = Requests.get("https://saucenao.com/search.php")
            .params(params)
            .timeout(20000)
            .send()

        val results = mutableListOf<Sauce>()
        if (res.statusCode() == 200) {
            val obj = res.readToJson(JSONObject::class.java)
            obj.getJSONArray("results").forEach {
                val sauceJson = JSONObject.parseObject(it.toString())
                if (sauceJson.getJSONObject("header").getDouble("similarity") > 80.0) {
                    try {
                        results.add(
                            Sauce(
                                sauceJson.getJSONObject("header").getDouble("similarity"),
                                sauceJson.getJSONObject("header").getString("index_name"),
                                URL(sauceJson.getJSONObject("header").getString("thumbnail").toString()),
                                URL(sauceJson.getJSONObject("data").getJSONArray("ext_urls")[0].toString())
                            )
                        )
                    } catch (e: NullPointerException) {
                        // 部分网站无 ext_urls, 此处直接忽略
                        milkyTea.logger.ex(tag, "Instantiating Sauce NullPointerException")
                    }
                }
            }
        } else return null
        return if (results.size > 0) results else null
    }
}