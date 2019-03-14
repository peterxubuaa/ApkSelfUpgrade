package com.fih.apkselfupgrade;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

class SelfUpgrade {
    private static final String TAG = SysUtil.TAG;
    public static final int DOWNLOAD_BYHTTP = 0;
    public static final int DOWNLOAD_BYDLM = 1;
    private final int DLG_DURATION = 10000; //ms
    private final int TRY_AGAIN_DELAY = 10000; //ms
    private final boolean ONLY_DEMO_WITHOUT_INTERNET = SysUtil.getBooleanProperty("persist.sys.demo_upgrade", false);
    private final int TRY_AGAIN = 0;
    private final int DOWN_UPDATE = 1;
    private final int DOWN_SUCCESS = 2;
    private final int DOWN_FAIL = 3;
    private final int DISMISS_PERMISSION_DLG = 4;
    private final int DISMISS_HINT_DLG = 5;
    private final int UPGRADE_PERMISSION_REQUEST = 1;
    private final int DEFAULT_APKSIZE = 20 * 1024 * 1024;
    //private final String SDCARD_PATH = Environment.getExternalStorageDirectory().getPath();
    private final String DOWNLOAD_DIR =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
    private final String SUB_DOWNLOAD_APKFILE_PATH = "/ApkSelfUpgrade/new.apk";
    private final String DOWNLOAD_APKFILE_PATH = DOWNLOAD_DIR + SUB_DOWNLOAD_APKFILE_PATH;
    private final String TMP_LATEST_APKINFO_PATH = DOWNLOAD_DIR + "/apkinfo.txt";
    private final String TMP_LATEST_APK_PATH = DOWNLOAD_DIR + "/Latest.apk";
    //"https://www.androidos.net.cn/android/9.0.0_r8/xref/bootable/recovery/etc/init.rc"
    private final String APK_INFO_HTTPPATH = SysUtil.getProperty("persist.sys.apkinfohttp", "https://raw.githubusercontent.com/peterxubuaa/ApkSelfUpgrade/master/apkinfo.txt");
    //"https://www.androidos.net.cn/android/9.0.0_r8/download/nohup.android-7.1.1_r28.out"
    private final String APK_FILE_HTTPPATH = SysUtil.getProperty("persist.sys.apkfilehttp", "https://github.com/peterxubuaa/ApkSelfUpgrade/raw/master/Latest.apk");
    private Context mContext;
    private ProgressBar mProgress;
    private boolean mCancelDownload;
    private AlertDialog mPermissonDlg, mHintDlg, mProgressDlg;
    private DownloadApkInfo mDownloadApkInfo;
    private InnerBroadcastReceiver mInnerBroadcastReceiver ;
    private int mDownloadType = DOWNLOAD_BYHTTP;

    //load bsdiff lib for Differential installed package
    static {
        System.loadLibrary("bsdiff-lib");
    }
    public native String version();
    public native int genDiffBetweenOldAndNewApk(String oldFile, String newFile, String patchFile);
    public native int genNewApkWithPatch(String oldFile, String newFile, String patchFile);
    //

    class DownloadApkInfo {
        DownloadApkInfo() {
            mVersion = "";
            mMD5 = "";
            mSize = 0;
        }
        String mVersion;
        String mMD5;
        int mSize;
    }

