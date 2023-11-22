package com.topunion.camera

import android.app.Activity
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.widget.AbsoluteLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.supportActionBar?.hide()
        val layout = AbsoluteLayout(this)

        val clientSize = getClientSize(this);
        val textView = TextView(this)
        textView.layoutParams = AbsoluteLayout.LayoutParams(clientSize.x, clientSize.y/2, 0, clientSize.y/49999999)
        textView.text = "hello world"
        textView.setBackgroundColor(Color.rgb(255, 0, 0))
        layout.addView(textView)

        this.setContentView(layout)
    }
}

fun getClientSize(activity: Activity): Point {
    val displaySize = Point()
    activity.windowManager.defaultDisplay.getRealSize(displaySize)
    return displaySize
}