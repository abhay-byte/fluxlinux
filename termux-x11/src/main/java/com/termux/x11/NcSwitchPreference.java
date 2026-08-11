package com.termux.x11;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

/**
 * SwitchPreference with NativeCode cyber-brutalist thumb/track (matches app CyberToggle).
 * Clears Material tints that otherwise paint stock round switches.
 */
public class NcSwitchPreference extends SwitchPreferenceCompat {

    public NcSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public NcSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public NcSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NcSwitchPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setWidgetLayoutResource(R.layout.preference_widget_switch_nc);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View widget = holder.findViewById(R.id.switchWidget);
        if (!(widget instanceof SwitchCompat))
            return;
        SwitchCompat sw = (SwitchCompat) widget;
        Context ctx = getContext();

        // Kill Material/AppCompat color filters that recolor our shapes wrong
        sw.setTrackTintList(null);
        sw.setThumbTintList(null);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            sw.setBackgroundTintList(null);
        }

        Drawable track = AppCompatResources.getDrawable(ctx, R.drawable.nc_switch_track);
        Drawable thumb = AppCompatResources.getDrawable(ctx, R.drawable.nc_switch_thumb);
        if (track != null) {
            track = DrawableCompat.wrap(track.mutate());
            DrawableCompat.setTintList(track, null);
            sw.setTrackDrawable(track);
        }
        if (thumb != null) {
            thumb = DrawableCompat.wrap(thumb.mutate());
            DrawableCompat.setTintList(thumb, null);
            sw.setThumbDrawable(thumb);
        }
        sw.setShowText(false);
        // Prevent split-track material look
        sw.setSplitTrack(false);
    }
}
