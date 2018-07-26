package com.matthewn4444.wonderfestbingo.ui;

import android.view.View;

public interface RecyclerViewAdapterListener {
    public void onClick(View v, int position);
    public boolean onLongClick(View v, int position);
}