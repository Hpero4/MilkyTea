package org.jetherun.milkytea.plugins.base

import net.mamoe.mirai.event.Event
import org.jetherun.milkytea.MilkyTea

/**
 * 事件插件接口
 *
 * [ceaseEvent] 用于标记该插件触发后事件是否终止向后传递
 * 若为 null 会阻塞主线程直至得到 true 或 false ([excess] 的执行结果)
 * @see MilkyTea.pluginHandler
 * 这样设计主要是为了兼容部分插件需要根据执行结果判定事件是否继续传递
 * 但原则上应尽量避免通过插件执行逻辑 [excess] 决定事件是否传递, 因为这与将 [excess] 独立的初衷相悖
 *
 * [excess] 插件的执行逻辑, 在 [MilkyTea] 中是通过协程 ·并发· 执行的
 * [filter] 插件的触发条件, 在 [MilkyTea] 中是 ·非并发· 执行的
 * 因此, 在 [filter] 中执行耗时操作将会阻塞事件的传递
 *
 * Ps: 如果可能的话尽量通过在 [MilkyTea.kwargs] 中添加参数后使用 [ClockPlugin] 解决耗时操作(如网络请求)对插件触发条件的判定从而避免对事件传递的阻塞
 */
interface EventPlugin<T: Event>: Plugin {
    val ceaseEvent: Boolean?

    /**
     * 插件功能实现
     *
     * 该插件的具体功能实现, 所有的耗时操作都应在该函数内执行
     *
     * 可以选择返回 null 或返回 Boolean 作为执行结果的标志
     * @see ceaseEvent
     */
    fun excess(event: T): Boolean?

    /**
     * 过滤器
     * 决定插件的触发条件
     *
     * 原则上应避免在 [filter] 中执行耗时操作, 因为这与将 [excess] 独立的初衷相悖
     *
     * @return: 协变后的 Event 或 null
     */
    fun filter(event: Event): T?

}