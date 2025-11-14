#include <jni.h>
#include "AddNative.h"

JNIEXPORT jint JNICALL Java_AddNative_add(JNIEnv *env, jobject obj, jint a, jint b) {
    return a + b;
}
