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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class VibFinderActivity extends Activity {
    private final static String TAG = VibFinderActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 1;

    boolean doubleBackToExitPressedOnce = false;

    private Button mStartStopButton;
    private Button mStopVibrationButton;
    private ListView mVibratorsList;

    private ConstraintLayout mBLEStatusView;
    private TextView mBLEStatusTextView;
    private Button mBLEEnableButton;

    private VibratorListAdapter mVibratorListAdapter;

    private VibDBHelper mVibDB;

    private VibFinderService mVibFinderService;


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "service connected to VibControlActivity");
            mVibFinderService = ((VibFinderService.LocalVibFinderServiceBinder) service).getService();
            if(!mVibFinderService.getBluetoothEnabled()){
                showBluetoothDisabledView();
            }
            if(mVibFinderService.getAlertActive()){
                mStopVibrationButton.setVisibility(View.VISIBLE);
            }
            else{
                mStopVibrationButton.setVisibility(View.GONE);
            }
            if(mVibFinderService.getSearchStarted()){
                mStartStopButton.setText(getString(R.string.stopSearch));
            }
            else{
                mStartStopButton.setText(getString(R.string.startSearch));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mVibFinderService = null;
        }
    };

    // Handles various events fired by the Service.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(VibFinderService.ACTION_BLUETOOTH_DISABLED.equals(action)){
                //request enable bluetooth again, disable UI
                showBluetoothDisabledView();
            }
            else if(VibFinderService.ACTION_BLUETOOTH_ENABLED.equals(action)){
                mBLEStatusView.setVisibility(View.GONE);
            }
            else if(VibFinderService.ACTION_BLUETOOTH_NOT_USABLE.equals(action)){
                Log.e(TAG, "Unable to initialize Bluetooth");
                exitApplication();
                finish();
            }
            else if(VibFinderService.ACTION_FOUND_VIBRATOR.equals(action)){
                mStopVibrationButton.setVisibility(View.VISIBLE);

                mVibratorListAdapter.clear();
                VibratorMatch foundVibrators[] = mVibDB.getAllValidatedMatches();
                for(VibratorMatch vib: foundVibrators){
                    mVibratorListAdapter.addVibrator(vib);
                }
                mVibratorListAdapter.notifyDataSetChanged();
            }
            else if(VibFinderService.ACTION_ALERT_STOPPED.equals(action)){
                mStopVibrationButton.setVisibility(View.GONE);
            }
            else if(VibFinderService.ACTION_VIBRATOR_DATA_CHANGED.equals(action)){
                //reread the list of vibrators that have been found
                mVibratorListAdapter.clear();
                VibratorMatch foundVibrators[] = mVibDB.getAllValidatedMatches();
                for(VibratorMatch vib: foundVibrators){
                    mVibratorListAdapter.addVibrator(vib);
                }
                mVibratorListAdapter.notifyDataSetChanged();
            }
        }
    };

    private void clearUI() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "create");

        if(!(Thread.getDefaultUncaughtExceptionHandler() instanceof CustomExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler(
                    Environment.getExternalStorageDirectory() + "/media/development/Vib-Finder", null));
        }

        setContentView(R.layout.activity_vib_finder);


        mStartStopButton = (Button)findViewById(R.id.startStopButton);
        mStopVibrationButton = (Button)findViewById(R.id.stopVibrationButton);
        mVibratorsList = (ListView)findViewById(R.id.vibrators_list);
        mBLEStatusTextView = (TextView)findViewById(R.id.ble_status_text_view);
        mBLEEnableButton = findViewById(R.id.ble_enable_button);
        mBLEStatusView = findViewById(R.id.layout_enable_bluetooth);

        mVibratorListAdapter = new VibratorListAdapter();
        mVibratorsList.addHeaderView(getLayoutInflater().inflate(R.layout.listheader_vibrator, mVibratorsList, false));
        mVibratorsList.setAdapter(mVibratorListAdapter);

        mStartStopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v){
                if(mVibFinderService != null){
                    //toggle search status
                    if(mVibFinderService.getSearchStarted()){
                        mVibFinderService.stopSearch();
                        mStartStopButton.setText(getString(R.string.startSearch));
                    }
                    else{
                        mVibFinderService.startSearch();
                        mStartStopButton.setText(getString(R.string.stopSearch));
                    }
                }
            }
        });

        mStopVibrationButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v){
                if(mVibFinderService != null){
                    mVibFinderService.stopAlert();
                }
            }
        });

        mBLEEnableButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if(mVibFinderService != null){
                    if(!mVibFinderService.enableBluetooth()){
                        return;
                    }
                    mBLEEnableButton.setVisibility(View.INVISIBLE);
                    mBLEStatusView.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.ble_enabling_color));
                    mBLEStatusTextView.setText(getString(R.string.enabling_bluetooth));
                }
            }
        });

        mVibDB = new VibDBHelper(this);

        Intent vibFinderService = new Intent(this, VibFinderService.class);
        startService(vibFinderService);
        bindService(vibFinderService, mServiceConnection, BIND_AUTO_CREATE);

        getActionBar().setTitle(getString(R.string.title_activity_vib_finder));
        getActionBar().setDisplayHomeAsUpEnabled(false);

    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "resume");

        if(mVibFinderService != null && !mVibFinderService.getBluetoothEnabled()){
            showBluetoothDisabledView();
        }
        else{
            mBLEStatusView.setVisibility(View.GONE);
        }
        //Enable bluetooth if disabled and stop vibration.
        if(mVibFinderService != null){
            if(mVibFinderService.getAlertActive()){
                mStopVibrationButton.setVisibility(View.VISIBLE);
            }
            else{
                mStopVibrationButton.setVisibility(View.GONE);
            }
            if(mVibFinderService.getSearchStarted()){
                mStartStopButton.setText(getString(R.string.stopSearch));
            }
            else{
                mStartStopButton.setText(getString(R.string.startSearch));
            }
        }

        mVibratorListAdapter.clear();
        VibratorMatch foundVibrators[] = mVibDB.getAllValidatedMatches();
        for(VibratorMatch vib: foundVibrators){
            mVibratorListAdapter.addVibrator(vib);
        }
        mVibratorListAdapter.notifyDataSetChanged();

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
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
        if(mVibFinderService != null) {
            unbindService(mServiceConnection);
        }
        mVibFinderService = null;
    }

    private void exitApplication(){
        if(mVibFinderService != null) {
            unbindService(mServiceConnection);
        }
        mVibFinderService = null;
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
        switch(item.getItemId()) {
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
            if(resultCode == Activity.RESULT_CANCELED){
                //exitApplication();
                return;
            }
            else if(resultCode == Activity.RESULT_OK){
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

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
                doubleBackToExitPressedOnce=false;
            }
        }, 2000);
    }

