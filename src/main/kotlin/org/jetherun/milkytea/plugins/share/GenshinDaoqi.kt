package org.jetherun.milkytea.plugins.share

import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import org.jetherun.milkytea.MilkyTea
import org.jetherun.milkytea.plugins.ShareMessagePlugin

class GenshinDaoqi(override val milkyTea: MilkyTea): ShareMessagePlugin {
    override val onlyCallMe: Boolean
        get() = true
    override val keyWords: MutableList<String>
        get() = mutableListOf("稻妻解密", "稻妻解谜")
    override val ceaseEvent: Boolean
        get() = true

    override suspend fun excess(event: MessageEvent): Boolean? {
        var msg = "请面向石柱, 编号自左往右从1开始:\n"
        val param = Regex("""\d+""").find(event.message.findIsInstance<PlainText>().toString()) ?. value ?: let {
            event.subject.sendMessage(event.message.quote() + "输入数据不符合规范, 以下为示例: \"稻妻解谜123\" 或 \"稻妻解密1121\"")
            return null
        }
        val list = mutableListOf<Int>()

        param.forEach {
            list.add(it.toInt() - 48)
        }

        if (list.size == 4) {
            while (list[0] != list[1]) {
                rotate(list, 2)
                msg += "旋转第3个石柱\n"
            }
            while (list[2] != list[3]) {
                rotate(list, 1)
                msg += "旋转第2个石柱\n"
            }
            while (list[0] != 3) {
                rotate(list, 0)
                msg += "旋转第1个石柱\n"
            }
            while (list[3] != 3) {
                rotate(list, 3)
                msg += "旋转第4个石柱\n"
            }
        } else if (list.size == 3) {
            while (list[0] != list[1]) {
                rotate(list, 2)
                msg += "旋转第3个石柱\n"
            }
            while (list[1] != list[2]) {
                rotate(list, 0)
                msg += "旋转第1个石柱\n"
            }
            while (list[1] != 3) {
                rotate(list, 1)
                msg += "旋转第2个石柱\n"
            }
        }

        event.subject.sendMessage(event.message.quote() + msg)

        return null
    }

    private fun rotate(list: MutableList<Int>, index: Int) {
        when (index) {
            0 -> {
                list[index + 1] = if ((list[index + 1] + 1) < 4) list[index + 1] + 1 else 1
                list[index] = if ((list[index] + 1) < 4) list[index] + 1 else 1
            }
            list.size - 1 -> {
                list[index - 1] = if ((list[index - 1] + 1) < 4) list[index - 1] + 1 else 1
                list[index] = if ((list[index] + 1) < 4) list[index] + 1 else 1
            }
            else -> {
                list[index - 1] = if ((list[index - 1] + 1) < 4) list[index - 1] + 1 else 1
                list[index] = if ((list[index] + 1) < 4) list[index] + 1 else 1
                list[index + 1] = if ((list[index + 1] + 1) < 4) list[index + 1] + 1 else 1
            }
        }
    }

    override val tag: String
        get() = "GenshinDaoqi"
}