package com.pexel.vibfinder.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;


public class CustomExceptionHandler implements Thread.UncaughtExceptionHandler {
    private final static String TAG = CustomExceptionHandler.class.getSimpleName();

    private Thread.UncaughtExceptionHandler defaultUEH;

    private String localPath;

    private String url;

    /*
     * if any of the parameters is null, the respective functionality
     * will not be used
     */
    public CustomExceptionHandler(String localPath, String url) {
        this.localPath = localPath;
        this.url = url;
        this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        if (this.localPath != null) {
            String tempPath = localPath;
            if (tempPath.charAt(tempPath.length() - 1) != '/') {
                tempPath += "/";
            }
            String path = "";
            while (tempPath.indexOf('/') != -1) {
                int index = tempPath.indexOf('/');
                path += tempPath.substring(0, index + 1);
                tempPath = tempPath.substring(index + 1);
                File folder = new File(path);
                if (!folder.exists()) {
                    boolean success = folder.mkdir();
                }
            }
        }
    }

    public void uncaughtException(Thread t, Throwable e) {
        //String timestamp = TimestampFormatter.getInstance().getTimestamp();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss");
        String timestamp = simpleDateFormat.format(new Date());
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        e.printStackTrace(printWriter);
        String stacktrace = result.toString();
        printWriter.close();
        String filename = timestamp + ".stacktrace";

        if (localPath != null) {
            writeToFile(stacktrace, filename);
        }
//        if (url != null) {
//            sendToServer(stacktrace, filename);
//        }

        defaultUEH.uncaughtException(t, e);
    }

    private void writeToFile(String stacktrace, String filename) {
        try {
            BufferedWriter bos = new BufferedWriter(new FileWriter(
                    localPath + "/" + filename));
            bos.write(stacktrace);
            bos.flush();
            bos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    private void sendToServer(String stacktrace, String filename) {
//        DefaultHttpClient httpClient = new DefaultHttpClient();
//        HttpPost httpPost = new HttpPost(url);
//        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
//        nvps.add(new BasicNameValuePair("filename", filename));
//        nvps.add(new BasicNameValuePair("stacktrace", stacktrace));
//        try {
//            httpPost.setEntity(
//                    new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
//            httpClient.execute(httpPost);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
}
