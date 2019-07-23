package com.matthewn4444.wonderfestbingo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.animation.PathInterpolatorCompat;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.matthewn4444.wonderfestbingo.ui.BingoCardAdapter;
import com.matthewn4444.wonderfestbingo.ui.BingoListAdapter;
import com.matthewn4444.wonderfestbingo.ui.RecyclerViewAdapterListener;
import com.matthewn4444.wonderfestbingo.ui.SpacesItemDecoration;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends BaseActivity implements ActionMode.Callback,
        EditDialog.OnEditDialogCompleteListener {
    private static final String TAG = "MainActivity";
    private static final String SAVE_FILE = "savedata.dat";
    private static final String SNAP_SHOT_NAME = "WonderFest-bingo-screenshot";
    private static final String ADAPTER_PREFIX_INSTANT = "instant";
    private static final float SOUND_EFFECT_VOLUME_STAMP = 0.3f;
    private static final float SOUND_EFFECT_VOLUME_BINGO = 0.8f;
    private static final float DRAG_DROP_SCALE = 1.2f;
    private static final int[] BINGO_SOUNDS = {
            R.raw.bingo1, R.raw.bingo2, R.raw.bingo3
    };
    private static final Interpolator STATUS_INTERPOLATOR = new AccelerateDecelerateInterpolator();
    private static final Interpolator BINGO_INTERPOLATOR =
            PathInterpolatorCompat.create(0.3f, 0.6f, 0.9f, 0.2f);

    private static final int LOAD_SUCCESS = 1;
    private static final int LOAD_ERROR = 2;
    private static final int LOAD_ERROR_MAY_DELETE = 3;

    public static final int REQUEST_CODE_SHARE_IMAGE = 2;
    public static final int REQUEST_CODE_EXPORT = 3;
    public static final int REQUEST_CODE_IMPORT = 4;

    private final Object mFileLock = new Object();
    private final Random mRandom = new Random();

    private RecyclerView mBingoView;
    private RecyclerView mInstantBingoView;
    private int mBingoCount;
    private View mBingoImage;
    private int mBingoImgHeight;
    private int mDisplayWidth;
    private int mDisplayHeight;
    private Uri mSharedImageUri;
    private BingoCardAdapter mAdapter;
    private BingoListAdapter mAdapterInstant;
    private ItemTouchHelper mBingoCardTouchHelper;
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
    private String mHideInstantKey;
    private SharedPreferences mPrefs;

    private List<String> mUniqueNamesList = new ArrayList<>(BingoCardAdapter.MAX_ITEMS);
    private Map<String, Integer> mNamesMap = new HashMap<>(BingoCardAdapter.MAX_ITEMS);

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

    private final ItemTouchHelper.Callback mBingoDragHelper = new ItemTouchHelper.Callback() {

        private RecyclerView.ViewHolder mHeldView;
        private ObjectAnimator mHeldAnimator;

        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder,
                                      int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                saveData();
                log(mHeldView);
                if (mHeldView != null && mHeldAnimator != null) {
                    if (mHeldAnimator.isRunning()) {
                        mHeldAnimator.cancel();
                    }
                    final View view = mHeldView.itemView;
                    PropertyValuesHolder pvhX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f);
                    PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f);
                    ObjectAnimator.ofPropertyValuesHolder(mHeldView.itemView, pvhX, pvhY)
                            .setDuration(150).start();
                }
                mHeldView = null;
                mHeldAnimator = null;
            } else if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                if (viewHolder != null) {
                    mHeldView = viewHolder;
                    PropertyValuesHolder pvhX = PropertyValuesHolder.ofFloat(View.SCALE_X,
                            DRAG_DROP_SCALE);
                    PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat(View.SCALE_Y,
                            DRAG_DROP_SCALE);
                    mHeldAnimator = ObjectAnimator.ofPropertyValuesHolder(mHeldView.itemView, pvhX, pvhY).setDuration(100);
                    mHeldAnimator.start();
                }
            }
        }

        @Override
        public void onMoved(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, int fromPos, @NonNull RecyclerView.ViewHolder target, int toPos, int x, int y) {
            super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder) {
            if (mAdapter.getEntry(viewHolder.getAdapterPosition()).isUnique()) {
                return 0;
            }
            return makeFlag(ItemTouchHelper.ACTION_STATE_DRAG, ItemTouchHelper.DOWN
                    | ItemTouchHelper.UP | ItemTouchHelper.START | ItemTouchHelper.END);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            int srcPos = viewHolder.getAdapterPosition();
            int dstPos = target.getAdapterPosition();
            if (mAdapter.getEntry(srcPos).isUnique() || mAdapter.getEntry(dstPos).isUnique()) {
                return false;
            }
            mAdapter.swap(viewHolder.getAdapterPosition(), target.getAdapterPosition());
            mAdapter.notifyItemMoved(viewHolder.getAdapterPosition(), target.getAdapterPosition());
            log("Swaped", srcPos, dstPos);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Resources res = getResources();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mMinBingoFontSize = getResources().getInteger(R.integer.bingo_square_min_font_size);
        mFontSizeKey = getString(R.string.settings_key_font_size_slider);
        mHideInstantKey = getString(R.string.settings_key_hide_instant);

        // Allow security to share bitmaps
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        TextView subtitleText = findViewById(R.id.year_season_text);
        mBingoView = findViewById(R.id.bingo);
        mInstantBingoView = findViewById(R.id.instant_bingo);
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
        int dividerSize = (int) getResources().getDimension(R.dimen.bingo_divider_size);
        mLayoutManager = new GridLayoutManager(this, BingoCardAdapter.COLUMNS);
        mBingoView.setLayoutManager(mLayoutManager);
        mBingoView.addItemDecoration(new SpacesItemDecoration(dividerSize));
        mBingoView.hasFixedSize();
        mBingoCardTouchHelper = new ItemTouchHelper(mBingoDragHelper);

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

        // Setup instant bingo, calculate the size of the square
        LinearLayoutManager manager = new LinearLayoutManager(this);
        mInstantBingoView.setLayoutManager(manager);
        ViewGroup.MarginLayoutParams lp =
                (ViewGroup.MarginLayoutParams) mBingoView.getLayoutParams();
        int size = (mDisplayWidth - lp.getMarginStart() - lp.getMarginEnd()
                - dividerSize * 2) / BingoCardAdapter.COLUMNS + dividerSize * 2;
        mInstantBingoView.setLayoutFrozen(true);
        mInstantBingoView.hasFixedSize();
        mInstantBingoView.addItemDecoration(new SpacesItemDecoration(dividerSize));
        lp = (ViewGroup.MarginLayoutParams) mInstantBingoView.getLayoutParams();
        lp.width = size;
        lp.height = size;
        mInstantBingoView.setLayoutParams(lp);

        // Setup the slider
        mFontSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int mOriginalFontSize;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int size = progress + mMinBingoFontSize;
                    mAdapter.setFontSize(size);
                    mAdapterInstant.setFontSize(size);
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

        // Switch to hide winner box
        Switch hideInstantSwitch = findViewById(R.id.hide_instant_switch);
        final View instantContainer = findViewById(R.id.instant_container);
        hideInstantSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, final boolean b) {
                mPrefs.edit().putBoolean(mHideInstantKey, b).apply();
                if (!b) {
                    instantContainer.setVisibility(View.VISIBLE);
                    instantContainer.setAlpha(0);
                }
                instantContainer.animate().alpha(b ? 0 : 1f).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        if (b) {
                            instantContainer.setVisibility(View.GONE);
                        }
                    }
                }).start();


