package com.example.melodira;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

public class MusicAdapter extends RecyclerView.Adapter<MusicAdapter.VH> implements SimpleItemTouchHelperCallback.ItemTouchHelperAdapter {

    public interface OnItemClick { void onItemClicked(int position); }

    private List<Track> items;
    private int selected = -1;
    private OnItemClick listener;

    public MusicAdapter(List<Track> list, int selectedIndex, OnItemClick l) {
        items = list;
        selected = selectedIndex;
        listener = l;
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        Track t = items.get(position);
        holder.tvTitle.setText(t.title);
        holder.tvArtist.setText(t.artist);
        holder.tvAlbum.setText(t.album);
        holder.ivHandle.setImageResource(android.R.drawable.ic_menu_sort_by_size);
        if (position == selected) {
            holder.card.setCardBackgroundColor(0xFFECECEC);
        } else {
            holder.card.setCardBackgroundColor(0xFFFFFFFF);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClicked(holder.getAdapterPosition());
            setSelected(holder.getAdapterPosition());
        });
    }

    @Override public int getItemCount() { return items == null ? 0 : items.size(); }

    public void setSelected(int idx) {
        int prev = selected;
        selected = idx;
        if (prev >= 0) notifyItemChanged(prev);
        notifyItemChanged(idx);
    }

    @Override public void onItemMove(int fromPosition, int toPosition) {
        Collections.swap(items, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    @Override public void onItemDismiss(int position) {
        items.remove(position);
        notifyItemRemoved(position);
    }

    public static class VH extends RecyclerView.ViewHolder {
        CardView card;
        ImageView ivHandle;
        TextView tvTitle, tvArtist, tvAlbum;
        public VH(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            ivHandle = v.findViewById(R.id.ivHandle);
            tvTitle = v.findViewById(R.id.tvItemTitle);
            tvArtist = v.findViewById(R.id.tvItemArtist);
            tvAlbum = v.findViewById(R.id.tvItemAlbum);
        }
    }
}
