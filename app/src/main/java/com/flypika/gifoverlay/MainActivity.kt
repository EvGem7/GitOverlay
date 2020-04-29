package com.flypika.gifoverlay

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.MediaController
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

        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)
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

        val musicFile = getCacheFile("music.mp3")
        assets.open("music.mp3").copyTo(musicFile.outputStream())

        val noAudio = getCacheFile("noaudio.mp4").absolutePath
        val output = getCacheFile("output.mp4").absolutePath

        setShowingView(ShowingView.PROGRESS)
        val start = System.currentTimeMillis()
        FFmpegThread.run {
            val rc1 = FFmpeg.execute("-i ${cachedImageFile.absolutePath} -i ${gifFile.absolutePath} -filter_complex '[0:v]scale=540:h=960,setsar=1,overlay' -c:v mpeg4 -qscale 0 $noAudio")
            if (rc1 != RETURN_CODE_SUCCESS) {
                return@run
            }
            val rc = FFmpeg.execute("-i $noAudio -i ${musicFile.absolutePath} -c copy -map 0:v:0 -map 1:a:0 $output")
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

                videoView.setVideoPath(output)
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

    private fun getCacheFile(name: String): File {
        val outputFile = File(cacheDir, name)
        if (outputFile.exists()) {
            outputFile.delete()
        }
        return outputFile
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
