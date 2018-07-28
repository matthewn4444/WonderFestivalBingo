package com.matthewn4444.wonderfestbingo;

import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class BingoSquareData implements Serializable {
    private static final String TAG = "BingoSquareData";

    public static final String FreeSpace = "Free Space";
    public static final String Unset = "Unset";

    public static final String[] DefaultItemNames = {
            "Anything", "Nendoroid", "Figma", "Prototype", "Painted Prototype", "Color Variant"
    };
    public static final List<String> DefaultItemNamesList = Arrays.asList(DefaultItemNames);

    private final long mId;
    private String mName;
    private String mCroppedImageUrl;
    private String mOriginalImageUrl;
    private boolean mStamped;

    private boolean mImageInvalided;

    public BingoSquareData(int id, String name, Uri imageUrl) {
        mId = id;
        mCroppedImageUrl = imageUrl != null ? imageUrl.toString() : null;
        setName(name);
    }

    public void setImageUri(Uri url) {
        mCroppedImageUrl = url != null ? url.toString() : null;
    }

    public void setOriginalImageUri(Uri url) {
        mOriginalImageUrl = url != null ? url.toString() : null;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            mName = null;
            mStamped = false;
        } else {
            mName = name;
        }
    }

    public boolean isUnique() {
        return mName != null && mName.equals(FreeSpace);
    }

    public boolean setStampedState(boolean stamped) {
        if (!isUnique()) {
            mStamped = stamped;
            return true;
        }
        return false;
    }

    public boolean toggleStamped() {
        if (!isUnique() && mName != null) {
            mStamped = !mStamped;
            return true;
        }
        return false;
    }

    public void loadImageInto(ImageView view) {
        if (mCroppedImageUrl != null) {
            RequestCreator creator = Picasso.get().load(mCroppedImageUrl);
            if (mImageInvalided) {
                creator.memoryPolicy(MemoryPolicy.NO_CACHE);
                mImageInvalided = false;
            }
            creator.into(view);
        } else {
            view.setImageDrawable(null);
        }
    }

    public long getId() {
        return mId;
    }

    public boolean isStamped() {
        return mStamped && mName != null;
    }

    public Uri getCroppedImageUri() {
        return mCroppedImageUrl != null ? Uri.parse(mCroppedImageUrl) : null;
    }

    public Uri getOriginalImageUri() {
        return mOriginalImageUrl != null ? Uri.parse(mOriginalImageUrl) : null;
    }

    public String getName() {
        return mName;
    }

    public String getDisplayName() {
        return mName != null ? mName : Unset;
    }

    public void invalidateImage() {
        mImageInvalided = true;
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
