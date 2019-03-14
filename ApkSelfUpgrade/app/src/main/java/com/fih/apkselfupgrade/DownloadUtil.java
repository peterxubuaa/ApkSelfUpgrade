package com.fih.apkselfupgrade;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.File;

import static android.content.Context.DOWNLOAD_SERVICE;
import static java.lang.Thread.sleep;

public class DownloadUtil {
    private final String TAG = SysUtil.TAG;
    private final boolean ASYNC_DOWNLOAD = true;
    private Context mContext;
    private long mDownloadId = -1;
    private String mSaveFilePath;
    private String mDownloadHttpPath;

    DownloadUtil(Context ctx) {
        mContext = ctx;
        mSaveFilePath = null;
        mDownloadHttpPath = "";
        // /storage/emulated/0/Android/data/files
        //DOWNLOAD_DIR = mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getPath();
    }

    //download file to  Environment.DIRECTORY_DOWNLOADS = "sdcard/Download/"
    public String downloadApk(String subSaveFilePath, String downloadHttpPath) {
        final String DOWNLOAD_DIR =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
        final int WAITING_DURATION = 5000; //5 seconds
        final int MAX_WAITING_TIMES = 120; // 10 minutes

        File saveFile = new File(DOWNLOAD_DIR, subSaveFilePath);
        while (saveFile.exists()) {
            Log.i(TAG, "Downloader: Delete old file..." + saveFile.getName());
            saveFile.delete();
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mSaveFilePath = saveFile.getAbsolutePath();
        mDownloadHttpPath = downloadHttpPath;

        //Create download manager
        DownloadManager downloadManager = (DownloadManager) mContext.getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadHttpPath));
        //Declare (download folder, filename)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, subSaveFilePath);
        request.allowScanningByMediaScanner();
        //Start download
        mDownloadId = downloadManager.enqueue(request);

        if (ASYNC_DOWNLOAD) {
            DownApkReceiver downloadBroadcastReceiver = new DownApkReceiver();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.intent.action.DOWNLOAD_COMPLETE");
            intentFilter.addAction("android.intent.action.DOWNLOAD_NOTIFICATION_CLICKED");
            mContext.registerReceiver(downloadBroadcastReceiver, intentFilter);
            return "Pending";
        } else {
            int timeout = 0, statusInt = getStatus(mDownloadId, downloadManager, "Initial");
            //Wait for download status be changed from pending to the successful or the others.(Maximum 60 sec)
            while (statusInt != DownloadManager.STATUS_SUCCESSFUL) {
                try {
                    sleep(WAITING_DURATION);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                statusInt = getStatus(mDownloadId, downloadManager, "Current");
                timeout++;
                if (timeout > MAX_WAITING_TIMES || statusInt == DownloadManager.STATUS_FAILED || statusInt == DownloadManager.STATUS_PAUSED) {
                    break;
                }
            }
            String retVal;
            switch (getStatus(mDownloadId, downloadManager, "Final")) {
                case DownloadManager.STATUS_PAUSED:
                    retVal = "Pause";
                    break;
                case DownloadManager.STATUS_PENDING:
                    retVal = "Pending";
                    break;
                case DownloadManager.STATUS_RUNNING:
                    retVal = "Running";
                    break;
                case DownloadManager.STATUS_SUCCESSFUL:
                    retVal = "Success";
                    break;
                case DownloadManager.STATUS_FAILED:
                    retVal = "Failure";
                    break;
                default:
                    retVal = "Status";
                    break;
            }

            Log.w(TAG, " DOWNLOAD: retVal = " + retVal);
            return retVal;
        }
    }

    private int getStatus(long downloadId, DownloadManager downloadManager, String Timing) {
        int Status = 0;
        int Reason = 0;
        String StrReason = "Unknown";
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        Cursor c = downloadManager.query(query);
        if (c != null && c.moveToFirst()) {
            Status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
            Reason = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON));
        }
        if (c != null) {
            c.close();
        }

        switch (Status) {
            case DownloadManager.STATUS_PAUSED:
                switch (Reason) {
                    case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
                        StrReason = "PAUSED_QUEUED_FOR_WIFI: the download exceeds a size limit for downloads over the mobile network and the download manager is waiting for a Wi-Fi connection to proceed.";
                        break;
                    case DownloadManager.PAUSED_UNKNOWN:
                        StrReason = "PAUSED_UNKNOWN: the download is paused for some other reason.";
                        break;
                    case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
                        StrReason = "PAUSED_WAITING_FOR_NETWORK: the download is waiting for network connectivity to proceed.";
                        break;
                    case DownloadManager.PAUSED_WAITING_TO_RETRY:
                        StrReason = "PAUSED_WAITING_TO_RETRY: the download is paused because some network error occurred and the download manager is waiting before retrying the request.";
                        break;
                }
                Log.w(TAG, Timing + " STATUS_PAUSED : " + StrReason);
                break;
            case DownloadManager.STATUS_PENDING:
                Log.d(TAG, Timing + " STATUS_PENDING");
                break;
            case DownloadManager.STATUS_RUNNING:
                Log.d(TAG, Timing + " STATUS_RUNNING");
                break;
            case DownloadManager.STATUS_SUCCESSFUL:
                Log.d(TAG, Timing + " STATUS_SUCCESSFUL");
                break;
            case DownloadManager.STATUS_FAILED:
                switch (Reason) {
                    case DownloadManager.ERROR_CANNOT_RESUME:
                        StrReason = "ERROR_CANNOT_RESUME: some possibly transient error occurred but we can't resume the download.";
                        break;
                    case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                        StrReason = "ERROR_DEVICE_NOT_FOUND: no external storage device was found.";
                        break;
                    case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                        StrReason = "ERROR_FILE_ALREADY_EXISTS: the requested destination file already exists (the download manager will not overwrite an existing file).";
                        break;
                    case DownloadManager.ERROR_FILE_ERROR:
                        StrReason = "ERROR_FILE_ERROR: a storage issue arises which doesn't fit under any other error code.";
                        break;
                    case DownloadManager.ERROR_HTTP_DATA_ERROR:
                        StrReason = "ERROR_HTTP_DATA_ERROR: an error receiving or processing data occurred at the HTTP level.";
                        break;
                    case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                        StrReason = "ERROR_INSUFFICIENT_SPACE: there was insufficient storage space.";
                        break;
                    case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                        StrReason = "ERROR_TOO_MANY_REDIRECTS: there were too many redirects.";
                        break;
                    case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                        StrReason = "ERROR_UNHANDLED_HTTP_CODE: an HTTP code was received that download manager can't handle.";
                        break;
                    case DownloadManager.ERROR_UNKNOWN:
                        StrReason = "ERROR_UNKNOWN: the download has completed with an error that doesn't fit under any other error code.";
                        break;
                }
                Log.e(TAG, Timing + " STATUS_FAILED : " + StrReason);
                break;
        }
        return Status;
    }

    public class DownApkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (null == action) return;

            if (action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
                long downloadApkId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (downloadApkId >= 0 && downloadApkId == mDownloadId) {
                    //install apk
                    Intent innerIntent = new Intent( "INSTALL_APK");
                    innerIntent.putExtra("APK_PATH", mSaveFilePath);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(innerIntent);

                    mDownloadId = -1;
                }
            } else if (action.equals(DownloadManager.ACTION_NOTIFICATION_CLICKED)) {
                //处理 如果还未完成下载，用户点击Notification ，跳转到下载中心
                Intent viewDownloadIntent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                viewDownloadIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(viewDownloadIntent);
            }
        }
    }
}
