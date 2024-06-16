package org.autojs.autojs.ui.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import org.autojs.autojs.core.ui.widget.JsSwitch;
import org.autojs.autojs.pref.Pref;
import org.autojs.autojs.theme.ThemeColor;
import org.autojs.autojs.theme.ThemeColorHelper;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs.theme.ThemeColorMutable;
import com.tencent.apphelper.R;

/**
 * Created by Stardust on Aug 6, 2017.
 */
public class PrefSwitch extends JsSwitch implements SharedPreferences.OnSharedPreferenceChangeListener, ThemeColorMutable {

    private String mPrefKey;
    private boolean mDefaultChecked;

    public PrefSwitch(Context context) {
        super(context);
        init(null);
    }

    public PrefSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public PrefSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        ThemeColorManager.add(this);
        if (attrs == null)
            return;
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.PrefSwitch);
        mPrefKey = a.getString(R.styleable.PrefSwitch_key);
        mDefaultChecked = a.getBoolean(R.styleable.PrefSwitch_defaultVal, false);
        if (mPrefKey != null) {
            Pref.registerOnSharedPreferenceChangeListener(this);
            readInitialState();
        } else {
            setChecked(mDefaultChecked, false);
        }
        a.recycle();
    }

    public void setThemeColor(ThemeColor color) {
        ThemeColorHelper.setColorPrimary(this, color.colorPrimary);
    }

    private void readInitialState() {
        if (mPrefKey != null) {
            setChecked(Pref.getBoolean(mPrefKey, mDefaultChecked), false);
        }
    }

    private void notifyPrefChanged(boolean isChecked) {
        if (mPrefKey != null) {
            Pref.putBoolean(mPrefKey, isChecked);
        }
    }

    public void setPrefKey(String prefKey) {
        mPrefKey = prefKey;
        Pref.registerOnSharedPreferenceChangeListener(this);
        if (mPrefKey != null) {
            setChecked(Pref.getBoolean(mPrefKey, mDefaultChecked), false);
        }
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        notifyPrefChanged(checked);
    }

    public void setChecked(boolean checked, boolean notifyChange) {
        super.setChecked(checked, notifyChange);
        if (notifyChange) {
            notifyPrefChanged(checked);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mPrefKey != null && mPrefKey.equals(key)) {
            setChecked(Pref.getBoolean(mPrefKey, isChecked()), false);
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        if (visibility == VISIBLE) {
            readInitialState();
        }
    }

}
