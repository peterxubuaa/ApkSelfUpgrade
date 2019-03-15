//
// Created by H0135699 on 3/8/2019.
//

#ifndef BSDIFF_SO_ANDROID_LOG_H
#define BSDIFF_SO_ANDROID_LOG_H

#include <android/log.h>

#define  TAG    "BSDIFF"
// 定义info信息
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
// 定义debug信息
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
// 定义error信息
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG,__VA_ARGS__)

#endif //BSDIFF_SO_ANDROID_LOG_H
