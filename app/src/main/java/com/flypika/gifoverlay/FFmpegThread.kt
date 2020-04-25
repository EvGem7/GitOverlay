package com.flypika.gifoverlay

import android.os.Handler
import android.os.HandlerThread

object FFmpegThread : HandlerThread(FFmpegThread::class.java.name) {

    private lateinit var handler: Handler

    init {
        start()
    }

    override fun onLooperPrepared() {
        handler = Handler()
    }

    fun run(block: () -> Unit) {
        while (!this::handler.isInitialized) {}
        handler.post { block() }
    }
}
