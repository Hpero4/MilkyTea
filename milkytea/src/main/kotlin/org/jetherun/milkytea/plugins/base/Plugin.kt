package org.jetherun.milkytea.plugins.base

import org.jetherun.milkytea.MilkyTea

/**
 * 插件的基类
 *
 * [milkyTea] 绑定该插件的机器人
 * [tag] 插件标签, 一般用于写日志
 */
interface Plugin {
    val milkyTea: MilkyTea
    val tag: String
}