//                instantContainer.setVisibility(b ? View.GONE : View.VISIBLE);
            }
        });
        boolean hideInstantBox = mPrefs.getBoolean(mHideInstantKey, false);
        if (hideInstantBox) {
            instantContainer.setVisibility(View.GONE);
            hideInstantSwitch.setChecked(true);
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
        Intent intent;
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
                                + ((View) mBingoView.getParent()).getPaddingTop()
                                + ((View) mBingoView.getParent()).getPaddingBottom();
                        Bitmap bitmap = loadBitmapFromView(findViewById(R.id.main_layout),
                                mDisplayWidth, bottom);
                        mSharedImageUri = Uri.parse(MediaStore.Images.Media.insertImage(
                                getContentResolver(), bitmap, SNAP_SHOT_NAME, null));

                        // Share the image
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.putExtra(Intent.EXTRA_STREAM, mSharedImageUri);
                        intent.setType("image/jpeg");
                        startActivityForResult(Intent.createChooser(intent, "Share bingo via"),
                                REQUEST_CODE_SHARE_IMAGE);
                    }
                });
                return true;
            case R.id.export_data:
                Calendar calendar = Calendar.getInstance();
                SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy",
                        Locale.getDefault());
                intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_TITLE, String.format(Locale.getDefault(),
                        "bingo-%s.dat", dateFormat.format(calendar.getTime())));
                startActivityForResult(intent, REQUEST_CODE_EXPORT);
                return true;
            case R.id.import_data:
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/octet-stream");
                startActivityForResult(intent, REQUEST_CODE_IMPORT);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SHARE_IMAGE) {
            if (mSharedImageUri != null) {
                getContentResolver().delete(mSharedImageUri, null, null);
            } else {
                Log.w(TAG, "Shared image returned with no valid member");
            }
        } else if (requestCode == REQUEST_CODE_EXPORT) {
            Uri uri = data != null ? data.getData() : null;
            if (uri != null) {
                AsyncTask.execute(() -> {
                    try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w");
                         FileOutputStream fos = pfd != null ?
                                 new FileOutputStream(pfd.getFileDescriptor()) : null) {
                        if (fos != null) {
                            saveData(new ObjectOutputStream((fos)));
                            runOnUiThread(() -> Toast.makeText(this, "Export complete",
                                    Toast.LENGTH_SHORT).show());
                        } else {
                            runOnUiThread(() -> Toast.makeText(this, "Cannot export data",
                                    Toast.LENGTH_SHORT).show());
                        }
                    } catch (IOException e) {
                        runOnUiThread(() -> Toast.makeText(this, "Cannot export data, write issue",
                                Toast.LENGTH_SHORT).show());
                        e.printStackTrace();
                    }
                });
            } else {
                Toast.makeText(this, "Unable to export data to device", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_IMPORT) {
            Uri uri = data != null ? data.getData() : null;
            if (uri != null) {
                AsyncTask.execute(() -> {
                    final List<BingoSquareData> dataList = new ArrayList<>();
                    final List<BingoSquareData> instantDataList = new ArrayList<>();
                    try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                         FileInputStream fis = pfd != null ?
                                 new FileInputStream(pfd.getFileDescriptor()) : null) {
                        if (fis != null) {
                            loadData(new ObjectInputStream((fis)), dataList, instantDataList);
                            runOnUiThread(() -> {
                                mAdapter = new BingoCardAdapter(dataList);
                                mAdapterInstant = new BingoListAdapter(instantDataList,
                                        ADAPTER_PREFIX_INSTANT);
                                initAdapter();
                                Toast.makeText(this, "Import complete", Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(this, "Cannot import data",
                                    Toast.LENGTH_SHORT).show());
                        }
                    } catch (IOException e) {
                        runOnUiThread(() -> Toast.makeText(this, "Cannot import data, read issue",
                                Toast.LENGTH_SHORT).show());
                        e.printStackTrace();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                        runOnUiThread(() -> Toast.makeText(this, "Invalid data",
                                Toast.LENGTH_SHORT).show());
                    }
                });
            } else {
                Toast.makeText(this, "Unable to import data from device", Toast.LENGTH_SHORT)
                        .show();
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
        if (mAdapterInstant.getPrefix().equals(data.getPrefix())) {
            mAdapterInstant.notifyDataSetChanged();
        } else {
            mAdapter.notifyDataSetChanged();
        }

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

    private void handleClick(View v, int position, BingoListAdapter adapter) {
        if (mBingoAnimator != null && mBingoAnimator.isRunning()) {
            return;
        }
        if (mActionMode != null) {
            BingoSquareData data = adapter.getEntry(position);
            mNameDataBeforeDialog = data.getName();
            mDialog.show(data, mUniqueNamesList);
        } else {
            // Stamping time
            BingoSquareData data = adapter.getEntry(position);
            if (data.toggleStamped()) {
                adapter.notifyItemChanged(position);
                saveData();

                // See if we just bingo-ed!
                int count = adapter.getBingoCount();
                if (mBingoCount < count || (adapter == mAdapterInstant && count > 0)) {
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
                if (adapter == mAdapter) {
                    mBingoCount = count;
                }
            }
        }
        v.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? HapticFeedbackConstants.CONTEXT_CLICK : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    @Override
    public void onDetachedFromWindow() {
        mDialog.hide();
        super.onDetachedFromWindow();
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
        mBingoCardTouchHelper.attachToRecyclerView(mBingoView);
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

    private boolean handleLongClick(View v, int position, BingoListAdapter adapter) {
        if (adapter.getEntry(position).getName() == null) {
            return false;
        }
        Toast.makeText(this, adapter.getEntry(position).getName(), Toast.LENGTH_SHORT).show();
        return true;
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
        mBingoCardTouchHelper.attachToRecyclerView(null);
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
            mAdapter = new BingoCardAdapter();
        }
        mAdapter.setAdapterListener(new RecyclerViewAdapterListener() {
            @Override
            public void onClick(View v, int position) {
                handleClick(v, position, mAdapter);
            }

            @Override
            public boolean onLongClick(View v, int position) {
                return mActionMode == null && handleLongClick(v, position, mAdapter);
            }
        });
        mBingoView.setAdapter(mAdapter);
        mBingoCount = mAdapter.getBingoCount();
        setSavedFontSize();

        // Instant bingo
        if (mAdapterInstant == null) {
            mAdapterInstant = new BingoListAdapter(1, ADAPTER_PREFIX_INSTANT);
        }
        mInstantBingoView.setAdapter(mAdapterInstant);
        mAdapterInstant.setAdapterListener(new RecyclerViewAdapterListener() {
            @Override
            public void onClick(View v, int position) {
                handleClick(v, position, mAdapterInstant);
            }

            @Override
            public boolean onLongClick(View v, int position) {
                return handleLongClick(v, position, mAdapterInstant);
            }
        });
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
                        saveData(stream);
                    } catch (IOException e) {
                        e.printStackTrace();
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Unable to save",
                                Toast.LENGTH_SHORT).show());
                    }
                }
            }
        });
    }

    @WorkerThread
    private void saveData(@NonNull ObjectOutputStream stream) throws IOException {
        // Save the bingo card data
        for (int i = 0; i < BingoCardAdapter.MAX_ITEMS; i++) {
            stream.writeObject(mAdapter.getEntry(i));
        }

        // Save the instant bingo
        stream.writeObject(mAdapterInstant.getEntry(0));
        stream.flush();         // TODO prob dont need this
    }


    private void setSavedFontSize() {
        int fontSizeDiff = mPrefs.getInt(mFontSizeKey,
                BingoListAdapter.DEFAULT_FONT_SIZE - mMinBingoFontSize);
        mFontSizeSeekBar.setProgress(fontSizeDiff);
        int size = fontSizeDiff + mMinBingoFontSize;
        mAdapter.setFontSize(size);
        if (mAdapterInstant != null) {
            mAdapterInstant.setFontSize(size);
        }
    }

    private void loadData() {
        final File saveFile = new File(getFilesDir(), SAVE_FILE);
        if (!saveFile.exists()) {
            initAdapter();
            return;
        }

        // Load the data on another thread
        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            final List<BingoSquareData> dataList = new ArrayList<>();
            final List<BingoSquareData> instantDataList = new ArrayList<>();
            int resultCode = LOAD_SUCCESS;
            synchronized (mFileLock) {
                try (ObjectInputStream stream = new ObjectInputStream(new FileInputStream(
                        saveFile))) {
                    loadData(stream, dataList, instantDataList);
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
            runOnUiThread(() -> {
                switch (result) {
                    case LOAD_ERROR:
                        Toast.makeText(MainActivity.this, "Unable to load data",
                                Toast.LENGTH_SHORT).show();
                        break;
                    case LOAD_ERROR_MAY_DELETE:
                        new AlertDialog.Builder(MainActivity.this)
                                .setMessage(R.string.message_delete_data_unparsable_data)
                                .setPositiveButton(android.R.string.yes,
                                        (dialog, which) -> {
                                            if (!saveFile.delete()) {
                                                Log.w(TAG, "Unable to delete save");
                                            }
                                            initAdapter();
                                        })
                                .setNegativeButton(android.R.string.no,
                                        (dialog, which) -> initAdapter()).show();
                        return;
                    default:
                        mAdapter = new BingoCardAdapter(dataList);
                        mAdapterInstant = new BingoListAdapter(instantDataList,
                                ADAPTER_PREFIX_INSTANT);
                        setSavedFontSize();
                        break;
                }
                initAdapter();
            });
        });
    }

    @WorkerThread
    private void loadData(@NonNull ObjectInputStream stream, List<BingoSquareData> dataList,
                             List<BingoSquareData> instantDataList) throws IOException,
            ClassNotFoundException {
        for (int i = 0; i < BingoCardAdapter.MAX_ITEMS; i++) {
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

        // Load instant bingo data
        BingoSquareData data = (BingoSquareData) stream.readObject();
        if (data != null) {
            instantDataList.add(data);
        }
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
