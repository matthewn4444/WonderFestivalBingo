package com.matthewn4444.wonderfestbingo;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;

import com.matthewn4444.wonderfestbingo.utils.SimpleHtmlParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;

public class DownloadImageTask extends AsyncTask<String, Void, Boolean> {
    private static final String TAG = "DownloadImageTask";
    private static final String MFC_URL = "myfigurecollection.net";
    private static final String MFC_STATIC_URL = "static.myfigurecollection.net";
    private static final String MFC_URL_PIC = "myfigurecollection.net/picture/";
    private static final String MFC_URL_ITEM = "myfigurecollection.net/item/";

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
        boolean fromMFC = src.contains(MFC_URL) && !src.contains(MFC_STATIC_URL);
        if (fromMFC && (!src.contains(MFC_URL_PIC) && !src.contains(MFC_URL_ITEM))) {
            Log.w(TAG, "MFC url not supported: " + src);
            return false;
        }

        HttpURLConnection urlConnection = get(src);
        if (urlConnection == null) {
            return false;
        }

        if (fromMFC) {
            String text = getResponseText(urlConnection);
            if (text == null) {
                Log.w(TAG, "Cannot get response text from connection, " + src);
                return false;
            }
            src = extractMFCImageUrl(src, text);
            log("MFC", src);
            if (src == null) {
                return false;
            }

            urlConnection = get(src);
            if (urlConnection == null) {
                Log.w(TAG, "Failed to download image from MFC url, " + src);
                return false;
            }
        }
        return downloadConnection(urlConnection, dst, dstTmp);
    }

    @Nullable
    private String extractMFCImageUrl(String src, String html) {
        // Search for the image url in MFC before downloading
        SimpleHtmlParser parser = new SimpleHtmlParser(html);
        try {
            if (src.contains(MFC_URL_PIC)) {
                parser.skipText("the-picture", "<img");
                return parser.getAttributeInCurrentElement("src");
            } else if (src.contains(MFC_URL_ITEM)) {
                parser.skipText("Open official gallery", "<img");
                String link = parser.getAttributeInCurrentElement("src");
                if (link.contains("/big/")) {
                    link = link.replace("/big/", "/large/");
                }
                return link;
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    private HttpURLConnection get(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setConnectTimeout(8000);
            urlConnection.setReadTimeout(10000);
            urlConnection.setInstanceFollowRedirects(true);
            urlConnection.connect();
            return urlConnection;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    @Nullable
    private String getResponseText(HttpURLConnection connection) {
        try (BufferedReader reader = new BufferedReader(getConnectionStream(connection))) {
            StringBuilder stringBuilder = new StringBuilder();

            String line;
            while (!isCancelled() && (line = reader.readLine()) != null) {
                stringBuilder.append(line).append('\n');
            }
            if (isCancelled()) {
                return null;
            }
            return stringBuilder.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private InputStreamReader getConnectionStream(HttpURLConnection connection) throws IOException {
        return new InputStreamReader((connection.getResponseCode() / 100) == 2 ?
                connection.getInputStream() : connection.getErrorStream());
    }

    private boolean downloadConnection(HttpURLConnection urlConnection, String dst, String dstTmp) {
        try (FileOutputStream fileOutput = mPrivateModeFile != null
                ? mContext.get().openFileOutput(dstTmp, Context.MODE_PRIVATE)
                : new FileOutputStream(dstTmp)) {
            log("urlConnection", urlConnection.getResponseCode());
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
