#include <jni.h>
#include <android/log.h>
#include <vector>
#include <string>

#include "apriltag.h"
#include "tag36h11.h"
#include "tag25h9.h"
#include "tag16h5.h"
#include "tagCircle21h7.h"
#include "tagCircle49h12.h"
#include "tagStandard41h12.h"
#include "tagStandard52h13.h"
#include "tagCustom48h12.h"
#include "common/matd.h"
#include "common/image_u8.h"

#define TAG "AprilTagNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

// 全局变量存储detector实例
static apriltag_detector_t* detector = nullptr;
static apriltag_family_t* tagFamily = nullptr;

JNIEXPORT void JNICALL
Java_edu_umich_eecs_april_apriltag_ApriltagNative_native_1init(JNIEnv *env, jclass clazz) {
    LOGD("Initializing AprilTag native library");
}

JNIEXPORT void JNICALL
Java_edu_umich_eecs_april_apriltag_ApriltagNative_apriltag_1init(JNIEnv *env, jclass clazz,
                                                                 jstring tagFamilyStr,
                                                                 jint errorBits,
                                                                 jdouble decimateFactor,
                                                                 jdouble blurSigma,
                                                                 jint nthreads) {
    const char* familyName = env->GetStringUTFChars(tagFamilyStr, 0);

    // 如果已经初始化了，先清理
    if (detector != nullptr) {
        apriltag_detector_destroy(detector);
        detector = nullptr;
    }
    if (tagFamily != nullptr) {
        // 根据当前标签族类型销毁对应的标签族
        if (strcmp(tagFamily->name, "tag36h11") == 0) {
            tag36h11_destroy(tagFamily);
        } else if (strcmp(tagFamily->name, "tag25h9") == 0) {
            tag25h9_destroy(tagFamily);
        } else if (strcmp(tagFamily->name, "tag16h5") == 0) {
            tag16h5_destroy(tagFamily);
        } else if (strcmp(tagFamily->name, "tagCircle21h7") == 0) {
            tagCircle21h7_destroy(tagFamily);
        } else if (strcmp(tagFamily->name, "tagCircle49h12") == 0) {
            tagCircle49h12_destroy(tagFamily);
        } else if (strcmp(tagFamily->name, "tagStandard41h12") == 0) {
            tagStandard41h12_destroy(tagFamily);
        } else if (strcmp(tagFamily->name, "tagStandard52h13") == 0) {
            tagStandard52h13_destroy(tagFamily);
        } else if (strcmp(tagFamily->name, "tagCustom48h12") == 0) {
            tagCustom48h12_destroy(tagFamily);
        }
        tagFamily = nullptr;
    }

    // 创建标签族
    if (strcmp(familyName, "tag36h11") == 0) {
        tagFamily = tag36h11_create();
    } else if (strcmp(familyName, "tag25h9") == 0) {
        tagFamily = tag25h9_create();
    } else if (strcmp(familyName, "tag16h5") == 0) {
        tagFamily = tag16h5_create();
    } else if (strcmp(familyName, "tagCircle21h7") == 0) {
        tagFamily = tagCircle21h7_create();
    } else if (strcmp(familyName, "tagCircle49h12") == 0) {
        tagFamily = tagCircle49h12_create();
    } else if (strcmp(familyName, "tagStandard41h12") == 0) {
        tagFamily = tagStandard41h12_create();
    } else if (strcmp(familyName, "tagStandard52h13") == 0) {
        tagFamily = tagStandard52h13_create();
    } else if (strcmp(familyName, "tagCustom48h12") == 0) {
        tagFamily = tagCustom48h12_create();
    } else {
        LOGE("Unknown tag family: %s", familyName);
        env->ReleaseStringUTFChars(tagFamilyStr, familyName);
        return;
    }

    // 设置hamming参数 (h字段)
    tagFamily->h = errorBits;

    // 创建detector
    detector = apriltag_detector_create();
    apriltag_detector_add_family(detector, tagFamily);

    // 设置参数
    detector->quad_decimate = decimateFactor;
    detector->quad_sigma = blurSigma;
    detector->nthreads = nthreads;
    detector->decode_sharpening = 0.25;

    env->ReleaseStringUTFChars(tagFamilyStr, familyName);
    LOGD("AprilTag initialized with family: %s, errorBits: %d, decimate: %f, sigma: %f, threads: %d",
         familyName, errorBits, decimateFactor, blurSigma, nthreads);
}

