package org.jetherun.milkytea.function

import org.ktorm.database.Database
import org.ktorm.dsl.*
import java.time.LocalDateTime

// [Database] 类标记为final, 无法继承或委托. 故采用Kotlin装饰器附加功能


/**
 * 补充群信息, 多补少不删
 *
 * @param groupId: 群号
 */
fun Database.replenishGroups(groupId: String) {
    if (this.from(TGroups).select().where(TGroups.group_id eq groupId).rowSet.size() == 0) {
        this.insert(TGroups) { row ->
            set(row.group_id, groupId)
            set(row.upd, LocalDateTime.now())
        }
    }
}


/**
 * 补充好友信息, 多补少不删
 *
 * @param friendId: 群号
 */
fun Database.replenishFriends(friendId: String) {
    if (this.from(TFriends).select().where(TFriends.friend_id eq friendId).rowSet.size() == 0) {
        this.insert(TFriends) { row ->
            set(row.friend_id, friendId)
            set(row.upd, LocalDateTime.now())
        }
    }
}