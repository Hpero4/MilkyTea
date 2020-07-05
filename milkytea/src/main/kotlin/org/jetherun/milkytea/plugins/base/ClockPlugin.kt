package org.jetherun.milkytea.plugins.base

/**
 * 定时插件
 *
 * [interval] 执行间隔, 单位毫秒
 * [lastRunTime] 上次运行的时间, 未曾运行为 null
 *
 * [timer] 计时器, 决定了插件是否执行
 * [run] 执行插件功能并更新 [lastRunTime], 是 MilkyTea 中所调用的方法, 不建议重写. 若重写必须刷新 [lastRunTime], 否则插件将异常
 * [foo] 插件的具体执行逻辑
 */
@Suppress("UNREACHABLE_CODE")
interface ClockPlugin: Plugin {
    val interval: Long
    var lastRunTime: Long?

    /**
     * 计时器
     *
     * @return: 时间到 -> true, 时间未到 -> false
     */
    fun timer(): Boolean{
        lastRunTime ?. let {
            return System.currentTimeMillis() - lastRunTime!! >= interval
        } ?: let {
            return true
        }
    }

    fun run(){
        lastRunTime = System.currentTimeMillis()
        try {
            foo()
        } catch (e: Exception) {
            milkyTea.logger.ex(tag, "Running Exception: ${e.message}")
        }
    }

    fun foo()
}