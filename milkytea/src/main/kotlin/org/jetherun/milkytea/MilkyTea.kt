package org.jetherun.milkytea

/**
 * 奶茶由原本使用 Python(request / reply / 插件逻辑) + CoolQ(接收) + NoneBot(插件框架) + MPQ(发送) + MaHua(转发)
 * 改为使用 Kotlin(逻辑) + Mirai(协议) 全部重写.
 * 项目为纯 Kotlin 构成, 引入部分 Java 库.
 *
 * 鸣谢:
 *      Mirai --
 *          基于 Kotlin 可在全平台运行, 提供 QQ Android + TIM PC 协议的高效率机器人框架
 *          Github: https://github.com/mamoe/mirai
 *
 *      JetBrains --
 *          IntelliJ IDEA是一个在各个方面都最大程度地提高开发人员的生产力的 IDE, 适用于 JVM 平台语言。
 *          特别感谢 JetBrains 为学生及开源项目提供免费的 IntelliJ IDEA 等 IDE 的授权
 *
 */

import kotlinx.coroutines.*
import net.mamoe.mirai.*
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.message.GroupMessageEvent
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.*
import org.jetherun.milkytea.plugins.SessionMessagePlugin
import org.jetherun.milkytea.plugins.ShareMessagePlugin
import org.jetherun.milkytea.plugins.base.EventPlugin
import org.jetherun.milkytea.plugins.base.ClockPlugin
import org.jetherun.milkytea.plugins.base.Plugin
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.collections.ArrayList

