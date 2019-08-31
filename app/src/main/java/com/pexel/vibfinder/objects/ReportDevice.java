package com.pexel.vibfinder.objects;

import java.util.ArrayList;

public class ReportDevice {

    private String deviceName;
    private String address;
    private ArrayList<String> uuids;

    public ReportDevice(String deviceName, String address) {
        this.deviceName = deviceName;
        this.address = address;
        this.uuids = new ArrayList<>();
    }

    public ReportDevice(String deviceName, String address, String uuid) {
        this.deviceName = deviceName;
        this.address = address;
        this.uuids = new ArrayList<>();
        this.uuids.add(uuid);
    }

    public ReportDevice(String deviceName, String address, ArrayList<String> uuids) {
        this.deviceName = deviceName;
        this.address = address;
        this.uuids = uuids;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public ArrayList<String> getUuids() {
        return uuids;
    }

    public void setUuids(ArrayList<String> uuids) {
        this.uuids = uuids;
    }

    public void addUuid(String uuid) {
        this.uuids.add(uuid);
    }
}
