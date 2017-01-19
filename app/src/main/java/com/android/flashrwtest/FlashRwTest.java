package com.android.flashrwtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

import static java.util.concurrent.ForkJoinTask.invokeAll;

public class FlashRwTest extends Activity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private static final String TAG = "FlashRwTest";

    private static final String PREF_WRITE_SIZE = "write_size";
    private static final String PREF_FILE_SIZE = "file_size";
    private static final String PREF_ACTUAL_WRITE_SIZE = "actual_write_size";
    private static final String PREF_CHECK_FILE_RESULT = "check_file_result";
    private static final String PREF_WRITE_SPEED = "write_speed";
    private static final String PREF_TEST_RESULT = "test_result";

    private static final int MSG_UPDATE_WRITE_FILE = 0;
    private static final int MSG_UPDATE_DIALOG = 1;
    private static final int MSG_UPDATE_DELETE_FILE = 2;

    private EditText mWriteSizeEt;
    private EditText mFileSizeEt;
    private Button mStartBt;
    private Button mBackBt;
    private Button mDeleteBt;
    private TextView mLastWriteSizeTv;
    private TextView mLastFileSizeTv;
    private TextView mLastActualWriteSizeTv;
    private TextView mFileCheckResultTv;
    private TextView mWriteSpeedTv;
    private TextView mTestResultTv;
    private TextView mCurrentAvailSpaceTv;
    private CheckBox mCyclicCb;
    private View mDialogView;
    private TextView mDialogTitleTv;
    private TextView mDialogInfoTv;
    private TextView mDialogProgressTv;
    private ProgressBar mDialogProgressBar;
    private TextView mDialogSpeedTv;
    private Dialog mTestDialog;
    private State mState;
    private RwAsyncTask mTestTask;
    private DeleteAsyncTask mDeleteTask;
    private SharedPreferences mSharedPreferences;
    private final static ForkJoinPool forkJoinPool = new ForkJoinPool();

    private String mTestDirectoryPath;
    private String mCurrentFilePath;
    private long mWriteSize;
    private long mFileSize;
    private long mActualWriteSize;
    private long mFileCount;
    private long mWriteFileCount;
    private long mCheckFileCount;
    private long mDeleteFileCount;
    private long mWriteTotalSpace;
    private long mTotalTime;
    private byte[] mBuffer;
    private boolean mWriteResult;
    private boolean mCheckResult;
    private boolean mIsStop;
    private boolean mIsCyclicTest;

    enum State {
        UNKNOWS, WRITE_FILE, CHECK_FILE, DELETE_FILE
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 保持亮屏，不锁屏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.activity_flash_rw_test);

        mWriteSize = 0;
        mFileSize = 0;
        mFileCount = 0;
        mTotalTime = 0;
        mWriteFileCount = 0;
        mCheckFileCount = 0;
        mDeleteFileCount = 0;
        mWriteTotalSpace = 0;
        mState = State.UNKNOWS;
        mBuffer = new byte[1024];
        Arrays.fill(mBuffer, (byte) 0xff);
        mCurrentFilePath = "";
        mIsStop = false;
        mIsCyclicTest = getResources().getBoolean(R.bool.default_cyclic_test);
        mTestDirectoryPath = getTestDirectoryPath();
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mWriteSizeEt = (EditText) findViewById(R.id.write_size);
        mFileSizeEt = (EditText) findViewById(R.id.file_size);
        mStartBt = (Button) findViewById(R.id.start_test);
        mBackBt = (Button) findViewById(R.id.back);
        mDeleteBt = (Button) findViewById(R.id.delete);
        mLastWriteSizeTv = (TextView) findViewById(R.id.last_write_size);
        mLastFileSizeTv = (TextView) findViewById(R.id.last_file_size);
        mLastActualWriteSizeTv = (TextView) findViewById(R.id.last_actual_file_size);
        mFileCheckResultTv = (TextView) findViewById(R.id.last_file_check_result);
        mWriteSpeedTv = (TextView) findViewById(R.id.write_speed);
        mTestResultTv = (TextView) findViewById(R.id.last_test_result);
        mCurrentAvailSpaceTv = (TextView) findViewById(R.id.current_avail_space);
        mCyclicCb = (CheckBox) findViewById(R.id.cyclic_test);

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mDialogView = inflater.inflate(R.layout.dialog_test, null);
        mDialogTitleTv = (TextView) mDialogView.findViewById(R.id.title);
        mDialogInfoTv = (TextView) mDialogView.findViewById(R.id.info);
        mDialogProgressBar = (ProgressBar) mDialogView.findViewById(R.id.progress_bar);
        mDialogProgressTv = (TextView) mDialogView.findViewById(R.id.progress_text);
        mDialogSpeedTv = (TextView) mDialogView.findViewById(R.id.speed);
        mTestDialog = getTestDialog();

        mWriteSizeEt.addTextChangedListener(mTextWatcher);
        mFileSizeEt.addTextChangedListener(mTextWatcher);
        mStartBt.setEnabled(false);
        mStartBt.setOnClickListener(this);
        mBackBt.setOnClickListener(this);
        mDeleteBt.setOnClickListener(this);
        mCyclicCb.setOnCheckedChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()...");
        updateViews();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()...");
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopTest();
        super.onDestroy();
        Log.d(TAG, "onStop()...");
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                stopTest();
                finish();
                return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start_test:
                startTest();
                break;

            case R.id.back:
                stopTest();
                finish();
                break;

            case R.id.delete:
                if (mDeleteTask != null) {
                    if (mDeleteTask.getStatus() == AsyncTask.Status.RUNNING) {
                        mDeleteTask.cancel(true);
                    }
                    mDeleteTask = null;
                }
                mDeleteTask = new DeleteAsyncTask();
                mDeleteTask.execute(new Void[]{});
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Log.d(TAG, "onCheckedChanged=>isChecked: " + isChecked);
        mIsCyclicTest = isChecked;
    }

    private void updateViews() {
        long writeSize = mSharedPreferences.getLong(PREF_WRITE_SIZE, -1);
        long fileSize = mSharedPreferences.getLong(PREF_FILE_SIZE, -1);
        long actualWriteSize = mSharedPreferences.getLong(PREF_ACTUAL_WRITE_SIZE, -1);
        int fileCheckResult = mSharedPreferences.getInt(PREF_CHECK_FILE_RESULT, -1);
        String speed = mSharedPreferences.getString(PREF_WRITE_SPEED, "");
        int testResult = mSharedPreferences.getInt(PREF_TEST_RESULT, -1);

        if (writeSize >= 0) {
            mLastWriteSizeTv.setTextColor(getColor(R.color.green));
            mLastWriteSizeTv.setText(writeSize + "");
        } else {
            mLastWriteSizeTv.setText("");
        }

        if (fileSize >= 0) {
            mLastFileSizeTv.setTextColor(getColor(R.color.green));
            mLastFileSizeTv.setText(fileSize + "");
        } else {
            mLastFileSizeTv.setText("");
        }

        if (actualWriteSize >= 0) {
            mLastActualWriteSizeTv.setTextColor(getColor(R.color.green));
            mLastActualWriteSizeTv.setText(actualWriteSize + "");
        } else {
            mLastActualWriteSizeTv.setText("");
        }

        if (fileCheckResult == 1) {
            mFileCheckResultTv.setTextColor(getColor(R.color.green));
            mFileCheckResultTv.setText(R.string.success);
        } else if (fileCheckResult == 0) {
            mFileCheckResultTv.setTextColor(getColor(R.color.red));
            mFileCheckResultTv.setText(R.string.fail);
        } else {
            mFileCheckResultTv.setText("");
        }

        if (!TextUtils.isEmpty(speed)) {
            mWriteSpeedTv.setTextColor(getColor(R.color.green));
            mWriteSpeedTv.setText(speed);
        } else {
            mWriteSpeedTv.setText("");
        }

        if (testResult == 1) {
            mTestResultTv.setTextColor(getColor(R.color.green));
            mTestResultTv.setText(R.string.success);
        } else if (testResult == 0) {
            mTestResultTv.setTextColor(getColor(R.color.red));
            mTestResultTv.setText(R.string.fail);
        } else {
            mTestResultTv.setText("");
        }

        if (!TextUtils.isEmpty(mWriteSizeEt.getText()) && !TextUtils.isEmpty(mFileSizeEt.getText())) {
            mStartBt.setEnabled(true);
        } else {
            mStartBt.setEnabled(false);
        }

        long free = getStorageFreeSpace();
        if (free > 0) {
            mCurrentAvailSpaceTv.setText(getString(R.string.current_avail_space, free / 1024));
        } else {
            mCurrentAvailSpaceTv.setText(getString(R.string.current_avail_space, 0));
        }

        mCyclicCb.setChecked(mIsCyclicTest);
    }

    /**
     * 开始测试
     */
    private void startTest() {
        String writeSizeStr = mWriteSizeEt.getText().toString();
        String fileSizeStr = mFileSizeEt.getText().toString();
        Log.d(TAG, "startTest=>writeSize: " + writeSizeStr + " fileSize: " + fileSizeStr);
        try {
            mWriteSize = Long.parseLong(writeSizeStr);
            mFileSize = Long.parseLong(fileSizeStr);
            if (mWriteSize > 0 && mFileSize > 0) {
                if (mFileSize <= mWriteSize) {
                    if (getStorageFreeSpace() >= mWriteSize * 1024) {
                        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                        mIsStop = false;
                        mWriteResult = false;
                        mCheckResult = false;
                        mWriteFileCount = 0;
                        mCheckFileCount = 0;
                        mDeleteFileCount = 0;
                        mTotalTime = 0;
                        File file = new File(mTestDirectoryPath);
                        if (file.exists()) {
                            deleteDir(file);
                        }
                        file.mkdirs();
                        clearSharedPreferences();
                        mSharedPreferences.edit().putLong(PREF_WRITE_SIZE, mWriteSize).commit();
                        mSharedPreferences.edit().putLong(PREF_FILE_SIZE, mFileSize).commit();
                        mFileCount = (mWriteSize % mFileSize != 0 ? mWriteSize / mFileSize + 1 : mWriteSize / mFileSize);
                        mState = State.WRITE_FILE;
                        if (mTestTask != null) {
                            if (mTestTask.getStatus() == AsyncTask.Status.RUNNING) {
                                mTestTask.cancel(true);
                            }
                            mTestTask = null;
                        }
                        mTestTask = new RwAsyncTask();
                        mTestTask.execute(new Void[]{});
                    } else {
                        Toast.makeText(this, R.string.no_space, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, R.string.file_and_write_limit, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, R.string.value_greater_zero, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "startTest=>error: ", e);
            Toast.makeText(this, R.string.invalid_value, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 停止测试
     */
    private void stopTest() {
        mIsStop = true;
        if (mTestTask != null) {
            if (mTestTask.getStatus() == AsyncTask.Status.RUNNING) {
                mTestTask.cancel(true);
                mWriteTotalSpace = forkJoinPool.invoke(new FileSizeFinder(new File(mTestDirectoryPath)));
                Log.d(TAG, "stopTest=>actualWriteSize: " + mWriteTotalSpace + " directory: " + mWriteTotalSpace + " writeResult: " + mWriteResult);
                mState = State.UNKNOWS;
                int time = (int) (mTotalTime / 1000000000);
                String writeSpeed = "0.00 MB/S";
                double speed = 0.00;
                if (time != 0) {
                    speed = (mWriteTotalSpace / (1024.0 * 1024.0)) / time;
                    DecimalFormat df = new DecimalFormat("#.00");
                    writeSpeed = df.format(speed) + " MB/S";
                }

                mSharedPreferences.edit().putLong(PREF_ACTUAL_WRITE_SIZE, mWriteTotalSpace / 1024).commit();
                mSharedPreferences.edit().putInt(PREF_CHECK_FILE_RESULT, mCheckResult ? 1 : 0).commit();
                mSharedPreferences.edit().putString(PREF_WRITE_SPEED, writeSpeed).commit();
                mSharedPreferences.edit().putInt(PREF_TEST_RESULT, mWriteTotalSpace == mWriteSize * 1024 ? 1 : 0).commit();
                updateViews();
                if (mTestDialog != null) {
                    if (mTestDialog.isShowing()) {
                        mTestDialog.dismiss();
                    }
                }
            }
            mTestTask = null;
        }
    }

    /**
     * 获取存储可用空间
     * @return 返回可用空间大小
     */
    private long getStorageFreeSpace() {
        File file = Environment.getExternalStorageDirectory();
        return file.getFreeSpace();
    }

    /**
     * 获取测试文件存放目录路径
     * @return 返回测试文件目录路径
     */
    private String getTestDirectoryPath() {
        String path = null;
        File internalFile = Environment.getExternalStorageDirectory();
        if (internalFile != null && internalFile.exists() && internalFile.isDirectory()) {
            path = internalFile.getAbsolutePath() + "/" + getString(R.string.test_directory);
        } else {
            path = "/data/" + getString(R.string.test_directory);
        }
        File file = new File(path);
        if (file.exists()) {
            if (deleteDir(file)) {
                file.mkdirs();
                path = file.getAbsolutePath();
            } else {
                path = null;
            }
        } else {
            file.mkdirs();
            path = file.getAbsolutePath();
        }
        Log.d(TAG, "getTestDirectoryPath=>path: " + path);
        return path;
    }

    /**
     * 清除SharedPreferences与Flash读写测试有关的首选项
     */
    private void clearSharedPreferences() {
        SharedPreferences.Editor e = mSharedPreferences.edit();
        e.remove(PREF_TEST_RESULT);
        e.remove(PREF_ACTUAL_WRITE_SIZE);
        e.remove(PREF_WRITE_SPEED);
        e.remove(PREF_CHECK_FILE_RESULT);
        e.remove(PREF_FILE_SIZE);
        e.remove(PREF_WRITE_SIZE);
        e.commit();
    }

    /**
     * 更新对话框信息
     */
    private void updateDialog() {
        Log.d(TAG, "updateDialog=>state: " + mState);
        switch (mState) {
            case WRITE_FILE:
                updateWriteFileStateDialog();
                break;

            case CHECK_FILE:
                updateCheckFileStateDialog();
                break;

            case DELETE_FILE:
                updateDeleteFileStateDialog();
                break;
        }
    }

    /**
     * 更新写入文件对话框信息
     */
    private void updateWriteFileStateDialog() {
        mDialogSpeedTv.setVisibility(View.VISIBLE);
        mDialogTitleTv.setText(getString(R.string.write_file_dialog_title, mWriteFileCount, mFileCount));
        mDialogInfoTv.setText(mCurrentFilePath);
        mWriteTotalSpace = forkJoinPool.invoke(new FileSizeFinder(new File(mTestDirectoryPath)));
        Log.d(TAG, "updateWriteFileStateDialog=>totalSpace: " + mWriteTotalSpace);
        int pregress = (int) (((double) mWriteTotalSpace / (mWriteSize * 1024)) * 100);
        int time = (int) (mTotalTime / 1000000000);
        Log.d(TAG, "updateWriteFileStateDialog=>time: " + time);
        double speed = 0.00;
        if (time != 0) {
            speed = (mWriteTotalSpace / (1024.0 * 1024.0)) / time;
        }
        DecimalFormat df = new DecimalFormat("#0.00");
        mDialogProgressBar.setMax(100);
        mDialogProgressBar.setProgress(pregress);
        mDialogProgressTv.setText(pregress + "%");
        mDialogSpeedTv.setText(getString(R.string.write_speed_info, df.format(speed)));
    }

    /**
     * 更新校验文件对话框信息
     */
    private void updateCheckFileStateDialog() {
        mDialogTitleTv.setText(getString(R.string.check_file_dialog_title, mCheckFileCount, mFileCount));
        mDialogInfoTv.setText(mCurrentFilePath);
        File file = new File(mTestDirectoryPath);
        int pregress = (int) (((double) mCheckFileCount / mFileCount) * 100);
        mDialogProgressBar.setMax(100);
        mDialogProgressBar.setProgress(pregress);
        mDialogProgressTv.setText(pregress + "%");
        mDialogSpeedTv.setVisibility(View.GONE);
    }

    /**
     * 更新删除文件对话框信息
     */
    private void updateDeleteFileStateDialog() {
        mDialogTitleTv.setText(getString(R.string.delete_file_dialog_title, mDeleteFileCount, mFileCount));
        mDialogInfoTv.setText(mCurrentFilePath);
        File file = new File(mTestDirectoryPath);
        int pregress = (int) (((double) mDeleteFileCount / mFileCount) * 100);
        mDialogProgressBar.setMax(100);
        mDialogProgressBar.setProgress(pregress);
        mDialogProgressTv.setText(pregress + "%");
        mDialogSpeedTv.setVisibility(View.GONE);
    }

    /**
     * 删除目录及其下的所有文件
     * @param dir　要删除目录的File对象
     * @return 返回删除结果，删除成功返回true，否则返回false
     */
    public boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }

    /**
     * 写入文件
     * @param path　文件路径
     * @param buffer　文件内容
     * @param length　文件大小
     * @return 返回写入结果，写入成功返回true，否则返回false
     */
    private boolean writeFile(String path, byte[] buffer, long length) {
        boolean result = false;
        FileOutputStream out = null;
        File toFile = new File(path);
        if (toFile.exists()) {
            toFile.delete();
        } else {
            toFile.getParentFile().mkdirs();
        }
        try {
            out = new FileOutputStream(path);
            int len = -1;
            for (int i = 0; i < length; i++) {
                out.write(buffer, 0, buffer.length);
            }
            out.flush();
            out.close();
            result = true;
        } catch (Exception e) {
            Log.e(TAG, "writeFile=>error: ", e);
            result = false;
        } finally {
            try {
                if (out != null) out.close();
            } catch (IOException e) {
            }

        }
        return result;
    }

    /**
     * 写入测试文件
     * @return 返回测试结果，写入成功返回true，否则返回false; 只有要有一个文件写入失败即停止测试，并返回false
     */
    private boolean writeFiles() {
        Log.d(TAG, "writeFiles=>fileCount: " + mFileCount);
        boolean result = true;
        mWriteFileCount = 0;
        File file = null;
        boolean success = false;
        long startTime = 0;
        long endTime = 0;
        for (int i = 0; i < mFileCount; i++) {
            if (mIsStop) {
                result = false;
                break;
            }
            file = new File(mTestDirectoryPath + "/" + getString(R.string.test_file_name, i) + "." + getString(R.string.test_file_suffix));
            mCurrentFilePath = file.getAbsolutePath();
            mHandler.sendEmptyMessage(MSG_UPDATE_DIALOG);
            if (i != mFileCount - 1) {
                startTime = Debug.threadCpuTimeNanos();
                success = writeFile(mCurrentFilePath, mBuffer, mFileSize);
                endTime = Debug.threadCpuTimeNanos();
            } else {
                if (mWriteSize % mFileSize != 0) {
                    startTime = Debug.threadCpuTimeNanos();
                    success = writeFile(mCurrentFilePath, mBuffer, mWriteSize % mFileSize);
                    endTime = Debug.threadCpuTimeNanos();
                } else {
                    startTime = Debug.threadCpuTimeNanos();
                    success = writeFile(mCurrentFilePath, mBuffer, mFileSize);
                    endTime = Debug.threadCpuTimeNanos();
                }
            }
            mTotalTime += endTime - startTime;
            mWriteFileCount++;
            if (!success && result) {
                result = false;
                mIsStop = true;
                break;
            }
        }
        mHandler.sendEmptyMessage(MSG_UPDATE_DIALOG);
        return result;
    }

    /**
     * 校验测试文件
     * @return 返回校验结果，校验成功返回true, 否则返回false；只要其中有一个文件校验失败
     */
    private boolean checkFiles() {
        boolean result = true;
        mWriteFileCount = 0;
        File file = null;
        boolean success = false;
        for (int i = 0; i < mFileCount; i++) {
            if (mIsStop) {
                result = false;
                break;
            }
            file = new File(mTestDirectoryPath + "/" + getString(R.string.test_file_name, i) + "." + getString(R.string.test_file_suffix));
            mCurrentFilePath = file.getAbsolutePath();
            mHandler.sendEmptyMessage(MSG_UPDATE_DIALOG);
            success = checkFile(mCurrentFilePath);
            mCheckFileCount++;
            if (!success) {
                Log.e(TAG, "checkFiles=>error->file: " + file.getAbsolutePath() + " check fail.");
                result = false;
                break;
            }
        }
        mHandler.sendEmptyMessage(MSG_UPDATE_DIALOG);
        return result;
    }

    /**
     * 校验文件
     * @param path　文件路径
     * @return 返回校验结果，校验成功返回true，否则返回false
     */
    private boolean checkFile(String path) {
        boolean result = true;
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            FileInputStream in = null;
            try {
                in = new FileInputStream(file);
                int len = -1;
                byte[] buffer = new byte[1024];
                while ((len = in.read(buffer)) != -1) {
                    for (int i = 0; i < len; i++) {
                        if ((buffer[i] != (byte) 0xff) && result) {
                            result = false;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "checkFile=>error: ", e);
                result = false;
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
        Log.d(TAG, "checkFile=>result: " + result + " path: " + path);
        return result;
    }

    /**
     * 删除测试文件
     * @param dir　文件目录的File对象
     */
    private void deleteFiles(File dir) {
        if (mIsStop) {
            return;
        }
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
            }
        } else {
            mCurrentFilePath = dir.getAbsolutePath();
            dir.delete();
            mDeleteFileCount++;
            mHandler.sendEmptyMessage(MSG_UPDATE_DIALOG);
        }
    }

    /**
     * 测试信息对话框
     * @return 返回Dialog对象
     */
    private Dialog getTestDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(mDialogView);
        builder.setNegativeButton(R.string.cancel, mCancelListener);
        Dialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }


    /**
     * Dialog取消按钮点击实际
     */
    private DialogInterface.OnClickListener mCancelListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (mState == State.CHECK_FILE || mState == State.WRITE_FILE) {
                stopTest();
            } else if (mState == State.DELETE_FILE) {
                if (mDeleteTask != null) {
                    if (mDeleteTask.getStatus() == AsyncTask.Status.RUNNING) {
                        mDeleteTask.cancel(true);
                    }
                    mDeleteTask = null;
                }
            }
        }
    };

    /**
     * EditText监听器
     */
    private TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (!TextUtils.isEmpty(mWriteSizeEt.getText()) && !TextUtils.isEmpty(mFileSizeEt.getText())) {
                mStartBt.setEnabled(true);
            } else {
                mStartBt.setEnabled(false);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_DIALOG:
                    updateDialog();
                    break;
            }
        }
    };

    /**
     * Flash读写测试异步线程
     */
    private class RwAsyncTask extends AsyncTask<Void, Long, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.d(TAG, "onPreExecute=>dialog: " + mTestDialog);
            if (mTestDialog == null) {
                mTestDialog = getTestDialog();
            } else {
                if (mTestDialog.isShowing()) {
                    mTestDialog.dismiss();
                }
            }
            mTestDialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Log.d(TAG, "doInBackground=>state: " + mState + " writeSize: " + mWriteSize + " fileSize: " + mFileSize + " path: " + mTestDirectoryPath);
            if (mFileSize > 0 && mWriteSize > 0 && mTestDirectoryPath != null) {
                if (mIsCyclicTest) {
                    while (!mIsStop) {
                        File file = new File(mTestDirectoryPath);
                        if (file.exists()) {
                            deleteDir(file);
                        }
                        file.mkdirs();
                        mWriteResult = false;
                        mCheckResult = false;
                        mWriteFileCount = 0;
                        mCheckFileCount = 0;
                        mDeleteFileCount = 0;
                        mTotalTime = 0;
                        mState = State.WRITE_FILE;
                        publishProgress(new Long[]{});
                        if (!mIsStop) {
                            mWriteResult = writeFiles();
                        }
                        mCheckFileCount = 0;
                        mState = State.CHECK_FILE;
                        publishProgress(new Long[]{});
                        if (!mIsStop) {
                            mCheckResult = checkFiles();
                        }
                    }
                } else {
                    mState = State.WRITE_FILE;
                    publishProgress(new Long[]{});
                    if (!mIsStop) {
                        mWriteResult = writeFiles();
                    }
                    mCheckFileCount = 0;
                    mState = State.CHECK_FILE;
                    publishProgress(new Long[]{});
                    if (!mIsStop) {
                        mCheckResult = checkFiles();
                    }
                }
            }
            return true;
        }

        @Override
        protected void onProgressUpdate(Long... values) {
            super.onProgressUpdate(values);
            mHandler.sendEmptyMessage(MSG_UPDATE_DIALOG);
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            mWriteTotalSpace = forkJoinPool.invoke(new FileSizeFinder(new File(mTestDirectoryPath)));
            Log.d(TAG, "onPostExecte=>time: " + mTotalTime);
            Log.d(TAG, "onPostExecute=>actualWriteSize: " + mWriteTotalSpace + " directory: " + mWriteTotalSpace + " writeResult: " + mWriteResult);
            mState = State.UNKNOWS;
            int time = (int) (mTotalTime / 1000000000);
            String writeSpeed = "0.00 MB/S";
            double speed = 0.00;
            if (time != 0) {
                speed = (mWriteTotalSpace / (1024.0 * 1024.0)) / time;
                DecimalFormat df = new DecimalFormat("#.00");
                writeSpeed = df.format(speed) + " MB/S";
            }

            mSharedPreferences.edit().putLong(PREF_ACTUAL_WRITE_SIZE, mWriteTotalSpace / 1024).commit();
            mSharedPreferences.edit().putInt(PREF_CHECK_FILE_RESULT, mCheckResult ? 1 : 0).commit();
            mSharedPreferences.edit().putString(PREF_WRITE_SPEED, writeSpeed).commit();
            mSharedPreferences.edit().putInt(PREF_TEST_RESULT, mWriteTotalSpace == mWriteSize * 1024 ? 1 : 0).commit();
            updateViews();
            if (mTestDialog != null) {
                if (mTestDialog.isShowing()) {
                    mTestDialog.dismiss();
                }
            }
        }
    }

    /**
     * 删除测试文件异步线程
     */
    private class DeleteAsyncTask extends AsyncTask<Void, Long, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.d(TAG, "onPreExecute=>dialog: " + mTestDialog);
            if (mTestDialog == null) {
                mTestDialog = getTestDialog();
            } else {
                if (mTestDialog.isShowing()) {
                    mTestDialog.dismiss();
                }
            }
            mTestDialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Log.d(TAG, "doInBackground=>state: " + mState + " writeSize: " + mWriteSize + " fileSize: " + mFileSize + " path: " + mTestDirectoryPath);
            mDeleteFileCount = 0;
            mState = State.DELETE_FILE;
            publishProgress(new Long[]{});
            if (!mIsStop) {
                deleteFiles(new File(mTestDirectoryPath));
            }
            if (!mIsStop) {
                new File(mTestDirectoryPath).delete();
            }
            return true;
        }

        @Override
        protected void onProgressUpdate(Long... values) {
            super.onProgressUpdate(values);
            mHandler.sendEmptyMessage(MSG_UPDATE_DIALOG);
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            if (mTestDialog != null && mTestDialog.isShowing()) {
                mTestDialog.dismiss();
            }
            updateViews();
            Toast.makeText(FlashRwTest.this, R.string.delete_file_completed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 获取文件夹大小类
     */
    private class FileSizeFinder extends RecursiveTask<Long> {
        final File file;

        public FileSizeFinder(final File theFile) {
            file = theFile;
        }

        @Override
        public Long compute() {
            long size = 0;
            if (file.isFile()) {
                size = file.length();
            } else {
                final File[] children = file.listFiles();
                if (children != null) {
                    List<ForkJoinTask<Long>> tasks = new ArrayList<ForkJoinTask<Long>>();
                    for (final File child : children) {
                        if (child.isFile()) {
                            size += child.length();
                        } else {
                            tasks.add(new FileSizeFinder(child));
                        }
                    }
                    for (final ForkJoinTask<Long> task : invokeAll(tasks)) {
                        size += task.join();
                    }
                }
            }
            return size;
        }
    }

}
