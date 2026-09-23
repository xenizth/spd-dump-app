cmake_minimum_required(VERSION 3.22.1)
project(spddump)

# --- libusb -------------------------------------------------------------
# Drop a prebuilt libusb-android (with wrap_sys_device support, i.e. >=1.0.22)
# into third_party/libusb/{include,lib/${ANDROID_ABI}}. The libusb-android
# project (github.com/libusb/libusb, or the "libusb-android" wrapper repos)
# ships prebuilt .so files per ABI - only arm64-v8a is needed here.
set(LIBUSB_DIR ${CMAKE_SOURCE_DIR}/third_party/libusb)

add_library(usb1.0 SHARED IMPORTED)
set_target_properties(usb1.0 PROPERTIES
    IMPORTED_LOCATION ${LIBUSB_DIR}/lib/${ANDROID_ABI}/libusb1.0.so
)

# --- spd_dump core --------------------------------------------------------
# Drop the upstream spd_dump.c (and any headers it needs) into third_party/spd_dump/.
# It needs the two small patches described in PATCHING.md before this builds:
#   1. an spd_dump_run(argc, argv, log_fn) entry point instead of main()
#   2. a way to hand it an already-open libusb_device_handle (non-root path)
set(SPD_DUMP_DIR ${CMAKE_SOURCE_DIR}/third_party/spd_dump)

add_library(spddump SHARED
    native-lib.cpp
    ${SPD_DUMP_DIR}/spd_dump.c
)

target_include_directories(spddump PRIVATE
    ${LIBUSB_DIR}/include
    ${SPD_DUMP_DIR}
)

target_link_libraries(spddump
    usb1.0
    log
)
