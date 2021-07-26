package org.jetherun.milkytea.plugins

import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.events.GroupTempMessageEvent
import org.jetherun.milkytea.plugins.base.MessagePlugin

/**
 * 共有消息插件接口, 响应任何 [MessageEvent], 即下述所有接口响应的所有事件
 */
interface ShareMessagePlugin: MessagePlugin<MessageEvent>

/**
 * 群组消息插件接口, 响应任何 [GroupMessageEvent]
 */
interface GroupMessagePlugin: MessagePlugin<GroupMessageEvent>

/**
 * 好友消息插件接口, 响应任何 [FriendMessageEvent]
 */
interface FriendMessagePlugin: MessagePlugin<FriendMessageEvent>

/**
 * 临时消息插件接口, 响应任何 [GroupTempMessageEvent]
 */
interface TempMessagePlugin: MessagePlugin<GroupTempMessageEvent>