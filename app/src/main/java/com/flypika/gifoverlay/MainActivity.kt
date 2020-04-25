package com.flypika.gifoverlay

import android.content.Intent
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toFile
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.FFmpeg
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.InputStream


class MainActivity : AppCompatActivity() {

    companion object {
        private const val PICK_IMAGE = 123
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setLoading(false)
        chooseButton.setOnClickListener {
            onChooseClicked()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE) {
            data?.data?.let {
                overlayImage(it)
            }
        }
    }

    private fun overlayImage(imageUri: Uri) {
        val cachedImageFile = File(cacheDir, "image")
        contentResolver.openInputStream(imageUri)?.copyTo(cachedImageFile.outputStream())

        val gifFile = File(cacheDir, "input.gif")
        assets.open("input.gif").copyTo(gifFile.outputStream())

        val outputFile = File(cacheDir, "out.mp4")
        if (outputFile.exists()) {
            outputFile.delete()
        }
        val outputPath = outputFile.absolutePath

        setLoading(true)
        val start = System.currentTimeMillis()
        FFmpegThread.run {
            val command = "-i ${cachedImageFile.absolutePath} -i ${gifFile.absolutePath} -filter_complex '[0:v]scale=540:h=960,setsar=1,overlay' -c:v mpeg4 -qscale 0 $outputPath"
//            val command = "-i ${gifFile.absolutePath} -c:v mpeg4 -qscale 0 $outputPath"
            Log.d(TAG, "ffmpeg $command")
            val rc = FFmpeg.execute(command)
            runOnUiThread {
                Toast.makeText(this, "Elapsed ${(System.currentTimeMillis() - start) / 1000} seconds", Toast.LENGTH_LONG).show()
                setLoading(false)
                if (rc != RETURN_CODE_SUCCESS) {
                    Log.e(TAG, "rc=$rc")
                    return@runOnUiThread
                }

                runOnUiThread {
                    videoView.setVideoPath(outputPath)
                    videoView.start()
                }
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

    private fun setLoading(loading: Boolean) {
        videoView.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        progressBar.visibility = if (loading) View.VISIBLE else View.INVISIBLE
    }
}
