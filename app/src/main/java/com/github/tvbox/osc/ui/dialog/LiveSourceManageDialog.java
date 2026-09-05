package com.github.tvbox.osc.ui.dialog;

import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.QRCodeUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

public class LiveSourceManageDialog extends Dialog {

    private RecyclerView recyclerView;
    private SourceAdapter adapter;
    private EditText etName, etUrl;
    private ImageView ivQrCode;
    private TextView tvDeviceInfo;
    private OnSourceChangeListener listener;

    public interface OnSourceChangeListener {
        void onSourceChanged();
    }

    public LiveSourceManageDialog(@NonNull Context context, OnSourceChangeListener listener) {
        super(context, R.style.DialogSourceManage);  // 使用半屏样式
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_live_source_manage);

        // 设置窗口宽高（半屏，居中）
        Window window = getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.85);
            params.height = (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.75);
            params.gravity = android.view.Gravity.CENTER;
            window.setAttributes(params);
        }

        recyclerView = findViewById(R.id.recyclerView);
        etName = findViewById(R.id.et_name);
        etUrl = findViewById(R.id.et_url);
        ivQrCode = findViewById(R.id.iv_qr_code);
        tvDeviceInfo = findViewById(R.id.tv_device_info);

        // 显示二维码
        String ip = getDeviceIp();
        String port = "9978";
        String content = "http://" + ip + ":" + port + "/";
        tvDeviceInfo.setText(content);
        Bitmap qr = QRCodeUtil.createQRCode(content, 300);
        if (qr != null) {
            ivQrCode.setImageBitmap(qr);
        } else {
            // 如果二维码生成失败，不设置图片（移除错误行）
            ivQrCode.setImageDrawable(null);
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SourceAdapter();
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btn_add).setOnClickListener(v -> addSource());
        findViewById(R.id.btn_close).setOnClickListener(v -> dismiss());

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

    private void loadData() {
        JsonArray array = Hawk.get(HawkConfig.LIVE_SOURCE_LIST, new JsonArray());
        List<SourceItem> list = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject obj = array.get(i).getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "";
            String url = obj.has("url") ? obj.get("url").getAsString() : "";
            list.add(new SourceItem(name, url));
        }
        adapter.setData(list);
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

    class SourceAdapter extends RecyclerView.Adapter<SourceAdapter.ViewHolder> {
        private List<SourceItem> data = new ArrayList<>();

        public void setData(List<SourceItem> list) {
            data.clear();
            data.addAll(list);
            notifyDataSetChanged();
        }

        public List<SourceItem> getData() { return data; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_source_manage, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SourceItem item = data.get(position);
            holder.tvName.setText(item.name);
            holder.tvUrl.setText(item.url);
            holder.btnCopy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setText(item.url);
                Toast.makeText(getContext(), "已复制", Toast.LENGTH_SHORT).show();
            });
            holder.btnDelete.setOnClickListener(v -> {
                data.remove(position);
                notifyDataSetChanged();
                saveData(data);
                Toast.makeText(getContext(), "已删除", Toast.LENGTH_SHORT).show();
            });
            holder.btnUp.setOnClickListener(v -> {
                if (position > 0) {
                    SourceItem prev = data.get(position - 1);
                    data.set(position - 1, item);
                    data.set(position, prev);
                    notifyDataSetChanged();
                    saveData(data);
                }
            });
            holder.btnDown.setOnClickListener(v -> {
                if (position < data.size() - 1) {
                    SourceItem next = data.get(position + 1);
                    data.set(position + 1, item);
                    data.set(position, next);
                    notifyDataSetChanged();
                    saveData(data);
                }
            });
        }

        @Override
        public int getItemCount() { return data.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvUrl;
            View btnCopy, btnDelete, btnUp, btnDown;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_name);
                tvUrl = v.findViewById(R.id.tv_url);
                btnCopy = v.findViewById(R.id.btn_copy);
                btnDelete = v.findViewById(R.id.btn_delete);
                btnUp = v.findViewById(R.id.btn_up);
                btnDown = v.findViewById(R.id.btn_down);
            }
        }
    }
}
