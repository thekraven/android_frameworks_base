LOCAL_PATH:= $(call my-dir)

#
# libcameraservice
#

include $(CLEAR_VARS)

LOCAL_SRC_FILES:=               \
    CameraService.cpp

LOCAL_SHARED_LIBRARIES:= \
    libui \
    libutils \
    libbinder \
    libcutils \
    libmedia \
    libcamera_client \
    libgui \
    libhardware

LOCAL_MODULE:= libcameraservice

ifeq ($(BOARD_USE_FROYO_LIBCAMERA), true) 
LOCAL_CFLAGS += -DBOARD_USE_FROYO_LIBCAMERA 
endif 


ifeq ($(BOARD_HAVE_HTC_FFC), true)
LOCAL_CFLAGS += -DBOARD_HAVE_HTC_FFC
endif
#LOCAL_SHARED_LIBRARIES += libthunderccameraif 
include $(BUILD_SHARED_LIBRARY)
