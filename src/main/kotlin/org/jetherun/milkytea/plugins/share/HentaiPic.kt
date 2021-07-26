package org.jetherun.milkytea.plugins.share

import com.alibaba.fastjson.JSONObject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.dongliu.requests.Requests
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetherun.milkytea.MilkyTea
import org.jetherun.milkytea.function.MtUtil
import org.jetherun.milkytea.plugins.ShareMessagePlugin
import java.io.File
import java.lang.Exception
import java.net.URL

class HentaiPic(override val milkyTea: MilkyTea) : ShareMessagePlugin {
    override val keyWords: MutableList<String> = mutableListOf("插画", "漫画")
    override val tag: String = "HentaiPic"
    override val onlyCallMe: Boolean = true
    override val ceaseEvent: Boolean = true
    private lateinit var hitKewWord: String

    override fun checkHitKeyWord(event: MessageEvent): Boolean {
        keyWords.forEach {
            if ( it in event.message.content){
                hitKewWord = it
                return true
            }
        }
        return false
    }

    override suspend fun excess(event: MessageEvent): Boolean? {
        val params = paramsHandle(event)
        val apiKey = "159765615ef0cc2b6973e3"
        val pics = mutableListOf<Setu>()
        val requestParams = mutableMapOf<String, Any>("num" to params.num, "apikey" to apiKey)
        val facePath = "${milkyTea.resPath}/pornCheckFace"
        params.keyWord?.let {
            requestParams["keyword"] = it
        }

        milkyTea.logger.i(tag, "(${(event.message[0] as MessageSource).fromId}) -> " +
                "${params.keyWord} Response ${params.num} HentaiPictures"
        )

        val resJson = Requests.get(
            "https://api.lolicon.app/setu").params(requestParams).send().readToJson(JSONObject::class.java)
        val setuArray = resJson.getJSONArray("data")
        if (setuArray.size > 0) {
            if (setuArray.size < params.num)
                event.subject.sendMessage("只找到了 ${setuArray.size} 张 ${params.keyWord} 插画")

            setuArray.forEach {
                val setuJson = JSONObject.parseObject(it.toString())
                pics.add(
                        Setu(
                                URL(setuJson["url"].toString()),
                                setuJson["pid"].toString(),
                                setuJson["title"].toString(),
                                setuJson["author"].toString()
                        )
                )
            }

            event.subject.sendImage(
                    MtUtil.randomFace(
                            facePath,
                            "hentai"
                    )
            )

            for ((index, pic) in pics.withIndex()) {
                val info = PlainText("(${index + 1}) 《${pic.title}》[Pid${pic.pid}] -- ${pic.author}\n")
                val img = try {
                    milkyTea.logger.i(tag, "try upload image: Url: ${pic.url}")
                    val imgData = Requests.get(pic.url).send().readToBytes()
                    event.subject.uploadImage(imgData.toExternalResource())
                } catch (e: Exception) {
                    milkyTea.logger.w(tag, "UploadImage Exception: ${e.message}")
                    PlainText("这张图片被删除了...") + event.subject.uploadImage(
                        File(milkyTea.resPath + "/pornCheckFace/pornBan (1).jpg").toExternalResource()
                    )
                }
                event.subject.sendMessage(info + img)
            }
        } else event.subject.sendMessage("没有找到标签为 ${params.keyWord} 的插画")
        return null
    }

    /**
     * Params 数据类
     *
     * @param num: 请求次数
     * @param keyWord: 关键词
     */
    data class Params(val num: Int = 1, val keyWord: String? = null)

    /**
     * @param url: 图片url
     * @param pid: 图片pid
     * @param title: 作品名
     * @param author: 作者名
     */
    data class Setu(val url: URL, val pid: String, val title: String, val author: String)

    /**
     * 从输入消息中获取插件所需参数
     *
     * @return: -> [Params]
     */
    private suspend fun paramsHandle(event: MessageEvent): Params {

        // 剔除所有除纯文字外的消息内容
        var onlyText = ""
        event.message.forEach {
            if (it is PlainText) onlyText += it.content
        }

        val split = onlyText.split(hitKewWord)

        if (split.size > 2){
            var params = ""
            split.forEach {
                params += "$it "
            }
            milkyTea.logger.w(tag, "(${(event.message[0] as MessageSource).fromId}) Unable Decode Params: $params")

            event.subject.sendMessage("参数过多, 无法识别: $params 采取默认值")

            return Params()
        } else {
            var num = MtUtil.toNumber(split[split.size - 1])
            num?. let {
                num = if (it > 10) 10 else it
                return if (split.size == 2){
                    Params(num!!, split[0].replace(" ", ""))
                } else Params(num!!)
            }
            return Params(keyWord = split[0].replace(" ", ""))
        }
    }
}