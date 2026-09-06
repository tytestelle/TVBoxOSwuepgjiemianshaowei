package com.github.tvbox.osc.util.epg;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.text.TextUtils;
import com.github.tvbox.osc.util.LOG;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private static final int DB_VERSION = 1;
    private static EpgManager instance;
    private Context context;
    private SQLiteDatabase db;
    private OkHttpClient httpClient;
    private String epgUrl;

    public static synchronized EpgManager getInstance(Context context) {
        if (instance == null) instance = new EpgManager(context.getApplicationContext());
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
        if (TextUtils.isEmpty(epgUrl)) loadDefaultEpgUrl();
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
                        break;
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void refreshEpg(RefreshCallback callback) {
        if (TextUtils.isEmpty(epgUrl)) { if (callback != null) callback.onError("EPG URL 未设置"); return; }
        new AsyncTask<Void, Void, Boolean>() {
            @Override protected Boolean doInBackground(Void... voids) {
                try {
                    Request request = new Request.Builder().url(epgUrl).build();
                    Response response = httpClient.newCall(request).execute();
                    if (!response.isSuccessful()) return false;
                    String xml = response.body().string();
                    return parseAndStore(xml);
                } catch (Exception e) { e.printStackTrace(); return false; }
            }
            @Override protected void onPostExecute(Boolean success) {
                if (callback != null) { if (success) callback.onSuccess(); else callback.onError("解析失败"); }
            }
        }.execute();
    }

    private boolean parseAndStore(String xml) {
        db.delete("programs", null, null);
        db.delete("channels", null, null);
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new ByteArrayInputStream(xml.getBytes("UTF-8")), null);
            int eventType = parser.getEventType();
            String currentChannelId = null, currentTitle = null, currentStart = null, currentStop = null, currentDesc = null;
            ContentValues channelValues = new ContentValues();
            ContentValues programValues = new ContentValues();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("channel".equals(tagName)) {
                            currentChannelId = parser.getAttributeValue(null, "id");
                        } else if ("display-name".equals(tagName) && currentChannelId != null) {
                            String name = parser.nextText();
                            channelValues.clear();
                            channelValues.put("channel_id", currentChannelId);
                            channelValues.put("display_name", name);
                            db.insert("channels", null, channelValues);
                        } else if ("programme".equals(tagName)) {
                            currentStart = parser.getAttributeValue(null, "start");
                            currentStop = parser.getAttributeValue(null, "stop");
                            currentChannelId = parser.getAttributeValue(null, "channel");
                            currentTitle = null; currentDesc = null;
                        } else if ("title".equals(tagName)) currentTitle = parser.nextText();
                        else if ("desc".equals(tagName)) currentDesc = parser.nextText();
                        break;
                    case XmlPullParser.END_TAG:
                        if ("programme".equals(tagName) && currentChannelId != null && currentStart != null && currentStop != null) {
                            programValues.clear();
                            programValues.put("channel_id", currentChannelId);
                            programValues.put("start_time", currentStart);
                            programValues.put("stop_time", currentStop);
                            programValues.put("title", currentTitle != null ? currentTitle : "");
                            programValues.put("desc", currentDesc != null ? currentDesc : "");
                            db.insert("programs", null, programValues);
                        }
                        break;
                }
                eventType = parser.next();
            }
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public List<EpgProgram> getProgramsForChannel(String channelName) {
        String epgId = EpgDataLoader.getEpgId(channelName);
        if (epgId == null) return new ArrayList<>();
        String channelId = null;
        Cursor c = db.query("channels", new String[]{"channel_id"}, "display_name=?", new String[]{epgId}, null, null, null);
        if (c.moveToFirst()) channelId = c.getString(0);
        c.close();
        if (channelId == null) return new ArrayList<>();
        List<EpgProgram> programs = new ArrayList<>();
        Cursor cur = db.query("programs", null, "channel_id=?", new String[]{channelId}, null, null, "start_time ASC");
        while (cur.moveToNext()) {
            String start = cur.getString(cur.getColumnIndex("start_time"));
            String stop = cur.getString(cur.getColumnIndex("stop_time"));
            String title = cur.getString(cur.getColumnIndex("title"));
            String desc = cur.getString(cur.getColumnIndex("desc"));
            programs.add(new EpgProgram(title, desc, parseXmltvTime(start), parseXmltvTime(stop)));
        }
        cur.close();
        return programs;
    }

    public EpgProgram getCurrentProgram(String channelName) {
        List<EpgProgram> list = getProgramsForChannel(channelName);
        Date now = new Date();
        for (EpgProgram p : list) if (p.start.before(now) && p.stop.after(now)) return p;
        return null;
    }

    public EpgProgram getNextProgram(String channelName) {
        List<EpgProgram> list = getProgramsForChannel(channelName);
        Date now = new Date();
        for (EpgProgram p : list) if (p.start.after(now)) return p;
        return null;
    }

    private Date parseXmltvTime(String time) {
        try {
            if (time.length() >= 14) {
                int year = Integer.parseInt(time.substring(0,4));
                int month = Integer.parseInt(time.substring(4,6));
                int day = Integer.parseInt(time.substring(6,8));
                int hour = Integer.parseInt(time.substring(8,10));
                int minute = Integer.parseInt(time.substring(10,12));
                int second = Integer.parseInt(time.substring(12,14));
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
                return sdf.parse(year+"-"+month+"-"+day+" "+hour+":"+minute+":"+second);
            }
        } catch (Exception e) {}
        return new Date();
    }

    public interface RefreshCallback { void onSuccess(); void onError(String msg); }

    public static class EpgProgram {
        public String title, description; public Date start, stop;
        public EpgProgram(String t, String d, Date s, Date e) { title=t; description=d; start=s; stop=e; }
    }

    private static class DBHelper extends SQLiteOpenHelper {
        public DBHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE channels (channel_id TEXT PRIMARY KEY, display_name TEXT)");
            db.execSQL("CREATE TABLE programs (id INTEGER PRIMARY KEY AUTOINCREMENT, channel_id TEXT, start_time TEXT, stop_time TEXT, title TEXT, desc TEXT)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
    }
}
