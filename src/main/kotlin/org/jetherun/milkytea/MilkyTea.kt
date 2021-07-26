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
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.BotConfiguration
import org.jetherun.milkytea.function.*
import org.jetherun.milkytea.plugins.base.ClockPlugin
import org.jetherun.milkytea.plugins.base.EventPlugin
import org.jetherun.milkytea.plugins.base.Plugin
import org.jetherun.milkytea.plugins.share.MsgPool
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.support.sqlite.SQLiteDialect
import org.sqlite.SQLiteDataSource
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JOptionPane
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.system.exitProcess

@Suppress("ControlFlowWithEmptyBody", "Unused")
class MilkyTea(
        val qqId: Long,
        passwords: String,
        val resPath: String = "resources"
) {
    val bot = BotFactory.newBot(qqId, passwords) {
//        fileBasedDeviceInfo("device.json")
        loadDeviceInfoJson(File("device.json").readText())
        protocol = BotConfiguration.MiraiProtocol.ANDROID_PHONE
        heartbeatStrategy = BotConfiguration.HeartbeatStrategy.REGISTER
    }
    val logger = Log()

    val startTime = System.currentTimeMillis()
    val startTimeFormat: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(startTime))!!

    /**
     * 数据库实例 [Database]
     */
    val db = let {
        val ds = SQLiteDataSource()
        ds.url = "jdbc:sqlite:${File("").absolutePath}/mt.sqlite"
        Database.Companion.connect(
            ds,
            dialect = SQLiteDialect()
        )
    }

    private val tag = "MilkyTea"

    /**
     * 插件 [Plugin] 组, 分为事件触发插件 [EventPlugin] 与 定时触发 [ClockPlugin] 插件
     *
     * 事件触发插件组 [eventPlugins]:
     * 当收到事件 [Event] 广播时, 由上自下将事件传递到各插件
     * 若事件成功通过某一插件的 [EventPlugin.filter] 且该插件声明截断事件 [EventPlugin.ceaseEvent] 则不会继续向下传递该事件
     * 在 MilkyTea 实例化后可以通过 [MutableList.add] 添加插件, 也可以放入List中使用 [MutableList.addAll] 添加
     * 插件添加的先后顺序影响事件传递, 位于前面的插件 可以选择不继续传递事件(截断事件), 这将会导致后面的插件收不到事件广播, 具体插件先后顺序需要根据需要自行安排
     * : 注意 : 该组中存放的是插件的类对象, 需要使用时通过反射生成实例
     *
     *
     * 定时触发插件组 [clockPlugins]:
     * MilkyTea初始化时会新建一个线程用于监听各[ClockPlugin]
     * 当插件 未曾执行过 或 当前时间 - 上次执行时间 >= 间隔时间 [ClockPlugin.interval] , 就会执行该插件
     * : 注意 : 该组中存放的是插件的实例, 需要使用时直接通过实例调用方法
     *
     * PS: 一个插件可以同时作为 EventPlugin 与 ClockPlugin, 只需要同时实现接口即可
     */
    private val eventPlugins: MutableList<KClass<out EventPlugin<*>>> = CopyOnWriteArrayList(mutableListOf(
            MsgPool::class,
    ))

    private val clockPlugins = CopyOnWriteArrayList<KClass<out ClockPlugin>>()

    private val clocks = ConcurrentHashMap<KClass<out ClockPlugin>, Long>()  // 用于记录上次运行ClockPlugin的时间
    private val lastRunClocks = ConcurrentHashMap<KClass<out ClockPlugin>, Long>()  // 用于记录上次成功运行ClockPlugin的时间

    init {
        logger.i(tag, "Logging")
        var notExcept = true
        runBlocking {
            try {
                bot.login()
            } catch (e: Exception){
                notExcept = false
                if ("Error" in e.message.toString()) {
                    logger.er(tag, "Logging Error ${e.message}, Exit.")
                    JOptionPane.showMessageDialog(null, "${e.message}", "Logging Error", JOptionPane.ERROR_MESSAGE)
                    exitProcess(0)  // 退出
                } else {
                    logger.w(tag, "Logging Exception ${e.message}, Main Thread Delay 10s")
                }
            }
        }
        if (!notExcept) Thread.sleep(10000)
        logger.i(tag, "Logined")

        // 刷新好友与群
        logger.i(tag, "Loading Groups")
        bot.groups.forEach {
            db.replenishGroups(it.id.toString())
        }
        logger.i(tag, "Loading Friends")
        bot.friends.forEach {
            db.replenishFriends(it.id.toString())
        }

        // 清空数据库
        db.useConnection { conn ->
            val sql = """
                DELETE FROM 'p_msg_pool';
                UPDATE sqlite_sequence SET seq = 0 WHERE name='p_msg_pool';
                DELETE FROM 'p_sess_pool';
                UPDATE sqlite_sequence SET seq = 0 WHERE name='p_sess_pool';
            """
            conn.prepareStatement(sql).executeUpdate()
        }

        // 成功加群事件, 刷新群
        bot.eventChannel.subscribeAlways<BotJoinGroupEvent> {
            logger.i(tag, "New Group ${it.groupId}")
            db.replenishGroups(it.groupId.toString())
        }

        // 成功加好友事件, 刷新好友
        bot.eventChannel.subscribeAlways<FriendAddEvent> {
            logger.i(tag, "New Friend ${it.friend.id}")
            db.replenishFriends(it.friend.id.toString())
        }

        logger.i(tag, "Initializing Plugins")
        bot.eventChannel.subscribeAlways<Event> { event ->
            coroutineScope {
                // 异常处理器
                val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
                    println(exception.message)
                }

                launch(coroutineExceptionHandler) {
                    logger.i(tag, "New plugin thread start.")
                    var isSessionContinue = false
                    if (event is MessageEvent) {
                        // 当有相应会话时优先响应会话而不触发其他插件
                        db.from(TPSessPool).select().where { (
                                TPSessPool.user_id eq event.sender.id.toString()
                                ) and (
                                TPSessPool.plugin_timeout greater LocalDateTime.now()
                                )
                        }.forEach {
                            // 此处将[isSessionContinue]标记后由[MessageEvent.nextMessage]接管
                            val name = TPInfo.getValue<String>(db, it[TPSessPool.plugin_id]!!, TPInfo.plugin_name)
                            logger.i(tag, "Continue session: (${event.sender.id})$name")
                            isSessionContinue = true
                        }
                    }

                    if (!isSessionContinue) {
                        var cease = false
                        for (pluginClassObj in eventPlugins) {
                            pluginClassObj.primaryConstructor?.call(this@MilkyTea)?.let { plugin->
                                if (pluginHandler(plugin, event)) {
                                    logger.i(tag, "Event cease from plugin ${plugin.javaClass.name}")
                                    cease = true
                                }
                                logger.d(tag, "Plugin ${plugin.javaClass.name} end.")
                            }
                            if (cease) break
                        }
                    }
                    logger.i(tag, "Plugin thread end")
                }
            }
        }
        logger.i(tag, "Initialized Plugins")

        logger.i(tag, "Initialized ClockPlugin Thread")
        Thread {
            while (true) {
                clockPlugins.forEach {
                    val obj = it.primaryConstructor?.call(this@MilkyTea)!!  // 构建实例
                    val nowTime = System.currentTimeMillis()
                    val clockTime = clocks.getOrDefault(it, 0)  // 获取上次运行时间, 若未运行过则返回0

                    if (nowTime - clockTime > obj.interval) {
                        logger.d(tag, "ClockPlugin ${obj.javaClass.name} run.")
                        clocks[it] = nowTime  // 刷新运行时间
                        obj.lastRuntime = lastRunClocks.getOrDefault(it, 0)  // 为插件提供lastRuntime
                        runBlocking {
                            launch {
                                if (obj.foo()) {  // 运行插件
                                    // 更新上次成功运行时间
                                    lastRunClocks[it] = nowTime  // 刷新成功运行时间
                                } else {
                                    logger.i(tag, "ClockPlugin ${obj.javaClass.name} runtime return false.")  // 运行时返回false
                                }
                            }
                        }
                    }
                }
                Thread.sleep(200)
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
    private suspend fun <T: Event> pluginHandler(plugin: EventPlugin<T>, event: Event): Boolean{
        plugin.filter(event) ?. let {
            logger.i(tag, "Hit Plugin ${plugin.javaClass.name}")
            var result = plugin.excess(it)

            // 当 ceaseEvent 未被定义时, 根据 excess 执行结果决定事件是否传递, 若 excess 也返回 null 则会抛出异常
            return if (plugin.ceaseEvent !is Boolean) {
                val startTime = System.currentTimeMillis()
                while (result == null) {
                    if (System.currentTimeMillis() - startTime > 10000){
                        logger.ex(tag, "Plugin ${plugin.javaClass.name} running timeout")
                        result = false
                    }
                }
                result
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
         * 在整个消息池中查找指定ids的消息
         *
         * @see ids
         *
         * @return:
         *  找到 -> 原消息 [MessageChain]
         *  未找到 -> null
         */
        fun inquiryAllMessageFromId(msgIds: IntArray): MessageChain?{
            for (obj in this.values){
                for (msg in obj){
                    if (msg[MessageSource]?.ids?.equals(msgIds) == true) {
                        return msg
                    }
                }
            }
            return null
        }

        /**
         * 在指定对象的消息池中查找指定ids的消息
         *
         * @see ids
         *
         * @param objKey: 对象Key, 群号(群聊) 或 Q号(私聊)
         *
         * @return:
         *  找到 -> 原消息 [MessageChain]
         *  未找到 -> null
         */
        fun inquiryMessageFromId(msgIds: IntArray, objKey: String): MessageChain? {
            checkPool(objKey) ?. let {
                for (msg in it){
                    if (msg[MessageSource]?.ids?.equals(msgIds) == true) {
                        return msg
                    }
                }
            }
            return null
        }
    }


    /**
     * 添加插件
     */
    @Suppress("unchecked")
    fun addPlugin(plugins: MutableList<KClass<out Plugin>>) {
        plugins.forEach { pluginClassObj ->
            val cls = pluginClassObj.primaryConstructor?.call(this@MilkyTea)
            if (cls is ClockPlugin) {
                clockPlugins.add(pluginClassObj as KClass<out ClockPlugin>)
            }

            if (cls is EventPlugin<*>) {
                eventPlugins.add(pluginClassObj as KClass<out EventPlugin<*>>)
            }

            if (cls !is ClockPlugin && cls !is EventPlugin<*>) {
                throw IllegalArgumentException("Type \"${pluginClassObj.java.name}\" is accident.")
            }
        }
    }
}
