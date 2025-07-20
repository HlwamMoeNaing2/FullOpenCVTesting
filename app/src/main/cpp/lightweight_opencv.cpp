#include <jni.h>
#include <android/bitmap.h>
#include <opencv2/opencv.hpp>
#include <vector>
#include <memory>

using namespace cv;
using namespace std;

// Global JNI environment
JNIEnv* g_env = nullptr;

// Helper function to convert Java Point to cv::Point
cv::Point javaPointToCvPoint(JNIEnv* env, jobject pointObj) {
    jclass pointClass = env->GetObjectClass(pointObj);
    jfieldID xField = env->GetFieldID(pointClass, "x", "D");
    jfieldID yField = env->GetFieldID(pointClass, "y", "D");
    
    double x = env->GetDoubleField(pointObj, xField);
    double y = env->GetDoubleField(pointObj, yField);
    
    return cv::Point(x, y);
}

// Helper function to create Java Point from cv::Point
jobject cvPointToJavaPoint(JNIEnv* env, const cv::Point& point) {
    jclass pointClass = env->FindClass("mm/com/wavemoney/fullopencvtesting/utils/LightweightOpenCV$Point");
    jmethodID constructor = env->GetMethodID(pointClass, "<init>", "(DD)V");
    return env->NewObject(pointClass, constructor, point.x, point.y);
}

// Helper function to create Java Rect from cv::Rect
jobject cvRectToJavaRect(JNIEnv* env, const cv::Rect& rect) {
    jclass rectClass = env->FindClass("mm/com/wavemoney/fullopencvtesting/utils/LightweightOpenCV$Rect");
    jmethodID constructor = env->GetMethodID(rectClass, "<init>", "(IIII)V");
    return env->NewObject(rectClass, constructor, rect.x, rect.y, rect.width, rect.height);
}

// Helper function to create Java Contour from vector<Point>
jobject vectorToJavaContour(JNIEnv* env, const vector<cv::Point>& points) {
    jclass contourClass = env->FindClass("mm/com/wavemoney/fullopencvtesting/utils/LightweightOpenCV$Contour");
    jclass pointClass = env->FindClass("mm/com/wavemoney/fullopencvtesting/utils/LightweightOpenCV$Point");
    
    // Create Point array
    jobjectArray pointArray = env->NewObjectArray(points.size(), pointClass, nullptr);
    for (size_t i = 0; i < points.size(); i++) {
        jobject point = cvPointToJavaPoint(env, points[i]);
        env->SetObjectArrayElement(pointArray, i, point);
    }
    
    // Create Contour
    jmethodID constructor = env->GetMethodID(contourClass, "<init>", "([Lmm/com/wavemoney/fullopencvtesting/utils/LightweightOpenCV$Point;)V");
    return env->NewObject(contourClass, constructor, pointArray);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_createMat(
        JNIEnv* env, jobject thiz, jint rows, jint cols, jint type) {
    Mat* mat = new Mat(rows, cols, type);
    return reinterpret_cast<jlong>(mat);
}

JNIEXPORT void JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_releaseMat(
        JNIEnv* env, jobject thiz, jlong matPtr) {
    Mat* mat = reinterpret_cast<Mat*>(matPtr);
    delete mat;
}

JNIEXPORT jlong JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_bitmapToMat(
        JNIEnv* env, jobject thiz, jobject bitmap) {
    AndroidBitmapInfo info;
    void* pixels;
    
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        return 0;
    }
    
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return 0;
    }
    
    Mat* mat = new Mat(info.height, info.width, CV_8UC4);
    memcpy(mat->data, pixels, info.height * info.width * 4);
    
    AndroidBitmap_unlockPixels(env, bitmap);
    return reinterpret_cast<jlong>(mat);
}