//    /**
//     * Creates a dialog demanding activation of bluetooth.
//     */
//    private void demandBluetoothActivation(){
//        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//    }

    /**
     * This function makes the line in the layout notifying the user that bluetooth is disabled
     * and giving him the chance to enable it visible.
     */
    private void showBluetoothDisabledView(){
        mBLEEnableButton.setVisibility(View.VISIBLE);
        mBLEStatusView.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.ble_disabled_color));
        mBLEStatusTextView.setText(getString(R.string.bluetooth_disabled));
        mBLEStatusView.setVisibility(View.VISIBLE);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(VibFinderService.ACTION_BLUETOOTH_DISABLED);
        intentFilter.addAction(VibFinderService.ACTION_BLUETOOTH_NOT_USABLE);
        intentFilter.addAction(VibFinderService.ACTION_FOUND_VIBRATOR);
        intentFilter.addAction(VibFinderService.ACTION_ALERT_STOPPED);
        intentFilter.addAction(VibFinderService.ACTION_VIBRATOR_DATA_CHANGED);
        intentFilter.addAction(VibFinderService.ACTION_BLUETOOTH_ENABLED);
        return intentFilter;
    }

    /**
     * Handler for clicks on the alert enabled checkbox in the vibratorsList.
     * Will update the list and the database
     */
    public void vibratorAlertEnabledClickHandler(View v){
        int position = mVibratorsList.getPositionForView(v);
        //careful, the value that should be set is ignored is the opposite of the alertEnabled value!
        //additionally this value should be toggeled -> we need to set !!alertEnabled as the new
        //vibrator ignored value
        mVibDB.setVibratorIgnored(mVibratorListAdapter.getVibrator(position),
                mVibratorListAdapter.getVibrator(position).getAlertEnabled());
        mVibratorListAdapter.getVibrator(position).toggleAlertEnabled();
        mVibratorListAdapter.notifyDataSetChanged();
    }

    /**
     * Adapter for holding validated matches, thus found vibrators.
     */
    public class VibratorListAdapter extends BaseAdapter {
        private ArrayList<VibratorMatch> mVibrators;
        private LayoutInflater mInflator;

        public VibratorListAdapter() {
            super();
            mVibrators = new ArrayList<VibratorMatch>();
            mInflator = VibFinderActivity.this.getLayoutInflater();
        }

        public void addVibrator(VibratorMatch vibrator) {
            if(!mVibrators.contains(vibrator)) {
                mVibrators.add(vibrator);
            }
        }

        public VibratorMatch getVibrator(int position){
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
                viewHolder.deviceName = (TextView) view.findViewById(R.id.list_device_name);
                viewHolder.lastFoundTime = (TextView) view.findViewById(R.id.list_last_seen_time);
                viewHolder.alertEnabled = (CheckBox) view.findViewById(R.id.list_itemUsage);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            VibratorMatch vibrator = mVibrators.get(i);
            String name = vibrator.getName();
            String time = vibrator.getLastSeenTime();
            boolean alertEnabled = vibrator.getAlertEnabled();
            viewHolder.alertEnabled.setChecked(alertEnabled);
            viewHolder.deviceName.setText(name);
            viewHolder.lastFoundTime.setText(time);

            return view;
        }
    }

    /**
     * Help class for the TimerListAdapter
     */
    static class ViewHolder {
        TextView deviceName;
        TextView lastFoundTime;
        CheckBox alertEnabled;
    }

}
