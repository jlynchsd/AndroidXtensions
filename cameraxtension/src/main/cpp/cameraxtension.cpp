#include <jni.h>
#include <GLES3/gl3.h>

extern "C"
JNIEXPORT void
Java_com_androidxtensions_cameraxtension_gl_GlNativeBinding_readPixelsToBuffer(
        JNIEnv *env, jobject thiz,
        jint x, jint y,
        jint width, jint height,
        jint format, jint type) {
    glReadPixels(x, y, width, height, format, type, nullptr);
}

extern "C"
JNIEXPORT void
Java_com_androidxtensions_cameraxtension_gl_GlNativeBinding_rgbaToPackedArgb(
        JNIEnv *env, jobject thiz,
        jint width, jint height,
        jbyteArray input, jintArray output) {

    jbyte* inputPtr = env->GetByteArrayElements(input, nullptr);
    jint* outputPtr = env->GetIntArrayElements(output, nullptr);

    int dataIndex = 0;
    for (int i = 0; i < width * height; ++i) {
        unsigned char red = inputPtr[dataIndex++];
        unsigned char green = inputPtr[dataIndex++];
        unsigned char blue = inputPtr[dataIndex++];
        unsigned char alpha = inputPtr[dataIndex++];
        outputPtr[i] = (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    env->ReleaseByteArrayElements(input, inputPtr, 0);
    env->ReleaseIntArrayElements(output, outputPtr, 0);
}