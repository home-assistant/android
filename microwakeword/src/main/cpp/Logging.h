#ifndef LOGGING_H
#define LOGGING_H

#ifdef __ANDROID__
#include <android/log.h>

#define LOGD(tag, ...) __android_log_print(ANDROID_LOG_DEBUG, tag, __VA_ARGS__)
#define LOGI(tag, ...) __android_log_print(ANDROID_LOG_INFO, tag, __VA_ARGS__)
#define LOGE(tag, ...) __android_log_print(ANDROID_LOG_ERROR, tag, __VA_ARGS__)
#else
#define LOGD(tag, ...) ((void)0)
#define LOGI(tag, ...) ((void)0)
#define LOGE(tag, ...) ((void)0)
#endif

#endif // LOGGING_H
