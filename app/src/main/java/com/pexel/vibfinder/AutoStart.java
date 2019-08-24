package com.pexel.vibfinder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Class that is only used to handle the autostart. The onReceive method will be called after the
 * phone's boot has finished.
 */
public class AutoStart extends BroadcastReceiver {
    private final static String TAG = AutoStart.class.getSimpleName();

    /**
     * This method will be called by the Android system after the phone's boot has finished.
     * It will then start the VibFinderService and let it know via an Extra in the intent, that it
     * has been started automatically by the autostart function.
     */
    public void onReceive(Context context, Intent i) {
        Intent intent = new Intent(context, VibFinderService.class);
        intent.putExtra(VibFinderService.EXTRA_SERVICE_START_MODE, context.getString(R.string.start_mode_autostart));
        context.startService(intent);
        Log.i(TAG, "started service VibFinderService in AutoStart");
    }
}
