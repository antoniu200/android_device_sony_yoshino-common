#include "hwc_sunlight_enhancement.h"

#include <cutils/properties.h>
#include <cutils/sockets.h>
#include <utils/debug.h>

#include <chrono>
#include <cstring>
#include <sys/socket.h>
#include <unistd.h>

#define __CLASS__ "HWCSunlightEnhancement"

namespace {

constexpr const char *kAdSupportProperty = "ro.qcom.ad";
constexpr const char *kPpdProperty = "init.svc.ppd";
constexpr const char *kVendorPpdProperty = "init.svc.vendor.ppd";
constexpr const char *kSocketName = "pps";
constexpr const char *kStatusCommand = "ad:status";
constexpr const char *kEnableCommand = "ad:on;1;16";
constexpr const char *kDisableCommand = "ad:off";
constexpr const char *kEnabledPrefix = "1;1";
constexpr const char *kSuccessResponse = "Success";
constexpr int kCommandAttempts = 10;
constexpr size_t kCommandResponseSize = 32;

bool WriteAll(int fd, const char *data, size_t size) {
  size_t written = 0;
  while (written < size) {
    ssize_t ret = TEMP_FAILURE_RETRY(write(fd, data + written, size - written));
    if (ret <= 0) {
      return false;
    }
    written += static_cast<size_t>(ret);
  }
  return true;
}

bool IsPpdRunningProperty(const char *name) {
  char value[PROPERTY_VALUE_MAX] = {};
  property_get(name, value, "");
  return !strcmp(value, "running");
}

}  // anonymous namespace

namespace sdm {

HWCSunlightEnhancement::HWCSunlightEnhancement()
    : worker_(&HWCSunlightEnhancement::WorkerLoop, this) {}

HWCSunlightEnhancement::~HWCSunlightEnhancement() {
  {
    std::lock_guard<std::mutex> lock(mutex_);
    stop_ = true;
  }
  condition_.notify_all();
  if (worker_.joinable()) {
    worker_.join();
  }
}

bool HWCSunlightEnhancement::IsSupported() {
  char value[PROPERTY_VALUE_MAX] = {};
  property_get(kAdSupportProperty, value, "0");
  return value[0] == '1';
}

Return<bool> HWCSunlightEnhancement::isEnabled() {
  std::lock_guard<std::mutex> lock(mutex_);
  return desired_enabled_;
}

Return<bool> HWCSunlightEnhancement::setEnabled(bool enabled) {
  if (!IsSupported()) {
    DLOGW("Assertive Display is not supported by %s", kAdSupportProperty);
    return false;
  }

  {
    std::lock_guard<std::mutex> lock(mutex_);
    desired_enabled_ = enabled;
    pending_ = true;
  }
  condition_.notify_all();
  return true;
}

void HWCSunlightEnhancement::WorkerLoop() {
  for (;;) {
    bool desired = false;
    {
      std::unique_lock<std::mutex> lock(mutex_);
      condition_.wait(lock, [this]() { return stop_ || pending_; });
      if (stop_) {
        return;
      }
      desired = desired_enabled_;
      pending_ = false;
    }

    if (!WaitForPpd()) {
      continue;
    }

    {
      std::lock_guard<std::mutex> lock(mutex_);
      if (desired != desired_enabled_) {
        pending_ = true;
        continue;
      }
    }

    Reconcile(desired);

    // If another request arrived while the socket work was in progress, process the latest one.
    std::lock_guard<std::mutex> lock(mutex_);
    if (desired != desired_enabled_) {
      pending_ = true;
      condition_.notify_all();
    }
  }
}

bool HWCSunlightEnhancement::WaitForPpd() {
  for (int i = 0; i < kMaxPpdWaitSeconds; i++) {
    if (IsPpdRunningProperty(kVendorPpdProperty) || IsPpdRunningProperty(kPpdProperty)) {
      return true;
    }

    std::unique_lock<std::mutex> lock(mutex_);
    if (condition_.wait_for(lock, std::chrono::seconds(1), [this]() { return stop_; })) {
      return false;
    }
  }

  DLOGW("ppd did not become ready for Assertive Display reconciliation");
  return false;
}

bool HWCSunlightEnhancement::Reconcile(bool desired_enabled) {
  if (!IsSupported()) {
    return false;
  }

  const int actual = QueryEnabled();
  if (actual < 0) {
    DLOGW("Unable to query Assertive Display state");
    return false;
  }

  if ((actual != 0) == desired_enabled) {
    DLOGI("Assertive Display already %s", desired_enabled ? "enabled" : "disabled");
    return true;
  }

  const char *command = desired_enabled ? kEnableCommand : kDisableCommand;
  if (!SendCommand(command)) {
    DLOGW("Failed to send Assertive Display command %s", command);
    return false;
  }

  DLOGI("Assertive Display reconciled to %s", desired_enabled ? "enabled" : "disabled");
  return true;
}

int HWCSunlightEnhancement::QueryEnabled() {
  for (int attempt = 0; attempt < kStatusQueryAttempts; attempt++) {
    int fd = socket_local_client(kSocketName, ANDROID_SOCKET_NAMESPACE_RESERVED, SOCK_STREAM);
    if (fd < 0) {
      continue;
    }

    if (!WriteAll(fd, kStatusCommand, strlen(kStatusCommand))) {
      close(fd);
      continue;
    }

    char response[64] = {};
    ssize_t size = TEMP_FAILURE_RETRY(read(fd, response, sizeof(response) - 1));
    close(fd);
    if (size <= 0) {
      continue;
    }
    response[size] = '\0';

    return strncmp(response, kEnabledPrefix, strlen(kEnabledPrefix)) == 0 ? 1 : 0;
  }

  return -1;
}

bool HWCSunlightEnhancement::SendCommand(const char *command) {
  for (int attempt = 0; attempt < kCommandAttempts; attempt++) {
    int fd = socket_local_client(kSocketName, ANDROID_SOCKET_NAMESPACE_RESERVED, SOCK_STREAM);
    if (fd < 0) {
      continue;
    }

    if (!WriteAll(fd, command, strlen(command))) {
      close(fd);
      continue;
    }

    char response[kCommandResponseSize + 1] = {};
    ssize_t size = TEMP_FAILURE_RETRY(read(fd, response, kCommandResponseSize));
    close(fd);
    if (size <= 0) {
      continue;
    }
    response[size] = '\0';

    if (!strcmp(response, kSuccessResponse)) {
      return true;
    }

    DLOGW("Unexpected pps response to %s: %s", command, response);
  }

  DLOGW("Assertive Display command failed after %d attempts: %s", kCommandAttempts, command);
  return false;
}

}  // namespace sdm
