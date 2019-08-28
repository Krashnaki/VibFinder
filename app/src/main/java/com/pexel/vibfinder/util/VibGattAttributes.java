package com.pexel.vibfinder.util;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Class to hold BLE UUIDs of matching devices.
 *
 * //FIXME Documentation
 */
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
            "50300001-0020-4bd4-bbd5-a6920e4c5653", //UUID
            "10111111-1110-1111-1111-111111111111"  //Mask
    };

    //Rends Vorze SA Toys
    public static final String VORZE_SERVICE = "40ee1111-63ec-4b7f-8ce7-712efd55b90e";

    //Kiiroo Onyx/Pearl 1
    public static final String KIIROO_1_SERIVCE = "49535343-fe7d-4ae5-8fa9-9fafd205e455";

    //Kiiroo Onyx 2
    public static final ArrayList<String> KIIROO_2_SERIVCE = new ArrayList<>(Arrays.asList(
            "f60402a6-0294-4bdb-9f20-6758133f7090", //Needed uuid 1
            "02962ac9-e86f-4094-989d-231d69995fc2"  //Needed uuid 2
    ));

    //Vibratissimo Services
    public static String VBS_SERVICE = "00001337-3353-4ce1-f424-117057610263";
    public static String VBS_VIB_CRTL_CHARA = "00001338-3353-4ce1-f424-117057610263";
    public static String VBS_RHTHM_CMD_CHARA = "00001339-3353-4ce1-f424-117057610263";
    public static String VBS_RHTHM_DATA_CHARA = "00001340-3353-4ce1-f424-117057610263";
}
