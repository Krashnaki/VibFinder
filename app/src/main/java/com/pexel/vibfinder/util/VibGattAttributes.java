package com.pexel.vibfinder.util;

public class VibGattAttributes {
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String DEVICE_INFORMATION_SERVICE = "0000180a-0000-1000-8000-00805f9b34fb";
    public static String GENERIC_ACCESS_SERVICE = "00001800-0000-1000-8000-00805f9b34fb";
    public static String DEVICE_NAME_CHARA = "00002a00-0000-1000-8000-00805f9b34fb";
    public static String BATTERY_SERVICE = "0000180f-0000-1000-8000-00805f9b34fb";
    public static String MANUFACTURER_NAME_CHARA = "00002a29-0000-1000-8000-00805f9b34fb";

    //Armor Vibratissimo
    public static String ARMOR_SERVICE = "00001523-1212-efde-1523-785feabcd123";
    
    //Mysteryvibe
    public static String VIBRATISSIMO_SERVICE = "f0006900-110c-478b-b74b-6f403b364a9c";

    //Lovense
    public static String LOVENSE_SERVICE_1 = "0000fff0-0000-1000-8000-00805f9b34fb";
    public static String LOVENSE_SERVICE_2 = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static String[] LOVENSE_SERVICE_3 ={
            "50300001-0020-4bd4-bbd5-a6920e4c5653",
            "10111111-1110-1111-1111-111111111111"
    };


    //Vibrator Service
    public static String VBS_SERVICE = "00001337-3353-4ce1-f424-117057610263";
    public static String VBS_VIB_CRTL_CHARA = "00001338-3353-4ce1-f424-117057610263";
    public static String VBS_RHTHM_CMD_CHARA = "00001339-3353-4ce1-f424-117057610263";
    public static String VBS_RHTHM_DATA_CHARA = "00001340-3353-4ce1-f424-117057610263";
}
