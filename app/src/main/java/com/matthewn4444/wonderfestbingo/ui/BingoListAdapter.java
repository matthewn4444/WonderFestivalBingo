package com.matthewn4444.wonderfestbingo.ui;

import android.graphics.Color;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
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
    private static final String TAG = "BingoListAdapter";
    public static final int DEFAULT_FONT_SIZE = 14;

    private int mFontSize = DEFAULT_FONT_SIZE;
    private final String mPrefix;
    protected final int mSideSize;

    public BingoListAdapter(int sideSize, @NonNull String prefix) {
        super(new ArrayList<BingoSquareData>(sideSize * sideSize));
        mSideSize = sideSize;
        mPrefix = prefix;
        init(prefix);
    }

    public BingoListAdapter(@NonNull List<BingoSquareData> data, @Nullable String prefix) {
        super(data);

        mSideSize = (int) Math.sqrt(data.size());
        mPrefix = prefix;
        int size = mSideSize * mSideSize;
        if (size != data.size()) {
            Log.w(TAG, "Data length should be able to be square root, will be missing data");
        }

        // If there are too many items, remove the access
        if (data.size() > size) {
            data.subList(size, data.size()).clear();
        }
        init(prefix);
    }

    public void shuffle() {
        Collections.shuffle(mEntries);
        notifyDataSetChanged();
    }

    public void swap(int first, int second) {
        Collections.swap(mEntries, first, second);
    }

    public void setFontSize(int spValue) {
        mFontSize = spValue;
        notifyDataSetChanged();
    }

    public String getPrefix() {
        return mPrefix;
    }

    public int getBingoCount() {
        int count = 0;
        // Loop throw each row
        boolean bingo;
        for (int r = 0; r < mSideSize; r++) {
            bingo = true;
            for (int c = 0; c < mSideSize; c++) {
                if (!getEntry(r * mSideSize  /* columns */ + c).isStamped()) {
                    bingo = false;
                    break;
                }
            }
            if (bingo) {
                count++;
            }
        }

        // Loop through columms
        for (int c = 0; c < mSideSize; c++) {
            bingo = true;
            for (int r = 0; r < mSideSize; r++) {
                if (!getEntry(r * mSideSize  /* columns */ + c).isStamped()) {
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
        for (int i = 0; i < mSideSize /* should be min of width and height */; i++) {
            if (!getEntry((r + i) * mSideSize /* columns */ + (c + i)).isStamped()) {
                bingo = false;
                break;
            }
        }
        if (bingo) {
            count++;
        }

        // Top left diagonal to bottom right
        c = mSideSize - 1;
        r = 0;
        bingo = true;
        for (int i = 0; i < mSideSize /* should be min of width and height */; i++) {
            if (!getEntry((r + i) * mSideSize /* columns */ + (c - i)).isStamped()) {
                bingo = false;
                break;
            }
        }
        if (bingo) {
            count++;
        }
        return count;
    }

    private void init(String prefix) {
        for (int i = getItemCount(); i < mSideSize * mSideSize; i++) {
            add(new BingoSquareData(i, null, null, prefix));
        }
        postSquareChange();
        setHasStableIds(true);
        notifyDataSetChanged();
    }

    static class Holder extends ClickableAdapter.ViewHolder {
        final View view;
        final ImageView imageView;
        final TextView nameView;
        final ImageView stampView;

        Holder(View v) {
            super(v);
            view = v;
            imageView = v.findViewById(R.id.image);
            nameView = v.findViewById(R.id.text);
            stampView = v.findViewById(R.id.stamp);
        }

        int getColor(@ColorRes int id) {
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
        boolean applyShadow = entry.getCroppedImageUri() != null || entry.isStamped();
        holder.nameView.setTextColor(applyShadow ? Color.WHITE : holder.getColor(R.color.bingo_dark_text));
        if (applyShadow) {
            holder.nameView.setShadowLayer(8f, 0, 0, Color.BLACK);
        } else {
            holder.nameView.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        }
        entry.loadImageInto(holder.imageView);
        holder.nameView.setText(Html.fromHtml(entry.getDisplayName()));
        holder.nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mFontSize);
        holder.stampView.setVisibility(entry.isStamped() ? View.VISIBLE : View.GONE);
    }

    @Override
    public long getItemId(int position) {
        return mEntries.get(position).getId();
    }

    protected void postSquareChange() {
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
