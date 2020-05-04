package com.flypika.gifoverlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.theartofdev.edmodo.cropper.CropImageView

class OverlayView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attributeSet, defStyleAttr) {

    var cropImageView: CropImageView? = null

    var bitmap: Bitmap? = null

    var alwaysRedraw = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val cropImageView = this.cropImageView ?: return
        val bitmap = this.bitmap ?: return
        canvas.drawBitmap(bitmap, null, cropImageView.cropWindowRect, null)
        if (alwaysRedraw) {
            invalidate()
        }
    }
}