JNIEXPORT jobject JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_matToBitmap(
        JNIEnv* env, jobject thiz, jlong matPtr) {
    Mat* mat = reinterpret_cast<Mat*>(matPtr);
    
    // Create Bitmap
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMethod = env->GetStaticMethodID(bitmapClass, "createBitmap", 
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    
    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888Field = env->GetStaticFieldID(configClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject config = env->GetStaticObjectField(configClass, argb8888Field);
    
    jobject bitmap = env->CallStaticObjectMethod(bitmapClass, createBitmapMethod, 
        mat->cols, mat->rows, config);
    
    // Copy data to bitmap
    AndroidBitmapInfo info;
    void* pixels;
    AndroidBitmap_getInfo(env, bitmap, &info);
    AndroidBitmap_lockPixels(env, bitmap, &pixels);
    memcpy(pixels, mat->data, mat->rows * mat->cols * 4);
    AndroidBitmap_unlockPixels(env, bitmap);
    
    return bitmap;
}

JNIEXPORT jlong JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_rotateMat(
        JNIEnv* env, jobject thiz, jlong matPtr, jint rotation) {
    Mat* src = reinterpret_cast<Mat*>(matPtr);
    Mat* dst = new Mat();
    
    int rotateCode;
    switch (rotation) {
        case 0: rotateCode = ROTATE_90_CLOCKWISE; break;
        case 1: rotateCode = ROTATE_180; break;
        case 2: rotateCode = ROTATE_90_COUNTERCLOCKWISE; break;
        default: rotateCode = ROTATE_90_CLOCKWISE;
    }
    
    rotate(*src, *dst, rotateCode);
    return reinterpret_cast<jlong>(dst);
}

JNIEXPORT jlong JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_cvtColor(
        JNIEnv* env, jobject thiz, jlong matPtr, jint code) {
    Mat* src = reinterpret_cast<Mat*>(matPtr);
    Mat* dst = new Mat();
    
    cvtColor(*src, *dst, code);
    return reinterpret_cast<jlong>(dst);
}

JNIEXPORT jlong JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_equalizeHist(
        JNIEnv* env, jobject thiz, jlong matPtr) {
    Mat* src = reinterpret_cast<Mat*>(matPtr);
    Mat* dst = new Mat();
    
    equalizeHist(*src, *dst);
    return reinterpret_cast<jlong>(dst);
}

JNIEXPORT jlong JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_gaussianBlur(
        JNIEnv* env, jobject thiz, jlong matPtr, jint kernelSize) {
    Mat* src = reinterpret_cast<Mat*>(matPtr);
    Mat* dst = new Mat();
    
    Size ksize(kernelSize, kernelSize);
    GaussianBlur(*src, *dst, ksize, 0);
    return reinterpret_cast<jlong>(dst);
}

JNIEXPORT jlong JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_canny(
        JNIEnv* env, jobject thiz, jlong matPtr, jdouble lowThreshold, jdouble highThreshold) {
    Mat* src = reinterpret_cast<Mat*>(matPtr);
    Mat* dst = new Mat();
    
    Canny(*src, *dst, lowThreshold, highThreshold);
    return reinterpret_cast<jlong>(dst);
}

JNIEXPORT jlong JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_morphologyEx(
        JNIEnv* env, jobject thiz, jlong matPtr, jint operation, jint kernelSize) {
    Mat* src = reinterpret_cast<Mat*>(matPtr);
    Mat* dst = new Mat();
    
    Mat kernel = getStructuringElement(MORPH_RECT, Size(kernelSize, kernelSize));
    morphologyEx(*src, *dst, operation, kernel);
    return reinterpret_cast<jlong>(dst);
}

JNIEXPORT jobjectArray JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_findContours(
        JNIEnv* env, jobject thiz, jlong matPtr) {
    Mat* src = reinterpret_cast<Mat*>(matPtr);
    vector<vector<Point>> contours;
    vector<Vec4i> hierarchy;
    
    findContours(*src, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
    
    // Convert to Java Contour array
    jclass contourClass = env->FindClass("mm/com/wavemoney/fullopencvtesting/utils/LightweightOpenCV$Contour");
    jobjectArray contourArray = env->NewObjectArray(contours.size(), contourClass, nullptr);
    
    for (size_t i = 0; i < contours.size(); i++) {
        jobject contour = vectorToJavaContour(env, contours[i]);
        env->SetObjectArrayElement(contourArray, i, contour);
    }
    
    return contourArray;
}

JNIEXPORT jdouble JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_arcLength(
        JNIEnv* env, jobject thiz, jobject contour, jboolean closed) {
    // Convert Java Contour to vector<Point>
    jclass contourClass = env->GetObjectClass(contour);
    jfieldID pointsField = env->GetFieldID(contourClass, "points", 
        "[Lmm/com/wavemoney/fullopencvtesting/utils/LightweightOpenCV$Point;");
    jobjectArray pointArray = (jobjectArray)env->GetObjectField(contour, pointsField);
    
    jsize length = env->GetArrayLength(pointArray);
    vector<Point> points;
    for (int i = 0; i < length; i++) {
        jobject pointObj = env->GetObjectArrayElement(pointArray, i);
        points.push_back(javaPointToCvPoint(env, pointObj));
    }
    
    return arcLength(points, closed);
}

JNIEXPORT jobjectArray JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_approxPolyDP(
        JNIEnv* env, jobject thiz, jobject contour, jdouble epsilon, jboolean closed) {
    // Convert Java Contour to vector<Point>
    jclass contourClass = env->GetObjectClass(contour);
    jfieldID pointsField = env->GetFieldID(contourClass, "points", 
        "[Lmm/com/wavemoney/fullopencvtesting/utils/LightweightOpenCV$Point;");
    jobjectArray pointArray = (jobjectArray)env->GetObjectField(contour, pointsField);
    
    jsize length = env->GetArrayLength(pointArray);
    vector<Point> points;
    for (int i = 0; i < length; i++) {
        jobject pointObj = env->GetObjectArrayElement(pointArray, i);
        points.push_back(javaPointToCvPoint(env, pointObj));
    }
    
    vector<Point> approx;
    approxPolyDP(points, approx, epsilon, closed);
    
    // Convert back to Java Point array
    jclass pointClass = env->FindClass("mm/com/wavemoney/fullopencvtesting/utils/LightweightOpenCV$Point");
    jobjectArray result = env->NewObjectArray(approx.size(), pointClass, nullptr);
    for (size_t i = 0; i < approx.size(); i++) {
        jobject point = cvPointToJavaPoint(env, approx[i]);
        env->SetObjectArrayElement(result, i, point);
    }
    
    return result;
}

JNIEXPORT jdouble JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_contourArea(
        JNIEnv* env, jobject thiz, jobject contour) {
    // Convert Java Contour to vector<Point>
    jclass contourClass = env->GetObjectClass(contour);
    jfieldID pointsField = env->GetFieldID(contourClass, "points", 
        "[Lmm/com/wavemoney/fullopencvtesting/utils/LightweightOpenCV$Point;");
    jobjectArray pointArray = (jobjectArray)env->GetObjectField(contour, pointsField);
    
    jsize length = env->GetArrayLength(pointArray);
    vector<Point> points;
    for (int i = 0; i < length; i++) {
        jobject pointObj = env->GetObjectArrayElement(pointArray, i);
        points.push_back(javaPointToCvPoint(env, pointObj));
    }
    
    return contourArea(points);
}

JNIEXPORT jobject JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_boundingRect(
        JNIEnv* env, jobject thiz, jobject contour) {
    // Convert Java Contour to vector<Point>
    jclass contourClass = env->GetObjectClass(contour);
    jfieldID pointsField = env->GetFieldID(contourClass, "points", 
        "[Lmm/com/wavemoney/fullopencvtesting/utils/LightweightOpenCV$Point;");
    jobjectArray pointArray = (jobjectArray)env->GetObjectField(contour, pointsField);
    
    jsize length = env->GetArrayLength(pointArray);
    vector<Point> points;
    for (int i = 0; i < length; i++) {
        jobject pointObj = env->GetObjectArrayElement(pointArray, i);
        points.push_back(javaPointToCvPoint(env, pointObj));
    }
    
    Rect rect = boundingRect(points);
    return cvRectToJavaRect(env, rect);
}

JNIEXPORT jlong JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_getPerspectiveTransform(
        JNIEnv* env, jobject thiz, jobjectArray srcPoints, jobjectArray dstPoints) {
    // Convert Java Point arrays to vector<Point2f>
    jsize srcLength = env->GetArrayLength(srcPoints);
    jsize dstLength = env->GetArrayLength(dstPoints);
    
    vector<Point2f> srcVec, dstVec;
    for (int i = 0; i < srcLength; i++) {
        jobject pointObj = env->GetObjectArrayElement(srcPoints, i);
        Point p = javaPointToCvPoint(env, pointObj);
        srcVec.push_back(Point2f(p.x, p.y));
    }
    for (int i = 0; i < dstLength; i++) {
        jobject pointObj = env->GetObjectArrayElement(dstPoints, i);
        Point p = javaPointToCvPoint(env, pointObj);
        dstVec.push_back(Point2f(p.x, p.y));
    }
    
    Mat* transform = new Mat();
    *transform = getPerspectiveTransform(srcVec, dstVec);
    return reinterpret_cast<jlong>(transform);
}

JNIEXPORT jlong JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_warpPerspective(
        JNIEnv* env, jobject thiz, jlong srcMatPtr, jlong transformMatPtr, jint width, jint height) {
    Mat* src = reinterpret_cast<Mat*>(srcMatPtr);
    Mat* transform = reinterpret_cast<Mat*>(transformMatPtr);
    Mat* dst = new Mat();
    
    warpPerspective(*src, *dst, *transform, Size(width, height));
    return reinterpret_cast<jlong>(dst);
}

// Mat utility functions
JNIEXPORT jint JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_00024Mat_getMatWidth(
        JNIEnv* env, jobject thiz, jlong matPtr) {
    Mat* mat = reinterpret_cast<Mat*>(matPtr);
    return mat->cols;
}

JNIEXPORT jint JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_00024Mat_getMatHeight(
        JNIEnv* env, jobject thiz, jlong matPtr) {
    Mat* mat = reinterpret_cast<Mat*>(matPtr);
    return mat->rows;
}

JNIEXPORT void JNICALL
Java_mm_com_wavemoney_fullopencvtesting_utils_LightweightOpenCV_00024Mat_copyMat(
        JNIEnv* env, jobject thiz, jlong srcPtr, jlong dstPtr) {
    Mat* src = reinterpret_cast<Mat*>(srcPtr);
    Mat* dst = reinterpret_cast<Mat*>(dstPtr);
    src->copyTo(*dst);
}

} // extern "C"
extern "C"
JNIEXPORT jlong JNICALL
Java_org_opencv_core_Mat_n_1Mat__III(JNIEnv *env, jclass clazz, jint rows, jint cols, jint type) {
    // TODO: implement n_Mat()
}
extern "C"
JNIEXPORT jlong JNICALL
Java_org_opencv_core_Mat_n_1Mat__I_3II(JNIEnv *env, jclass clazz, jint ndims, jintArray sizes,
                                       jint type) {
    // TODO: implement n_Mat()
}