@Suppress("ControlFlowWithEmptyBody", "Unused")
class MilkyTea(
        val qqId: Long,
        passwords: String,
        val resPath: String = "src/main/resources"
) {
    val bot = Bot(qqId, passwords)
    val kwargs = ConcurrentHashMap<String, Any>()
    val logger = Log()

    // 会话池结构: Pool{"Obj1": Session, "Obj2"...}; Obj为触发会话的消息的发送者, 无论是群消息还是好友消息都是发送者的Q号
    val sessionPool = Pool<Session>()

    // 消息池结构: Pool{"Obj1": [Message1, Message2...], "Obj2":...}; Obj为任何可以与Bot发消息的对象, 群为群号, 好友为Q号, 以此类推
    val messagePool = MessagePool()
    private val tag = "MilkyTea"

    val startTime = System.currentTimeMillis()
    val startTimeFormat: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(startTime))!!

    /**
     * 插件 [Plugin] 组, 分为事件触发插件 [EventPlugin] 与 定时触发 [ClockPlugin] 插件
     *
     * 事件触发插件:
     * 当收到事件 [Event] 广播时, 由上自下将事件传递到各插件
     * 若事件成功通过某一插件的 [EventPlugin.filter] 且该插件声明截断事件 [EventPlugin.ceaseEvent] 则不会继续向下传递该事件
     * 在 MilkyTea 实例化后可以通过 [MutableList.add] 添加插件, 也可以放入List中使用 [MutableList.addAll] 添加
     * 插件添加的先后顺序影响事件传递, 位于前面的插件 可以选择不继续传递事件(截断事件), 这将会导致后面的插件收不到事件广播, 具体插件先后顺序需要根据需要自行安排
     *
     * 定时触发插件:
     * MilkyTea初始化时会新建一个线程用于监听各[ClockPlugin]
     * 当插件 未曾执行过 或 当前时间 - 上次执行时间 [ClockPlugin.lastRunTime] >= 间隔时间 [ClockPlugin.interval] , 就会执行该插件
     *
     * PS: 一个插件可以同时作为 EventPlugin 与 ClockPlugin, 只需要同时实现接口即可
     */
    val plugins: MutableList<Plugin> = CopyOnWriteArrayList(mutableListOf(
            MessagePool.MessagePoolRecorder(this)  // 消息池记录
    ))

    init {
        logger.i(tag, "Logging")
        var notExcept = true
        runBlocking {
            try {
                bot.login()
            } catch (e: Exception){
                notExcept = false
                logger.w(tag, "Logging Exception ${e.message}, Main Thread Delay 10s")
            }
        }
        if (!notExcept) Thread.sleep(10000)
        logger.i(tag, "Logined")

        logger.i(tag, "Initializing Plugins")
        bot.subscribeAlways<Event> { event ->
            Thread {
                logger.i(tag, "new plugin thread start.")
                var isSessionContinue = false
                if (event is MessageEvent) {
                    val sessionKey = (event.message[0] as MessageSource).fromId.toString()
                    sessionPool.checkPool(sessionKey)?.let {
                        logger.i(tag, "Continue Session: ($sessionKey)${it.name}")
                        isSessionContinue = true
                    }
                }

                // 当有相应会话时优先响应会话而不触发其他插件
                if (!isSessionContinue) {
                    for (plugin in plugins) {
                        if (plugin is EventPlugin<*>) {
                            if (pluginHandler(plugin, event)) {
                                logger.i(tag, "Event Cease From Plugin ${plugin.javaClass.name}")
                                break
                            }
                        }
                    }
                }
                logger.i(tag, "plugin thread end")
            }.start()
        }
        logger.i(tag, "Initialized Plugins")

        Thread {
            while (true) {
                val nowTime = System.currentTimeMillis()
                var sessionMsg = ""
                for (key in sessionPool.keys) {
                    if ((sessionPool[key]!!.overTime) - nowTime < 0L)
                        sessionPool.remove(key)
                    else
                        sessionMsg += "${sessionPool[key]!!.name}($key) "
                }
                if (sessionMsg != "") { logger.d(tag, "Now SessionPool: $sessionMsg") }
                Thread.sleep(60000)
            }
        }.start()
        logger.i(tag, "SessionPool Thread Running")

        logger.i(tag, "Initialized MessagePool")
        Thread {
            while (true) {
                val nowTime = System.currentTimeMillis() / 1000  // 该处的时间戳单位是秒
                for (obj in messagePool){
                    val removeList = arrayListOf<MessageChain>()
                    for ( msg in obj.value){
                        if (nowTime - msg.time > 3600){
                            logger.d(tag, "Remove Message (${obj.key}): ${msg.content}")
                            removeList.add(msg)
                        } else break
                    }
                    obj.value.removeAll(removeList)
                }
                Thread.sleep(60000)
            }
        }.start()
        logger.i(tag, "MessagePool Thread Running")

        logger.i(tag, "Initialized ClockPlugin Thread")
        Thread {
            while (true) {
                plugins.forEach {
                    if (it is ClockPlugin) {
                        if (it.timer()) {
//                            logger.d(tag, "Run ClockPlugin ${it.javaClass.name}")
                            it.run()
                        }
                    }
                }
            }
        }.start()
        logger.i(tag, "ClockPlugin Thread Running")
    }

    /**
     * 收到事件广播时, 判定事件是否符合插件规则, 决定是否执行插件与是否继续传递事件
     * 当 [EventPlugin.ceaseEvent] 未被定义时, 根据 [EventPlugin.excess] 执行结果决定事件是否传递, 若 excess 也返回 null 则会抛出异常
     * 等待 [EventPlugin.excess] 返回时有10秒超时时间, 若超时返回 false (不截断事件), 并写入日志
     *
     * @param plugin: 需要处理的插件
     * @param event: 收到广播的事件实例
     *
     * @return: 事件是否被截断, 为 true 则事件广播不再继续传递
     *
     * @throws Exception: [EventPlugin.ceaseEvent] 与 [EventPlugin.excess] 的返回值 同时为 null 时, 会抛出异常
     */
    private fun <T: Event> pluginHandler(plugin: EventPlugin<T>, event: Event): Boolean{
        plugin.filter(event) ?. let {
            logger.i(tag, "Hit Plugin ${plugin.javaClass.name}")
            var result: Boolean? = null
            GlobalScope.launch { result = plugin.excess(it) }  // 协程执行插件逻辑提高吞吐量

            // 当 ceaseEvent 未被定义时, 根据 excess 执行结果决定事件是否传递, 若 excess 也返回 null 则会抛出异常
            return if (plugin.ceaseEvent !is Boolean) {
                val startTime = System.currentTimeMillis()
                while (result == null) {
                    if (System.currentTimeMillis() - startTime > 10000){
                        logger.ex(tag, "Plugin ${plugin.javaClass.name} running timeout")
                        result = false
                    }
                }
                if (result is Boolean) result!!
                else {
                    logger.er(tag, "Plugin ${plugin.javaClass.name} \"excess Return\" as \"ceaseEvent\" is null")
                    bot.close()
                    throw Exception("Plugin ${plugin.javaClass.name} \"excess Return\" as \"ceaseEvent\" is null")
                }
            } else plugin.ceaseEvent!!
        } ?: return false
    }

    /**
     * 池子的基类
     *
     * [V] : 池子内的数据结构
     */
    open class Pool<V>: ConcurrentHashMap<String, V>() {
        fun checkPool(key: String): V?{
            return if (key in this.keys){
                this[key]!!
            } else null
        }
    }

    class MessagePool: Pool<CopyOnWriteArrayList<MessageChain>>() {
        /**
         * 在整个消息池中查找指定ID的消息
         *
         * @return:
         *  找到 -> 原消息 [MessageChain]
         *  未找到 -> null
         */
        fun inquiryAllMessageFromId(msgId: Int): MessageChain?{
            for (obj in this.values){
                for (msg in obj){
                    if (msg.id == msgId) {
                        return msg
                    }
                }
            }
            return null
        }

        /**
         * 在指定对象的消息池中查找指定ID的消息
         *
         * @param objKey: 对象Key, 群号(群聊) 或 Q号(私聊)
         *
         * @return:
         *  找到 -> 原消息 [MessageChain]
         *  未找到 -> null
         */
        fun inquiryMessageFromId(msgId: Int, objKey: String): MessageChain? {
            checkPool(objKey) ?. let {
                for (msg in it){
                    if (msg.id == msgId) {
                        return msg
                    }
                }
            }
            return null
        }

        /**
         * 将收到的消息加入消息池, 该类本质是一个插件
         *
         * 注意: 该插件操作的是 [MilkyTea.messagePool] 而非任何 [MessagePool] 实例本身
         */
        class MessagePoolRecorder(override val milkyTea: MilkyTea) : ShareMessagePlugin {
            override val ceaseEvent: Boolean = false
            override val tag: String = "MessagePool"
            override val keyWords: ArrayList<String>? = null
            override val onlyCallMe: Boolean = false

            override fun excess(event: MessageEvent): Boolean? {
                val objKey = if (event is GroupMessageEvent)
                    event.group.id.toString()
                else
                    (event.message[0] as MessageSource).fromId.toString()

                milkyTea.messagePool.checkPool(objKey) ?. add(event.message) ?: let {
                    milkyTea.messagePool[objKey] = CopyOnWriteArrayList(arrayListOf(event.message))
                }
                milkyTea.logger.d(tag, "Add New Message In MessagePool (${objKey}): ${event.message.content}")
                return null
            }
        }
    }

    /**
     * 会话类, 为避免多轮对话中触发其他插件
     *
     * @param overTime: 过期时间, 时间戳, 单位毫秒
     */
    data class Session (val name: String, val overTime: Long, val nameZh: String, val plugin: SessionMessagePlugin)

    /**
     * 往 kwargs 添加 或 取得已存在 的一个键所对应值
     *
     * @return:
     *      已存在 -> 直接返回
     *      未存在 -> 创建并返回
     */
    inline fun <reified T: Any> getOrAddArg(k:String, v: T): T {
        kwargs[k]?:let{
            kwargs[k] = v
        }
        return kwargs[k] as T
    }
}
