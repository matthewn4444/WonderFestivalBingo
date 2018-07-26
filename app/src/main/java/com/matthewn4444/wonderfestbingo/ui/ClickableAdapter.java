package com.matthewn4444.wonderfestbingo.ui;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import java.util.Collection;
import java.util.List;

public abstract class ClickableAdapter<VH extends RecyclerView.ViewHolder, Type> extends RecyclerView.Adapter<VH>
        implements RecyclerViewAdapterListener {
    protected List<Type> mEntries;
    protected RecyclerViewAdapterListener mAdapterListener;

    public ClickableAdapter(List<Type> entries) {
        mEntries = entries;
    }

    public static abstract class ViewHolder extends RecyclerView.ViewHolder implements
            View.OnClickListener, View.OnLongClickListener {
        private RecyclerViewAdapterListener mListener;

        public ViewHolder(View v) {
            super(v);
            v.setOnClickListener(this);
            v.setOnLongClickListener(this);
        }

        public void setRecyclerviewListener(RecyclerViewAdapterListener listener) {
            mListener = listener;
        }

        @Override
        public void onClick(View v) {
            mListener.onClick(v, getAdapterPosition());
        }

        @Override
        public boolean onLongClick(View v) {
            return mListener.onLongClick(v, getAdapterPosition());
        }
    }

    public void add(Type entry) {
        mEntries.add(entry);
        notifyDataSetChanged();
    }

    public void addAll(Collection<Type> entries) {
        mEntries.addAll(entries);
        notifyDataSetChanged();
    }

    public void clear() {
        mEntries.clear();
        notifyDataSetChanged();
    }

    public void setAdapterListener(RecyclerViewAdapterListener listener) {
        mAdapterListener = listener;
    }

    public Type getEntry(int i) {
        return mEntries.get(i);
    }

    @Override
    public void onClick(View v, int position) {
        if (mAdapterListener != null) {
            mAdapterListener.onClick(v, position);
        }
    }

    @Override
    public boolean onLongClick(View v, int position) {
        if (mAdapterListener != null) {
            return mAdapterListener.onLongClick(v, position);
        }
        return false;
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        ((ViewHolder) holder).setRecyclerviewListener(this);
    }

    @Override
    public int getItemCount() {
        return mEntries.size();
    }
}