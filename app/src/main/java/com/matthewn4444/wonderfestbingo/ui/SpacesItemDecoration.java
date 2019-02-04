package com.matthewn4444.wonderfestbingo.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;

public class SpacesItemDecoration extends RecyclerView.ItemDecoration {
    private int space;
    private Paint paint;

    public SpacesItemDecoration(int space) {
        this.space = space;
        paint = new Paint();
        paint.setColor(Color.BLACK);
    }

    @Override
    public void getItemOffsets(Rect outRect, View view,
                               RecyclerView parent, RecyclerView.State state) {
        outRect.left = space;
        outRect.right = space;
        outRect.bottom = space;
        outRect.top = space;
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);

            // Top border
            c.drawRect(child.getLeft() - space, child.getTop() - space,
                    child.getRight() + space, child.getTop(), paint);

            // Left border
            c.drawRect(child.getLeft() - space, child.getTop() - space, child.getLeft(),
                    child.getBottom() + space, paint);

            // Right border
            c.drawRect(child.getRight(), child.getTop() - space, child.getRight() + space,
                    child.getBottom() + space, paint);

            // Bottom border
            c.drawRect(child.getLeft() - space, child.getBottom() - space,
                    child.getRight() + space, child.getBottom() + space, paint);

        }
        super.onDraw(c, parent, state);
    }
}
