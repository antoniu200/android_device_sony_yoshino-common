#ifndef __HWC_SUNLIGHT_ENHANCEMENT_H__
#define __HWC_SUNLIGHT_ENHANCEMENT_H__

#include <vendor/lineage/livedisplay/2.0/ISunlightEnhancement.h>

#include <condition_variable>
#include <mutex>
#include <thread>

namespace sdm {

using ::android::hardware::Return;
using ::vendor::lineage::livedisplay::V2_0::ISunlightEnhancement;

class HWCSunlightEnhancement : public ISunlightEnhancement {
 public:
  HWCSunlightEnhancement();
  ~HWCSunlightEnhancement() override;

  static bool IsSupported();

  Return<bool> isEnabled() override;
  Return<bool> setEnabled(bool enabled) override;

 private:
  static constexpr int kMaxPpdWaitSeconds = 1000;
  static constexpr int kStatusQueryAttempts = 10;

  void WorkerLoop();
  bool WaitForPpd();
  bool Reconcile(bool desired_enabled);
  int QueryEnabled();
  bool SendCommand(const char *command);

  std::mutex mutex_;
  std::condition_variable condition_;
  std::thread worker_;
  bool desired_enabled_ = false;
  bool pending_ = false;
  bool stop_ = false;
};

}  // namespace sdm

#endif  // __HWC_SUNLIGHT_ENHANCEMENT_H__
