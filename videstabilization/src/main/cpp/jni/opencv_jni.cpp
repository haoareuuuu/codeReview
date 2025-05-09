#include <jni.h>
#include <string>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

#define TAG "OpenCV_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

// 测试OpenCV是否正确集成
JNIEXPORT jstring JNICALL
Java_com_hsl_videstabilization_util_OpenCVJNI_getOpenCVVersion(JNIEnv *env, jclass clazz) {
    LOGI("Getting OpenCV version");
    std::string version = cv::getVersionString();
    LOGI("OpenCV version: %s", version.c_str());
    return env->NewStringUTF(version.c_str());
}

// 简单的图像处理示例 - 灰度转换
JNIEXPORT jboolean JNICALL
Java_com_hsl_videstabilization_util_OpenCVJNI_convertToGray(
        JNIEnv *env, jclass clazz,
        jlong matAddrRgba, jlong matAddrGray) {
    
    try {
        // 获取Mat对象
        cv::Mat &rgba = *(cv::Mat *) matAddrRgba;
        cv::Mat &gray = *(cv::Mat *) matAddrGray;
        
        // 转换为灰度
        cv::cvtColor(rgba, gray, cv::COLOR_RGBA2GRAY);
        
        return JNI_TRUE;
    } catch (cv::Exception &e) {
        LOGE("OpenCV error: %s", e.what());
        return JNI_FALSE;
    }
}

} // extern "C"
