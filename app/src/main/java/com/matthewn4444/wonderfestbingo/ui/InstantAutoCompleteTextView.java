package com.matthewn4444.wonderfestbingo.ui;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.AppCompatAutoCompleteTextView;
import android.util.AttributeSet;
import android.view.View;

public class InstantAutoCompleteTextView extends AppCompatAutoCompleteTextView {

    public InstantAutoCompleteTextView(Context context) {
        super(context);
        init();
    }

    public InstantAutoCompleteTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public InstantAutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @Override
    public boolean enoughToFilter() {
        return true;
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        showDropDownIfFocused();
    }

    private void init() {
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showDropDownIfFocused();
            }
        });
    }

    private void showDropDownIfFocused() {
        if (enoughToFilter() && isFocused() && getWindowVisibility() == View.VISIBLE
                && !isPopupShowing()) {
            showDropDown();
        }
    }

}