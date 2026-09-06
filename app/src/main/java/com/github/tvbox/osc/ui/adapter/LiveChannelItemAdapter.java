package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.util.logo.LogoManager; // 包名已修正

import java.io.File;
import java.util.ArrayList;

/**
 * @author pj567
 * @date :2021/1/12
 * @description: 频道列表适配器（含台标异步加载）
 */
public class LiveChannelItemAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    private int selectedChannelIndex = -1;
    private int focusedChannelIndex = -1;

    public LiveChannelItemAdapter() {
        super(R.layout.item_live_channel, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveChannelItem item) {
        // 原有视图绑定
        TextView tvChannelNum = holder.getView(R.id.tvChannelNum);
        TextView tvChannel = holder.getView(R.id.tvChannelName);
        tvChannelNum.setText(String.format("%s", item.getChannelNum()));
        tvChannel.setText(item.getChannelName());
        tvChannelNum.setSelected(true);
        tvChannel.setSelected(true);

        // ---------- 新增：台标加载 ----------
        ImageView ivLogo = holder.getView(R.id.ivChannelLogo); // 请确保布局中存在该id
        // 使用系统默认占位图（已按需求修正为 android.R.drawable.ic_menu_gallery）
        Glide.with(mContext).load(android.R.drawable.ic_menu_gallery).into(ivLogo);

        String channelName = item.getChannelName();
        String channelLogoUrl = item.getChannelLogo(); // 假设LiveChannelItem有getChannelLogo()方法

        // 异步下载台标
        LogoManager.getInstance(mContext).downloadLogo(channelName, channelLogoUrl, new LogoManager.LogoCallback() {
            @Override
            public void onSuccess(File file) {
                // 在UI线程加载图片
                if (holder.getLayoutPosition() >= 0) { // 简单检查item是否仍然有效
                    Glide.with(mContext).load(file).into(ivLogo);
                }
            }

            @Override
            public void onError(String msg) {
                // 加载失败保留默认占位
                // 可在此记录日志
            }
        });
        // ---------- 新增结束 ----------

        // 原有选中/焦点颜色逻辑
        int channelIndex = item.getChannelIndex();
        holder.itemView.setSelected(channelIndex == selectedChannelIndex);
        if (channelIndex == selectedChannelIndex && channelIndex != focusedChannelIndex) {
            tvChannelNum.setTextColor(mContext.getResources().getColor(R.color.color_1890FF));
            tvChannel.setTextColor(mContext.getResources().getColor(R.color.color_1890FF));
        } else {
            tvChannelNum.setTextColor(Color.WHITE);
            tvChannel.setTextColor(Color.WHITE);
        }
    }

    public void setSelectedChannelIndex(int selectedChannelIndex) {
        if (selectedChannelIndex == this.selectedChannelIndex) return;
        int preSelectedChannelIndex = this.selectedChannelIndex;
        this.selectedChannelIndex = selectedChannelIndex;
        if (preSelectedChannelIndex != -1)
            notifyItemChanged(preSelectedChannelIndex);
        if (this.selectedChannelIndex != -1)
            notifyItemChanged(this.selectedChannelIndex);
    }

    public void setFocusedChannelIndex(int focusedChannelIndex) {
        int preFocusedChannelIndex = this.focusedChannelIndex;
        this.focusedChannelIndex = focusedChannelIndex;
        if (preFocusedChannelIndex != -1)
            notifyItemChanged(preFocusedChannelIndex);
        if (this.focusedChannelIndex != -1)
            notifyItemChanged(this.focusedChannelIndex);
        else if (this.selectedChannelIndex != -1)
            notifyItemChanged(this.selectedChannelIndex);
    }
}
