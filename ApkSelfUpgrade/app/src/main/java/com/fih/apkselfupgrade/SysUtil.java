package com.fih.apkselfupgrade;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class SysUtil {
    public static final String TAG = "SELF_UPGRADE";

    public static String readFile(String filePath){
        StringBuilder sbInfo = new StringBuilder();
        File file = new File(filePath);
        try {
            int readNum;
            InputStream inStream = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            while ((readNum = inStream.read(buffer)) != -1) {
                sbInfo.append(new String(buffer, 0, readNum));
            }
            inStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return sbInfo.toString();
    }

    public static boolean copyFile(String oldPath, String newPath) {
        try {
            File newfile = new File(newPath);
            if (!newfile.getParentFile().exists()) {
                newfile.getParentFile().mkdirs();
            }
            newfile.createNewFile();

            int byteread;
            File oldfile = new File(oldPath);
            if (oldfile.exists()) {
                InputStream inStream = new FileInputStream(oldPath);
                FileOutputStream fs = new FileOutputStream(newPath);
                byte[] buffer = new byte[4096];
                while ((byteread = inStream.read(buffer)) != -1) {
                    fs.write(buffer, 0, byteread);
                }
                inStream.close();
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "fail to copy file from " + oldPath + " to " + newPath);
            e.printStackTrace();
        }

        return false;
    }

    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        boolean value = defaultValue;
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method getBoolean = c.getMethod("getBoolean", String.class, boolean.class);
            value = (boolean)(getBoolean.invoke(c, key, defaultValue ));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return value;
    }

    public static String getProperty(String key, String defaultValue) {
        String value = defaultValue;
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            value = (String)(get.invoke(c, key, defaultValue ));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return value;
    }

    public static boolean execCmd(String command) {
        //#chmod 6755 su
        //#setenforce 0
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (Exception e) {
            Log.e(TAG, "Fail to exec shell cmd: " + e.toString());
            return false;
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                if (process != null) {
                    process.destroy();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    public static String getFileMD5(String filePath){
        File file = new File(filePath);
        if (!file.isFile()) {
            return null;
        }
        MessageDigest digest;
        FileInputStream in;
        byte buffer[] = new byte[4096];
        int len;
        try {
            digest = MessageDigest.getInstance("MD5");
            in = new FileInputStream(file);
            while ((len = in.read(buffer, 0, 1024)) != -1) {
                digest.update(buffer, 0, len);
            }
            in.close();
            BigInteger bigInt = new BigInteger(1, digest.digest());
            return bigInt.toString(16);
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    public static String collectCertificates(String apkPath) {
        StringBuilder sbResult = new StringBuilder();
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(apkPath);
            JarEntry jarEntry = jarFile.getJarEntry("AndroidManifest.xml");
            byte[] readBuffer = new byte[1024];
            InputStream is = new BufferedInputStream(jarFile.getInputStream(jarEntry));
            while (is.read(readBuffer, 0, readBuffer.length) != -1) {
                // not using
            }
            is.close();
            if (null != jarEntry) {
                Certificate[] certs = jarEntry.getCertificates();
                //Public-Key: (1024 bit)
                //Modulus:
                //00:8b:50:b7:c2:8d:44:1d:98:98:df:bf:38:5f:7e:
                //5d:04:66:27:39:68:c0:2d:83:13:48:be:b9:40:8f:
                //23:23:47:a9:34:74:36:c1:11:e4:4a:10:f3:45:3a:
                //78:6c:41:11:86:76:41:83:90:a6:ee:ef:45:4e:07:
                //06:f1:c7:be:37:4d:68:49:4c:b7:c3:7c:5e:b4:da:
                //71:c9:87:31:2b:46:ca:82:5d:24:ac:90:60:fa:aa:
                //93:73:42:2a:e5:31:9d:98:ef:4b:b6:12:c8:9d:7a:
                //17:76:f9:88:30:09:a9:e6:0b:ac:2e:10:5b:cd:4f:
                //cd:9a:38:26:16:31:64:9c:25
                //Exponent: 65537 (0x10001)
                if (certs.length > 0) {
                    //sbResult.append(certs[0].toString());
                    String certInfo = certs[0].toString();
                    //Modulus
                    int start = certInfo.indexOf("Modulus:");
                    int end = certInfo.indexOf("Exponent:");
                    String modulus = certInfo.substring(start + "Modulus:".length() + 1, end);
                    modulus = modulus.replaceAll(":|\n| +", "").trim();
                    //Exponent
                    start = certInfo.indexOf("(0x", end);
                    end = certInfo.indexOf(")", end);
                    String exponent = certInfo.substring(start + "(0x".length(), end);
                    //modulus + Exponent(公钥)
                    sbResult.append(modulus);
                    sbResult.append(",");
                    sbResult.append(exponent);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sbResult.toString();
    }

    private static Certificate[] loadCertificates(JarFile jarFile, JarEntry je,
                                           byte[] readBuffer) throws IOException {
        // We must read the stream for the JarEntry to retrieve
        // its certificates.
        InputStream is = new BufferedInputStream(jarFile.getInputStream(je));
        while (is.read(readBuffer, 0, readBuffer.length) != -1) {
            // not using
        }
        is.close();
        return je != null ? je.getCertificates() : null;
    }

    public static String getLocalSignature(Context ctx) {
        StringBuilder sbResult = new StringBuilder();
        //get signature info depends on package name
        PackageInfo packageInfo = null;
        try {
            packageInfo = ctx.getPackageManager().getPackageInfo(
                    ctx.getPackageName(), PackageManager.GET_SIGNATURES); //GET_SIGNING_CERTIFICATES
            android.content.pm.Signature[] signs = packageInfo.signatures;
            Signature sign = signs[0];
            CertificateFactory certFactory = CertificateFactory
                    .getInstance("X.509");
            X509Certificate cert = (X509Certificate) certFactory
                    .generateCertificate(new ByteArrayInputStream(sign.toByteArray()));
            String pubKey = cert.getPublicKey().toString();
            if (pubKey != null && !pubKey.isEmpty()) {
                //OpenSSLRSAPublicKey{modulus=8b50b7c28d441d9898dfbf385f7e5d0466273968c02d831348beb9408f232347a9347436c111e44a10f3453a786c41118676418390a6eeef454e0706f1c7be374d68494cb7c37c5eb4da71c987312b46ca825d24ac9060faaa9373422ae5319d98ef4bb612c89d7a1776f9883009a9e60bac2e105bcd4fcd9a38261631649c25,publicExponent=10001}
                Log.i(TAG, pubKey);
                int start = pubKey.indexOf("{");
                int end = pubKey.lastIndexOf("}");
                pubKey = pubKey.substring(start + 1, end);
                String[] infos = pubKey.split(",");
                //modulus + publicexponent(公钥)
                for (int i = 0; i < infos.length; i++) {
                    String[] keyValue = infos[i].split("=");
                    if (keyValue.length != 2) continue;
                    if ("modulus".equals(keyValue[0])) {
                        sbResult.append(keyValue[1]);
                        continue;
                    }
                    if ("publicExponent".equals(keyValue[0])) {
                        sbResult.append(",");
                        sbResult.append(keyValue[1]);
                    }
                }

                return sbResult.toString();
            }
        } catch (PackageManager.NameNotFoundException | CertificateException e) {
            e.printStackTrace();
        }

        return null;
    }
}
