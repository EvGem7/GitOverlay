package com.flypika.gifoverlay

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.FFmpeg
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File


class MainActivity : AppCompatActivity() {

    companion object {
        private const val PICK_IMAGE = 123
        private const val TAG = "MainActivity"
    }

    private lateinit var cachedImageFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cachedImageFile = File(cacheDir, "image")
        initView()
        setListeners()
    }

    private fun setListeners() {
        chooseButton.setOnClickListener {
            onChooseClicked()
        }
        cropButton.setOnClickListener {
            cropImageView.getCroppedImageAsync()
        }
        cropImageView.setOnCropImageCompleteListener { _, result ->
            result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, cachedImageFile.outputStream())
            overlayImage()
        }
    }

    private fun initView() {
        setShowingView(ShowingView.NONE)

        cropImageView.apply {
            setFixedAspectRatio(true)
            setAspectRatio(540, 960)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE) {
            data?.data?.let {
                setShowingView(ShowingView.CROP)
                cropImageView.setImageUriAsync(it)
            }
        }
    }

    private fun overlayImage() {
        val gifFile = File(cacheDir, "input.gif")
        assets.open("input.gif").copyTo(gifFile.outputStream())

        val outputFile = File(cacheDir, "out.mp4")
        if (outputFile.exists()) {
            outputFile.delete()
        }
        val outputPath = outputFile.absolutePath

        setShowingView(ShowingView.PROGRESS)
        val start = System.currentTimeMillis()
        FFmpegThread.run {
            val command = "-i ${cachedImageFile.absolutePath} -i ${gifFile.absolutePath} -filter_complex '[0:v]scale=540:h=960,setsar=1,overlay' -c:v mpeg4 -qscale 0 $outputPath"
            Log.d(TAG, "ffmpeg $command")
            val rc = FFmpeg.execute(command)
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Elapsed ${(System.currentTimeMillis() - start) / 1000} seconds",
                    Toast.LENGTH_LONG
                ).show()
                setShowingView(ShowingView.VIDEO)
                if (rc != RETURN_CODE_SUCCESS) {
                    Log.e(TAG, "rc=$rc")
                    return@runOnUiThread
                }

                videoView.setVideoPath(outputPath)
                videoView.start()
            }
        }
    }

    private fun onChooseClicked() {
        val intent = Intent().apply {
            type = "image/*"
            action = Intent.ACTION_GET_CONTENT
        }
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE)
    }

    private fun setShowingView(showingView: ShowingView) {
        videoView.isVisible = showingView == ShowingView.VIDEO
        progressBar.isVisible = showingView == ShowingView.PROGRESS
        cropLayout.isVisible = showingView == ShowingView.CROP
    }

    private enum class ShowingView {
        VIDEO, PROGRESS, CROP, NONE
    }

    private var View.isVisible: Boolean
        get() = visibility == View.VISIBLE
        set(value) {
            visibility = if (value) View.VISIBLE else View.INVISIBLE
        }
}
