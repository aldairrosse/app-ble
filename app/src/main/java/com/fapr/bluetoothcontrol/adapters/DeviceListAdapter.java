package com.fapr.bluetoothcontrol.adapters;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fapr.bluetoothcontrol.R;
import com.fapr.bluetoothcontrol.models.DeviceModel;

import java.util.List;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {
    private final List<DeviceModel> dataset;
    private final OnDeviceClickListener listener;

    public DeviceListAdapter(List<DeviceModel> dataset, OnDeviceClickListener listener) {
        this.dataset = dataset;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.device_list_item, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeviceModel item = dataset.get(position);
        String name = item.getName();
        if(name == null) name = item.getMac();

        holder.getTextView().setText(name);
        holder.getView().setOnClickListener(view -> listener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;
        private final View view;

        public ViewHolder(View view) {
            super(view);
            this.view = view;
            textView = view.findViewById(R.id.device_name);
        }

        public TextView getTextView() {
            return textView;
        }
        public View getView() {
            return view;
        }
    }

    public interface OnDeviceClickListener {
        void onClick(DeviceModel item);
    }

}
