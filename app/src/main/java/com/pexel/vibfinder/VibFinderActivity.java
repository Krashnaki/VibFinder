package com.pexel.vibfinder;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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

import com.pexel.vibfinder.api.APIUtils;
import com.pexel.vibfinder.api.IAPI;
import com.pexel.vibfinder.api.ResponseInterceptor;
import com.pexel.vibfinder.objects.ReportDevice;
import com.pexel.vibfinder.objects.ResponseMessage;
import com.pexel.vibfinder.objects.Update;
import com.pexel.vibfinder.objects.VibratorMatch;
import com.pexel.vibfinder.services.VibFinderService;
import com.pexel.vibfinder.services.VibFinderService.LocalVibFinderServiceBinder;
import com.pexel.vibfinder.util.CustomExceptionHandler;
import com.pexel.vibfinder.util.DialogBuilder;
import com.pexel.vibfinder.util.VibDBHelper;
import com.pexel.vibfinder.util.VibratorListViewAdapter;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class VibFinderActivity extends Activity implements Callback<Update> {
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
    boolean alreadyDismissd = false;
    private VibratorListViewAdapter vibListViewAdapter;
    private VibDBHelper vibDBHelper;
    private VibFinderService vibFinderService;
    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            vibFinderService = ((LocalVibFinderServiceBinder) service).getService();
            if (!vibFinderService.getBluetoothEnabled()) {
                showBluetoothDisabledView();
            }

            if (vibFinderService.getAlertActive()) {
                Log.d(TAG, "onServiceConnected: Alert active");
                stopVibrationButton.setVisibility(View.VISIBLE);
            } else {
                Log.d(TAG, "onServiceConnected: Alert inactive");
                stopVibrationButton.setVisibility(View.GONE);
            }

            if (vibFinderService.getSearchStarted()) {
                Log.d(TAG, "onServiceConnected: Search running");
                searchButton.setText(getString(R.string.stopSearch));
            } else {
                Log.d(TAG, "onServiceConnected: Search stopped");
                searchButton.setText(getString(R.string.startSearch));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisconnected");
            vibFinderService = null;
        }
    };
    // Handles various events fired by the Service.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive");

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

    public void ensureReportDevice(VibratorMatch device) {

        Log.d(TAG, "ensureReportDevice");

        DialogBuilder.start(this)
                .title(getString(R.string.report_device_title))
                .content(getString(R.string.report_device_text))
                .positive("Yes", dialog -> reportDevice(device))
                .cancelable()
                .build().show();
    }

    private boolean reportDevice(VibratorMatch device) {

        Log.d(TAG, "reportDevice");

        IAPI iapi = APIUtils.getApiInterface();

        ReportDevice reportDevice = new ReportDevice(device.getAddress(),
                device.getName(),
                new ArrayList<>());

        iapi.reportDevice(reportDevice)
                .enqueue(new Callback<ResponseMessage>() {
                    @Override
                    public void onResponse(Call<ResponseMessage> call, Response<ResponseMessage> response) {

                        if (response.isSuccessful()) {
                            DialogBuilder.start(VibFinderActivity.this)
                                    .title(getString(R.string.report_device_successfull))
                                    .cancelable()
                                    .positive()
                                    .build().show();
                        } else {
                            DialogBuilder.start(VibFinderActivity.this)
                                    .title(getString(R.string.report_device_failed))
                                    .cancelable()
                                    .positive()
                                    .build().show();
                        }
                        //TODO Show snackbar or something
                    }

                    @Override
                    public void onFailure(Call<ResponseMessage> call, Throwable t) {
                        //TODO Show snackbar or something
                    }
                });

        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_vib_finder);
        ButterKnife.bind(this);

        /*
          Ask for Location (needed for Bluetooth)
         */
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onCreate: Ask for permission");
            ActivityCompat.requestPermissions(VibFinderActivity.this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_ENABLE_BT);
        }
        Log.d(TAG, "onCreate: Asked for permission");


        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof CustomExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler(
                    Environment.getExternalStorageDirectory() + "/media/development/VibFinder", null));
        }
        Log.d(TAG, "onCreate: Set custom exception handler");

        vibListViewAdapter = new VibratorListViewAdapter(this, this.getApplicationContext());
        //vibList.addHeaderView(getLayoutInflater().inflate(R.layout.listheader_vibrator, vibList, false));
        vibList.setOnItemClickListener((adapterView, view, i, l) -> {
            reportDevice(vibListViewAdapter.getVibrator(i));
            //return true;
        });
        vibList.setAdapter(vibListViewAdapter);

        Log.d(TAG, "onCreate: list set up");


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
        Log.d(TAG, "onCreate: Set seach button click listener");


        stopVibrationButton.setOnClickListener(v -> {
            if (vibFinderService != null) {
                vibFinderService.stopAlert();
            }
        });
        Log.d(TAG, "onCreate: Set alert button click listener");


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
        Log.d(TAG, "onCreate: Set enable bluetooth button click listener");


        vibDBHelper = new VibDBHelper(this);
        Log.d(TAG, "onCreate: Instantiated database helper");

        Intent vibFinderService = new Intent(this, VibFinderService.class);
        startService(vibFinderService);
        bindService(vibFinderService, mServiceConnection, BIND_AUTO_CREATE);
        Log.d(TAG, "onCreate: Bound to service");

        Objects.requireNonNull(getActionBar()).setTitle(getString(R.string.title_activity_vib_finder));
        getActionBar().setDisplayHomeAsUpEnabled(false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult: " + requestCode);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (grantResults.length <= 0
                    || grantResults[0] != PackageManager.PERMISSION_GRANTED && !alreadyDismissd) {

                alreadyDismissd = true;

                DialogBuilder.start(this)
                        .content(getString(R.string.location_permission_explanation))
                        .positive("OK", v ->
                                ActivityCompat.requestPermissions(VibFinderActivity.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        REQUEST_ENABLE_BT))
                        .cancelable()
                        .onCancel(v -> finish())
                        .build().show();
            } else {
                //finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume");

        if (vibFinderService != null && !vibFinderService.getBluetoothEnabled())
            showBluetoothDisabledView();
        else
            enableBLEView.setVisibility(View.GONE);

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

        checkForUpdate();
    }

    private void checkForUpdate() {
        Log.d(TAG, "checkForUpdate");

        SharedPreferences preferences = getSharedPreferences("VIBFINDER_UPDATE", MODE_PRIVATE);
        GregorianCalendar currentTime = new GregorianCalendar();

        long lastUpdateCheck = preferences.getLong("UPDATE_LAST_CHECK", 0);

        Log.d(TAG, "checkForUpdate: Last update check = " + lastUpdateCheck);
        Log.d(TAG, "checkForUpdate: Current time - 1h = " + (currentTime.getTimeInMillis() - 60 * 60 * 1000));

        if ((currentTime.getTimeInMillis() - 60 * 60 * 1000) > lastUpdateCheck || BuildConfig.DEBUG) {

            IAPI iapi = APIUtils.getApiInterface();

            iapi.getLatestVersion().enqueue(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
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

    @Override
    public void onResponse(Call<Update> call, Response<Update> response) {
        Log.d(TAG, "checkForUpdate$onResponse");

        GregorianCalendar currentTime = new GregorianCalendar();
        SharedPreferences preferences = getSharedPreferences("VIBFINDER_UPDATE", MODE_PRIVATE);

        preferences.edit().
                putLong("UPDATE_LAST_CHECK", currentTime.getTimeInMillis())
                .apply();

        if (response.isSuccessful() && response.body() != null) {
            Update update = response.body();
            long lastAnnouncement = preferences.getLong("UPDATE_LAST_ANNOUNCEMENT", 0);

            Log.d(TAG, "checkForUpdate$onResponse: Current versioncode = " + BuildConfig.VERSION_CODE);
            Log.d(TAG, "checkForUpdate$onResponse: New versioncode = " + update.getBuildNumber());
            Log.d(TAG, "checkForUpdate$onResponse: Last Announcement = " + lastAnnouncement);
            Log.d(TAG, "checkForUpdate$onResponse: Current time = " + currentTime.getTimeInMillis());

            if (update.getBuildNumber() > BuildConfig.VERSION_CODE &&
                    (currentTime.getTimeInMillis() - 60 * 60 * 1000) > lastAnnouncement) {

                DialogBuilder.start(VibFinderActivity.this)
                        .content(getString(R.string.update_announcement_text))
                        .positive()
                        .cancelable()
                        .build().show();

                preferences.edit()
                        .putLong("UPDATE_LAST_ANNOUNCEMENT",
                                currentTime.getTimeInMillis())
                        .apply();
            }
        } else {
            try {
                JSONObject jObjError = null;
                if (response.errorBody() != null) {
                    jObjError = new JSONObject(response.errorBody().string());
                }
                Log.d(TAG,
                        "checkForUpdate$onResponse: errorbody " + jObjError);
            } catch (Exception ignored) {
            }
        }


    }

    @Override
    public void onFailure(Call<Update> call, Throwable t) {
        Log.e(TAG, "checkForUpdate$onFailure", t);
        //Do nothing for now
    }
}
