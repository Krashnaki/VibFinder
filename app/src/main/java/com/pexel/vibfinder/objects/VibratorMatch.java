package com.pexel.vibfinder.objects;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * This class is merely a holder for the information needed to identify a found vibrator and some
 * additional information interesting to the user.
 */
public class VibratorMatch {
    private final static String TAG = VibratorMatch.class.getSimpleName();

    private String name = "Device Name";
    private String address = "";
    private String lastSeenTime = "00:00";
    private boolean alertEnabled = true;

    public VibratorMatch(String name, String address, long lastSeenTime, boolean alertEnabled) {
        this.name = name;
        this.address = address;
        this.alertEnabled = alertEnabled;
        setLastSeenTime(lastSeenTime);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLastSeenTime() {
        return lastSeenTime;
    }

    public void setLastSeenTime(long time) {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(time);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DATE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        this.lastSeenTime = String.format("%02d:%02d", hour, minute);
    }

    public boolean getAlertEnabled() {
        return alertEnabled;
    }

    public void toggleAlertEnabled() {
        alertEnabled = !alertEnabled;
    }
}
