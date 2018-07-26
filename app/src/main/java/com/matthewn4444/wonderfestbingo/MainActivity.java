package com.matthewn4444.wonderfestbingo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends BaseActivity implements RecyclerViewAdapterListener,
        ActionMode.Callback, EditDialog.OnEditDialogCompleteListener {
    private static final String TAG = "MainActivity";
    private static final String SAVE_FILE = "savedata.dat";

    private final Object mFileLock = new Object();

    private TextView mWFSubtitleText;
    private RecyclerView mBingoView;
    private BingoListAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private ActionMode mActionMode;
    private EditDialog mDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWFSubtitleText = findViewById(R.id.year_season_text);
        mBingoView = findViewById(R.id.bingo);
        mDialog = new EditDialog(this);
        mDialog.setOnEditDialogCompleteListener(this);

        // Setup title font
        AssetManager am = getApplicationContext().getAssets();
        final Typeface typeface = Typeface.createFromAsset(am,
                String.format(Locale.US, "fonts/%s", "milford_black.ttf"));
        mWFSubtitleText.setTypeface(typeface);

        // Setup bingo layout
        mLayoutManager = new GridLayoutManager(this, 5);
        mBingoView.setLayoutManager(mLayoutManager);
        mBingoView.addItemDecoration(new SpacesItemDecoration((int) getResources().getDimension(R.dimen.bingo_divider_size)));
        mBingoView.hasFixedSize();

        if (verifyStoragePermissionsOrShowDialogs()) {
            loadData();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
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
    public void onEditDialogComplete(int id, BingoSquareData data) {
        mAdapter.notifyDataSetChanged();

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

    @Override
    public void onClick(View v, int position) {
        if (mActionMode != null) {
            mDialog.show(position, mAdapter.getEntry(position));
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
                if (mActionMode == null) {
                    break;
                }
                mode.finish();
                return true;
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
    }

    private void loadData() {
        final File saveFile = new File(getFilesDir(), SAVE_FILE);
        if (!saveFile.exists()) {
            mAdapter = new BingoListAdapter();
            mAdapter.setAdapterListener(this);
            mBingoView.setAdapter(mAdapter);
            return;
        }

        // Load the data on another thread
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final List<BingoSquareData> data = new ArrayList<>();
                synchronized (mFileLock) {
                    try (ObjectInputStream stream = new ObjectInputStream(new FileInputStream(
                            new File(getFilesDir(), SAVE_FILE)))) {
                        for (int i = 0; i < BingoListAdapter.MAX_ITEMS; i++) {
                            data.add((BingoSquareData) stream.readObject());
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Unable to load data",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Put the data in the adapter
                        mAdapter = new BingoListAdapter(data);
                        mAdapter.setAdapterListener(MainActivity.this);
                        mBingoView.setAdapter(mAdapter);
                    }
                });
            }
        });
    }
}
