package com.pr0gramm.donation.vib_finder_pr0;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Objects;

public class VibFinderActivity extends Activity {
    private final static String TAG = VibFinderActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 1;

    boolean doubleBackToExitPressedOnce = false;

    private Button searchButton;
    private Button stopVibrationButton;
    private ListView vibList;

    private ConstraintLayout enableBLEView;
    private TextView BLEstatusTextView;
    private Button enableBLEButton;

    private VibratorListAdapter vibListViewAdapter;

    private VibDBHelper vibDBHelper;

    private VibFinderService vibFinderService;


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "service connected to VibControlActivity");
            vibFinderService = ((VibFinderService.LocalVibFinderServiceBinder) service).getService();
            if (!vibFinderService.getBluetoothEnabled()) {
                showBluetoothDisabledView();
            }

            if (vibFinderService.getAlertActive()) {
                stopVibrationButton.setVisibility(View.VISIBLE);
            } else {
                stopVibrationButton.setVisibility(View.GONE);
            }

            if (vibFinderService.getSearchStarted()) {
                searchButton.setText(getString(R.string.stopSearch));
            } else {
                searchButton.setText(getString(R.string.startSearch));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            vibFinderService = null;
        }
    };

    // Handles various events fired by the Service.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            final String action = intent.getAction();

            switch (action) {
                case VibFinderService.ACTION_BLUETOOTH_DISABLED:
                    showBluetoothDisabledView();
                    break;


                case VibFinderService.ACTION_BLUETOOTH_ENABLED:
                    enableBLEView.setVisibility(View.GONE);
                    break;


                case VibFinderService.ACTION_BLUETOOTH_NOT_USABLE:

                    //TODO Show user error message

                    Log.e(TAG, "Unable to initialize Bluetooth");
                    exitApplication();
                    finish();
                    break;


                case VibFinderService.ACTION_FOUND_VIBRATOR:
                    stopVibrationButton.setVisibility(View.VISIBLE);

                    vibListViewAdapter.clear();
                    for (VibratorMatch vib : vibDBHelper.getAllValidatedMatches()) {
                        vibListViewAdapter.addVibrator(vib);
                    }
                    vibListViewAdapter.notifyDataSetChanged();
                    break;


                case VibFinderService.ACTION_ALERT_STOPPED:
                    stopVibrationButton.setVisibility(View.GONE);
                    break;

                case VibFinderService.ACTION_VIBRATOR_DATA_CHANGED:
                    vibListViewAdapter.clear();
                    for (VibratorMatch vib : vibDBHelper.getAllValidatedMatches()) {
                        vibListViewAdapter.addVibrator(vib);
                    }
                    vibListViewAdapter.notifyDataSetChanged();
                    break;
            }
        }
    };

    private static IntentFilter createGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(VibFinderService.ACTION_BLUETOOTH_DISABLED);
        intentFilter.addAction(VibFinderService.ACTION_BLUETOOTH_NOT_USABLE);
        intentFilter.addAction(VibFinderService.ACTION_FOUND_VIBRATOR);
        intentFilter.addAction(VibFinderService.ACTION_ALERT_STOPPED);
        intentFilter.addAction(VibFinderService.ACTION_VIBRATOR_DATA_CHANGED);
        intentFilter.addAction(VibFinderService.ACTION_BLUETOOTH_ENABLED);
        return intentFilter;
    }

    private void clearUI() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "create");

        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof CustomExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler(
                    Environment.getExternalStorageDirectory() + "/media/development/Vib-Finder", null));
        }

        setContentView(R.layout.activity_vib_finder);


        searchButton = findViewById(R.id.startStopButton);
        stopVibrationButton = findViewById(R.id.stopVibrationButton);
        vibList = findViewById(R.id.vibrators_list);
        BLEstatusTextView = findViewById(R.id.ble_status_text_view);
        enableBLEButton = findViewById(R.id.ble_enable_button);
        enableBLEView = findViewById(R.id.layout_enable_bluetooth);

        vibListViewAdapter = new VibratorListAdapter();
        vibList.addHeaderView(getLayoutInflater().inflate(R.layout.listheader_vibrator, vibList, false));
        vibList.setAdapter(vibListViewAdapter);

        searchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (vibFinderService != null) {
                    if (vibFinderService.getSearchStarted()) {
                        vibFinderService.stopSearch();
                        searchButton.setText(getString(R.string.startSearch));
                    } else {
                        vibFinderService.startSearch();
                        searchButton.setText(getString(R.string.stopSearch));
                    }
                }
            }
        });

        stopVibrationButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (vibFinderService != null) {
                    vibFinderService.stopAlert();
                }
            }
        });

        enableBLEButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (vibFinderService != null) {
                    if (!vibFinderService.enableBluetooth()) {
                        return;
                    }
                    enableBLEButton.setVisibility(View.INVISIBLE);
                    enableBLEView.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.ble_enabling_color));
                    BLEstatusTextView.setText(getString(R.string.enabling_bluetooth));
                }
            }
        });

        vibDBHelper = new VibDBHelper(this);

        Intent vibFinderService = new Intent(this, VibFinderService.class);
        startService(vibFinderService);
        bindService(vibFinderService, mServiceConnection, BIND_AUTO_CREATE);

        Objects.requireNonNull(getActionBar()).setTitle(getString(R.string.title_activity_vib_finder));
        getActionBar().setDisplayHomeAsUpEnabled(false);

    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "resume");

        if (vibFinderService != null && !vibFinderService.getBluetoothEnabled()) {
            showBluetoothDisabledView();
        } else {
            enableBLEView.setVisibility(View.GONE);
        }

        //Enable bluetooth if disabled and stop vibration.
        if (vibFinderService != null) {
            if (vibFinderService.getAlertActive()) {
                stopVibrationButton.setVisibility(View.VISIBLE);
            } else {
                stopVibrationButton.setVisibility(View.GONE);
            }

            if (vibFinderService.getSearchStarted()) {
                searchButton.setText(getString(R.string.stopSearch));
            } else {
                searchButton.setText(getString(R.string.startSearch));
            }
        }

        vibListViewAdapter.clear();
        VibratorMatch[] foundVibrators = vibDBHelper.getAllValidatedMatches();
        for (VibratorMatch vib : foundVibrators) {
            vibListViewAdapter.addVibrator(vib);
        }
        vibListViewAdapter.notifyDataSetChanged();

        registerReceiver(mGattUpdateReceiver, createGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "pause");
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "destroy");
        invalidateOptionsMenu();
        clearUI();
        if (vibFinderService != null) {
            unbindService(mServiceConnection);
        }
        vibFinderService = null;
    }

    private void exitApplication() {
        if (vibFinderService != null) {
            unbindService(mServiceConnection);
        }
        vibFinderService = null;
        Intent vibFinderService = new Intent(this, VibFinderService.class);
        stopService(vibFinderService);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.vib_finder, menu);
        //menu.findItem(R.id.menu_refresh).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_exit:
                exitApplication();
                return true;