    SelfUpgrade(Context ctx) {
        mContext = ctx;
        mProgress = null;
        mCancelDownload = false;
        mDownloadApkInfo = null;
        mPermissonDlg = null;
        mHintDlg = null;
        mProgressDlg = null;
        //for policy to access internet
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    final private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case DOWN_UPDATE:
                    mProgress.setProgress(msg.arg1);
                    break;
                case DOWN_SUCCESS:
                    installApp(DOWNLOAD_APKFILE_PATH);
                    break;
                case DOWN_FAIL:
                    deleteDownloadApk(DOWNLOAD_APKFILE_PATH);
                    if (mProgressDlg.isShowing()) {
                        mProgressDlg.dismiss();
                        mProgressDlg = null;
                    }
                    break;
                case TRY_AGAIN:
                    upgradeApk(mDownloadType);
                    break;
                case DISMISS_PERMISSION_DLG:
                    if (mPermissonDlg.isShowing()) {
                        mPermissonDlg.dismiss();
                        mPermissonDlg = null;
                    }
                    break;
                case DISMISS_HINT_DLG:
                    if (mHintDlg.isShowing()) {
                        mHintDlg.dismiss();
                        mHintDlg = null;
                    }
                    break;
                default:
                    break;
            }
            return true;
        }
    });

    void upgradeApk(int type) {
        mDownloadType = type;
        //1. check permission of external storage
        if (!hasExternalStoragePermission()) {
            Log.w(TAG, "No permission to R/W external storage!");
            showPermissionDialog();
            return;
        }
        //2. delete installed apk file
        deleteDownloadApk(DOWNLOAD_APKFILE_PATH);
        //3. check version
        if (!getDownloadApkInfoFromServer()) return;
        String curVer = getCurVersion();
        if (null == curVer || curVer.equals(mDownloadApkInfo.mVersion)) return;
        //4. show upgrade hint
        showHintDialog(curVer, mDownloadApkInfo.mVersion);
    }

    private boolean hasExternalStoragePermission(){
        return (mContext.checkCallingOrSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")
                == PackageManager.PERMISSION_GRANTED);
    }

    private String getCurVersion() {
        try {
            PackageManager packageManager = mContext.getPackageManager();
            PackageInfo packInfo = packageManager.getPackageInfo(mContext.getPackageName(), 0);
            return packInfo.versionName;
        } catch (PackageManager.NameNotFoundException ex){
            Log.w(TAG, "fail to get version");
        }
        return null;
    }

    private boolean getDownloadApkInfoFromServer() {
        if(ONLY_DEMO_WITHOUT_INTERNET) {
            return parseDownloadApkInfo(demoDownloadApkInfoFromServer());
        } else {
            //from server
            StringBuilder sbApkInfo = new StringBuilder();
            try {
                URL url = new URL(APK_INFO_HTTPPATH);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                //conn.connect();
                //int fileSize = conn.getContentLength();
                InputStream ins = conn.getInputStream();
                byte[] buf = new byte[1024];
                int readNum;
                while ((readNum = ins.read(buf)) > 0) {
                    sbApkInfo.append(new String(buf, 0, readNum));
                }
                ins.close();
                Log.i(TAG, "ApkInfo from Server: " + sbApkInfo.toString());
                showLog("Success to get ApkInfo from server! -> " + sbApkInfo.toString() + "\n");
            } catch (Exception e) {
                e.printStackTrace();
                Log.w(TAG, "Fail to get latest version from Server!");
                //Toast.makeText(mContext, "Fail to get latest version from Server!", Toast.LENGTH_LONG).show();
                return false;
            }
            return parseDownloadApkInfo(sbApkInfo.toString());
        }
    }

    private boolean parseDownloadApkInfo(String info) {
        //Version=8.4.1
        //Size=12582912
        //MD5=49e43915e8f0e854af3e2743737e9c12
        if (null == info || info.isEmpty()) return false;
        String[] line = info.split("\n");
        if (line.length <= 0) return false;

        mDownloadApkInfo = new DownloadApkInfo();
        for (String lineInfo : line) {
            String[] keyValue = lineInfo.trim().split("=");
            if (keyValue.length < 2) continue;
            if (0 == "Version".compareToIgnoreCase(keyValue[0])) {
                mDownloadApkInfo.mVersion = keyValue[1];
            } else if (0 == "Size".compareToIgnoreCase(keyValue[0])) {
                mDownloadApkInfo.mSize = Integer.parseInt(keyValue[1]);
            } else if (0 == "MD5".compareToIgnoreCase(keyValue[0])) {
                mDownloadApkInfo.mMD5 = keyValue[1];
            }
        }

        return !(mDownloadApkInfo.mVersion.isEmpty() || mDownloadApkInfo.mSize <= 0);
    }

    private boolean setDownloadPath() {
        File file = new File(DOWNLOAD_APKFILE_PATH);
        if (file.getParentFile().exists()) return true;

        return file.getParentFile().mkdirs();
    }

    private void DownloadLatestApk() {
        switch (mDownloadType) {
            case DOWNLOAD_BYDLM:
                downloadManager();
                break;
            case DOWNLOAD_BYHTTP:
                showDownloadProgressDialog();
                new Thread(mDownloadApkFileRunnable).start();
                break;
            default:
                Log.w(TAG, "wrong download type! do nothing");
                break;
        }
    }

    private Runnable mDownloadApkFileRunnable = new Runnable() {
        @Override
        public void run() {
            if(ONLY_DEMO_WITHOUT_INTERNET) {
                demoDownloadApkFile();
            } else {
                downloadApkFileFromServer();
            }
        }
    };

    private void downloadApkFileFromServer() {
        //from server
        try {
            URL url = new URL(APK_FILE_HTTPPATH);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setConnectTimeout(5000);
            //conn.setRequestMethod("POST");
            //conn.addRequestProperty("Accept-Encoding", "identity");
            //conn.setRequestProperty("User-Agent", " Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.120 Safari/537.36");
            long fileSize = conn.getContentLength(); //fail to get the size of file???
            if (fileSize < 0 && mDownloadApkInfo.mSize > 0) fileSize = mDownloadApkInfo.mSize;

            InputStream ins = conn.getInputStream();
            File file = new File(DOWNLOAD_APKFILE_PATH);
            FileOutputStream fos = new FileOutputStream(file);
            byte[] buf = new byte[4096];
            int readNum;
            int totalReadNum = 0;
            while ((readNum = ins.read(buf)) > 0) {
                if (mCancelDownload) break;

                fos.write(buf, 0, readNum);
                totalReadNum += readNum;
                int progress = (int) (((float) totalReadNum / fileSize) * 100);
                if ( mProgress.getProgress() != progress) {
                    Message message = Message.obtain();
                    message.what = DOWN_UPDATE;
                    message.arg1 = progress;
                    mHandler.sendMessage(message);
                }
            }
            fos.close();
            ins.close();

            if (mCancelDownload) {
                mHandler.sendEmptyMessage(DOWN_FAIL);
            } else {
                //SysUtil.copyFile(TMP_LATEST_APK_PATH, DOWNLOAD_APKFILE_PATH); //temp ....
                mHandler.sendEmptyMessage(DOWN_SUCCESS);
                showLog("Success to download apk file from server!\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.w(TAG, "Fail to download apk file from server!");
            //Toast.makeText(mContext, "Fail to download apk file from server!", Toast.LENGTH_LONG).show();
            showLog("Fail to download apk file from server!\n");
            mHandler.sendEmptyMessage(DOWN_FAIL);
        }
        mCancelDownload = false;
        mProgressDlg.dismiss();
    }

    private void installApp(String installApkPath) {
        File file = new File(installApkPath);
        if (!file.exists()) return;

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            uri = FileProvider.getUriForFile(mContext, mContext.getPackageName() + ".fileProvider", file);
        } else {
            uri = Uri.fromFile(file);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        mContext.startActivity(intent);
    }

    private void deleteDownloadApk(String downloadApkPath) {
        File file = new File(downloadApkPath);
        if (!file.exists()) return;

        String apkFileVersion = getVersionFromApkFile(downloadApkPath);
        String curVersion = getCurVersion();
        if (null == apkFileVersion || null == curVersion || apkFileVersion.equals(curVersion)) {
            if (file.delete()) {
                Log.d(TAG, "success to delete file: " + downloadApkPath);
            } else {
                Log.d(TAG, "fail to delete file: " + downloadApkPath);
            }
        }
    }

    private String getVersionFromApkFile(String apkPath) {
        PackageManager pm = mContext.getPackageManager();
        PackageInfo pkgInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES);
        if (pkgInfo != null) return pkgInfo.versionName;

        return null;
    }

    //Show UI
    private void showPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(mContext.getString(R.string.upgrade_permission_title));
        builder.setMessage(mContext.getString(R.string.upgrade_permission_message));
        builder.setPositiveButton(mContext.getString(R.string.upgrade_setpermissionbtn),
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if(!ActivityCompat.shouldShowRequestPermissionRationale((AppCompatActivity)mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri1 = Uri.fromParts("package", mContext.getPackageName(), null);
                        intent.setData(uri1);
                        mContext.startActivity(intent);
                    }

                    String[] permissions = {
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            //Manifest.permission.REQUEST_INSTALL_PACKAGES
                    };

                    ActivityCompat.requestPermissions(
                            (AppCompatActivity)mContext,
                            permissions,
                            UPGRADE_PERMISSION_REQUEST);
                    Log.i(TAG, "requestPermissions");
                }
            });
        builder.setNegativeButton(mContext.getString(R.string.upgrade_ignorebtn),
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mPermissonDlg.dismiss();
                    mHandler.removeMessages(DISMISS_PERMISSION_DLG);
                    Toast.makeText(mContext, mContext.getString(R.string.upgrade_permission_warn), Toast.LENGTH_LONG).show();
                }
            });
        mPermissonDlg = builder.create();
        setAlwaysTopShowType(mPermissonDlg);
        mPermissonDlg.show();
        mHandler.sendEmptyMessageDelayed(DISMISS_PERMISSION_DLG, DLG_DURATION);
    }

    private  void showHintDialog(String curVer, String latestVer){
        String title = mContext.getString(R.string.upgrade_title);
        String msg = String.format(mContext.getString(R.string.upgrade_message), curVer, latestVer);

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(title);
        builder.setMessage(msg);
        builder.setPositiveButton(mContext.getString(R.string.upgrade_downloadbtn),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!setDownloadPath()) return;
                        DownloadLatestApk();
                    }
                });
        builder.setNegativeButton(mContext.getString(R.string.upgrade_ignorebtn),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mHandler.removeMessages(DISMISS_HINT_DLG);
                        dialog.dismiss();
                    }
                });
        mHintDlg = builder.create();
        setAlwaysTopShowType(mHintDlg);
        mHintDlg.show();
        mHandler.sendEmptyMessageDelayed(DISMISS_HINT_DLG, DLG_DURATION);
    }

    private void showDownloadProgressDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(mContext.getString(R.string.upgrade_title));
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View v = inflater.inflate(R.layout.progressbar, null);
        mProgress = v.findViewById(R.id.progress);
        builder.setView(v);
        builder.setNegativeButton(mContext.getString(R.string.upgrade_ignorebtn), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            mCancelDownload = true;
            dialog.dismiss();
            }
        });

        mProgressDlg = builder.create();
        setAlwaysTopShowType(mProgressDlg);
        mProgressDlg.show();
    }

    private void setAlwaysTopShowType(AlertDialog dlg) {
        WindowManager.LayoutParams attrs = dlg.getWindow().getAttributes();
        attrs.flags =  WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;//| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        dlg.getWindow().setAttributes(attrs);
    }

    //should override it in activity
    void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case UPGRADE_PERMISSION_REQUEST:
                //Manifest.permission.WRITE_EXTERNAL_STORAGE
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mHandler.sendEmptyMessageDelayed(TRY_AGAIN, TRY_AGAIN_DELAY);
                }
                break;
        }
    }

    private class InnerBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction() ;
            if ("INSTALL_APK".equals( action )){
                String apkPath = intent.getStringExtra("APK_PATH");
                LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
                localBroadcastManager.unregisterReceiver(mInnerBroadcastReceiver);
                mInnerBroadcastReceiver = null;
                installApp(apkPath);
            }
        }
    }

    private void showLog(String log) {
        ((MainActivity)mContext).addShowInfo(log);
    }

    //temp demo **************************************************************
    //wrong?
    private void moveFileToSystem(String filePath, String sysFilePath) {
        SysUtil.execCmd("mount -o rw,remount /system");
        SysUtil.execCmd("chmod 777 /system");
        SysUtil.execCmd("cp  " + filePath + " " + sysFilePath);
    }

    private String demoDownloadApkInfoFromServer(){
        return SysUtil.readFile(TMP_LATEST_APKINFO_PATH);
    }

    private void demoDownloadApkFile() {
        for (int pos = 0; pos <= 10; pos ++) {
            Message message = Message.obtain();
            message.what = DOWN_UPDATE;
            message.arg1 = pos * 10;
            mHandler.sendMessage(message);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (mCancelDownload) break;
        }

        if (mCancelDownload) {
            mHandler.sendEmptyMessage(DOWN_FAIL);
        } else {
            SysUtil.copyFile(TMP_LATEST_APK_PATH, DOWNLOAD_APKFILE_PATH);
            mHandler.sendEmptyMessage(DOWN_SUCCESS);
        }
    }

    void demoBsdiff() {
        StringBuilder sbLog = new StringBuilder();
        sbLog.append("Version = " + version() + "\n");

        String installedApkPath = mContext.getApplicationContext().getPackageResourcePath();
        String oldApk = DOWNLOAD_DIR + "/ApkSelfUpgrade/bsdiff_test/old.apk";
        SysUtil.copyFile(installedApkPath, oldApk);

        String newApk = DOWNLOAD_DIR + "/ApkSelfUpgrade/bsdiff_test/new.apk";
        SysUtil.copyFile(TMP_LATEST_APK_PATH, newApk);
        String bsdiff = DOWNLOAD_DIR + "/ApkSelfUpgrade/bsdiff_test/bsdiff";

        File bsdiffFile = new File(bsdiff);
        if (bsdiffFile.exists()) bsdiffFile.delete();
        sbLog.append("1. generate bsdiff file between old apk and new apk\n");
        sbLog.append("oldApk = " + oldApk + "\n");
        sbLog.append("newApk = " + newApk + "\n");
        sbLog.append("bsdiff = " + bsdiff + "\n");
        int result = genDiffBetweenOldAndNewApk(oldApk, newApk, bsdiff);
        sbLog.append("result = " + result + "\n");
        showLog(sbLog.toString());
        sbLog.setLength(0);

        String newApk2 = DOWNLOAD_DIR + "/ApkSelfUpgrade/bsdiff_test/new2.apk";
        File newApk2File = new File(newApk2);
        if (newApk2File.exists()) newApk2File.delete();
        sbLog.append("2. generate new apk file from old apk and bsdiff\n");
        sbLog.append("oldApk = " + oldApk + "\n");
        sbLog.append("bsdiff = " + bsdiff + "\n");
        sbLog.append("newApk2 = " + newApk2 + "\n");

        result = genNewApkWithPatch(oldApk, newApk2, bsdiff);
        sbLog.append("result = " + result + "\n");
        showLog(sbLog.toString());

        demoMD5(newApk, newApk2);
    }
    //check download file is full?
    void demoMD5(String filePath, String cmpFilePath) {
        String md5_file = SysUtil.getFileMD5(filePath);
        String md5_cmpFile = SysUtil.getFileMD5(cmpFilePath);

        if (null != md5_file && md5_file.equals(md5_cmpFile)) {
            showLog("Pass md5 check\n");
            showLog(md5_file + "\n");
            Log.i(TAG, "Pass md5 check = " + md5_file);
        } else {
            showLog("Fail md5 check\n");
            showLog(md5_file + "\n");
            showLog(md5_cmpFile + "\n");
        }
    }

    void downloadManager() {
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
        mInnerBroadcastReceiver = new InnerBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter( "INSTALL_APK");
        localBroadcastManager.registerReceiver(mInnerBroadcastReceiver , intentFilter);

        String result = new DownloadUtil(mContext).downloadApk(SUB_DOWNLOAD_APKFILE_PATH, APK_FILE_HTTPPATH);
        showLog(result);
    }

    void demoApkSignature() {
        String sign = SysUtil.getLocalSignature(mContext);
        showLog("get signature from package:\n");
        showLog(sign);

        sign = SysUtil.collectCertificates(TMP_LATEST_APK_PATH);
        showLog("\n\nget signature from apk file:\n");
        showLog(sign);
    }

    void demoFuns() {
        moveFileToSystem(TMP_LATEST_APK_PATH,"/system/lib/test.so");
        //collectCertificates(TMP_LATEST_APK_PATH);  //pass
    }
}
