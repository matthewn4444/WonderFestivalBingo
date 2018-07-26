package com.matthewn4444.wonderfestbingo;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadImageTask extends AsyncTask<String, Void, Boolean> {
    private static final String TAG = "DownloadImageTask";

    public interface OnDownloadImageFinishedListener {
        public void onDownloadImageFinished(boolean success);
    }

    private final File mPrivateModeFile;
    private final WeakReference<Context> mContext;
    private OnDownloadImageFinishedListener mListener;

    public DownloadImageTask(Context context) {
        this(context, null);
    }

    public DownloadImageTask(Context context, File privateModePath) {
        mContext = new WeakReference<>(context);
        mPrivateModeFile = privateModePath;
    }

    public void setOnDownloadImageFinishedListener(OnDownloadImageFinishedListener listener) {
        mListener = listener;
    }

    @Override
    protected Boolean doInBackground(String... urls) {
        String src = urls[0];
        String dst = urls[1];
        String dstTmp = dst + ".tmp";
        HttpURLConnection urlConnection;
        try {
            URL url = new URL(src);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoOutput(true);
            urlConnection.connect();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        try (FileOutputStream fileOutput = mPrivateModeFile != null
                ? mContext.get().openFileOutput(dstTmp, Context.MODE_PRIVATE)
                : new FileOutputStream(dstTmp)) {
            InputStream inputStream = urlConnection.getInputStream();
            byte[] buffer = new byte[1024];
            int bufferLength;
            while ((bufferLength = inputStream.read(buffer)) > 0) {
                if (isCancelled()) {
                    urlConnection.disconnect();
                    return false;
                }
                fileOutput.write(buffer, 0, bufferLength);
            }

            // Move the file
            File srcFile = new File(mPrivateModeFile, dstTmp);
            File dstFile = new File(mPrivateModeFile, dst);
            return srcFile.renameTo(dstFile);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        if (mListener != null) {
            mListener.onDownloadImageFinished(false);
        } else {
            Log.v(TAG, "No listener, image download cancelled");
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        super.onPostExecute(success);
        if (mListener != null) {
            mListener.onDownloadImageFinished(success);
        } else {
            Log.v(TAG, "No listener, image finished, success? " + success);
        }
    }
}
