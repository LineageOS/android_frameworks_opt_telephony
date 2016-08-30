LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := telresources
LOCAL_CERTIFICATE := platform
LOCAL_MODULE_TAGS := optional eng
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
include $(BUILD_PACKAGE)
include $(call all-makefiles-under,$(LOCAL_PATH))
