package com.github.tvbox.osc.util.epg;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.text.TextUtils;

import com.github.tvbox.osc.util.FileLogger;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class EpgManager {
    private static final String DB_NAME = "epg_cache.db";
    private static final int DB_VERSION = 2; // 版本升级，清旧数据
    private static final String TABLE_CHANNELS = "channels";
    private static final String TABLE_PROGRAMS = "programs";

    private static EpgManager instance;
    private Context context;
    private SQLiteDatabase db;
    private OkHttpClient httpClient;
    private String epgUrl;

    public static synchronized EpgManager getInstance(Context context) {
        if (instance == null) {
            instance = new EpgManager(context.getApplicationContext());
        }
        return instance;
    }

    private EpgManager(Context context) {
        this.context = context;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        DBHelper helper = new DBHelper(context);
        db = helper.getWritableDatabase();
        epgUrl = EpgSettings.getEpgUrl(context);
        if (TextUtils.isEmpty(epgUrl)) {
            loadDefaultEpgUrl();
        }
        FileLogger.write("EpgManager", "初始化完成，EPG URL: " + epgUrl);
    }

    private void loadDefaultEpgUrl() {
        try {
            InputStream is = context.getAssets().open("configuration.json");
            JsonObject config = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
            String epgUrls = config.getAsJsonObject("Configuration").get("EPG_URLS").getAsString();
            if (!TextUtils.isEmpty(epgUrls)) {
                String[] parts = epgUrls.split("\\|\\|");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (trimmed.isEmpty()) continue;
                    int idx = trimmed.lastIndexOf('$');
                    String url = idx > 0 ? trimmed.substring(0, idx).trim() : trimmed;
                    if (!TextUtils.isEmpty(url)) {
                        epgUrl = url;
                        EpgSettings.saveEpgUrl(context, epgUrl);
                        FileLogger.write("EpgManager", "从配置加载 EPG URL: " + epgUrl);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            FileLogger.write("EpgManager", "加载默认EPG URL失败: " + e.getMessage());
        }
    }

    public void refreshEpg(RefreshCallback callback) {
        if (TextUtils.isEmpty(epgUrl)) {
            FileLogger.write("EpgManager", "EPG URL 为空，无法刷新");
            if (callback != null) callback.onError("EPG URL 未设置");
            return;
        }
        FileLogger.write("EpgManager", "开始刷新 EPG，URL: " + epgUrl);
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    Request request = new Request.Builder().url(epgUrl).build();
                    Response response = httpClient.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        FileLogger.write("EpgManager", "下载失败 HTTP: " + response.code());
                        return false;
                    }
                    String xml = response.body().string();
                    FileLogger.write("EpgManager", "下载完成，XML 长度: " + xml.length());
                    return parseAndStore(xml);
                } catch (Exception e) {
                    FileLogger.write("EpgManager", "下载异常: " + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (callback != null) {
                    if (success) {
                        FileLogger.write("EpgManager", "EPG 刷新成功");
                        callback.onSuccess();
                    } else {
                        FileLogger.write("EpgManager", "EPG 刷新失败");
                        callback.onError("解析失败");
                    }
                }
            }
        }.execute();
    }

    private boolean parseAndStore(String xml) {
        // 清空旧数据
        db.delete(TABLE_PROGRAMS, null, null);
        db.delete(TABLE_CHANNELS, null, null);

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new ByteArrayInputStream(xml.getBytes("UTF-8")), null);

            int eventType = parser.getEventType();
            String currentChannelId = null;
            String currentDisplayName = null;
            String currentIcon = null;

            // 存储 channel 映射 (display-name -> channelId, icon)
            ContentValues channelValues = new ContentValues();

            // 存储 programme
            ContentValues programValues = new ContentValues();
            String progChannelId = null;
            String progStart = null;
            String progStop = null;
            String progTitle = null;
            String progDesc = null;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("channel".equals(tagName)) {
                            currentChannelId = parser.getAttributeValue(null, "id");
                            currentDisplayName = null;
                            currentIcon = null;
                        } else if ("display-name".equals(tagName)) {
                            if (currentChannelId != null) {
                                currentDisplayName = parser.nextText().trim();
                            }
                        } else if ("icon".equals(tagName) && currentChannelId != null) {
                            currentIcon = parser.getAttributeValue(null, "src");
                        } else if ("programme".equals(tagName)) {
                            progChannelId = parser.getAttributeValue(null, "channel");
                            progStart = parser.getAttributeValue(null, "start");
                            progStop = parser.getAttributeValue(null, "stop");
                            progTitle = null;
                            progDesc = null;
                        } else if ("title".equals(tagName) && progChannelId != null) {
                            progTitle = parser.nextText().trim();
                        } else if ("desc".equals(tagName) && progChannelId != null) {
                            progDesc = parser.nextText().trim();
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("channel".equals(tagName)) {
                            if (currentChannelId != null && currentDisplayName != null) {
                                // 存储映射
                                channelValues.clear();
                                channelValues.put("channel_id", currentChannelId);
                                channelValues.put("display_name", currentDisplayName);
                                if (currentIcon != null) {
                                    channelValues.put("icon", currentIcon);
                                }
                                db.insert(TABLE_CHANNELS, null, channelValues);
                                FileLogger.write("EpgManager", "存储频道: " + currentDisplayName + " -> " + currentChannelId + (currentIcon != null ? " icon: " + currentIcon : ""));
                            }
                            currentChannelId = null;
                            currentDisplayName = null;
                            currentIcon = null;
                        } else if ("programme".equals(tagName)) {
                            if (progChannelId != null && progStart != null && progStop != null) {
                                programValues.clear();
                                programValues.put("channel_id", progChannelId);
                                programValues.put("start_time", progStart);
                                programValues.put("stop_time", progStop);
                                programValues.put("title", progTitle != null ? progTitle : "");
                                programValues.put("desc", progDesc != null ? progDesc : "");
                                db.insert(TABLE_PROGRAMS, null, programValues);
                            }
                            progChannelId = null;
                            progStart = null;
                            progStop = null;
                            progTitle = null;
                            progDesc = null;
                        }
                        break;
                }
                eventType = parser.next();
            }

            FileLogger.write("EpgManager", "解析完成，频道数: " + getChannelCount() + ", 节目数: " + getProgramCount());
            return true;
        } catch (Exception e) {
            FileLogger.write("EpgManager", "解析异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private int getChannelCount() {
        Cursor c = db.query(TABLE_CHANNELS, new String[]{"COUNT(*)"}, null, null, null, null, null);
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }

    private int getProgramCount() {
        Cursor c = db.query(TABLE_PROGRAMS, new String[]{"COUNT(*)"}, null, null, null, null, null);
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }

    // ========== 查询接口 ==========

    /**
     * 根据频道名称获取节目列表（已修正映射逻辑）
     * 步骤：频道名 -> epg_data.json 中的 epgid (display-name) -> 查询 channels 表获取 channel_id -> 查询 programmes
     */
    public List<EpgProgram> getProgramsForChannel(String channelName) {
        String epgId = EpgDataLoader.getEpgId(channelName);
        if (epgId == null) {
            FileLogger.write("EpgManager", "未找到 epgId: " + channelName);
            return new ArrayList<>();
        }
        FileLogger.write("EpgManager", "查询频道: " + channelName + " -> epgId: " + epgId);

        // 用 display_name (即 epgId) 查询 channel_id
        String channelId = null;
        Cursor c = db.query(TABLE_CHANNELS, new String[]{"channel_id"}, "display_name=?", new String[]{epgId}, null, null, null);
        if (c.moveToFirst()) {
            channelId = c.getString(0);
        }
        c.close();

        if (channelId == null) {
            FileLogger.write("EpgManager", "未找到 channel_id: " + epgId);
            return new ArrayList<>();
        }

        // 查询节目
        List<EpgProgram> programs = new ArrayList<>();
        Cursor cur = db.query(TABLE_PROGRAMS, null, "channel_id=?", new String[]{channelId}, null, null, "start_time ASC");
        while (cur.moveToNext()) {
            String start = cur.getString(cur.getColumnIndex("start_time"));
            String stop = cur.getString(cur.getColumnIndex("stop_time"));
            String title = cur.getString(cur.getColumnIndex("title"));
            String desc = cur.getString(cur.getColumnIndex("desc"));
            programs.add(new EpgProgram(title, desc, parseXmltvTime(start), parseXmltvTime(stop)));
        }
        cur.close();
        FileLogger.write("EpgManager", "找到 " + programs.size() + " 个节目");
        return programs;
    }

    public EpgProgram getCurrentProgram(String channelName) {
        List<EpgProgram> list = getProgramsForChannel(channelName);
        Date now = new Date();
        for (EpgProgram p : list) {
            if (p.start.before(now) && p.stop.after(now)) return p;
        }
        return null;
    }

    public EpgProgram getNextProgram(String channelName) {
        List<EpgProgram> list = getProgramsForChannel(channelName);
        Date now = new Date();
        for (EpgProgram p : list) {
            if (p.start.after(now)) return p;
        }
        return null;
    }

    /**
     * 获取频道台标（从 EPG 中提取的 icon）
     */
    public String getChannelIcon(String channelName) {
        String epgId = EpgDataLoader.getEpgId(channelName);
        if (epgId == null) return null;
        String icon = null;
        Cursor c = db.query(TABLE_CHANNELS, new String[]{"icon"}, "display_name=?", new String[]{epgId}, null, null, null);
        if (c.moveToFirst()) {
            icon = c.getString(0);
        }
        c.close();
        return icon;
    }

    private Date parseXmltvTime(String time) {
        try {
            if (time.length() >= 14) {
                int year = Integer.parseInt(time.substring(0, 4));
                int month = Integer.parseInt(time.substring(4, 6));
                int day = Integer.parseInt(time.substring(6, 8));
                int hour = Integer.parseInt(time.substring(8, 10));
                int minute = Integer.parseInt(time.substring(10, 12));
                int second = Integer.parseInt(time.substring(12, 14));
                // 处理时区（如果有 +0800）
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
                return sdf.parse(year + "-" + month + "-" + day + " " + hour + ":" + minute + ":" + second);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Date();
    }

    public interface RefreshCallback {
        void onSuccess();
        void onError(String msg);
    }

    public static class EpgProgram {
        public String title, description;
        public Date start, stop;
        public EpgProgram(String title, String description, Date start, Date stop) {
            this.title = title;
            this.description = description;
            this.start = start;
            this.stop = stop;
        }
    }

    private static class DBHelper extends SQLiteOpenHelper {
        public DBHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_CHANNELS + " (channel_id TEXT PRIMARY KEY, display_name TEXT, icon TEXT)");
            db.execSQL("CREATE TABLE " + TABLE_PROGRAMS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, channel_id TEXT, start_time TEXT, stop_time TEXT, title TEXT, desc TEXT)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHANNELS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROGRAMS);
            onCreate(db);
        }
    }
}
