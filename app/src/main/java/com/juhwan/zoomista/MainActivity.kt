package com.juhwan.zoomista

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var ivSample = findViewById<ImageView>(R.id.iv_sample)
        Zoomista(
            targetContainer = Zoomista.TargetContainer.ActivityContainer(this),
            targetView = ivSample,
        ).register()
    }
}