package com.pr0gramm.donation.vib_finder_pr0;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_FIRST_MATCH;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_MATCH_LOST;

public class VibFinderService extends Service {
    public final static String ACTION_BLUETOOTH_NOT_USABLE =
            "com.pr0gramm.donation.vib-finder.ACTION_BLUETOOTH_NOT_USABLE";
    public final static String ACTION_BLUETOOTH_DISABLED =
            "com.pr0gramm.donation.vib-finder.ACTION_BLUETOOTH_DISABLED";
    public final static String ACTION_BLUETOOTH_ENABLED =
            "com.pr0gramm.donation.vib-finder.ACTION_BLUETOOTH_ENABLED";
    public final static String ACTION_ALERT_STOPPED =
            "com.pr0gramm.donation.vib-finder.ACTION_ALERT_STOPPED";
    public final static String ACTION_VIBRATOR_DATA_CHANGED =
            "com.pr0gramm.donation.vib-finder.ACTION_VIBRATOR_DATA_CHANGED";
    public final static String ACTION_FOUND_VIBRATOR =
            "com.pr0gramm.donation.vib-finder.ACTION_FOUND_VIBRATOR";
    public final static String EXTRA_DEVICE_NAME =
            "com.pr0gramm.donation.vib-finder.EXTRA_DEVICE_NAME";
    public final static String EXTRA_SERVICE_START_MODE =
            "com.pr0gramm.donation.vib-finder.EXTRA_SERVICE_START_MODE";
    private final static String TAG = VibFinderService.class.getSimpleName();
    private final static int DISCONNECTED = 0;
    private final static int CONNECTING = 1;
    private final static int CONNECTED = 2;

