package org.jetherun.milkytea.function

import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.*
import org.sqlite.SQLiteException
import java.sql.SQLException

/**
 * 数据库中表的实体
 * 所有对象均以T开头
 *
 * 前缀 :p: plugin 插件
 * 前缀 :m: member 成员
 */

object TGroups: Table<Nothing>("m_groups") {
    val group_id = varchar("group_id").primaryKey()
    val upd = datetime("upd")
    val skin_id = int("skin_id")
    val is_skin_enable = boolean("is_skin_enable")
    val is_hentai_enable = boolean("is_hentai_enable")
}

object TFriends: Table<Nothing>("m_friends") {
    val friend_id = varchar("friend_id").primaryKey()
    val upd = datetime("upd")
}

object TPInfo: Table<Nothing>("p_info") {
    val plugin_id = int("plugin_id").primaryKey()
    val plugin_name_zh = varchar("plugin_name_zh")
    val plugin_name = varchar("plugin_name")
    val plugin_field = varchar("plugin_field")

    fun <T> getValue(db: Database, id: Int, column: Column<*>): T? {
        db.from(this).select().where(plugin_id eq id).forEach {
            return it[column] as T
        }
        return null
    }
}

object TPBan: Table<Nothing>("p_ban") {
    val plugin_ban_id = int("plugin_ban_id").primaryKey()
    val plugin_id = int("plugin_id")
    val ban_object = varchar("ban_object")
    val is_group = boolean("is_group")
    val upd = datetime("upd")
}

object TPMsgPool: Table<Nothing>("p_msg_pool") {
    val msg_ids = int("msg_ids").primaryKey()
    val upd = datetime("upd")
    val sender = varchar("sender")
    val subordinate = varchar("subordinate")
    val mirai_code_str = varchar("mirai_code_str")
    val is_recall = boolean("is_recall")
    val mirai_ids = varchar("mirai_ids")
    val last_msg_ids = int("last_msg_ids")
}

object TPSessPool: Table<Nothing>("p_sess_pool") {
    val user_id = varchar("user_id")
    val upd = datetime("upd")
    val plugin_id = int("plugin_id")
    val plugin_timeout = datetime("plugin_timeout")
}

object TPEchoHistory: Table<Nothing>("p_echo_history") {
    val group_id = varchar("group_id")
    val msg_ids = int("msg_ids")
}

object TPKeywords: Table<Nothing>("p_keywords") {
    val plugin_kwd = varchar("plugin_kwd").primaryKey()
    val plugin_id = int("plugin_id")

    /**
     * 检查消息是否命中关键词
     *
     * @return:
     *  命中 -> [TPInfo.plugin_id]
     *  未命中 -> null
     */
    fun checkHit(db: Database, msg: String): Int? {
        db.from(this).select().forEach {
            if (it[plugin_kwd]!! in msg) return it[plugin_id]!!
        }
        return null
    }
}

/**
 * 爬取到的LeagueSkin信息
 */
object SkinCrawler: Table<Nothing>("p_skin_crawler") {
    /**
     * 唯一标识. 供[SkinUpload.league_skin_id]做识别用
     */
    val skin_id = int("skin_id").primaryKey()

    /**
     * 爬取到的LeagueSkin版本
     */
    val version = varchar("version")

    /**
     * 执行爬取的时间
     */
    val upd = datetime("upd")

    /**
     * 文件名
     */
    val file_name = varchar("file_name")

    /**
     * 下载链接
     */
    val url = varchar("url")

    fun getVer(db: Database, skinId: Int): String {
        try {
            db.from(this).select().where(skin_id eq skinId).forEach {
                return it[version] ?: let {
                    throw SQLException("Skin id not found: $skinId")
                }
            }
        } catch (e: SQLiteException) {
            // 防止SkinCrawler表被删查不到当条id所对应的爬取信息
        }
        return ""
    }
}


/**
 * 奶茶更新到群里的LeagueSkin数据
 */
object SkinUpload: Table<Nothing>("skin_upload") {
    /**
     * 唯一标识
     */
    val id = int("id").primaryKey()

    /**
     * 上传日期
     */
    val update_time = datetime("update_time")

    /**
     * 目标, 私聊为q号, 群聊为群号
     */
    val target = varchar("target")

    /**
     * 该次更新所使用的LeagueSkin信息id[SkinCrawler.skin_id]
     */
    val league_skin_id = int("league_skin_id")
}