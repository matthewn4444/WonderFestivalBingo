package com.matthewn4444.wonderfestbingo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.annotation.RawRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.animation.PathInterpolatorCompat;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.SeekBar;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends BaseActivity implements RecyclerViewAdapterListener,
        ActionMode.Callback, EditDialog.OnEditDialogCompleteListener {
    private static final String TAG = "MainActivity";
    private static final String SAVE_FILE = "savedata.dat";
    private static final String SNAP_SHOT_NAME = "WonderFest-bingo-screenshot";
    private static final float SOUND_EFFECT_VOLUME_STAMP = 0.3f;
    private static final float SOUND_EFFECT_VOLUME_BINGO = 0.8f;
    private static final int[] BINGO_SOUNDS = {
            R.raw.bingo1, R.raw.bingo2, R.raw.bingo3
    };
    private static final Interpolator STATUS_INTERPOLATOR = new AccelerateDecelerateInterpolator();
    private static final Interpolator BINGO_INTERPOLATOR =
            PathInterpolatorCompat.create(0.3f, 0.6f, 0.9f, 0.2f);

    private static final int LOAD_SUCCESS = 1;
    private static final int LOAD_ERROR = 2;
    private static final int LOAD_ERROR_MAY_DELETE = 3;

    public static final int RESULT_CODE_SHARE_IMAGE = 1;

    private final Object mFileLock = new Object();
    private final Random mRandom = new Random();

    private RecyclerView mBingoView;
    private int mBingoCount;
    private View mBingoImage;
    private int mBingoImgHeight;
    private int mDisplayWidth;
    private int mDisplayHeight;
    private Uri mSharedImageUri;
    private BingoListAdapter mAdapter;
    private ValueAnimator mBingoAnimator;
    private RecyclerView.LayoutManager mLayoutManager;
    private ActionMode mActionMode;
    private EditDialog mDialog;
    private Dialog mAboutDialog;
    private String mNameDataBeforeDialog;
    private ValueAnimator mStatusBarAnimator;
    private SeekBar mFontSizeSeekBar;
    private View mSettingsArea;
    private int mMinBingoFontSize;
    private String mFontSizeKey;
    private SharedPreferences mPrefs;

    private List<String> mUniqueNamesList = new ArrayList<>(BingoListAdapter.MAX_ITEMS);
    private Map<String, Integer> mNamesMap = new HashMap<>(BingoListAdapter.MAX_ITEMS);

    public static Bitmap loadBitmapFromView(View v, int width, int height) {
        Bitmap b = Bitmap.createBitmap( width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.WHITE);
        v.layout(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
        v.draw(c);
        return b;
    }

    private final AnimatorListenerAdapter mSettingsEndListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            mSettingsArea.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mMinBingoFontSize = getResources().getInteger(R.integer.bingo_square_min_font_size);
        mFontSizeKey = getString(R.string.settings_key_font_size_slider);

        // Allow security to share bitmaps
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        TextView subtitleText = findViewById(R.id.year_season_text);
        mBingoView = findViewById(R.id.bingo);
        mBingoImage = findViewById(R.id.bingo_image);
        mFontSizeSeekBar = findViewById(R.id.font_size_slider);
        mSettingsArea = findViewById(R.id.settings_area);
        mDialog = new EditDialog(this);
        mDialog.setOnEditDialogCompleteListener(this);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        mDisplayWidth = displayMetrics.widthPixels;
        mDisplayHeight = displayMetrics.heightPixels;

        // Setup title font
        AssetManager am = getApplicationContext().getAssets();
        final Typeface typeface = Typeface.createFromAsset(am,
                String.format(Locale.US, "fonts/%s", "milford_black.ttf"));
        subtitleText.setTypeface(typeface);
        Calendar calendar = Calendar.getInstance();
        subtitleText.setText(String.format(Locale.getDefault(), "%d [%s]",
                calendar.get(Calendar.YEAR),
                isSummer() ? "Summer" : "Winter"));

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
                }
            });
        }

        // Setup the slider
        mFontSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int mOriginalFontSize;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mAdapter.setFontSize(progress + mMinBingoFontSize);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mOriginalFontSize = seekBar.getProgress();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                if (progress != mOriginalFontSize) {
                    mPrefs.edit().putInt(mFontSizeKey, progress).apply();
                }
            }
        });

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
            case R.id.about:
                if (mAboutDialog == null) {
                    mAboutDialog = new AlertDialog.Builder(this)
                            .setMessage(R.string.dialog_about_main_message)
                            .setTitle(R.string.dialog_about_title)
                            .setNeutralButton(R.string.dialog_button_close_label, null)
                            .create();
                }
                mAboutDialog.show();
                TextView tv = mAboutDialog.findViewById(android.R.id.message);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimension(R.dimen.about_dialog_font_size));
                tv.setMovementMethod(LinkMovementMethod.getInstance());
                return true;
            case R.id.share:
                if (!verifyStoragePermissionsOrShowDialogs()) {
                    break;
                }
                if (mBingoAnimator != null && mBingoAnimator.isRunning()) {
                    Toast.makeText(this, R.string.message_bingo_animation_runninng,
                            Toast.LENGTH_SHORT).show();
                    return true;
                }

                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        // Get the screenshot as a bitmap and insert into mediastorage, delete later
                        int bottom = mBingoView.getTop() + mBingoView.getHeight()
                                + ((View) mBingoView.getParent()).getPaddingTop();
                        Bitmap bitmap = loadBitmapFromView(findViewById(R.id.main_layout),
                                mDisplayWidth, bottom);
                        mSharedImageUri = Uri.parse(MediaStore.Images.Media.insertImage(
                                getContentResolver(), bitmap, SNAP_SHOT_NAME, null));

                        // Share the image
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.putExtra(Intent.EXTRA_STREAM, mSharedImageUri);
                        intent.setType("image/jpeg");
                        startActivityForResult(Intent.createChooser(intent, "Share bingo via"),
                                RESULT_CODE_SHARE_IMAGE);
                    }
                });
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RESULT_CODE_SHARE_IMAGE) {
            if (mSharedImageUri != null) {
                getContentResolver().delete(mSharedImageUri, null, null);
            } else {
                Log.w(TAG, "Shared image returned with no valid member");
            }
        } else if (!mDialog.handleActivityResult(requestCode, resultCode, data)) {
            if (resultCode == UCrop.RESULT_ERROR) {
                final Throwable cropError = UCrop.getError(data);
                if (cropError != null) {
                    Log.e(TAG, cropError.getMessage(), cropError);
                }
            }
        }
        mSharedImageUri = null;
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
                    mBingoAnimator.setInterpolator(BINGO_INTERPOLATOR);
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
        animateStatusBarColor(android.R.color.black);
        return true;
    }

    @Override
    public void onSupportActionModeFinished(@NonNull ActionMode mode) {
        super.onSupportActionModeFinished(mode);
        mSettingsArea.animate().alpha(0).setListener(mSettingsEndListener).start();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        mSettingsArea.setVisibility(View.VISIBLE);
        mSettingsArea.setAlpha(0);
        mSettingsArea.animate().alpha(1f).setListener(null).start();
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

    public void clickLogo(View view) {
        String url = String.format(Locale.getDefault(),
                "https://twitter.com/hashtag/wf%d%c", Calendar.getInstance().get(Calendar.YEAR),
                isSummer() ? 's' : 'w');
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        startActivity(i);
    }

    private boolean isSummer() {
        return Calendar.getInstance().get(Calendar.MONTH) > 4;
    }

    private void animateStatusBarColor(@ColorRes int id) {
        if (mStatusBarAnimator != null) {
            mStatusBarAnimator.cancel();
        }
        mStatusBarAnimator = ValueAnimator.ofArgb(getWindow().getStatusBarColor(),
                ContextCompat.getColor(this, id));
        mStatusBarAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                getWindow().setStatusBarColor((int) animation.getAnimatedValue());
            }
        });
        mStatusBarAnimator.setInterpolator(STATUS_INTERPOLATOR);
        mStatusBarAnimator.start();
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
        animateStatusBarColor(R.color.colorPrimaryDark);
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
        setSavedFontSize();
    }

    private void playStampSound() {
        playSoundEffect(R.raw.stamp, SOUND_EFFECT_VOLUME_STAMP);
    }

    private void playBingoSound() {
        int i = mRandom.nextInt(BINGO_SOUNDS.length);
        playSoundEffect(BINGO_SOUNDS[i], SOUND_EFFECT_VOLUME_BINGO);
    }

    private void playSoundEffect(@RawRes int id, float volume) {
        MediaPlayer mp = MediaPlayer.create(getApplicationContext(), id);
        mp.setVolume(volume, volume);
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

    private void setSavedFontSize() {
        int fontSizeDiff = mPrefs.getInt(mFontSizeKey,
                BingoListAdapter.DEFAULT_FONT_SIZE - mMinBingoFontSize);
        mFontSizeSeekBar.setProgress(fontSizeDiff);
        mAdapter.setFontSize(fontSizeDiff + mMinBingoFontSize);
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
                                setSavedFontSize();
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
        String lower = name.toLowerCase();
        if (mNamesMap.containsKey(lower)) {
            n = mNamesMap.get(lower) + 1;
        } else {
            mUniqueNamesList.add(name);
            Collections.sort(mUniqueNamesList);
        }
        mNamesMap.put(lower, n);
    }

    private int getRelativeTop(View myView) {
        if (myView.getParent() == myView.getRootView())
            return myView.getTop();
        else
            return myView.getTop() + getRelativeTop((View) myView.getParent());
    }

    private void removeNameFromList(@NonNull String name) {
        String lower = name.toLowerCase();
        if (mNamesMap.containsKey(lower)) {
            int n = mNamesMap.get(lower);
            if (n <= 1) {
                mNamesMap.remove(lower);
                mUniqueNamesList.remove(name);
            } else {
                mNamesMap.put(lower, n - 1);
            }
        }
    }

}
