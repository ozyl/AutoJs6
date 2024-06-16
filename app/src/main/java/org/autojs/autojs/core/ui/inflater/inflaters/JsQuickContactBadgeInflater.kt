package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsQuickContactBadge
import com.tencent.apphelper.R

class JsQuickContactBadgeInflater(resourceParser: ResourceParser) : QuickContactBadgeInflater<JsQuickContactBadge>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsQuickContactBadge> = object : ViewCreator<JsQuickContactBadge> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsQuickContactBadge {
            return (View.inflate(context, R.layout.js_quickcontactbadge, null) as JsQuickContactBadge).apply {
                setImageToDefault()
            }
        }
    }

}