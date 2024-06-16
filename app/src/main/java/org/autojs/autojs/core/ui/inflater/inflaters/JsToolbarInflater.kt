package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsToolbar
import com.tencent.apphelper.R

/**
 * Created by Stardust on Nov 5, 2017.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class JsToolbarInflater(resourceParser: ResourceParser) : ToolbarInflater<JsToolbar>(resourceParser) {

    override fun getCreator() = object : ViewCreator<JsToolbar> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsToolbar {
            return View.inflate(context, R.layout.js_toolbar, null) as JsToolbar
        }
    }

}