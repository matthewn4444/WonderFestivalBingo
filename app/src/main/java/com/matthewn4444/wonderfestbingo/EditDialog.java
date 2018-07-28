package com.matthewn4444.wonderfestbingo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.matthewn4444.wonderfestbingo.ui.BingoListAdapter;
import com.matthewn4444.wonderfestbingo.ui.InstantAutoCompleteTextView;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.yalantis.ucrop.UCrop;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class EditDialog {
    private static final String TAG = "EditDialog";
    private static final int OPEN_IMAGE_REQUEST_CODE = 10;
    private static final int MAX_CROP_SIZE = 500;
    private static final int INVALID_ID = -1;
    private static final float DISABLED_BUTTON_ALPHA = 0.4f;

    public interface OnEditDialogCompleteListener {
        public void onEditDialogComplete(long id, BingoSquareData data);
    }

    private final Activity mActivity;
    private final ClipboardManager mClipboardManager;

    private AlertDialog mDialog;
    private InstantAutoCompleteTextView mNameText;
    private ImageView mPreviewView;
    private long mCurrentSquareId;
    private BingoSquareData mCurrentData;
    private Uri mLastCroppedImageUri;
    private Uri mLastOriginalImageUri;
    private View mTypeContainer;
    private Button mPasteButton;
    private Button mOpenButton;
    private Button mDeleteImageButton;
    private OnEditDialogCompleteListener mListener;
    private boolean mCopying;

    private DownloadImageTask mImageDownloadTask;
    private ProgressDialog mImageDownloadDialog;

    public EditDialog(Activity context) {
        mActivity = context;
        mClipboardManager = (ClipboardManager) mActivity
                .getSystemService(Context.CLIPBOARD_SERVICE);
    }

    private final DialogInterface.OnClickListener mOkListener =
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (mCurrentData != null) {
                        // If chosen an image, then move any temp files as new files
                        if (mLastCroppedImageUri != null) {
                            File original = getOriginalFilePath(mCurrentSquareId, false);
                            File crop = getCropFilePath(mCurrentSquareId, false);
                            File originalTmp = getOriginalFilePath(mCurrentSquareId, true);
                            File cropTmp = getCropFilePath(mCurrentSquareId, true);

                            // If temp files exist, delete original files if exist before rename
                            if (originalTmp.exists()) {
                                if (original.exists() && !original.delete()) {
                                    toast("Cannot delete original image for new one.");
                                    return;
                                }
                                if (!originalTmp.renameTo(original)) {
                                    toast("Cannot set new original image.");
                                    return;
                                }
                                mLastOriginalImageUri = Uri.fromFile(original);
                            }
                            if (cropTmp.exists()) {
                                if (crop.exists() && !crop.delete()) {
                                    toast("Cannot delete crop image for new one.");
                                    return;
                                }
                                if (!cropTmp.renameTo(crop)) {
                                    toast("Cannot set new crop image.");
                                    return;
                                }
                                mLastCroppedImageUri = Uri.fromFile(crop);
                            }
                        }

                        mCurrentData.setName(mNameText.getText().toString().trim());
                        mCurrentData.setImageUri(mLastCroppedImageUri);
                        mCurrentData.setOriginalImageUri(mLastOriginalImageUri);
                    }
                    if (mListener != null) {
                        mListener.onEditDialogComplete(mCurrentSquareId, mCurrentData);
                        reset();
                    }
                }
            };

    private final DialogInterface.OnClickListener mCancelListener =
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    deleteImageFiles(mCurrentSquareId, true);
                    reset();
                }
            };

    private final DownloadImageTask.OnDownloadImageFinishedListener mImageDownloadListener =
            new DownloadImageTask.OnDownloadImageFinishedListener() {
                @Override
                public void onDownloadImageFinished(boolean success) {
                    mImageDownloadTask = null;
                    mImageDownloadDialog.hide();
                    if (!success) {
                        toast("Unable to download image from paste");
                        return;
                    }

                    // Crop downloaded image
                    File file = getOriginalFilePath(mCurrentSquareId, true);
                    if (file.exists()) {
                        mLastOriginalImageUri = Uri.fromFile(file);
                        crop(mLastOriginalImageUri);
                    } else {
                        toast("Cannot crop photo because downloaded file does not exist");
                    }
                }
            };


    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case UCrop.REQUEST_CROP:
                    log("Request crop now");
                    mLastCroppedImageUri = UCrop.getOutput(data);
                    loadPreview(true);
                    mPreviewView.setClickable(true);
                    if (mCurrentData != null) {
                        mCurrentData.invalidateImage();
                    } else {
                        toast("Cannot update image, invalid data used");
                    }
                    return true;
                case OPEN_IMAGE_REQUEST_CODE:
                    if (mCurrentSquareId <= INVALID_ID) {
                        toast("Cannot crop, invalid square");
                        break;
                    }
                    if (data != null && data.getData() != null) {
                        mLastOriginalImageUri = data.getData();
                        crop(data.getData());

                        // Copy
                        mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                        mOpenButton.setEnabled(false);
                        mPasteButton.setEnabled(false);
                        AsyncTask.execute(new Runnable() {
                            @Override
                            public void run() {
                                Uri dst = copyFile(mLastOriginalImageUri);
                                if (dst != null) {
                                    mLastOriginalImageUri = dst;
                                    mActivity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                                    .setEnabled(true);
                                            mOpenButton.setEnabled(true);
                                            mPasteButton.setEnabled(true);
                                        }
                                    });
                                } else {
                                    mActivity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            toast("Failed to copy source image");
                                        }
                                    });
                                }
                            }
                        });
                    }
                    return true;
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            switch (requestCode) {
                case UCrop.REQUEST_CROP:
                    // If crop is cancelled, set the original image from passed in data
                    mLastOriginalImageUri = mCurrentData.getOriginalImageUri();
                    return true;
            }
        }
        return false;
    }

    public void setOnEditDialogCompleteListener(OnEditDialogCompleteListener listener) {
        mListener = listener;
    }

    public void show(BingoSquareData data, List<String> names) {
        if (data.getId() <= INVALID_ID) {
            toast("Cannot show because invalid square");
            return;
        }
        if (mDialog == null) {
            LayoutInflater inflater = LayoutInflater.from(mActivity);
            @SuppressLint("InflateParams")
            View dialogView = inflater.inflate(R.layout.edit_squares_dialog_layout, null);
            mDialog = new AlertDialog.Builder(mActivity)
                    .setTitle(R.string.dialog_edit_squares_title)
                    .setView(dialogView)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, mOkListener)
                    .setNeutralButton(android.R.string.cancel, mCancelListener)
                    .create();

            mPreviewView = dialogView.findViewById(R.id.preview);
            mNameText = dialogView.findViewById(R.id.dropdown_name_text);
            mTypeContainer = dialogView.findViewById(R.id.type_container);
            mPasteButton = dialogView.findViewById(R.id.paste_and_crop_button);
            mOpenButton = dialogView.findViewById(R.id.open_and_crop_button);
            mDeleteImageButton = dialogView.findViewById(R.id.delete_image_button);

            // Setup name field
            mNameText.setThreshold(0);

            // Setup the button listeners
            mPasteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mCopying) {
                        toast("There is a copying session occuring");
                        return;
                    }
                    if (mCurrentSquareId <= INVALID_ID) {
                        toast("The current square is invalid by id");
                        return;
                    }
                    if (!mClipboardManager.hasPrimaryClip()
                            || mClipboardManager.getPrimaryClip().getItemCount() == 0) {
                        toast("Your clipboard is empty");
                        return;
                    }
                    String url = mClipboardManager.getPrimaryClip().getItemAt(0)
                            .getText().toString();
                    if (!URLUtil.isNetworkUrl(url)) {
                        toast("Your clipboard is not a valid url");
                        return;
                    }
                    mImageDownloadTask = new DownloadImageTask(mActivity, mActivity.getFilesDir());
                    mImageDownloadTask.setOnDownloadImageFinishedListener(mImageDownloadListener);
                    mImageDownloadTask.execute(url, mCurrentSquareId + ".jpg.tmp");

                    mImageDownloadDialog = new ProgressDialog(mActivity);
                    mImageDownloadDialog.setTitle(R.string.dialog_edit_squares_download_title);
                    mImageDownloadDialog.setCancelable(true);
                    mImageDownloadDialog.setOnCancelListener(
                            new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    if (mImageDownloadTask != null) {
                                        mImageDownloadTask.cancel(true);
                                        mImageDownloadTask = null;
                                    }
                                }
                            });
                    mImageDownloadDialog.show();
                }
            });
            mOpenButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mCopying) {
                        toast("There is a copying session occuring");
                        return;
                    }
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    mActivity.startActivityForResult(intent, OPEN_IMAGE_REQUEST_CODE);
                }
            });
            mDeleteImageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPreviewView.setImageDrawable(null);
                    mLastCroppedImageUri = null;
                    mLastOriginalImageUri = null;
                    mDeleteImageButton.setEnabled(false);
                    mDeleteImageButton.animate().alpha(DISABLED_BUTTON_ALPHA).start();
                }
            });

            dialogView.findViewById(R.id.preview).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mCopying) {
                                toast("There is a copying session occuring");
                                return;
                            }
                            if (mCurrentData == null) {
                                toast("Cannot edit image because no data");
                                return;
                            }
                            if (mLastOriginalImageUri != null) {
                                crop(mLastOriginalImageUri);
                            }
                        }
                    });
            dialogView.findViewById(R.id.clear_name_button).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mNameText.setText(null);
                            mNameText.requestFocus();
                        }
                    });
        }
        reset();

        // Set current data
        mCurrentSquareId = data.getId();
        mCurrentData = data;
        mLastCroppedImageUri = mCurrentData.getCroppedImageUri();
        mLastOriginalImageUri = mCurrentData.getOriginalImageUri();
        mDialog.setTitle(mActivity.getString(R.string.dialog_edit_squares_title));

        // Populate existing values to dialog
        mNameText.setAdapter(new ArrayAdapter<>(mActivity,
                android.R.layout.simple_dropdown_item_1line, names));
        mNameText.setText(mCurrentData.getName());
        if (mCurrentSquareId == BingoListAdapter.FREE_SPACE_INDEX) {
            mTypeContainer.setVisibility(View.GONE);
            mDialog.setTitle(mActivity.getString(R.string.dialog_edit_squares_free_space_title));
        }
        if (mLastCroppedImageUri != null) {
            loadPreview(false);
        } else {
            mPreviewView.setClickable(false);
        }
        mDialog.show();
    }

    public void hide() {
        if (mDialog != null) {
            mDialog.hide();
        }
        reset();
    }

    private void reset() {
        if (mImageDownloadTask != null) {
            mImageDownloadTask.cancel(true);
        }
        mImageDownloadTask =  null;
        mCopying = false;
        mCurrentSquareId = INVALID_ID;
        mCurrentData = null;
        mLastCroppedImageUri = null;
        mLastOriginalImageUri = null;

        if (mImageDownloadDialog != null) {
            mImageDownloadDialog.hide();
        }
        mDeleteImageButton.setEnabled(false);
        mDeleteImageButton.setAlpha(DISABLED_BUTTON_ALPHA);
        mOpenButton.setEnabled(true);
        mPasteButton.setEnabled(true);
        mNameText.setText(null);
        mPreviewView.setImageDrawable(null);
        mPreviewView.setClickable(true);
        mTypeContainer.setVisibility(View.VISIBLE);
    }

    private void toast(String text) {
        Toast.makeText(mActivity, text, Toast.LENGTH_SHORT).show();
    }

    private void crop(Uri source) {
        Uri destinationUri = Uri.fromFile(getCropFilePath(mCurrentSquareId, true));
        UCrop.of(source, destinationUri)
                .withAspectRatio(1, 1)
                .withMaxResultSize(MAX_CROP_SIZE, MAX_CROP_SIZE)
                .start(mActivity);
    }

    private void loadPreview(boolean noCache) {
        RequestCreator creator = Picasso.get().load(mLastCroppedImageUri);
        if (noCache) {
            creator.memoryPolicy(MemoryPolicy.NO_CACHE);
        }
        creator.into(mPreviewView);
        mDeleteImageButton.setEnabled(true);
        mDeleteImageButton.animate().alpha(1f).start();
    }

    private boolean deleteImageFiles(long id, boolean temp) {
        boolean res1 = getOriginalFilePath(id, temp).delete();
        boolean res2 = getCropFilePath(id, temp).delete();
        return res1 && res2;
    }

    private File getCropFilePath(long id, boolean temp) {
        return new File(mActivity.getFilesDir(),id + "_crop.jpg" + (temp ? ".tmp" : ""));
    }

    private File getOriginalFilePath(long id, boolean temp) {
        return new File(mActivity.getFilesDir(),id + ".jpg" + (temp ? ".tmp" : ""));
    }

    private Uri copyFile(Uri sourceUri) {
        if (mCopying) {
            Log.w(TAG, "You are already copying, prevented this copy");
            return null;
        }
        boolean success;
        String dstFileName = mCurrentSquareId + ".jpg.tmp";
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;

        try {
            bis = new BufferedInputStream(Objects.requireNonNull(mActivity.getContentResolver()
                    .openInputStream(sourceUri)));
            bos = new BufferedOutputStream(mActivity.openFileOutput(dstFileName,
                    Context.MODE_PRIVATE));
            byte[] buf = new byte[1024];
            bis.read(buf);
            mCopying = true;
            do {
                bos.write(buf);
            } while (bis.read(buf) != -1 && mCopying);
        } catch (IOException e) {
            e.printStackTrace();
            mCopying = false;
        } finally {
            try {
                if (bis != null) bis.close();
                if (bos != null) bos.close();
            } catch (IOException e) {
                e.printStackTrace();
                mCopying = false;
            } finally {
                success = mCopying;
                mCopying = false;
            }
        }
        return success ? Uri.fromFile(new File(mActivity.getFilesDir(), dstFileName)) : null;
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
