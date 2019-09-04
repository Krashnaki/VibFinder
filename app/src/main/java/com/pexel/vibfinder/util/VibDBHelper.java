package com.pexel.vibfinder.util;

import android.bluetooth.BluetoothDevice;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.ParcelUuid;
import android.util.Log;

import com.pexel.vibfinder.objects.VibratorMatch;


public class VibDBHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "Vibrators.db";
    private static final String VIBRATORS_TABLE_NAME = "vibrators";
    private static final String VIBRATORS_COLUMN_NAME = "name";
    private static final String VIBRATORS_COLUMN_ADDRESS = "address";
    private static final String VIBRATORS_COLUMN_STATE = "state";
    private static final String VIBRATORS_COLUMN_IGNORED = "ignored";
    private static final String VIBRATORS_COLUMN_LAST_ALARM_TIME = "lastAlarmTime";
    private static final String VIBRATORS_COLUMN_LAST_SEEN_TIME = "lastSeenTime";

    private static final String UUIDS_TABLE_NAME = "vibrator_uuids";
    private static final String UUIDS_COLUMN_ADDRESS = "address";
    private static final String UUIDS_COLUMN_UUID = "uuid";

    private static final int VALIDATED_STATE = 1;
    private static final int DISCARDED_STATE = 2;
    private final static String TAG = VibDBHelper.class.getSimpleName();
    private static final int TRUE_INT = 1;
    private static final int FALSE_INT = 0;

    public VibDBHelper(Context context) {
        super(context, DATABASE_NAME, null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + VIBRATORS_TABLE_NAME
                + " ("
                + VIBRATORS_COLUMN_NAME + " TEXT"
                + ", " + VIBRATORS_COLUMN_ADDRESS + " TEXT"
                + ", " + VIBRATORS_COLUMN_STATE + " INTEGER"
                + ", " + VIBRATORS_COLUMN_IGNORED + " INTEGER DEFAULT 0"
                + ", " + VIBRATORS_COLUMN_LAST_ALARM_TIME + " LONG DEFAULT 0"
                + ", " + VIBRATORS_COLUMN_LAST_SEEN_TIME + " LONG DEFAULT 0"
                + ", CONSTRAINT pk_Vibrator PRIMARY KEY (" + VIBRATORS_COLUMN_NAME + "," + VIBRATORS_COLUMN_ADDRESS + ")"
                + ", CONSTRAINT chk_State CHECK (" + VIBRATORS_COLUMN_STATE + "=" + VALIDATED_STATE
                + " OR " + VIBRATORS_COLUMN_STATE + "=" + DISCARDED_STATE + ")"
                + ")");

        db.execSQL("CREATE TABLE " + UUIDS_TABLE_NAME + " ( " +
                UUIDS_COLUMN_ADDRESS + " TEXT, " +
                UUIDS_COLUMN_UUID + "TEXT, " +
                "CONSTRAINT pk_uuids PRIMARY KEY (" + UUIDS_COLUMN_ADDRESS + "," + UUIDS_COLUMN_UUID + ")"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + VIBRATORS_TABLE_NAME);
        onCreate(db);
    }

    /**
     * This function adds an entry to the DB with the device information and sets the device state
     * to discarded.
     *
     * @param device The device that should be added as discardedMatch to the DB
     */
    public void addDiscardedMatch(BluetoothDevice device) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(VIBRATORS_COLUMN_NAME, device.getName());
        contentValues.put(VIBRATORS_COLUMN_ADDRESS, device.getAddress());
        contentValues.put(VIBRATORS_COLUMN_STATE, DISCARDED_STATE);
        db.insert(VIBRATORS_TABLE_NAME, null, contentValues);
    }

    /**
     * This function adds an entry to the DB with the device information and sets the device state
     * to validated.
     *
     * @param device The device that should be added as validatedMatch to the DB
     */
    public void addValidatedMatch(BluetoothDevice device) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(VIBRATORS_COLUMN_NAME, device.getName());
        contentValues.put(VIBRATORS_COLUMN_ADDRESS, device.getAddress());
        contentValues.put(VIBRATORS_COLUMN_STATE, VALIDATED_STATE);
        db.insert(VIBRATORS_TABLE_NAME, null, contentValues);

        for (ParcelUuid uuid :                device.getUuids()) {
            contentValues = new ContentValues();
            contentValues.put(UUIDS_COLUMN_ADDRESS, device.getAddress());
            contentValues.put(UUIDS_COLUMN_UUID, uuid.toString());
            db.insert(UUIDS_TABLE_NAME, null, contentValues);
        }
    }

    /**
     * This function checks if the specified device is already known as a validatedMatch in the DB.
     *
     * @param device The device to check.
     * @return true if the device is a known validatedMatch, falso otherwise.
     */
    public boolean getValidatedMatchesContains(BluetoothDevice device) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + VIBRATORS_TABLE_NAME + " WHERE "
                + VIBRATORS_COLUMN_NAME + "='" + device.getName() + "'"
                + " AND " + VIBRATORS_COLUMN_ADDRESS + "='" + device.getAddress() + "'"
                + " AND " + VIBRATORS_COLUMN_STATE + "=" + VALIDATED_STATE;
        Cursor res = db.rawQuery(query, null);
        if (res.getCount() <= 0) {
            res.close();
            return false;
        }
        res.close();
        return true;
    }

    /**
     * This function checks if the specified device is already known as a discardedMatch in the DB.
     *
     * @param device The device to check.
     * @return true if the device is a known discardedMatch, falso otherwise.
     */
    public boolean getDiscardedMatchesContains(BluetoothDevice device) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + VIBRATORS_TABLE_NAME + " WHERE "
                + VIBRATORS_COLUMN_NAME + "='" + device.getName() + "'"
                + " AND " + VIBRATORS_COLUMN_ADDRESS + "='" + device.getAddress() + "'"
                + " AND " + VIBRATORS_COLUMN_STATE + "=" + DISCARDED_STATE;
        Cursor res = db.rawQuery(query, null);
        if (res.getCount() <= 0) {
            res.close();
            return false;
        }
        res.close();
        return true;
    }

    /**
     * This function is used to check if the specified device is ignored or not, that means if
     * it should trigger an alert if it is found or not.
     *
     * @param device the device to check.
     * @return true if the specified device is ignored, false otherwise.
     */
    public boolean getVibratorIgnored(BluetoothDevice device) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + VIBRATORS_TABLE_NAME + " WHERE "
                + VIBRATORS_COLUMN_NAME + "='" + device.getName() + "'"
                + " AND " + VIBRATORS_COLUMN_ADDRESS + "='" + device.getAddress() + "'";
        Cursor res = db.rawQuery(query, null);
        if (!res.moveToFirst()) {
            return false;
        }
        Log.d(TAG, "Vibrator ignored: " + res.getInt(res.getColumnIndex(VIBRATORS_COLUMN_IGNORED)));
        if (res.getInt(res.getColumnIndex(VIBRATORS_COLUMN_IGNORED)) == FALSE_INT) {
            res.close();
            return false;
        }

        res.close();
        return true;
    }

    /**
     * @param device  the {@link VibratorMatch} whose ignored parameter should be set.
     * @param ignored true if the device should be ignored, false if not.
     * @see VibDBHelper#setVibratorIgnored(String, String, boolean)
     */
    public void setVibratorIgnored(BluetoothDevice device, boolean ignored) {
        setVibratorIgnored(device.getName(), device.getAddress(), ignored);
    }

    /**
     * @param vib     the {@link VibratorMatch} whose ignored parameter should be set.
     * @param ignored true if the device should be ignored, false if not.
     * @see VibDBHelper#setVibratorIgnored(String, String, boolean)
     */
    public void setVibratorIgnored(VibratorMatch vib, boolean ignored) {
        setVibratorIgnored(vib.getName(), vib.getAddress(), ignored);
    }

    /**
     * Sets a specified device to ignored or not, that means if it should trigger an alert if it is
     * found or not.
     *
     * @param name    name of the device
     * @param address address of the device
     * @param ignored true if the device should be ignored, false if not.
     */

    private void setVibratorIgnored(String name, String address, boolean ignored) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(VIBRATORS_COLUMN_IGNORED, ignored ? TRUE_INT : FALSE_INT);
        String where = VIBRATORS_COLUMN_NAME + "='" + name + "'"
                + " AND " + VIBRATORS_COLUMN_ADDRESS + "='" + address + "'";
        db.update(VIBRATORS_TABLE_NAME, contentValues, where, null);
    }

    /**
     * This function checks when an alert has been triggered for a device the last time.
     *
     * @param device The device to check.
     * @return The last time when an alert has been triggered for the device in ms after the start
     * of the epoch. 0 if no alert has been triggered yet.
     */
    public long getLastAlertTime(BluetoothDevice device) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + VIBRATORS_TABLE_NAME + " WHERE "
                + VIBRATORS_COLUMN_NAME + "='" + device.getName() + "'"
                + " AND " + VIBRATORS_COLUMN_ADDRESS + "='" + device.getAddress() + "'";
        Cursor c = db.rawQuery(query, null);
        if (!c.moveToFirst()) {
            return 0;
        }
        Log.d(TAG, "Vibrator last alerted: " + c.getLong(c.getColumnIndex(VIBRATORS_COLUMN_LAST_ALARM_TIME)));
        long res = c.getLong(c.getColumnIndex(VIBRATORS_COLUMN_LAST_ALARM_TIME));
        c.close();
        return res;
    }

    /**
     * This function sets the last time when an alert has been triggered for a device.
     *
     * @param device The device whose lastAlertTime should be modified.
     * @param time   The time to which the device's lastAlertTime should be set.
     */
    public void setLastAlertTime(BluetoothDevice device, long time) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(VIBRATORS_COLUMN_LAST_ALARM_TIME, time);
        String where = VIBRATORS_COLUMN_NAME + "='" + device.getName() + "'"
                + " AND " + VIBRATORS_COLUMN_ADDRESS + "='" + device.getAddress() + "'";
        db.update(VIBRATORS_TABLE_NAME, contentValues, where, null);
    }

    /**
     * This function checks when a device has been seen the last time.
     *
     * @param device The device to check.
     * @return The last time when the device has been seen the last time in ms after the start
     * of the epoch. 0 if no alert has never been seen yet.
     */
    public long getLastSeenTime(BluetoothDevice device) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + VIBRATORS_TABLE_NAME + " WHERE "
                + VIBRATORS_COLUMN_NAME + "='" + device.getName() + "'"
                + " AND " + VIBRATORS_COLUMN_ADDRESS + "='" + device.getAddress() + "'";
        Cursor c = db.rawQuery(query, null);
        if (!c.moveToFirst()) {
            return 0;
        }
        Log.d(TAG, "Vibrator last seen: " + c.getLong(c.getColumnIndex(VIBRATORS_COLUMN_LAST_SEEN_TIME)));
        long res = c.getLong(c.getColumnIndex(VIBRATORS_COLUMN_LAST_SEEN_TIME));
        c.close();
        return res;
    }

    /**
     * This function sets the lastSeenTime of a device.
     *
     * @param device The device whose lastSeenTime should be modified.
     * @param time   The time that should be set as the specified device's lastSeenTime in ms after
     *               the start of the epoch. 0 if the device as never been seen yet.
     */
    public void setLastSeenTime(BluetoothDevice device, long time) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(VIBRATORS_COLUMN_LAST_SEEN_TIME, time);
        String where = VIBRATORS_COLUMN_NAME + "='" + device.getName() + "'"
                + " AND " + VIBRATORS_COLUMN_ADDRESS + "='" + device.getAddress() + "'";
        db.update(VIBRATORS_TABLE_NAME, contentValues, where, null);
    }

    /**
     * This Function returns an array with all the validated matches that have been found so far.
     * They are ordered by the lastSeenTime. The devices that have been seen more recently are
     * in the beginning of the array.
     *
     * @return A VibratorMatch[] array with all the validated matches that have been found so far.
     */
    public VibratorMatch[] getAllValidatedMatches() {

        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + VIBRATORS_TABLE_NAME + " WHERE"
                + " " + VIBRATORS_COLUMN_STATE + "=" + VALIDATED_STATE
                + " ORDER BY " + VIBRATORS_COLUMN_LAST_SEEN_TIME + " DESC";
        Cursor c = db.rawQuery(query, null);

        if (c.getCount() > 0) {
            VibratorMatch[] vibrators = new VibratorMatch[c.getCount()];
            c.moveToFirst();

            for (int i = 0; i < c.getCount(); i++) {
                String name = c.getString((c.getColumnIndex(VIBRATORS_COLUMN_NAME)));
                String address = c.getString(c.getColumnIndex(VIBRATORS_COLUMN_ADDRESS));
                long lastSeenTime = c.getLong(c.getColumnIndex(VIBRATORS_COLUMN_LAST_SEEN_TIME));
                //careful, alertEnabled is the inverted value stored in the ignored field in the db!
                boolean alertEnabled = c.getInt(c.getColumnIndex(VIBRATORS_COLUMN_IGNORED)) == FALSE_INT;
                VibratorMatch vib = new VibratorMatch(name, address, lastSeenTime, alertEnabled);
                vibrators[i] = vib;
                c.moveToNext();
            }

            c.close();
            return vibrators;
        } else {

            c.close();
            return new VibratorMatch[0];
        }
    }
}
