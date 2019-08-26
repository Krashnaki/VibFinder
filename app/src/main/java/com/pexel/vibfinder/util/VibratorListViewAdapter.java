package com.pexel.vibfinder.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.pexel.vibfinder.R;
import com.pexel.vibfinder.objects.VibratorMatch;

import java.util.ArrayList;

public class VibratorListViewAdapter extends ArrayAdapter<VibratorMatch> {

    private Context context;
    private ArrayList<VibratorMatch> vibrators;

    public VibratorListViewAdapter(@NonNull Context context) {
        super(context, R.layout.listitem_vibrator);
        this.context = context;
        this.vibrators = new ArrayList<>();
    }

    public void addVibrator(VibratorMatch vibrator) {
        if (!vibrators.contains(vibrator)) {
            vibrators.add(vibrator);
        }
    }

    public VibratorMatch getVibrator(int position) {
        return vibrators.get(position);
    }

    public void clear() {
        vibrators.clear();
    }

    @Override
    public int getCount() {
        return vibrators.size();
    }

    @Override
    public VibratorMatch getItem(int i) {
        return vibrators.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup parent) {
        ViewHolder viewHolder;
        // General ListView optimization code.
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.listitem_vibrator, null);
            viewHolder = new ViewHolder();
            viewHolder.deviceName = view.findViewById(R.id.list_device_name);
            viewHolder.lastFoundTime = view.findViewById(R.id.list_last_seen_time);
            viewHolder.alertEnabled = view.findViewById(R.id.list_itemUsage);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        viewHolder.alertEnabled.setOnClickListener(v -> {

            //careful, the value that should be set is ignored is the opposite of the alertEnabled value!
            //additionally this value should be toggeled -> we need to set !!alertEnabled as the new
            //vibrator ignored value
            new VibDBHelper(context).setVibratorIgnored(getVibrator(i),
                    getVibrator(i).getAlertEnabled());
            getVibrator(i).toggleAlertEnabled();
            notifyDataSetChanged();
        });

        VibratorMatch vibrator = vibrators.get(i);
        String name = vibrator.getName();
        String time = vibrator.getLastSeenTime();

        viewHolder.alertEnabled.setChecked(vibrator.getAlertEnabled());
        viewHolder.deviceName.setText(name);
        viewHolder.lastFoundTime.setText(time);

        return view;
    }

    class ViewHolder {
        TextView deviceName;
        TextView lastFoundTime;
        CheckBox alertEnabled;
    }
}
