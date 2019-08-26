package com.pexel.vibfinder;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.pexel.vibfinder.objects.VibratorMatch;
import com.pexel.vibfinder.util.VibDBHelper;

import java.util.ArrayList;

/**
 * Adapter for holding validated matches, thus found vibrators.
 */
public class VibratorListAdapter extends ArrayAdapter<VibratorMatch> {

    private ArrayList<VibratorMatch> mVibrators;
    private Context context;

    public VibratorListAdapter(Context context) {
        super(context, R.layout.listitem_vibrator);
        this.context = context;
        this.mVibrators = new ArrayList<>();
    }

    public void addVibrator(VibratorMatch vibrator) {
        if (!mVibrators.contains(vibrator)) {
            mVibrators.add(vibrator);
        }
        notifyDataSetChanged();
    }

    public VibratorMatch getVibrator(int position) {
        return mVibrators.get(position);
    }

    public void clear() {
        mVibrators.clear();
    }

    @Override
    public int getCount() {
        return mVibrators.size();
    }

    @Override
    public VibratorMatch getItem(int i) {
        return mVibrators.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        ViewHolder viewHolder;

        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.listitem_vibrator, parent);
            viewHolder = new ViewHolder();
            viewHolder.deviceName = view.findViewById(R.id.list_device_name);
            viewHolder.lastFoundTime = view.findViewById(R.id.list_last_seen_time);
            viewHolder.alertEnabled = view.findViewById(R.id.list_itemUsage);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        viewHolder.alertEnabled.setOnClickListener(v -> {
            VibratorMatch vibrator = getVibrator(position);
            new VibDBHelper(v.getContext()).setVibratorIgnored(vibrator, vibrator.getAlertEnabled());
            vibrator.toggleAlertEnabled();
            this.notifyDataSetChanged();
        });

        VibratorMatch vibrator = mVibrators.get(position);
        String name = vibrator.getName();
        String time = vibrator.getLastSeenTime();

        viewHolder.alertEnabled.setChecked(vibrator.getAlertEnabled());
        viewHolder.deviceName.setText(name);
        viewHolder.lastFoundTime.setText(time);

        return view;
    }

    private class ViewHolder {
        TextView deviceName;
        TextView lastFoundTime;
        CheckBox alertEnabled;
    }


}