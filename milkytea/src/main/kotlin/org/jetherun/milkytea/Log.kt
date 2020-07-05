package org.jetherun.milkytea

import java.io.File
import java.io.FileWriter
import java.lang.Exception
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Log(private val path: String = "/log"){
    var level = 0  // 日志等级, 默认DEBUG, 会响应该等级(含)或以上的消息, 可以随时修改
    private val pathF = File(path)

    init {
        // 无文件夹则新建
        if (!pathF.exists()){
            pathF.mkdir()
        }
    }

    private fun writeLog(tag: String, msg: String, lv: Int){
        if (level <= lv) {

            // 更新日志文件信息, 用于换天时重新创建新的文件写入
            val logFileName = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now()) + ".log"
            val logFile = File("$path/$logFileName")
            if (!logFile.exists()){
                logFile.createNewFile()
            }

            val lvName = when (lv) {
                0 -> "D"
                1 -> "I"
                2 -> "W"
                3 -> "EX"
                4 -> "ER"
                else -> "level not found"
            }

            val lineNum = try {
                Throwable().stackTrace[2].lineNumber
            } catch (e: Exception){
                "line not found"
            }

            val codeFile = try {
                Throwable().stackTrace[2].fileName
            } catch (e: Exception){
                "code file not found"
            }

            val logTime = DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now())

            // "[MilkyTea] 时:分:秒 等级/标签 行号: 消息内容"
            val logData = "[MilkyTea] $logTime $lvName/$tag $codeFile:$lineNum: $msg\n"

            val logFileWriter = FileWriter(logFile, true)
            logFileWriter.write(logData)
            logFileWriter.close()

            // War或以上的用红色打印
            if (lv > 1){
                System.err.println(logData)
            } else print(logData)
        }
    }

    /**
     * 写 DEBUG 日志
     */
    fun d(tag: String, msg: String){
        writeLog(tag, msg, DEBUG)
    }

    /**
     * 写 INFO 日志
     */
    fun i(tag: String, msg: String){
        writeLog(tag, msg, INFO)
    }

    /**
     * 写 WARING 日志
     */
    fun w(tag: String, msg: String){
        writeLog(tag, msg, WARING)
    }

    /**
     * 写 EXCEPT 日志
     */
    fun ex(tag: String, msg: String){
        writeLog(tag, msg, EXCEPT)
    }

    /**
     * 写 ERROR 日志
     */
    fun er(tag: String, msg: String){
        writeLog(tag, msg, ERROR)
    }

    companion object{
        const val DEBUG = 0  // 调试
        const val INFO = 1  // 运行信息
        const val WARING = 2  // 警告, 程序可以自行修正并继续运行但可能与预计有偏差
        const val EXCEPT = 3  // 异常, 程序可能可以修正并继续运行但一定与预计有偏差
        const val ERROR = 4  // 错误, 程序无法修正且随时可能终止运行
    }
}