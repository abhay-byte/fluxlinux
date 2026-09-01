LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := termux
LOCAL_SRC_FILES := termux.c
LOCAL_CFLAGS := -std=c11 -Wall -Wextra -Werror -Os -fno-stack-protector
# Android 15's 16 KB page-size devices require every arm64 LOAD segment to
# have at least 0x4000 alignment.  Keep this in the linker flags (rather than
# relying on a particular NDK default) so the native replacement is explicit.
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_SHARED_LIBRARY)
