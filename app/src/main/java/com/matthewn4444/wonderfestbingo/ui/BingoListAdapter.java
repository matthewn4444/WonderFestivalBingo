package com.matthewn4444.wonderfestbingo.ui;

import android.graphics.Color;
import android.support.annotation.ColorRes;
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
import java.util.List;

public class BingoListAdapter extends ClickableAdapter<BingoListAdapter.Holder, BingoSquareData> {

    public static final int ROWS = 5;
    public static final int COLUMNS = 5;
    public static final int MAX_ITEMS = ROWS * COLUMNS;

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

    private void init() {
        for (int i = getItemCount(); i < MAX_ITEMS; i++) {
            add(new BingoSquareData(BingoSquareData.TYPE.UNSET, null, null));
        }

        // Set the hardcoded free space
        BingoSquareData data = getEntry(ROWS / 2 * COLUMNS + COLUMNS / 2);
        data.setType(BingoSquareData.TYPE.FREE_SPACE);
        notifyDataSetChanged();
    }

    public static class Holder extends ClickableAdapter.ViewHolder {
        public final View view;
        public final ImageView imageView;
        public final TextView nameView;

        public Holder(View v) {
            super(v);
            view = v;
            imageView = v.findViewById(R.id.image);
            nameView = v.findViewById(R.id.text);
        }

        public int getColor(@ColorRes int id) {
            return view.getResources().getColor(id);
        }
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.bingo_square, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        super.onBindViewHolder(holder, position);
        BingoSquareData entry = mEntries.get(position);
        final boolean filledImage = entry.getCroppedImageUri() != null;
        holder.nameView.setTextColor(filledImage ? Color.WHITE : holder.getColor(R.color.bingo_dark_text));
        if (filledImage) {
            holder.nameView.setShadowLayer(8f, 0, 0, Color.BLACK);
        } else {
            holder.nameView.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        }
        entry.loadImageInto(holder.imageView);
        holder.nameView.setText(Html.fromHtml(entry.getType().text()));
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
