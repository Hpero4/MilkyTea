package org.jetherun.milkytea.function

import net.dongliu.requests.Requests
import java.io.File
import java.net.URL
import java.util.*
import kotlin.random.Random

@Suppress("unused")
class MtUtil {
    companion object{
        /**
         * 在目录下随机抽取一张图片(表情)返回
         *
         * @param path: 抽取目录
         * @param re: 文件名中需要含有的关键词, 可以为空
         */
        fun randomFace(path: String, re: String = ""): File{
            val mPath = File(path)
            if (!mPath.isDirectory) throw IllegalArgumentException("实参 path 期望是路径, 得到 ${mPath.absolutePath}")

            val files = mPath.listFiles()!!
            val results = arrayListOf<File>()
            for ((index, fn) in mPath.list()!!.withIndex()){
                if (re in fn){
                    results.add(files[index])
                }
            }

            return results[Random.nextInt(results.size - 1)]
        }

        /**
         * 将含有中文数字的字符串转化为阿拉伯数字(Int)
         *
         * 只保留0~99
         *
         * @return:
         *      传入不合规的字符串 -> null
         *      传入字符串中含有不合法的数字组合则忽略不合法部分 如: "三十三三" -> 33
         */
        fun toNumber(str: String): Int?{
            try {
                return str.toInt()
            } catch (e: Exception){
                Regex("([0-9]+|[零〇洞一壹幺吆二两贰三叁四肆五伍六陆七柒拐八捌九玖勾十拾]+)").find(str)?.let {
                    try {
                        return it.value.toInt()
                    } catch (e: Exception){
                        val zhKey = listOf("零〇洞", "一壹幺吆", "二两贰", "三叁", "四肆", "五伍", "六陆", "七柒拐",
                            "八捌", "九玖勾", "十拾")

                        // split方法没法直接逐字分割
                        val list = mutableListOf<String>()

                        it.value.forEach {char ->
                            list.add(char.toString())
                        }

                        var result = 0
                        list.forEach { flag ->
                            for ((index, key) in zhKey.withIndex()){
                                if (flag in key){
                                    if (index == 10) {
                                        if (result == 0) result = 10
                                        else result *= 10
                                    }
                                    else {
                                        if (result % 10 == 0)
                                            result += index
                                    }
                                    break
                                }
                            }
                        }
                        return result
                    }
                }
            }
            return null
        }

        /**
         * 网络图片(Url)转base64
         *
         * @return: base64字符串
         * 注意: 返回的字串没有头!! (data:"FileType"/"Format";base64,)
         */
        fun webImgToBase64(imgUrl: String): String{
            val imgBytes = Requests.get(imgUrl).send().readToBytes()
            return Base64.getEncoder().encodeToString(imgBytes)
        }

        /**
         * 网络图片转Byte数组
         */
        fun webImgToBytes(imgUrl: String): ByteArray {
            return Requests.get(imgUrl).send().readToBytes()
        }
    }
}

fun String.urlGetImage(): ByteArray {
    return Requests.get(this).send().readToBytes()
}


fun URL.urlGetImage(): ByteArray {
    return Requests.get(this).send().readToBytes()
}