package com.pexel.vibfinder.objects;

public class Update {

    private String apk;
    private String version;
    private Integer buildNumber;
    private String changelog;

    public Update(String apk, String version, Integer buildNumber, String changelog) {
        this.apk = apk;
        this.version = version;
        this.buildNumber = buildNumber;
        this.changelog = changelog;
    }

    public String getApk() {
        return apk;
    }

    public String getVersion() {
        return version;
    }

    public Integer getBuildNumber() {
        return buildNumber;
    }

    public String getChangelog() {
        return changelog;
    }
}
