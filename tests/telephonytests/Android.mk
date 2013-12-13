LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional debug

LOCAL_SRC_FILES := $(call all-subdir-java-files)

#LOCAL_STATIC_JAVA_LIBRARIES := librilproto-java

LOCAL_JAVA_LIBRARIES := android.test.runner telephony-common

LOCAL_PACKAGE_NAME := FrameworksTelephonyTests

LOCAL_PROGUARD_ENABLED := disabled
LOCAL_CERTIFICATE := platform
include $(BUILD_PACKAGE)
