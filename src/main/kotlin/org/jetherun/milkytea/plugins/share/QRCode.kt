package org.jetherun.milkytea.plugins.share

import com.google.zxing.BinaryBitmap
import com.google.zxing.ReaderException
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import org.jetherun.milkytea.MilkyTea
import org.jetherun.milkytea.function.MtUtil
import org.jetherun.milkytea.plugins.ShareMessagePlugin
import org.jetherun.milkytea.plugins.base.ImageMessagePlugin
import java.util.*
import javax.imageio.ImageIO

class QRCode(override val milkyTea: MilkyTea): ShareMessagePlugin, ImageMessagePlugin<MessageEvent>{
    override val keyWords: MutableList<String>? = null
    override val onlyCallMe: Boolean = false
    override val tag: String = "QRCode"
    override var ceaseEvent: Boolean? = null
    private var running = false

    override suspend fun excess(event: MessageEvent): Boolean {
        var res = false
        if (!running) {
            running = true
            event.message.forEach {
                try {
                    if (it is Image) {
                        val imgUrl = runBlocking { it.queryUrl() }
                        val imgBase64 = MtUtil.webImgToBase64(imgUrl)
                        val bytes = Base64.getDecoder().decode(imgBase64)
                        val source = BufferedImageLuminanceSource(ImageIO.read(bytes.inputStream()))
                        val bitmap = BinaryBitmap(HybridBinarizer(source))
                        val reader = QRCodeReader()
                        try {
                            val result = reader.decode(bitmap)
                            event.subject.sendMessage("二维码内容: ${result.text}")
                            res = true
                        } catch (e: ReaderException) {
                            milkyTea.logger.d(tag, "QR Code Reading Exception, This Image Notfound QRCode?")
                        }
                    }
                } catch (e: Exception) {
                    milkyTea.logger.w(tag, "Image format not supported")
                }
            }
            running = false
        }
        return res
    }
}