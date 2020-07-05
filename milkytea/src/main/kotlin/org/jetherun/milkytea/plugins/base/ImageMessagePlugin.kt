package org.jetherun.milkytea.plugins.base

import net.mamoe.mirai.event.Event
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.noneContent

/**
 * 声明插件只响应消息中含有 Image 内容的消息事件
 */
interface ImageMessagePlugin<T: MessageEvent>: MessagePlugin<T> {
    override fun filter(event: Event): T? {
        super.filter(event) ?. let {
            return if (it.message.noneContent { content -> content is Image })
                null
            else
                it
        } ?: let {
            return null
        }
    }
}