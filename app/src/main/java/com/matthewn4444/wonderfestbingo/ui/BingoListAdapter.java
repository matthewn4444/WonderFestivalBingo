package com.matthewn4444.wonderfestbingo.ui;

import android.graphics.Color;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.matthewn4444.wonderfestbingo.BingoSquareData;
import com.matthewn4444.wonderfestbingo.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BingoListAdapter extends ClickableAdapter<BingoListAdapter.Holder, BingoSquareData> {

    public static final int ROWS = 5;
    public static final int COLUMNS = 5;
    public static final int MAX_ITEMS = ROWS * COLUMNS;
    public static final int FREE_SPACE_INDEX = ROWS / 2 * COLUMNS + COLUMNS / 2;

    public BingoListAdapter() {
        super(new ArrayList<BingoSquareData>(MAX_ITEMS));
        init();
    }

    public BingoListAdapter(List<BingoSquareData> data) {
        super(data);

        // If there are too many items, remove the access
        if (data.size() > MAX_ITEMS) {
            for (int i = data.size() - 1; i >= MAX_ITEMS; i--) {
                data.remove(i);
            }
        }
        init();
    }

    public void shuffle() {
        BingoSquareData freespace = mEntries.remove(FREE_SPACE_INDEX);
        Collections.shuffle(mEntries);
        mEntries.add(FREE_SPACE_INDEX, freespace);
        notifyDataSetChanged();
    }

    private void init() {
        for (int i = getItemCount(); i < MAX_ITEMS; i++) {
            add(new BingoSquareData(i, null, null));
        }

        // Set the hardcoded free space
        BingoSquareData data = getEntry(FREE_SPACE_INDEX);
        data.setStampedState(true);
        data.setName(BingoSquareData.FreeSpace);
        setHasStableIds(true);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return mEntries.get(position).getId();
    }

    public void swap(int first, int second) {
        Collections.swap(mEntries, first, second);
    }

    public int getBingoCount() {
        int count = 0;
        // Loop throw each row
        boolean bingo;
        for (int r = 0; r < BingoListAdapter.ROWS; r++) {
            bingo = true;
            for (int c = 0; c < BingoListAdapter.COLUMNS; c++) {
                if (!getEntry(r * BingoListAdapter.COLUMNS + c).isStamped()) {
                    bingo = false;
                    break;
                }
            }
            if (bingo) {
                count++;
            }
        }

        // Loop through columms
        for (int c = 0; c < BingoListAdapter.COLUMNS; c++) {
            bingo = true;
            for (int r = 0; r < BingoListAdapter.ROWS; r++) {
                if (!getEntry(r * BingoListAdapter.COLUMNS + c).isStamped()) {
                    bingo = false;
                    break;
                }
            }
            if (bingo) {
                count++;
            }
        }

        // Top left diagonal to bottom right
        int c = 0, r = 0;
        bingo = true;
        for (int i = 0; i < Math.min(BingoListAdapter.COLUMNS, BingoListAdapter.ROWS); i++) {
            if (!getEntry((r + i) * BingoListAdapter.COLUMNS + (c + i)).isStamped()) {
                bingo = false;
                break;
            }
        }
        if (bingo) {
            count++;
        }

        // Top left diagonal to bottom right
        c = BingoListAdapter.COLUMNS - 1;
        r = 0;
        bingo = true;
        for (int i = 0; i < Math.min(BingoListAdapter.COLUMNS, BingoListAdapter.ROWS); i++) {
            if (!getEntry((r + i) * BingoListAdapter.COLUMNS + (c - i)).isStamped()) {
                bingo = false;
                break;
            }
        }
        if (bingo) {
            count++;
        }
        return count;
    }

    public static class Holder extends ClickableAdapter.ViewHolder {
        public final View view;
        public final ImageView imageView;
        public final TextView nameView;
        public final ImageView stampView;

        public Holder(View v) {
            super(v);
            view = v;
            imageView = v.findViewById(R.id.image);
            nameView = v.findViewById(R.id.text);
            stampView = v.findViewById(R.id.stamp);
        }

        public int getColor(@ColorRes int id) {
            return view.getResources().getColor(id);
        }
    }

    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.bingo_square, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        super.onBindViewHolder(holder, position);
        BingoSquareData entry = mEntries.get(position);
        final boolean applyShadow = entry.getCroppedImageUri() != null || entry.isStamped();
        holder.nameView.setTextColor(applyShadow ? Color.WHITE : holder.getColor(R.color.bingo_dark_text));
        if (applyShadow) {
            holder.nameView.setShadowLayer(8f, 0, 0, Color.BLACK);
        } else {
            holder.nameView.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        }
        entry.loadImageInto(holder.imageView);
        holder.nameView.setText(Html.fromHtml(entry.getDisplayName()));
        holder.stampView.setVisibility(entry.isStamped() ? View.VISIBLE : View.GONE);

    }

    protected static void log(Object... txt) {
        String returnStr = "";
        int i = 1;
        int size = txt.length;
        if (size != 0) {
            returnStr = txt[0] == null ? "null" : txt[0].toString();
            for (; i < size; i++) {
                returnStr += ", "
                        + (txt[i] == null ? "null" : txt[i].toString());
            }
        }
        Log.i("lunch", returnStr);
    }
}