JNIEXPORT jobject JNICALL
Java_edu_umich_eecs_april_apriltag_ApriltagNative_apriltag_1detect_1yuv(JNIEnv *env, jclass clazz,
                                                                        jbyteArray yuvBytes,
                                                                        jint width, jint height) {
    if (detector == nullptr || tagFamily == nullptr) {
        LOGE("AprilTag not initialized!");
        return nullptr;
    }

    // 获取字节数组
    jbyte* yuv_data = env->GetByteArrayElements(yuvBytes, nullptr);
    int yuv_len = env->GetArrayLength(yuvBytes);

    // 创建image_u8_t对象
    image_u8_t img = {.width = width, .height = height, .stride = width, .buf = (uint8_t*)yuv_data};

    // 进行检测
    zarray_t* detections = apriltag_detector_detect(detector, &img);

    // 获取ApriltagDetection类和构造函数
    jclass detectionClass = env->FindClass("edu/umich/eecs/april/apriltag/ApriltagDetection");
    jmethodID detectionConstructor = env->GetMethodID(detectionClass, "<init>", "()V");

    // 创建ArrayList类
    jclass listClass = env->FindClass("java/util/ArrayList");
    jmethodID listConstructor = env->GetMethodID(listClass, "<init>", "()V");
    jmethodID listAdd = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");

    jobject detectionList = env->NewObject(listClass, listConstructor);

    // 遍历检测结果
    for (int i = 0; i < zarray_size(detections); i++) {
        apriltag_detection_t* det;
        zarray_get(detections, i, &det);

        // 创建ApriltagDetection对象
        jobject detectionObj = env->NewObject(detectionClass, detectionConstructor);

        // 设置属性
        jfieldID idField = env->GetFieldID(detectionClass, "id", "I");
        env->SetIntField(detectionObj, idField, det->id);

        jfieldID hammingField = env->GetFieldID(detectionClass, "hamming", "I");
        env->SetIntField(detectionObj, hammingField, det->hamming);

        // 设置中心点坐标
        jfieldID centerField = env->GetFieldID(detectionClass, "c", "[D");
        jdoubleArray centerArray = env->NewDoubleArray(2);
        jdouble centerCoords[2] = {det->c[0], det->c[1]};
        env->SetDoubleArrayRegion(centerArray, 0, 2, centerCoords);
        env->SetObjectField(detectionObj, centerField, centerArray);

        // 设置角点坐标 - 修复这里的错误
        jfieldID cornersField = env->GetFieldID(detectionClass, "p", "[D");
        jdoubleArray cornersArray = env->NewDoubleArray(8); // 4个角点 * 2个坐标 = 8
        jdouble cornerCoords[8];
        for (int j = 0; j < 4; j++) {
            cornerCoords[j * 2] = det->p[j][0];     // x坐标
            cornerCoords[j * 2 + 1] = det->p[j][1]; // y坐标
        }
        env->SetDoubleArrayRegion(cornersArray, 0, 8, cornerCoords);
        env->SetObjectField(detectionObj, cornersField, cornersArray);

        // 添加到列表
        env->CallBooleanMethod(detectionList, listAdd, detectionObj);

        // 清理局部引用
        env->DeleteLocalRef(detectionObj);
        env->DeleteLocalRef(centerArray);
        env->DeleteLocalRef(cornersArray);
    }

    // 销毁检测结果
    apriltag_detections_destroy(detections);

    // 释放字节数组
    env->ReleaseByteArrayElements(yuvBytes, yuv_data, JNI_ABORT);

    return detectionList;
}

} // extern "C"