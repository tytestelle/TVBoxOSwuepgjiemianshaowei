package com.github.tvbox.osc.ui.dialog;

import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.QRCodeUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public class LiveSourceManageDialog extends Dialog {

    private ListView listView;
    private SourceAdapter adapter;
    private EditText etName, etUrl;
    private ImageView ivQrCode;
    private TextView tvDeviceInfo;
    private OnSourceChangeListener listener;

    private static final String PREF_NAME = "live_source_pref";
    private static final String KEY_SOURCE_LIST = "source_list";

    public interface OnSourceChangeListener {
        void onSourceChanged();
    }

    public LiveSourceManageDialog(@NonNull Context context, OnSourceChangeListener listener) {
        super(context);
        this.listener = listener;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ----- 构建主布局 -----
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(0xDD000000);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 左侧：二维码
        LinearLayout leftPanel = new LinearLayout(getContext());
        leftPanel.setOrientation(LinearLayout.VERTICAL);
        leftPanel.setGravity(Gravity.CENTER);
        leftPanel.setPadding(8, 8, 8, 8);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        leftPanel.setLayoutParams(leftParams);

        ivQrCode = new ImageView(getContext());
        ivQrCode.setLayoutParams(new ViewGroup.LayoutParams(180, 180));
        leftPanel.addView(ivQrCode);

        tvDeviceInfo = new TextView(getContext());
        tvDeviceInfo.setTextColor(0xFFFFFFFF);
        tvDeviceInfo.setTextSize(13);
        tvDeviceInfo.setPadding(0, 12, 0, 0);
        leftPanel.addView(tvDeviceInfo);

        // 右侧：列表 + 输入
        LinearLayout rightPanel = new LinearLayout(getContext());
        rightPanel.setOrientation(LinearLayout.VERTICAL);
        rightPanel.setPadding(16, 0, 0, 0);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2);
        rightPanel.setLayoutParams(rightParams);

        TextView title = new TextView(getContext());
        title.setText("源列表");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        title.setPadding(0, 0, 0, 12);
        rightPanel.addView(title);

        listView = new ListView(getContext());
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        listView.setDivider(new ColorDrawable(0x44FFFFFF));
        listView.setDividerHeight(2);
        rightPanel.addView(listView);

        // 输入行
        LinearLayout inputRow = new LinearLayout(getContext());
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setPadding(0, 12, 0, 0);
        inputRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        etName = new EditText(getContext());
        etName.setHint("名称(选填)");
        etName.setTextColor(0xFFFFFFFF);
        etName.setHintTextColor(0xFF888888);
        etName.setBackgroundColor(0x33FFFFFF);
        etName.setPadding(8, 8, 8, 8);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        nameParams.setMargins(0, 0, 8, 0);
        etName.setLayoutParams(nameParams);
        inputRow.addView(etName);

        etUrl = new EditText(getContext());
        etUrl.setHint("地址");
        etUrl.setTextColor(0xFFFFFFFF);
        etUrl.setHintTextColor(0xFF888888);
        etUrl.setBackgroundColor(0x33FFFFFF);
        etUrl.setPadding(8, 8, 8, 8);
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2);
        urlParams.setMargins(0, 0, 8, 0);
        etUrl.setLayoutParams(urlParams);
        inputRow.addView(etUrl);

        Button btnAdd = new Button(getContext());
        btnAdd.setText("确定");
        btnAdd.setBackgroundColor(0xFF03DAC5);
        btnAdd.setTextColor(0xFF000000);
        inputRow.addView(btnAdd);

        rightPanel.addView(inputRow);

        // 关闭按钮
        Button btnClose = new Button(getContext());
        btnClose.setText("关闭");
        btnClose.setBackgroundColor(0x66FFFFFF);
        btnClose.setTextColor(0xFFFFFFFF);
        btnClose.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnClose.setPadding(0, 12, 0, 0);
        rightPanel.addView(btnClose);

        root.addView(leftPanel);
        root.addView(rightPanel);
        setContentView(root);

        // 设置窗口尺寸（更小一些）
        Window window = getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.80);
            params.height = (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.60);
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
            window.setDimAmount(0.5f);
        }

        // 显示二维码
        String ip = getDeviceIp();
        String port = "9978";
        String content = "http://" + ip + ":" + port + "/";
        tvDeviceInfo.setText(content);
        Bitmap qr = QRCodeUtil.createQRCode(content, 200);
        if (qr != null) {
            ivQrCode.setImageBitmap(qr);
        }

        adapter = new SourceAdapter();
        listView.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> addSource());
        btnClose.setOnClickListener(v -> dismiss());

        loadData();
    }

    private String getDeviceIp() {
        try {
            String addr = com.github.tvbox.osc.server.ControlManager.get().getAddress(true);
            if (addr != null && addr.startsWith("http://")) {
                addr = addr.replace("http://", "");
                int colon = addr.indexOf(':');
                if (colon > 0) return addr.substring(0, colon);
                return addr;
            }
        } catch (Exception e) { /* ignore */ }
        return "127.0.0.1";
    }

    // 从 SharedPreferences 加载数据
    private void loadData() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_SOURCE_LIST, "[]");
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        List<SourceItem> list = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject obj = array.get(i).getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "";
            String url = obj.has("url") ? obj.get("url").getAsString() : "";
            list.add(new SourceItem(name, url));
        }
        adapter.setData(list);
    }

    // 保存到 SharedPreferences
    private void saveData(List<SourceItem> list) {
        JsonArray array = new JsonArray();
        for (SourceItem item : list) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", item.name);
            obj.addProperty("url", item.url);
            array.add(obj);
        }
        SharedPreferences prefs = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SOURCE_LIST, array.toString()).apply();
        // 触发回调
        if (listener != null) {
            listener.onSourceChanged();
        }
    }

    private void addSource() {
        String name = etName.getText().toString().trim();
        String url = etUrl.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(getContext(), "地址不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(name)) {
            name = extractNameFromUrl(url);
        }
        List<SourceItem> list = adapter.getData();
        for (SourceItem item : list) {
            if (item.url.equals(url)) {
                Toast.makeText(getContext(), "地址已存在", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        list.add(new SourceItem(name, url));
        adapter.setData(list);
        saveData(list);
        etName.setText("");
        etUrl.setText("");
        Toast.makeText(getContext(), "添加成功", Toast.LENGTH_SHORT).show();
    }

    private String extractNameFromUrl(String url) {
        try {
            android.net.Uri uri = android.net.Uri.parse(url);
            String host = uri.getHost();
            if (host != null) {
                String[] parts = host.split("\\.");
                return parts.length > 0 ? parts[0] : "直播";
            }
        } catch (Exception e) {}
        return "直播";
    }

    class SourceItem {
        String name, url;
        SourceItem(String n, String u) { name = n; url = u; }
    }

    class SourceAdapter extends BaseAdapter {
        private List<SourceItem> data = new ArrayList<>();

        public void setData(List<SourceItem> list) {
            data.clear();
            data.addAll(list);
            notifyDataSetChanged();
        }

        public List<SourceItem> getData() { return data; }

        @Override
        public int getCount() { return data.size(); }

        @Override
        public Object getItem(int position) { return data.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout itemLayout;
            if (convertView == null) {
                itemLayout = new LinearLayout(getContext());
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setPadding(12, 12, 12, 12);
                itemLayout.setBackgroundColor(0x33FFFFFF);

                // 名称
                TextView tvName = new TextView(getContext());
                tvName.setId(R.id.tv_name);
                tvName.setTextColor(0xFFFFFFFF);
                tvName.setTextSize(15);
                tvName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                itemLayout.addView(tvName);

                // URL
                TextView tvUrl = new TextView(getContext());
                tvUrl.setId(R.id.tv_url);
                tvUrl.setTextColor(0xFFAAAAAA);
                tvUrl.setTextSize(12);
                tvUrl.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
                itemLayout.addView(tvUrl);

                // 复制按钮
                TextView btnCopy = new TextView(getContext());
                btnCopy.setId(R.id.btn_copy);
                btnCopy.setText("复制");
                btnCopy.setTextColor(0xFFFFFFFF);
                btnCopy.setBackgroundColor(0x33666666);
                btnCopy.setPadding(8, 4, 8, 4);
                btnCopy.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                itemLayout.addView(btnCopy);

                // 删除按钮
                TextView btnDelete = new TextView(getContext());
                btnDelete.setId(R.id.btn_delete);
                btnDelete.setText("删除");
                btnDelete.setTextColor(0xFFFF0000);
                btnDelete.setBackgroundColor(0x33666666);
                btnDelete.setPadding(8, 4, 8, 4);
                btnDelete.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                itemLayout.addView(btnDelete);

                convertView = itemLayout;
            } else {
                itemLayout = (LinearLayout) convertView;
            }

            SourceItem item = data.get(position);

            TextView tvName = itemLayout.findViewById(R.id.tv_name);
            TextView tvUrl = itemLayout.findViewById(R.id.tv_url);
            TextView btnCopy = itemLayout.findViewById(R.id.btn_copy);
            TextView btnDelete = itemLayout.findViewById(R.id.btn_delete);

            tvName.setText(item.name);
            tvUrl.setText(item.url);

            // 复制
            btnCopy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setText(item.url);
                Toast.makeText(getContext(), "已复制", Toast.LENGTH_SHORT).show();
            });

            // 删除
            btnDelete.setOnClickListener(v -> {
                data.remove(position);
                notifyDataSetChanged();
                saveData(data);
                Toast.makeText(getContext(), "已删除", Toast.LENGTH_SHORT).show();
            });

            return convertView;
        }
    }
}
