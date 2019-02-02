package com.matthewn4444.wonderfestbingo.ui;

import com.matthewn4444.wonderfestbingo.BingoSquareData;

import java.util.Collections;
import java.util.List;

public class BingoCardAdapter extends BingoListAdapter {
    public static final int ROWS = 5;
    public static final int COLUMNS = 5;
    public static final int MAX_ITEMS = ROWS * COLUMNS;
    public static final int FREE_SPACE_INDEX = ROWS / 2 * COLUMNS + COLUMNS / 2;

    public BingoCardAdapter() {
        super(ROWS, null);
    }

    public BingoCardAdapter(List<BingoSquareData> data) {
        super(data, null);
    }

    @Override
    public void shuffle() {
        BingoSquareData freespace = mEntries.remove(FREE_SPACE_INDEX);
        Collections.shuffle(mEntries);
        mEntries.add(FREE_SPACE_INDEX, freespace);
        notifyDataSetChanged();
    }

    @Override
    protected void postSquareChange() {
        super.postSquareChange();

        // Set the hardcoded free space
        BingoSquareData data = getEntry(FREE_SPACE_INDEX);
        data.setStampedState(true);
        data.setName(BingoSquareData.FreeSpace);
    }
}
