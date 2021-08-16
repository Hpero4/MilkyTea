package org.jetherun.milkytea

import org.jetherun.milkytea.plugins.group.*
import org.jetherun.milkytea.plugins.share.*

suspend fun main() {
    val mt = MilkyTea(QQ号, 密码)
    mt.addPlugin(
        mutableListOf(
            ReCallRecorder::class,  // 撤回消息池
            InquiryReCall::class,  // 查询群组撤回消息
            Setting::class,  // 设置
            LeagueSkin::class,  // LeagueSkin更新检测
            BiliCover::class,  // bilibili封面提取
            QRCode::class,  // 二维码识别
            HentaiPic::class,  // 插画
            SauceNAO::class,  // 以图搜图
            TraceMoe::class,  // 以图搜番
            PixivCat::class,  // Pid取图片
            Xslist:: class,  // 以图搜演员
            Echo::class,  // 群组复读
            GenshinDaoqi::class,  // 稻妻解密
        )
    )
    mt.logger.i("Main", "初始化完成, 保持运行")
    mt.bot.join()
}
