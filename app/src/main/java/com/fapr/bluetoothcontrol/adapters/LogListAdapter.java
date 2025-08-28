package com.fapr.bluetoothcontrol.adapters;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fapr.bluetoothcontrol.R;
import com.fapr.bluetoothcontrol.models.DeviceModel;
import com.fapr.bluetoothcontrol.models.LogModel;

import java.util.List;

public class LogListAdapter extends RecyclerView.Adapter<LogListAdapter.ViewHolder> {
    private final List<LogModel> dataset;

    public LogListAdapter(List<LogModel> dataset) {
        this.dataset = dataset;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.log_list_item, parent, false);
        return new LogListAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LogModel item = dataset.get(position);
        holder.getLabel().setText(item.getLabel());
        holder.getMessage().setText(item.getMessage());
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView label;
        private final TextView message;

        public ViewHolder(View view) {
            super(view);
            label = view.findViewById(R.id.item_label);
            message = view.findViewById(R.id.item_message);
        }

        public TextView getLabel() {
            return label;
        }
        public TextView getMessage() {
            return message;
        }
    }

}
