package com.matthewn4444.wonderfestbingo;

import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.io.Serializable;

public class BingoSquareData implements Serializable {
    private static String TAG = "BingoSquareData";

    public static String DefaultName = "Figure";

    public enum TYPE {
        NENDOROID("Nendoroid"),
        FIGMA("Figma"),
        PROTOTYPE("Prototype"),
        FREE_SPACE("Free Space"),
        PAINTED_PROTOTYPE("Painted Prototype"),
        COLOR_VARIANT("Color Variant"),
        UNSET("Unset");

        public static TYPE from(String name) {
            for (TYPE type: TYPE.values()) {
                if (type.text.equals(name)) {
                    return type;
                }
            }
            return null;
        }

        TYPE(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }

        String text;
    }

    public static String[] Options = {
            TYPE.NENDOROID.text, TYPE.FIGMA.text, TYPE.PROTOTYPE.text, TYPE.PAINTED_PROTOTYPE.text,
            TYPE.COLOR_VARIANT.text
    };

    private TYPE mType;
    private String mName = DefaultName;
    private String mCroppedImageUrl;
    private String mOriginalImageUrl;
    private boolean mStamped;

    private boolean mImageInvalided;

    public BingoSquareData() {
    }

    public BingoSquareData(TYPE type, String name, Uri imageUrl) {
        mCroppedImageUrl = imageUrl != null ? imageUrl.toString() : null;
        setName(name);
        mType = type;
    }

    public void setType(TYPE type) {
        if (mType != TYPE.FREE_SPACE) {
            mType = type;
        }
    }

    public void setImageUri(Uri url) {
        mCroppedImageUrl = url != null ? url.toString() : null;
    }

    public void setOriginalImageUri(Uri url) {
        mOriginalImageUrl = url != null ? url.toString() : null;
    }

    public void setName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            mName = name;
        }
    }

    public void setStampedState(boolean stamped) {
        mStamped = stamped;
    }

    public void loadImageInto(ImageView view) {
        if (mCroppedImageUrl != null) {
            RequestCreator creator = Picasso.get().load(mCroppedImageUrl);
            if (mImageInvalided) {
                creator.memoryPolicy(MemoryPolicy.NO_CACHE);
                mImageInvalided = false;
            }
            creator.into(view);
        }
    }

    public Uri getCroppedImageUri() {
        return mCroppedImageUrl != null ? Uri.parse(mCroppedImageUrl) : null;
    }

    public Uri getOriginalImageUri() {
        return mOriginalImageUrl != null ? Uri.parse(mOriginalImageUrl) : null;
    }

    public TYPE getType() {
        return mType;
    }

    public String getName() {
        return mName;
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
