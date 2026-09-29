LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
include $(LOCAL_PATH)/../../../common.mk


LOCAL_MODULE                  := hwcomposer.$(TARGET_BOARD_PLATFORM)
LOCAL_VENDOR_MODULE           := true
LOCAL_MODULE_RELATIVE_PATH    := hw
LOCAL_MODULE_TAGS             := optional
LOCAL_C_INCLUDES              := $(common_includes) \
                                 $(kernel_includes)
LOCAL_HEADER_LIBRARIES        := display_headers

LOCAL_CFLAGS                  := -Wno-missing-field-initializers -Wno-unused-parameter \
                                 -fcolor-diagnostics\
                                 -DLOG_TAG=\"SDM\" $(common_flags)
ifeq ($(TARGET_EXCLUDES_DISPLAY_PP), true)
LOCAL_CFLAGS += -DEXCLUDE_DISPLAY_PP
endif

LOCAL_CLANG                   := true

# TODO: Remove libui after addressing gpu_tonemapper issues
LOCAL_SHARED_LIBRARIES        := libsdmcore libqservice libbinder libhardware libhardware_legacy \
                                 libutils libcutils libsync libqdutils libqdMetaData libdl libdrmutils \
                                 libsdmutils libc++ liblog libgrallocutils libdl \
                                 vendor.display.config@1.0 libhidlbase \
                                 vendor.lineage.livedisplay@2.0 \
                                 libui libgpu_tonemapper


ifeq ($(display_config_version), DISPLAY_CONFIG_1_1)
LOCAL_SHARED_LIBRARIES        += vendor.display.config@1.1
endif

ifeq ($(display_config_version), DISPLAY_CONFIG_1_7)
LOCAL_SHARED_LIBRARIES        += vendor.display.config@1.7 \
                                 vendor.display.config@1.6 vendor.display.config@1.5 \
                                 vendor.display.config@1.4 vendor.display.config@1.3 \
                                 vendor.display.config@1.2 vendor.display.config@1.1
endif
ifeq ($(display_config_version), DISPLAY_CONFIG_1_8)
LOCAL_SHARED_LIBRARIES        += vendor.display.config@1.1 vendor.display.config@1.2 \
                                 vendor.display.config@1.3 vendor.display.config@1.4 \
                                 vendor.display.config@1.5 vendor.display.config@1.6 \
                                 vendor.display.config@1.7 vendor.display.config@1.8
endif
ifeq ($(display_config_version), DISPLAY_CONFIG_1_9)
LOCAL_SHARED_LIBRARIES        += vendor.display.config@1.1 vendor.display.config@1.2 \
                                 vendor.display.config@1.3 vendor.display.config@1.4 \
                                 vendor.display.config@1.5 vendor.display.config@1.6 \
                                 vendor.display.config@1.7 vendor.display.config@1.8 \
                                 vendor.display.config@1.9
endif

LOCAL_SRC_FILES               := hwc_session.cpp \
                                 hwc_sunlight_enhancement.cpp \
                                 hwc_session_services.cpp \
                                 hwc_display.cpp \
                                 hwc_display_primary.cpp \
                                 hwc_display_external.cpp \
                                 hwc_display_virtual.cpp \
                                 hwc_display_dummy.cpp \
                                 hwc_debugger.cpp \
                                 hwc_buffer_sync_handler.cpp \
                                 hwc_color_manager.cpp \
                                 hwc_layers.cpp \
                                 hwc_callbacks.cpp \
                                 cpuhint.cpp \
                                 hwc_socket_handler.cpp \
                                 display_null.cpp \
                                 hwc_tonemapper.cpp \
                                 hwc_display_external_test.cpp

LOCAL_SRC_FILES               += hwc_buffer_allocator.cpp

ifeq ($(TARGET_HAS_WIDE_COLOR_DISPLAY), true)
    LOCAL_CFLAGS += -DFEATURE_WIDE_COLOR
endif

include $(BUILD_SHARED_LIBRARY)
