package com.github.tvbox.osc.util;

import android.content.Context;
import android.os.Environment;

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
        // 使用应用私有目录 /data/data/包名/files/logs/
        File filesDir = context.getFilesDir(); // 对应 /data/data/包名/files/
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

    // 获取日志文件路径（方便调试）
    public static String getLogFilePath() {
        return instance != null ? instance.logFile.getAbsolutePath() : null;
    }
}
