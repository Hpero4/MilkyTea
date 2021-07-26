package org.jetherun.milkytea.plugins.base

/**
 * 定时插件
 *
 * [interval] 执行间隔, 单位毫秒
 * [lastRuntime] 插件上次运行的时间, 调用时会提供
 *
 * [foo] 插件的具体执行逻辑
 */
@Suppress("UNREACHABLE_CODE")
interface ClockPlugin: Plugin {
    val interval: Long
    var lastRuntime: Long?

    /**
     * 插件的具体执行逻辑
     *
     * 该方法可以含有耗时操作, 将在协程中执行.
     *
     * @return:
     *   true -> 成功执行, 刷新lastRuntime
     *   false -> 未成功执行
     */
    suspend fun foo(): Boolean
}