    private final static int MIN_ALERT_INTERVAL = 30000;//3600000; //time in ms that has to pass so that an already alerted device can be alerted again.
    private final static int VIBRATION_TIME = 10000; //time in ms, that the phone's vibrator is allowed to vibrate max. in case a vibrator has been found
    private final static int ALARM_INTERVAL = 60 * 1000; //interval in ms in which the alarm will be triggered
    private final static int MIN_SEND_TO_SERVER_INTERVAL = 3600000; //time in ms that has to pass so that an already sent match will be sent to the server again
    private final static int LOCATION_SEARCH_TIME = 60000; //time in ms that the phone will search for its current location before sending a found vibrator to the server
    private final static String SEARCHED_MANUFACTURER_NAME = "Amor AG";
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private static VibFinderService mSelfService = null;
    private final IBinder mBinder = new LocalVibFinderServiceBinder();
    BluetoothDevice mCurrentDevice;
    LocationManager locationManager;
    private BluetoothAdapter mBluetoothAdapter; //only needed to retrieve the ScanAdapter
    private BluetoothLeScanner mLeScanner; //needed to perform the LE Scan
    private boolean mScanning;
    private Vibrator mVibrator; //device vibrator used to make the phone vibrate
    private boolean mAlertActive = false; //is the phone currently vibrating
    private boolean mSearchEnabled = false; //is the search active = is the cron job active
    private Alarm mAlarm = new Alarm(); //used to create a cron job like task
    //stuff related to the check if it is a vibrator or not
    private BluetoothGattService mAmorVibService;
    private BluetoothGattCharacteristic mManufacturerNameChara;
    private String mManufacturerName = "";
    private Handler mHandler; //used to stop the search and vibration after a defined time
    private List<BluetoothDevice> mScanMatches = new ArrayList<>();
    private VibDBHelper mVibDB; //accessing the DB with validated and rejected scanMatches
    private String requiredAdvServices[] = {VibGattAttributes.ARMOR_VIB_SERVICE, VibGattAttributes.BATTERY_SERVICE};
    private int mConnectionState = DISCONNECTED;
    private BluetoothLeService mBluetoothLeService;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                //unbindService(mServiceConnection);
                //mBluetoothLeService = null;
                broadcastUpdate(ACTION_BLUETOOTH_NOT_USABLE);
                stopSelf();
                return;
            }
            //automatically starts connecting to already found matches if they exist
            resetForNewDeepTest();
            deepCheckMatches();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };
    //for gps
    private Location mCurrentLocation = null;
    LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            // Called when a new location is found by the network location provider.
            mCurrentLocation = location;
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onProviderDisabled(String provider) {
        }
    };
    private boolean mIsGettingLocation = false;
    private List<BluetoothDevice> mFoundVibrators = Collections.synchronizedList(new LinkedList<BluetoothDevice>());
    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnectionState = CONNECTED;
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnectionState = DISCONNECTED;
                //check next found matches, if possible
                resetForNewDeepTest();
                deepCheckMatches();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                boolean success = checkServices(mBluetoothLeService.getSupportedGattServices());
                if (!success) {
                    Log.d(TAG, "Not all necessary services found, disconnecting device");
                    mVibDB.addDiscardedMatch(mCurrentDevice);
                    mScanMatches.remove(mCurrentDevice);
                    mBluetoothLeService.disconnect();
                    return;
                }
                //start reading the necessary fields to determine, if it is a vibrator or not
                else {
                    Log.d(TAG, "Found all necessary services");
                    readManufacturerName();
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                handleDataReception(intent);
            } else if (ACTION_BLUETOOTH_DISABLED.equals(action)) {
                Log.d(TAG, "Bluetooth disabled");
                broadcastUpdate(ACTION_BLUETOOTH_DISABLED);
                mConnectionState = DISCONNECTED;
                resetForNewDeepTest();
            }
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        //only broadcast BLUETOOTH_DISABLED when the adapter is really turned off
                        broadcastUpdate(ACTION_BLUETOOTH_DISABLED);
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        //already cancel a possible running deepCheck as the BLE Adapter isn't turned on any more
                        mConnectionState = DISCONNECTED;
                        resetForNewDeepTest();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        if (mLeScanner == null && mBluetoothAdapter != null) {
                            mLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
                            if (mLeScanner == null) {
                                Log.d(TAG, "Unable to obtain bluetooth scanner");
                                broadcastUpdate(ACTION_BLUETOOTH_NOT_USABLE);
                                stopSelf();
                                return;
                            }
                        }
                        //start checking possible matches again
                        resetForNewDeepTest();
                        deepCheckMatches();
                        broadcastUpdate(ACTION_BLUETOOTH_ENABLED);
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            }
        }
    };
    /**
     * Callback function that will be called if a BLE Device whose advertised characteristics match
     * the criteria given in the filter has been found. This function will then pass it to
     * the Function handleFoundScanMatches to process the match.
     */
    private ScanCallback mLeScanCallback =
            new android.bluetooth.le.ScanCallback() {

                @Override
                public void onScanResult(int callbackType, final ScanResult result) {
                    Log.d(TAG, "onScanResult");
                    if (callbackType == CALLBACK_TYPE_ALL_MATCHES || callbackType == CALLBACK_TYPE_FIRST_MATCH) {
                        handleFoundScanMatch(result.getDevice());
                    } else if (callbackType == CALLBACK_TYPE_MATCH_LOST) {
                        mScanMatches.remove(result.getDevice());
                    }
                }
            };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        return intentFilter;
    }

    /**
     * This function initializes all the service and characteristic variables.
     * If the remote device doesn't support all necessary services and characteristics, this function
     * will return false, true otherwise.
     *
     * @param services List of supported services
     * @return True if all necessary services and characteristics are available, false otherwise
     */
    private boolean checkServices(List<BluetoothGattService> services) {
        for (BluetoothGattService s : services) {
            String uuid = s.getUuid().toString();
            if (uuid.equals(VibGattAttributes.DEVICE_INFORMATION_SERVICE)) {
                for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                    if (c.getUuid().toString().equals(VibGattAttributes.MANUFACTURER_NAME_CHARA)) {
                        mManufacturerNameChara = c;
                    }
                }
            } else if (uuid.equals(VibGattAttributes.ARMOR_VIB_SERVICE)) {
                mAmorVibService = s;
            }
        }
        if (mManufacturerNameChara == null || mAmorVibService == null) {
            return false;
        }
        return true;
    }

    /**
     * This function handles the data reception from the remote device.
     * This means that it will store the values of the characteristics that are interesting in order
     * to determine if the device is a vibrator or not in variables such as mManufacturerName
     *
     * @param intent The intent that has been received by the broadcastReceiver and that has been identified as a dataReception.
     */
    private void handleDataReception(Intent intent) {
        Log.d(TAG, "handleDataReception");
        //characteristic UUID of the characteristic written to
        String cUuid = intent.getStringExtra(BluetoothLeService.CHARA_UUID);
        //service UUID of the characteristic written to
        String sUuid = intent.getStringExtra(BluetoothLeService.SERVICE_UUID);
        //Data written to the characteristic
        byte[] data = intent.getByteArrayExtra(BluetoothLeService.CHARA_BYTE_ARRAY);
        //Select action based on the Service, that was written to
        Log.d(TAG, "sUuid: " + sUuid + "; cUuid: " + cUuid);
        if (sUuid.equals(VibGattAttributes.DEVICE_INFORMATION_SERVICE)) {
            //Select action base on the characteristic, that was written to
            if (cUuid.equals(VibGattAttributes.MANUFACTURER_NAME_CHARA)) {
                mManufacturerName = new String(data);
                Log.d(TAG, "Manufacturer Name: " + mManufacturerName);
                //// TODO: 29.11.2016 if there were more characteristics to check, do that here
                finishDeepCheck();
            }
        }
    }

    /**
     * This function starts/stops scanning LE Devices around the Phone for possible vibrators
     *
     * @param enable true: start scanning; false: stop scanning
     */
    private void scanLeDevice(final boolean enable) {
        if (mLeScanner == null || mBluetoothLeService == null || !mBluetoothLeService.getBluetoothEnabled()) {
            return;
        }
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mLeScanner.stopScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;

            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
            List<ScanFilter> filterList = new ArrayList<>();
            for (String s : requiredAdvServices) {
                filterList.add(new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(s)).build());
            }
            mLeScanner.startScan(filterList, settings, mLeScanCallback);
        } else {
            mScanning = false;
            mLeScanner.stopScan(mLeScanCallback);
        }
    }

    /**
     * This function can handle a found scan match, that means it will be called once a BLE
     * Device whose advertised services match the criteria in the filter.
     * This function will check if the device is already known. If it is known and validated, it
     * will simply pass it to the handleFoundValidatedMatch() function, if it is not know yet, the
     * function will add the device to the scanMatches and let it be deeply checked = its
     * characteristics be read.
     *
     * @param device the BluetoothDevice that has been found
     */
    private void handleFoundScanMatch(BluetoothDevice device) {
        if (!mVibDB.getDiscardedMatchesContains(device) &&
                !mVibDB.getValidatedMatchesContains(device) && !mScanMatches.contains(device)) {
            mScanMatches.add(device);
            resetForNewDeepTest();
            deepCheckMatches();
        } else if (mVibDB.getValidatedMatchesContains(device)) {
            handleFoundValidatedMatch(device);
        }
    }

    /**
     * This function causes the alert of the found match. It will make the phone vibrate.
     *
     * @param device The found match that should be alerted.
     */
    private void alertMatch(BluetoothDevice device) {
        final Intent intent = new Intent(ACTION_FOUND_VIBRATOR);
        intent.putExtra(EXTRA_DEVICE_NAME, device.getName());
        sendBroadcast(intent);
        long pattern[] = {0, 200, 200, 200, 200, 200, 200, 800, 200};
        mVibrator.vibrate(pattern, 0);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopAlert();
            }
        }, VIBRATION_TIME);
        //open activity
        Intent dialogIntent = new Intent(this, VibFinderActivity.class);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(dialogIntent);
        mAlertActive = true;
    }

    /**
     * This Function handles the mLastAlertTime variable and alerts the found device if the time is
     * already right and the device notifications are enabled.
     *
     * @param device the validated match that has been found
     */
    private void handleFoundValidatedMatch(BluetoothDevice device) {
        GregorianCalendar currentTime = new GregorianCalendar();
        long lastAlertedTime = mVibDB.getLastAlertTime(device);
        if (currentTime.getTimeInMillis() >= MIN_ALERT_INTERVAL + lastAlertedTime &&
                !mVibDB.getVibratorIgnored(device)) {
            mVibDB.setLastAlertTime(device, currentTime.getTimeInMillis());
            alertMatch(device);
        }
        //send match to server
        if (currentTime.getTimeInMillis() >= MIN_ALERT_INTERVAL + lastAlertedTime) {
            mFoundVibrators.add(device);
            Log.d(TAG, "added Vibrator to mFoundVibrators: address: " + device.getAddress() + " name: " + device.getName());
            if (!mIsGettingLocation) {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    if (Build.VERSION.SDK_INT < 23 ||
                            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                        mIsGettingLocation = true;
                    } else {
                        sendToServer();
                    }
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                locationManager.removeUpdates(locationListener);
                            } catch (SecurityException e) {
                            }
                            mIsGettingLocation = false;
                            sendToServer();
                        }
                    }, LOCATION_SEARCH_TIME);
                } else {
                    sendToServer();
                }
            }
        }
        mVibDB.setLastSeenTime(device, currentTime.getTimeInMillis());
        broadcastUpdate(ACTION_VIBRATOR_DATA_CHANGED);
    }

    private void sendToServer() {
        Log.d(TAG, "sending to server");
        for (BluetoothDevice d : mFoundVibrators) {
            Log.d(TAG, "https://vibpost.000webhostapp.com/post.php?address=" + d.getAddress()
                    + "&name=" + d.getName() + "&time=" + mVibDB.getLastSeenTime(d)
                    + "&position=" + (mCurrentLocation != null ? Location.convert(mCurrentLocation.getLatitude(), Location.FORMAT_DEGREES) : 0)
                    + "-" + (mCurrentLocation != null ? Location.convert(mCurrentLocation.getLongitude(), Location.FORMAT_DEGREES) : 0));

            if (d == null) {
                Log.d(TAG, "Device null");
                continue;
            }

            JsonObjectRequest jsObjRequest = new JsonObjectRequest
                    (Request.Method.GET, "https://vibpost.000webhostapp.com/post.php?address=" + d.getAddress()
                            + "&name=" + d.getName() + "&time=" + mVibDB.getLastSeenTime(d)
                            + "&position=" + (mCurrentLocation != null ? Location.convert(mCurrentLocation.getLatitude(), Location.FORMAT_DEGREES) : 0)
                            + "-" + (mCurrentLocation != null ? Location.convert(mCurrentLocation.getLongitude(), Location.FORMAT_DEGREES) : 0),
                            null, new Response.Listener<JSONObject>() {

                        @Override
                        public void onResponse(JSONObject response) {
                            Log.d("SIGNUP", response.toString());

                            if (response.has("success")) {
                                Log.d(TAG, "sending to server successfull");


                            } else {
                                Log.d(TAG, "sending to server failes");

                            }
                        }
                    }, new Response.ErrorListener() {

                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.d(TAG, "sending to server VolleyError" + error + "\n" + error.getMessage());
                            error.printStackTrace();

                        }
                    });

            // Access the RequestQueue through your singleton class.
            ServerConnectionSingleton.getInstance(this).addToRequestQueue(jsObjRequest);
            //remove from list of vibrators to send
        }
        mFoundVibrators.clear();

    }

    /**
     * This function initiates a deep check of a found scanMatch. That means that this device's
     * characteristics will be read and compared to the required values so that it can be a
     * vibrator.
     * If Bluetooth is disabled, a deep check is already going on or there are no more new
     * scanMatches, this function will do nothing.
     */
    private void deepCheckMatches() {
        if (mConnectionState != DISCONNECTED) {
            return;
        }
        if (mBluetoothLeService == null || !mBluetoothLeService.getBluetoothEnabled()) {
            return;
        }
        if (mScanMatches.isEmpty()) {
            return;
        }
        mCurrentDevice = mScanMatches.get(0);
        boolean success = mBluetoothLeService.connect(mCurrentDevice.getAddress());
        if (success) {
            mConnectionState = CONNECTING;
        }
    }

    /**
     * This function finishes the deepCheck, which means, that it will compare all the read
     * characteristics values to the required values and determine if the checked device is really
     * a vibrator or not. This function will also add the checked device to the Database as
     * validated or discarded, depending on the result of the check. After that, it will disconnect
     * from the device to make the phone ready to check the next found scanMatch.
     */
    private void finishDeepCheck() {
        boolean success = true;
        if (mManufacturerNameChara == null || mAmorVibService == null) {
            success = false;
        }
        if (!mManufacturerName.equals(SEARCHED_MANUFACTURER_NAME)) {
            success = false;
        }
        if (success) {
            if (!mVibDB.getValidatedMatchesContains(mCurrentDevice)) {
                mVibDB.addValidatedMatch(mCurrentDevice);
                mScanMatches.remove(mCurrentDevice);
            }
            handleFoundValidatedMatch(mCurrentDevice);
        } else {
            if (!mVibDB.getDiscardedMatchesContains(mCurrentDevice)) {
                mVibDB.addDiscardedMatch(mCurrentDevice);
                mScanMatches.remove(mCurrentDevice);
            }
        }
        //get ready for a new check
        mBluetoothLeService.disconnect();
    }

    /**
     * This function resets the variables relevant to determining if a device is a vibrator or not.
     */
    private void resetForNewDeepTest() {
        mAmorVibService = null;
        mManufacturerNameChara = null;
        mManufacturerName = "";
    }

    /**
     * This function starts the regular search for vibrators. It will start the Android equivalent
     * to a cron job, an Alarm
     */
    @SuppressLint("ApplySharedPref")
    public void startSearch() {

        if (mSearchEnabled) {
            return;
        }

        mSearchEnabled = true;
        mAlarm.setAlarm(this);
        startScanning();

        //remember that there is no alarm running in case that the OS kills the service and starts it again

        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.preferences_vib_finder_service), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.preference_search_enabled), true);
        editor.commit();
    }

    /**
     * This function stops the regular search for vibrators. It will stop the Android equivalent to
     * a cron job, an Alarm.
     */
    @SuppressLint("ApplySharedPref")
    public void stopSearch() {

        if (!mSearchEnabled) {
            return;
        }
        mSearchEnabled = false;
        mAlarm.cancelAlarm(this);
        //remember that there is no alarm running in case that the OS kills the service and starts it again
        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.preferences_vib_finder_service), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.preference_search_enabled), false);
        editor.commit();
    }

    /**
     * @return true if the regular sear for vibrators is going on. That does not mean that the phone is performing a scan right in this moment!
     */
    public boolean getSearchStarted() {
        return mSearchEnabled;
    }

    /*
     * All those functions are used to communicate with the Activities. They send broadcasts
     * for various purposes.
     */
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void readManufacturerName() {
        if (mBluetoothLeService != null && mBluetoothLeService.getBluetoothEnabled()
                && mManufacturerNameChara != null) {
            mBluetoothLeService.readCharacteristic(mManufacturerNameChara);
            Log.d(TAG, "reading manufacturer name");
        }
    }

    /**
     * This function starts scanning for BLE Devices
     */
    public void startScanning() {
        scanLeDevice(true);
    }

    /**
     * This function stops the Alert that can be started when a vibrator has been found.
     */
    public void stopAlert() {
        mVibrator.cancel();
        mAlertActive = false;
        broadcastUpdate(ACTION_ALERT_STOPPED);
    }

    /**
     * This function is used to check if Bluetooth is enabled on the phone.
     *
     * @return true if bluetooth is enabled, false otherwise.
     */
    public boolean getBluetoothEnabled() {
        return mBluetoothAdapter.isEnabled();
    }

    /**
     * This function enables the device's bluetooth adapter. It should not be called without user interaction.
     *
     * @return true on success, false otherwise.
     */
    public boolean enableBluetooth() {
        if (mBluetoothLeService != null) {
            return mBluetoothLeService.enableBluetooth();
        }
        return false;
    }

    /**
     * This function is used to find out if an alert is going on, that means, if the phone is
     * vibrating because a vibrator has been found.
     *
     * @return true if an alerti is active, fals otherwise.
     */
    public boolean getAlertActive() {
        return mAlertActive;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "in VibFinderService: onCreate");

        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof CustomExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler(
                    Environment.getExternalStorageDirectory() + "/media/development/Vib-Finder", null));
        }

        mHandler = new Handler();

        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Log.d(TAG, "Unable to obtain bluetooth adapter");
            broadcastUpdate(ACTION_BLUETOOTH_NOT_USABLE);
            stopSelf();
            return;
        }

        mLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (mLeScanner == null && mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Unable to obtain bluetooth scanner");
            broadcastUpdate(ACTION_BLUETOOTH_NOT_USABLE);
            stopSelf();
            return;
        }

        mSelfService = this;

        mVibDB = new VibDBHelper(this);

        //reload real state of the mSearchEnabled in case that the Service has been killed and restarted by the OS
        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.preferences_vib_finder_service), Context.MODE_PRIVATE);
        mSearchEnabled = sharedPref.getBoolean(getString(R.string.preference_search_enabled), false);
        if (mSearchEnabled) {
            //make sure that the search is really going on (important after reboothing the device)
            stopSearch();
            startSearch();
        }

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String startMode = intent.getStringExtra(EXTRA_SERVICE_START_MODE);
            if (startMode != null && startMode.equals(getString(R.string.start_mode_autostart))) {
                if (!mSearchEnabled) {
                    //this service does not need to be running, if it has been created automatically
                    //and no scan is performed anyways. It will be enough to start it on user
                    // interaction.
                    stopSelf();
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return this.mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (mConnectionState == CONNECTED && mBluetoothLeService != null) {
            mBluetoothLeService.disconnect();
            mConnectionState = DISCONNECTED;
        }
        unbindService(mServiceConnection);
        mBluetoothLeService = null;

        mSelfService = null;

        stopSearch();
        stopAlert();

        unregisterReceiver(mGattUpdateReceiver);
    }

    /**
     * This class is used to create a cron like job for the search for vibrators.
     * It needs to be static because it is registered in the manifest as a receiver.
     */
    public static class Alarm extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vibFinderWakeLock");
            wl.acquire();
            Log.d(TAG, "in Alarm");

            //actual action to be performed in the alarm
            if (mSelfService != null) {
                mSelfService.startScanning();
            }

            wl.release();
        }

        public void setAlarm(Context context) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, Alarm.class);
            PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), ALARM_INTERVAL, alarmIntent);
            Log.d(TAG, "Alarm set");
        }

        public void cancelAlarm(Context context) {
            Intent intent = new Intent(context, Alarm.class);
            PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            am.cancel(alarmIntent);
            Log.d(TAG, "Alarm canceled");
        }
    }

    public class LocalVibFinderServiceBinder extends Binder {
        VibFinderService getService() {
            return VibFinderService.this;
        }
    }

}
