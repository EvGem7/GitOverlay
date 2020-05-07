package com.flypika.gifoverlay

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.MediaController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.FFmpeg
import com.theartofdev.edmodo.cropper.CropImageView
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File


class MainActivity : AppCompatActivity() {

    companion object {
        private const val PICK_IMAGE = 123
        private const val TAG = "MainActivity"
    }

    private lateinit var cachedImageFile: File

    private lateinit var outputFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cachedImageFile = File(cacheDir, "image")
        outputFile = getCacheFile("output.mp4")

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
        cropImageView.apply {
            setOnCropImageCompleteListener { _, result ->
                result.bitmap.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    cachedImageFile.outputStream()
                )
                overlayImage()
            }
            setOnSetCropOverlayMovedListener {
                overlayView.invalidate()
            }
            setOnSetCropOverlayReleasedListener {
                overlayView.alwaysRedraw = true
                postDelayed({
                    overlayView.alwaysRedraw = false
                }, 300L)
            }
            setOnSetImageUriCompleteListener { _, _, _ ->
                overlayView.invalidate()
            }
        }
        shareButton.setOnClickListener {
            val uri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    outputFile
                ) else Uri.fromFile(outputFile)
            ShareCompat.IntentBuilder.from(this)
                .setStream(uri)
                .setType("video/mp4")
                .setChooserTitle("Share video...")
                .startChooser();
        }
    }

    private fun initView() {
        setShowingView(ShowingView.NONE)

        cropImageView.apply {
            setFixedAspectRatio(true)
            setAspectRatio(540, 960)
            guidelines = CropImageView.Guidelines.OFF
        }

        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        overlayView.apply {
            cropImageView = this@MainActivity.cropImageView
            bitmap = BitmapFactory.decodeResource(resources, R.drawable.final_frame)
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

        val musicFile = getCacheFile("music.mp3")
        assets.open("music.mp3").copyTo(musicFile.outputStream())

        val noAudio = getCacheFile("noaudio.mp4").absolutePath
        val output = outputFile.absolutePath

        setShowingView(ShowingView.PROGRESS)
        val start = System.currentTimeMillis()
        FFmpegThread.run {
//            val rc1 = FFmpeg.execute("-i ${cachedImageFile.absolutePath} -i ${gifFile.absolutePath} -filter_complex '[0:v]scale=540:h=960,setsar=1,overlay' -c:v mpeg4 -qscale 0 $noAudio")
            val rc1 = FFmpeg.execute(
                getNoAudioCommand(
                    cachedImageFile.absolutePath,
                    gifFile.absolutePath,
                    noAudio
                )
            )
            if (rc1 != RETURN_CODE_SUCCESS) {
                return@run
            }
            val rc =
                FFmpeg.execute("-i $noAudio -i ${musicFile.absolutePath} -c copy -map 0:v:0 -map 1:a:0 $output")
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

    private fun getNoAudioCommand(imagePath: String, gifPath: String, outputPath: String): String {
        val arr = resources.getStringArray(R.array.effects)
        val fadeIn = arr[0]
        val zoomIn = arr[1]
        val zoomOut = arr[2]
        return when (effectsSpinner.selectedItem.toString()) {
            fadeIn -> "-loop 1 -t 3.5 -i $imagePath -i $gifPath -filter_complex \"[0:v]scale=540:h=960,setsar=1,format=rgba,fade=in:st=1:d=2.5[ovr];[ovr][1]overlay\" -c:v mpeg4 -qscale 0 $outputPath"
            zoomIn -> "-i $imagePath -i $gifPath -filter_complex \"scale=4000x4000,zoompan=z='if(gte(on, 25), 1 + (on-25)/(duration-25)*0.4)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=45:s=540x960,setsar=1[img];[img][1]overlay\" -c:v mpeg4 -qscale 0 $outputPath"
            zoomOut -> "-i $imagePath -i $gifPath -filter_complex \"scale=4000x4000,zoompan=z='2.25-on/duration*1.25':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=43:s=540x960,setsar=1[img];[img][1]overlay\" -c:v mpeg4 -qscale 0 $outputPath"
            else -> throw IllegalArgumentException()
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
        videoLayout.isVisible = showingView == ShowingView.VIDEO
        progressBar.isVisible = showingView == ShowingView.PROGRESS

        (showingView == ShowingView.CROP).let {
            cropLayout.isVisible = it
            overlayView.isVisible = it
        }
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
