package com.github.tvbox.osc.util;

import android.content.Context;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileLogger {
    private static final String LOG_DIR = "logs";
    private static FileLogger instance;
    private File logFile;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private FileLogger(Context context) {
        File filesDir = context.getFilesDir();
        File logDir = new File(filesDir, LOG_DIR);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        String date = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        logFile = new File(logDir, "log_" + date + ".txt");
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new FileLogger(context);
        }
    }

    public static void write(String tag, String msg) {
        if (instance == null) return;
        instance._write(tag, msg);
    }

    public static void write(String tag, String msg, Throwable tr) {
        if (instance == null) return;
        instance._write(tag, msg + "\n" + android.util.Log.getStackTraceString(tr));
    }

    private void _write(String tag, String msg) {
        try {
            String time = dateFormat.format(new Date());
            String line = time + " [" + tag + "] " + msg + "\n";
            FileWriter fw = new FileWriter(logFile, true);
            PrintWriter pw = new PrintWriter(fw);
            pw.print(line);
            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getLogFilePath() {
        return instance != null ? instance.logFile.getAbsolutePath() : null;
    }
}
