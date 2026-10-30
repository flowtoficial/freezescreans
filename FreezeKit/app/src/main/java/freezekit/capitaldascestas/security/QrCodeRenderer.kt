package freezekit.capitaldascestas.security

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeRenderer {
    fun render(content: String, sizePx: Int = 512): Bitmap {
        require(content.isNotBlank()) { "Conteúdo do QR vazio" }
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                pixels[y * matrix.width + x] =
                    if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        return Bitmap.createBitmap(
            pixels,
            matrix.width,
            matrix.height,
            Bitmap.Config.ARGB_8888
        )
    }
}
