package com.github.tvbox.osc.ui.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.QRCodeUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

public class LiveSourceManageDialog extends Dialog {

    private ListView listView;
    private SourceAdapter adapter;
    private EditText etName, etUrl;
    private ImageView ivQrCode;
    private TextView tvDeviceInfo;
    private OnSourceChangeListener listener;

    public interface OnSourceChangeListener {
        void onSourceChanged();
    }

    public LiveSourceManageDialog(@NonNull Context context, OnSourceChangeListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 使用自定义布局，但这次用 LinearLayout 手动构建，避免加载 XML 失败
        View contentView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_live_source_manage, null);
        if (contentView == null) {
            // 如果布局文件加载失败，使用最简单的线性布局（应急方案）
            setContentView(createFallbackView());
        } else {
            setContentView(contentView);
        }

        // 如果布局加载成功，则正常绑定控件
        listView = findViewById(R.id.recyclerView);
        if (listView == null) {
            // 如果列表控件找不到，说明布局有问题，使用应急方案
            setContentView(createFallbackView());
            listView = findViewById(R.id.recyclerView);
        }

        etName = findViewById(R.id.et_name);
        etUrl = findViewById(R.id.et_url);
        ivQrCode = findViewById(R.id.iv_qr_code);
        tvDeviceInfo = findViewById(R.id.tv_device_info);

        // 显示二维码
        if (ivQrCode != null && tvDeviceInfo != null) {
            String ip = getDeviceIp();
            String port = "9978";
            String content = "http://" + ip + ":" + port + "/";
            tvDeviceInfo.setText(content);
            android.graphics.Bitmap qr = QRCodeUtil.createQRCode(content, 300);
            if (qr != null) {
                ivQrCode.setImageBitmap(qr);
            }
        }

        // 设置列表适配器
        if (listView != null) {
            adapter = new SourceAdapter();
            listView.setAdapter(adapter);
        }

        Button btnAdd = findViewById(R.id.btn_add);
        Button btnClose = findViewById(R.id.btn_close);

        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> addSource());
        }
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        loadData();

        // 设置窗口尺寸
        getWindow().setLayout(
                (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.85),
                (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.75)
        );
    }

    private View createFallbackView() {
        // 应急布局：简单的垂直线性布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        layout.setBackgroundColor(0xFF222222);

        // 标题
        TextView title = new TextView(getContext());
        title.setText("源列表");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        layout.addView(title);

        // 列表
        ListView lv = new ListView(getContext());
        lv.setId(R.id.recyclerView);
        lv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
        lv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400));
        layout.addView(lv);

        // 名称输入
        EditText nameInput = new EditText(getContext());
        nameInput.setId(R.id.et_name);
        nameInput.setHint("名称(选填)");
        nameInput.setTextColor(0xFFFFFFFF);
        nameInput.setHintTextColor(0xFF888888);
        layout.addView(nameInput);

        // 地址输入
        EditText urlInput = new EditText(getContext());
        urlInput.setId(R.id.et_url);
        urlInput.setHint("地址");
        urlInput.setTextColor(0xFFFFFFFF);
        urlInput.setHintTextColor(0xFF888888);
        layout.addView(urlInput);

        // 按钮行
        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(getContext());
        btnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        Button btnAdd = new Button(getContext());
        btnAdd.setId(R.id.btn_add);
        btnAdd.setText("确定");
        btnRow.addView(btnAdd);
        Button btnClose = new Button(getContext());
        btnClose.setId(R.id.btn_close);
        btnClose.setText("关闭");
        btnRow.addView(btnClose);
        layout.addView(btnRow);

        return layout;
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

    private void loadData() {
        JsonArray array = Hawk.get(HawkConfig.LIVE_SOURCE_LIST, new JsonArray());
        List<SourceItem> list = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject obj = array.get(i).getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "";
            String url = obj.has("url") ? obj.get("url").getAsString() : "";
            list.add(new SourceItem(name, url));
        }
        if (adapter != null) {
            adapter.setData(list);
        }
    }

    private void saveData(List<SourceItem> list) {
        JsonArray array = new JsonArray();
        for (SourceItem item : list) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", item.name);
            obj.addProperty("url", item.url);
            array.add(obj);
        }
        Hawk.put(HawkConfig.LIVE_SOURCE_LIST, array);
        if (listener != null) listener.onSourceChanged();
    }

    private void addSource() {
        if (etName == null || etUrl == null) {
            Toast.makeText(getContext(), "控件未初始化，请重试", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = etName.getText().toString().trim();
        String url = etUrl.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(getContext(), "地址不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(name)) {
            name = extractNameFromUrl(url);
        }
        List<SourceItem> list = adapter != null ? adapter.getData() : new ArrayList<>();
        for (SourceItem item : list) {
            if (item.url.equals(url)) {
                Toast.makeText(getContext(), "地址已存在", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        list.add(new SourceItem(name, url));
        if (adapter != null) {
            adapter.setData(list);
        }
        saveData(list);
        if (etName != null) etName.setText("");
        if (etUrl != null) etUrl.setText("");
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
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_source_manage, parent, false);
                if (convertView == null) {
                    // 如果 item 布局加载失败，使用简单 TextView
                    TextView tv = new TextView(getContext());
                    tv.setTextColor(0xFFFFFFFF);
                    tv.setPadding(16, 16, 16, 16);
                    convertView = tv;
                }
            }
            SourceItem item = data.get(position);

            TextView tvName = convertView.findViewById(R.id.tv_name);
            TextView tvUrl = convertView.findViewById(R.id.tv_url);
            if (tvName != null && tvUrl != null) {
                tvName.setText(item.name);
                tvUrl.setText(item.url);
            } else if (convertView instanceof TextView) {
                ((TextView) convertView).setText(item.name + " - " + item.url);
            }

            View btnCopy = convertView.findViewById(R.id.btn_copy);
            View btnDelete = convertView.findViewById(R.id.btn_delete);
            View btnUp = convertView.findViewById(R.id.btn_up);
            View btnDown = convertView.findViewById(R.id.btn_down);

            if (btnCopy != null) {
                btnCopy.setOnClickListener(v -> {
                    ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setText(item.url);
                    Toast.makeText(getContext(), "已复制", Toast.LENGTH_SHORT).show();
                });
            }
            if (btnDelete != null) {
                btnDelete.setOnClickListener(v -> {
                    data.remove(position);
                    notifyDataSetChanged();
                    saveData(data);
                    Toast.makeText(getContext(), "已删除", Toast.LENGTH_SHORT).show();
                });
            }
            if (btnUp != null) {
                btnUp.setOnClickListener(v -> {
                    if (position > 0) {
                        SourceItem prev = data.get(position - 1);
                        data.set(position - 1, item);
                        data.set(position, prev);
                        notifyDataSetChanged();
                        saveData(data);
                    }
                });
            }
            if (btnDown != null) {
                btnDown.setOnClickListener(v -> {
                    if (position < data.size() - 1) {
                        SourceItem next = data.get(position + 1);
                        data.set(position + 1, item);
                        data.set(position, next);
                        notifyDataSetChanged();
                        saveData(data);
                    }
                });
            }
            return convertView;
        }
    }
}
