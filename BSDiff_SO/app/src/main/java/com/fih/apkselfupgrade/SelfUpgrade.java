package com.fih.apkselfupgrade;

public class SelfUpgrade {
    static {
        System.loadLibrary("bsdiff-lib");
    }

    public native String version();
    public native int genDiffBetweenOldAndNewApk(String oldFile, String newFile, String patchFile);
    public native int genNewApkWithPatch(String oldFile, String newFile, String patchFile);
}
