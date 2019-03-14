package com.fih.apkselfupgrade;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private final int RUN_UPDATE = 1;
    private final int RUN_BSDIFF = 2;
    private final int RUN_APKSIGN = 3;
    private final int RUN_TEST = 4;
    private final int SHOW_TVINFO = 10;
    private final SelfUpgrade mSelfUpgrade = new SelfUpgrade(this);
    private TextView mTVHint;
    private StringBuffer mSBHintInfo = new StringBuffer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                //  引导用户手动开启安装权限
                //Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                //startActivityForResult(intent, 1);
            }
        });

        mTVHint = findViewById(R.id.tv_hint);
        showCurVersion();
    }

    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case RUN_UPDATE:
                    Switch sw = (Switch)findViewById(R.id.switch_type);
                    if (sw.isChecked()) {
                        mSelfUpgrade.upgradeApk(SelfUpgrade.DOWNLOAD_BYDLM);
                    } else {
                        mSelfUpgrade.upgradeApk(SelfUpgrade.DOWNLOAD_BYHTTP);
                    }
                    break;
                case RUN_BSDIFF:
                    mSelfUpgrade.demoBsdiff();
                    break;
                case RUN_APKSIGN:
                    mSelfUpgrade.demoApkSignature();
                    break;
                case SHOW_TVINFO:
                    if (null != mTVHint) mTVHint.setText(mSBHintInfo.toString());
                    break;
                case RUN_TEST:
                    mSelfUpgrade.demoFuns();
                    break;
                default:
                    break;
            }
            return true;
        }
    });

    private void showCurVersion() {
        try {
            PackageManager packageManager = getPackageManager();
            PackageInfo packInfo = packageManager.getPackageInfo(getPackageName(), 0);
            addShowInfo(packInfo.versionName+ "\n\n");
        } catch (PackageManager.NameNotFoundException ex){
            addShowInfo("fail to get version");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onBtnClickUpdateApk(android.view.View vt) {
        clearShowInfo();
        mHandler.sendEmptyMessage(RUN_UPDATE);
    }

    public void onBtnClickBsdiff(android.view.View vt) {
        clearShowInfo();
        mHandler.sendEmptyMessage(RUN_BSDIFF);
    }

    public void onBtnApkSignature(android.view.View vt) {
        clearShowInfo();
        mHandler.sendEmptyMessage(RUN_APKSIGN);
    }

    public void onBtnClickTestFuns(android.view.View vt) {
        clearShowInfo();
        mHandler.sendEmptyMessage(RUN_TEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mSelfUpgrade.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void addShowInfo(String info) {
        mSBHintInfo.append(info);
        mHandler.sendEmptyMessage(SHOW_TVINFO);
    }

    public void clearShowInfo() {
        mSBHintInfo.setLength(0);
        mHandler.sendEmptyMessage(SHOW_TVINFO);
    }
}
