#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_fih_apkselfupgrade_SelfUpgrade_version(
        JNIEnv *env,
        jobject /* this */) {
    std::string version = "bsdiff_1_0";
    return env->NewStringUTF(version.c_str());
}