//            case android.R.id.home:
//                onBackPressed();
//                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //exitApplication();
                return;
            } else if (resultCode == Activity.RESULT_OK) {
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

//    /**
//     * Creates a dialog demanding activation of bluetooth.
//     */
//    private void demandBluetoothActivation(){
//       Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            exitApplication();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }

    /**
     * This function makes the line in the layout notifying the user that bluetooth is disabled
     * and giving him the chance to enable it visible.
     */
    private void showBluetoothDisabledView() {
        enableBLEButton.setVisibility(View.VISIBLE);
        enableBLEView.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.ble_disabled_color));
        BLEstatusTextView.setText(getString(R.string.bluetooth_disabled));
        enableBLEView.setVisibility(View.VISIBLE);
    }

    /**
     * Handler for clicks on the alert enabled checkbox in the vibratorsList.
     * Will update the list and the database
     */
    public void vibratorAlertEnabledClickHandler(View v) {
        int position = vibList.getPositionForView(v);
        //careful, the value that should be set is ignored is the opposite of the alertEnabled value!
        //additionally this value should be toggeled -> we need to set !!alertEnabled as the new
        //vibrator ignored value
        vibDBHelper.setVibratorIgnored(vibListViewAdapter.getVibrator(position),
                vibListViewAdapter.getVibrator(position).getAlertEnabled());
        vibListViewAdapter.getVibrator(position).toggleAlertEnabled();
        vibListViewAdapter.notifyDataSetChanged();
    }

    /**
     * Help class for the TimerListAdapter
     */
    static class ViewHolder {
        TextView deviceName;
        TextView lastFoundTime;
        CheckBox alertEnabled;
    }

    /**
     * Adapter for holding validated matches, thus found vibrators.
     */
    public class VibratorListAdapter extends BaseAdapter {
        private ArrayList<VibratorMatch> mVibrators;
        private LayoutInflater mInflator;

        public VibratorListAdapter() {
            super();
            mVibrators = new ArrayList<>();
            mInflator = VibFinderActivity.this.getLayoutInflater();
        }

        public void addVibrator(VibratorMatch vibrator) {
            if (!mVibrators.contains(vibrator)) {
                mVibrators.add(vibrator);
            }
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
        public Object getItem(int i) {
            return mVibrators.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_vibrator, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceName = view.findViewById(R.id.list_device_name);
                viewHolder.lastFoundTime = view.findViewById(R.id.list_last_seen_time);
                viewHolder.alertEnabled = view.findViewById(R.id.list_itemUsage);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            VibratorMatch vibrator = mVibrators.get(i);
            String name = vibrator.getName();
            String time = vibrator.getLastSeenTime();
            
            viewHolder.alertEnabled.setChecked(vibrator.getAlertEnabled());
            viewHolder.deviceName.setText(name);
            viewHolder.lastFoundTime.setText(time);

            return view;
        }
    }

}
