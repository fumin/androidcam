package com.topunion.camera

import android.annotation.SuppressLint
import android.app.Activity
import android.util.TypedValue
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ConfigPage {
    lateinit var contentView: ScrollView
    lateinit var cameraIDInput: EditText
    lateinit var serverInput: EditText
    lateinit var okBtn: Button

    companion object {
        @SuppressLint("SetTextI18n")
        fun new(activity: Activity, cfg: Config): ConfigPage {
            val page = ConfigPage()
            page.contentView = ScrollView(activity)
            val layout = LinearLayout(activity)
            layout.orientation = LinearLayout.VERTICAL
            page.contentView.addView(layout)

            val cameraIDLabel = TextView(activity)
            layout.addView(cameraIDLabel)
            val clientSize = Util.getClientSize(activity)
            cameraIDLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 10).toFloat())
            cameraIDLabel.text = "Camera name"

            page.cameraIDInput = EditText(activity)
            layout.addView(page.cameraIDInput)
            page.cameraIDInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 32).toFloat())
            page.cameraIDInput.setText(cfg.cameraID)

            val serverLabel = TextView(activity)
            layout.addView(serverLabel)
            serverLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 10).toFloat())
            serverLabel.text = "Server"

            page.serverInput = EditText(activity)
            layout.addView(page.serverInput)
            page.serverInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 32).toFloat())
            page.serverInput.setText(cfg.uploadPath)

            val blank = TextView(activity)
            layout.addView(blank)
            blank.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 10).toFloat())

            page.okBtn = Button(activity)
            layout.addView(page.okBtn)
            page.okBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 10).toFloat())
            page.okBtn.text = "OK"
            return page
        }
    }
}