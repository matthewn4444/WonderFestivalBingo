package com.matthewn4444.wonderfestbingo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RawRes;
import android.support.v4.view.animation.PathInterpolatorCompat;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.TextView;
import android.widget.Toast;

import com.matthewn4444.wonderfestbingo.ui.BingoListAdapter;
import com.matthewn4444.wonderfestbingo.ui.RecyclerViewAdapterListener;
import com.matthewn4444.wonderfestbingo.ui.SpacesItemDecoration;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends BaseActivity implements RecyclerViewAdapterListener,
        ActionMode.Callback, EditDialog.OnEditDialogCompleteListener {
    private static final String TAG = "MainActivity";
    private static final String SAVE_FILE = "savedata.dat";
    private static final float SOUND_EFFECT_VOLUME = 0.3f;

    private static final int LOAD_SUCCESS = 1;
    private static final int LOAD_ERROR = 2;
    private static final int LOAD_ERROR_MAY_DELETE = 3;

    private final Object mFileLock = new Object();

    private RecyclerView mBingoView;
    private int mBingoCount;
    private View mBingoImage;
    private int mBingoImgHeight;
    private int mDisplayHeight;
    private BingoListAdapter mAdapter;
    private ValueAnimator mBingoAnimator;
    private RecyclerView.LayoutManager mLayoutManager;
    private ActionMode mActionMode;
    private EditDialog mDialog;
    private String mNameDataBeforeDialog;

    private List<String> mUniqueNamesList = new ArrayList<>(BingoListAdapter.MAX_ITEMS);
    private Map<String, Integer> mNamesMap = new HashMap<>(BingoListAdapter.MAX_ITEMS);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView subtitleText = findViewById(R.id.year_season_text);
        mBingoView = findViewById(R.id.bingo);
        mBingoImage = findViewById(R.id.bingo_image);
        mDialog = new EditDialog(this);
        mDialog.setOnEditDialogCompleteListener(this);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        mDisplayHeight = displayMetrics.heightPixels;

        // Setup title font
        AssetManager am = getApplicationContext().getAssets();
        final Typeface typeface = Typeface.createFromAsset(am,
                String.format(Locale.US, "fonts/%s", "milford_black.ttf"));
        subtitleText.setTypeface(typeface);
        Calendar calendar = Calendar.getInstance();
        subtitleText.setText(String.format(Locale.getDefault(), "%d [%s]",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) > 4 ? "Summer" : "Winter"));

        // Setup bingo layout
        mLayoutManager = new GridLayoutManager(this, 5);
        mBingoView.setLayoutManager(mLayoutManager);
        mBingoView.addItemDecoration(new SpacesItemDecoration(
                (int) getResources().getDimension(R.dimen.bingo_divider_size)));
        mBingoView.hasFixedSize();

        // Get the bingo image height based on screen width
        ViewTreeObserver viewTreeObserver = mBingoImage.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mBingoImage.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    mBingoImgHeight = mBingoImage.getHeight();
                    mBingoImage.setTranslationY(-mBingoImgHeight);
                    mBingoImage.setVisibility(View.GONE);
                    log("mBingoImgHeight", mBingoImgHeight);
                }
            });
        }

        if (verifyStoragePermissionsOrShowDialogs()) {
            loadData();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadData();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.edit:
                if (mActionMode != null || !verifyStoragePermissionsOrShowDialogs()) {
                    break;
                }
                mActionMode = startSupportActionMode(this);
                if (mActionMode != null) {
                    mActionMode.setTitle(R.string.action_mode_edit);
                } else {
                    Toast.makeText(this, "Edit mode failed to start", Toast.LENGTH_SHORT)
                            .show();
                    break;
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            mDialog.handleActivityResult(requestCode, resultCode, data);
        } else if (resultCode == UCrop.RESULT_ERROR) {
            final Throwable cropError = UCrop.getError(data);
            if (cropError != null) {
                Log.e(TAG, cropError.getMessage(), cropError);
            }
        }
    }

    @Override
    public void onEditDialogComplete(long id, BingoSquareData data) {
        mAdapter.notifyDataSetChanged();
        if (!data.isUnique()
                && (mNameDataBeforeDialog != null && !mNameDataBeforeDialog.equals(data.getName())
                || data.getName() != null && !data.getName().equals(mNameDataBeforeDialog))) {
            if (mNameDataBeforeDialog != null) {
                // Remove from list if exists
                removeNameFromList(mNameDataBeforeDialog);
            }
            if (data.getName() != null) {
                addNameToList(data.getName());
            }
        }
        mNameDataBeforeDialog = null;
        saveData();
    }

    @Override
    public void onClick(View v, int position) {
        if (mBingoAnimator != null && mBingoAnimator.isRunning()) {
            return;
        }
        if (mActionMode != null) {
            BingoSquareData data = mAdapter.getEntry(position);
            mNameDataBeforeDialog = data.getName();
            mDialog.show(data, mUniqueNamesList);
        } else {
            // Stamping time
            BingoSquareData data = mAdapter.getEntry(position);
            if (data.toggleStamped()) {
                mAdapter.notifyItemChanged(position);
                saveData();

                // See if we just bingo-ed!
                int count = mAdapter.getBingoCount();
                if (mBingoCount < count) {
                    // Animate the bingo image down the screen
                    mBingoAnimator = ValueAnimator.ofInt(-mBingoImgHeight, mDisplayHeight);
                    mBingoAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            mBingoImage.setTranslationY((int) animation.getAnimatedValue());
                            float fraction = animation.getAnimatedFraction();
                            mBingoImage.setAlpha(- 4 * fraction * (fraction - 1));
                        }
                    });
                    mBingoAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            super.onAnimationStart(animation);
                            mBingoImage.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            mBingoImage.setVisibility(View.GONE);
                            mBingoAnimator = null;
                        }
                    });
                    mBingoAnimator.setInterpolator(PathInterpolatorCompat.create(0.2f, 0.5f, 0.4f, 0.5f));
                    mBingoAnimator.setDuration(3000);
                    mBingoAnimator.start();
                    playBingoSound();
                } else if (data.isStamped()) {
                    playStampSound();
                }
                mBingoCount = count;
            }
            v.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                    HapticFeedbackConstants.CONTEXT_CLICK : HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        mDialog.hide();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onLongClick(View v, int position) {
        Toast.makeText(this, mAdapter.getEntry(position).getName(), Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        getMenuInflater().inflate(R.menu.action_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.exit:
                mode.finish();
                return true;
            case R.id.shuffle:
                mAdapter.shuffle();
                saveData();
                mBingoCount = mAdapter.getBingoCount();
                return true;
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
    }

    private void initAdapter() {
        // Add the default names if it doesnt already exist
        for (String name: BingoSquareData.DefaultItemNamesList) {
            if (!mNamesMap.containsKey(name)) {
                addNameToList(name);
            }
        }

        // Put the data in the adapter
        if (mAdapter == null) {
            mAdapter = new BingoListAdapter();
        }
        mAdapter.setAdapterListener(MainActivity.this);
        mBingoView.setAdapter(mAdapter);
        mBingoCount = mAdapter.getBingoCount();
    }

    private void playStampSound() {
        playSoundEffect(R.raw.stamp);
    }

    private void playBingoSound() {
        playSoundEffect(R.raw.stamp);
//        playSoundEffect(R.raw.bingo);
    }

    private void playSoundEffect(@RawRes int id) {
        MediaPlayer mp = MediaPlayer.create(getApplicationContext(), id);
        mp.setVolume(SOUND_EFFECT_VOLUME, SOUND_EFFECT_VOLUME);
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.setOnCompletionListener(null);
                mp.reset();
                mp.release();
            }
        });
        mp.start();
    }

    private void saveData() {
        // Save the data
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (mFileLock) {
                    try (ObjectOutputStream stream = new ObjectOutputStream(
                            openFileOutput(SAVE_FILE, Context.MODE_PRIVATE))) {
                        for (int i = 0; i < BingoListAdapter.MAX_ITEMS; i++) {
                            stream.writeObject(mAdapter.getEntry(i));
                        }
                        stream.flush();         // TODO prob dont need this
                    } catch (IOException e) {
                        e.printStackTrace();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Unable to save",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }
        });
    }

    private void loadData() {
        final File saveFile = new File(getFilesDir(), SAVE_FILE);
        if (!saveFile.exists()) {
            initAdapter();
            return;
        }

        // Load the data on another thread
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final List<BingoSquareData> dataList = new ArrayList<>();
                int resultCode = LOAD_SUCCESS;
                synchronized (mFileLock) {
                    try (ObjectInputStream stream = new ObjectInputStream(new FileInputStream(
                            saveFile))) {
                        for (int i = 0; i < BingoListAdapter.MAX_ITEMS; i++) {
                            BingoSquareData data = (BingoSquareData) stream.readObject();
                            dataList.add(data);

                            // Create unique name set, keep track of how many
                            if (!data.isUnique()) {
                                final String name = data.getName();
                                if (name != null) {
                                    addNameToList(name);
                                }
                            }
                        }
                    } catch (ObjectStreamException e) {
                        e.printStackTrace();
                        resultCode = LOAD_ERROR_MAY_DELETE;
                        mUniqueNamesList.clear();
                        mNamesMap.clear();
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                        resultCode = LOAD_ERROR;
                        mUniqueNamesList.clear();
                        mNamesMap.clear();
                    }
                }
                final int result = resultCode;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        switch (result) {
                            case LOAD_ERROR:
                                Toast.makeText(MainActivity.this, "Unable to load data",
                                        Toast.LENGTH_SHORT).show();
                                break;
                            case LOAD_ERROR_MAY_DELETE:
                                new AlertDialog.Builder(MainActivity.this)
                                        .setMessage(R.string.message_delete_data_unparsable_data)
                                        .setPositiveButton(android.R.string.yes,
                                                new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog,
                                                                        int which) {
                                                        if (!saveFile.delete()) {
                                                            Log.w(TAG, "Unable to delete save");
                                                        }
                                                        initAdapter();
                                                    }
                                                })
                                        .setNegativeButton(android.R.string.no,
                                                new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog,
                                                                        int which) {
                                                        initAdapter();
                                                    }
                                                })
                                        .show();
                                return;
                            default:
                                mAdapter = new BingoListAdapter(dataList);
                                break;
                        }
                        initAdapter();
                    }
                });
            }
        });
    }

    private void addNameToList(@NonNull String name) {
        int n = 1;
        if (mNamesMap.containsKey(name)) {
            n = mNamesMap.get(name) + 1;
        } else {
            mUniqueNamesList.add(name);
        }
        mNamesMap.put(name, n);
    }

    private void removeNameFromList(@NonNull String name) {
        if (mNamesMap.containsKey(name)) {
            int n = mNamesMap.get(name);
            if (n <= 1) {
                mNamesMap.remove(name);
                mUniqueNamesList.remove(name);
            } else {
                mNamesMap.put(name, n - 1);
            }
        }
    }

}
