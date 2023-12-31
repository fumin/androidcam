package com.topunion.camera

import android.annotation.SuppressLint
import android.graphics.Color.WHITE
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

class StatusPage {
    lateinit var contentView: ScrollView
    lateinit var zll: ZoomLinearLayout
    lateinit var ll: LinearLayout

    lateinit var cmdInput: EditText
    private lateinit var cameraIDInput: TextView
    private lateinit var serverInput: TextView
    private lateinit var videosPendingInput: TextView
    var uploadErrViews = emptyArray<TextView>()

    companion object {
        @SuppressLint("SetTextI18n")
        fun new(activity: MainActivity): StatusPage {
            // Use the dark mode theme.
            val typedValue = TypedValue();
            activity.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true);
            val colorBackground = ContextCompat.getColor(activity, typedValue.resourceId)

            val sp = StatusPage()
            sp.contentView = ScrollView(activity)
            sp.contentView.setBackgroundColor(colorBackground)

            sp.zll = ZoomLinearLayout(activity)
            sp.contentView.addView(sp.zll)

            sp.ll = LinearLayout(activity)
            sp.ll.orientation = LinearLayout.VERTICAL
            sp.zll.addView(sp.ll)

            sp.cmdInput = EditText(activity)
            sp.ll.addView(sp.cmdInput)
            val clientSize = Util.getClientSize(activity)
            sp.cmdInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 32).toFloat())
            sp.cmdInput.inputType = EditorInfo.TYPE_CLASS_TEXT
            sp.cmdInput.setLines(1)
            sp.cmdInput.imeOptions = EditorInfo.IME_ACTION_DONE

            val cameraIDLabel = TextView(activity)
            sp.ll.addView(cameraIDLabel)
            cameraIDLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 10).toFloat())
            cameraIDLabel.text = "Camera name"

            sp.cameraIDInput = TextView(activity)
            sp.ll.addView(sp.cameraIDInput)
            sp.cameraIDInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 32).toFloat())

            val serverLabel = TextView(activity)
            sp.ll.addView(serverLabel)
            serverLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 10).toFloat())
            serverLabel.text = "Server"

            sp.serverInput = TextView(activity)
            sp.ll.addView(sp.serverInput)
            sp.serverInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 32).toFloat())

            val videosPendingLabel = TextView(activity)
            sp.ll.addView(videosPendingLabel)
            videosPendingLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 10).toFloat())
            videosPendingLabel.text = "Videos pending"

            sp.videosPendingInput = TextView(activity)
            sp.ll.addView(sp.videosPendingInput)
            sp.videosPendingInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.y/ 32).toFloat())

            for (i in 0..2) {
                val v = TextView(activity)
                sp.uploadErrViews += v
                sp.ll.addView(v)
                v.setTextSize(TypedValue.COMPLEX_UNIT_PX, (clientSize.x/ 40).toFloat())
                v.visibility = View.GONE
            }

            return sp
        }
    }

    fun update(cfg: Config, videosPending: Int, uploadErrs: Array<UploadError>) {
        this.cameraIDInput.text = cfg.cameraID
        this.serverInput.text = cfg.uploadPath
        this.videosPendingInput.text = "%d".format(videosPending)

        for (v in this.uploadErrViews) {
            v.visibility = View.GONE
            v.text = ""
        }
        for ((i, ue) in uploadErrs.withIndex()) {
            if (i >= this.uploadErrViews.size) {
                break
            }
            val v = this.uploadErrViews[i]
            v.visibility = View.VISIBLE
            v.text = ue.err
        }
    }
}