package com.pexel.vibfinder;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.pexel.vibfinder.objects.VibratorMatch;
import com.pexel.vibfinder.services.VibFinderService;
import com.pexel.vibfinder.services.VibFinderService.LocalVibFinderServiceBinder;
import com.pexel.vibfinder.util.CustomExceptionHandler;
import com.pexel.vibfinder.util.VibDBHelper;
import com.pexel.vibfinder.util.VibratorListViewAdapter;

import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;

public class VibFinderActivity extends Activity {
    private final static String TAG = VibFinderActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 20;

    boolean doubleBackToExitPressedOnce = false;


    @BindView(R.id.startStopButton)
    Button searchButton;

    @BindView(R.id.stopVibrationButton)
    Button stopVibrationButton;

    @BindView(R.id.vibrators_list)
    ListView vibList;

    @BindView(R.id.layout_enable_bluetooth)
    ConstraintLayout enableBLEView;

    @BindView(R.id.ble_status_text_view)
    TextView BLEstatusTextView;

    @BindView(R.id.ble_enable_button)
    Button enableBLEButton;


    private VibratorListViewAdapter vibListViewAdapter;

    private VibDBHelper vibDBHelper;

    private VibFinderService vibFinderService;


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "service connected to VibControlActivity");
            vibFinderService = ((LocalVibFinderServiceBinder) service).getService();
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

            if (action != null) switch (action) {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "create");

        setContentView(R.layout.activity_vib_finder);
        ButterKnife.bind(this);

        /*
          Ask for Location (needed for Bluetooth)
         */
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    REQUEST_ENABLE_BT);
        }


        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof CustomExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler(
                    Environment.getExternalStorageDirectory() + "/media/development/VibFinder", null));
        }

        vibListViewAdapter = new VibratorListViewAdapter(this.getApplicationContext());
        vibList.addHeaderView(getLayoutInflater().inflate(R.layout.listheader_vibrator, vibList, false));
        vibList.setAdapter(vibListViewAdapter);

        searchButton.setOnClickListener(v -> {
            if (vibFinderService != null) {
                if (vibFinderService.getSearchStarted()) {
                    vibFinderService.stopSearch();
                    searchButton.setText(getString(R.string.startSearch));
                } else {
                    vibFinderService.startSearch();
                    searchButton.setText(getString(R.string.stopSearch));
                }
            }
        });

        stopVibrationButton.setOnClickListener(v -> {
            if (vibFinderService != null) {
                vibFinderService.stopAlert();
            }
        });

        enableBLEButton.setOnClickListener(v -> {
            if (vibFinderService != null) {
                if (!vibFinderService.enableBluetooth()) {
                    return;
                }
                enableBLEButton.setVisibility(View.INVISIBLE);
                enableBLEView.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.ble_enabling_color));
                BLEstatusTextView.setText(getString(R.string.enabling_bluetooth));
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (grantResults.length <= 0
                    || grantResults[0] != PackageManager.PERMISSION_GRANTED)
                finish();
        }
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
        if (vibFinderService != null) {
            unbindService(mServiceConnection);
        }
        vibFinderService = null;
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

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            exitApplication();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
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

}
