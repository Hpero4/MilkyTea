package org.jetherun.milkytea.plugins.base

import net.mamoe.mirai.event.Event
import net.mamoe.mirai.message.GroupMessageEvent
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.*

/**
 * 基础消息接口, 提供了关于消息事件处理所需的方法
 *
 * [onlyCallMe] 用于指明该插件是否只响应 @me 的消息
 * [keyWords] 触发该插件的关键词, 为 null 则相应所有对应消息事件 [MessageEvent] 的消息
 * [filter] 重写了触发规则, 添加消息事件特有的关于 [onlyCallMe] 与 [keyWords] 的处理
 *
 * [checkHitKeyWord] 用于检查消息事件内容是否命中关键词
 * [checkCallingMe] 用于检查消息事件为群消息时是否 @me
 * [getOriginalMessage] 用于获取原始消息内容, 即原消息去除 @me 后的内容(如果有的话)
 */
@Suppress("UNCHECKED_CAST")
interface MessagePlugin<T: MessageEvent>: EventPlugin<T> {
    val onlyCallMe: Boolean
    val keyWords: MutableList<String>?

    override fun filter(event: Event): T? {
        try {
            event as T  // 继承没法用 inline 关键字, 只能强转然后捕获异常
            if (checkCallingMe(event))
                if (checkHitKeyWord(event))
                    return event
            return null
        }catch (e: Exception){
            return null
        }
    }

    /**
     * 检查是否命中关键词
     *
     * 若 [keyWords] 为 null 则直接返回 true
     */
    fun checkHitKeyWord(event: T): Boolean{
        keyWords?: return true

        keyWords?.forEach {
            if ( it in event.message.content){
                return true
            }
        }

        return false
    }

    /**
     * 检查是否 calling me
     *
     * 若为私聊消息直接返回 true
     *
     * 若 [onlyCallMe] 为 false 直接返回 true
     */
    fun checkCallingMe(event: T): Boolean{
        return if (event is GroupMessageEvent){
            if (onlyCallMe){
                var callingMe = false
                event.message.forEachContent {
                    if (it is At){
                        callingMe = if (!callingMe) it.target == milkyTea.qqId else callingMe
                    }
                }
                callingMe
            } else true
        } else true
    }

    /**
     * 获取原始消息
     *
     * @return: 原消息除 @me 组成的消息链
     */
    fun getOriginalMessage(event: T): MessageChain{
        var newMessage = PlainText("").asMessageChain()
        event.message.forEachContent {
            if (it !is At)
                newMessage += it
            else if (it.target != milkyTea.qqId)
                newMessage += it
        }
        return newMessage
    }
}