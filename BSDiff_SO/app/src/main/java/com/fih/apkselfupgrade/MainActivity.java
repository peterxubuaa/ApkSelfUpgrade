package com.fih.apkselfupgrade;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "peterxu";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        TextView tv = findViewById(R.id.sample_text);
        tv.setText(new SelfUpgrade().version());
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public void onTestBtnClick(android.view.View vt) {
        int result = new SelfUpgrade().genDiffBetweenOldAndNewApk("/sdcard/bsdiff_test/apk_test/Latest.apk",
                "/sdcard/bsdiff_test/apk_test/app-debug.apk",
                "/sdcard/bsdiff_test/apk_test/diff.patch");

        Log.i(TAG, "genDiffBetweenOldAndNewApk = " + result);

        result = new SelfUpgrade().genNewApkWithPatch("/sdcard/bsdiff_test/apk_test/Latest.apk",
                "/sdcard/bsdiff_test/apk_test/app-debug_new.apk",
                "/sdcard/bsdiff_test/apk_test/diff.patch");

        Log.i(TAG, "genNewApkWithPatch = " + result);
    }
}
