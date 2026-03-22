package com.hension.havenx

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import coil.load
import java.io.File

class ImagePreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        val filePath = intent.getStringExtra("FILE_PATH") ?: return finish()
        val remotePath = intent.getStringExtra("REMOTE_PATH") ?: filePath

        supportActionBar?.title = remotePath.substringAfterLast('/')
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<ImageView>(R.id.imageView).load(File(filePath)) {
            crossfade(true)
            error(android.R.drawable.stat_notify_error)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
