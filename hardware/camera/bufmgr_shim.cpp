// libcamfix_bufmgr: LD_PRELOAD shim for the Sony Excalibur camera HAL.
//
// Problem: ReqCtrl restarts the preview and, in doing so, unregisters buffer
// ID 0 (BufMgr::freeBuffers) and registers it again about 2 ms later
// (BufMgr::registerBuffers). If StreamCtrl's CtrlBase::bindPorts calls
// BufMgr::getBuffers(0, ...) inside that gap, getBuffers returns -0x67 and
// leaves the array empty. bindPorts ignores the return value, the port is
// opened with an empty buffer list, and ImageConv::doStart later fails with
// "chokoball start fail".
//
// Fix: remember which IDs were freed a moment ago. If getBuffers fails with
// -0x67 for such an ID, wait (bounded) for it to be registered again and ask
// again. Every other call passes straight through unchanged.
//
// Exports the same mangled names as libexcal_system.so, so the dynamic linker
// binds the vtable slots (R_ARM_ABS32) and direct calls to these wrappers when
// this library is preloaded. Needs only libc, libdl and liblog.

#include <android/log.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdint.h>
#include <time.h>

#define TAG "camfix"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#define SYM_GET  "_ZN9excalibur6BufMgr10getBuffersENS0_5BufIdEPN9excal_pal5ArrayIPNS_8ImageBufEEE"
#define SYM_REG  "_ZN9excalibur6BufMgr15registerBuffersENS0_5BufIdEPN9excal_pal5ArrayIPNS_8ImageBufEEE"
#define SYM_FREE "_ZN9excalibur6BufMgr11freeBuffersENS0_5BufIdE"

namespace {

constexpr int kNumIds = 0x44;               // BufMgr rejects ids above 0x43
constexpr int kErrNoEntry = -0x67;          // 0xffffff99: id not registered / bad arg
constexpr int kErrAlreadyRegistered = -0x6d;
constexpr int64_t kRecentFreeUs = 100000;   // only react to a free this recent
constexpr int64_t kMaxWaitUs = 50000;       // never wait longer than this
constexpr int64_t kSliceUs = 1000;          // re-ask BufMgr at least this often
constexpr bool kTraceAll = true;            // log every tracked free/register

struct IdState {
    bool registered;   // the shim saw a successful register and no free since
    bool pending;      // freed recently, not registered again yet
    int64_t freedAtUs;
};

IdState gId[kNumIds];
pthread_mutex_t gLock = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t gCond;

typedef int (*GetFn)(void*, int, void*);
typedef int (*RegFn)(void*, int, void*);
typedef int (*FreeFn)(void*, int);

GetFn gRealGet;
RegFn gRealReg;
FreeFn gRealFree;

int64_t nowUs() {
    timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return int64_t(ts.tv_sec) * 1000000 + ts.tv_nsec / 1000;
}

void* findReal(const char* sym) {
    void* p = dlsym(RTLD_NEXT, sym);
    if (!p) {
        void* h = dlopen("libexcal_system.so", RTLD_NOW | RTLD_NOLOAD);
        if (h) p = dlsym(h, sym);
    }
    if (!p) LOGI("cannot resolve original %s", sym);
    return p;
}

template <typename F>
F resolve(F& cache, const char* sym) {
    F f = __atomic_load_n(&cache, __ATOMIC_ACQUIRE);
    if (!f) {
        f = reinterpret_cast<F>(findReal(sym));
        if (f) __atomic_store_n(&cache, f, __ATOMIC_RELEASE);
    }
    return f;
}

// Sleeps on gCond for at most kSliceUs. gLock must be held.
void waitSlice() {
    timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    int64_t ns = int64_t(ts.tv_nsec) + kSliceUs * 1000;
    ts.tv_sec += ns / 1000000000;
    ts.tv_nsec = ns % 1000000000;
    pthread_cond_timedwait(&gCond, &gLock, &ts);
}

__attribute__((constructor)) void init() {
    pthread_condattr_t attr;
    pthread_condattr_init(&attr);
    pthread_condattr_setclock(&attr, CLOCK_MONOTONIC);
    pthread_cond_init(&gCond, &attr);
    pthread_condattr_destroy(&attr);
    LOGI("libcamfix_bufmgr loaded");
}

}  // namespace

// ---------------------------------------------------------------------------
// BufMgr::getBuffers(BufMgr::BufId, Array<ImageBuf*>*)

extern "C" __attribute__((visibility("default"), used))
int camfix_getBuffers(void* self, int id, void* arr) __asm__(SYM_GET);

extern "C" int camfix_getBuffers(void* self, int id, void* arr) {
    GetFn real = resolve(gRealGet, SYM_GET);
    if (!real) return kErrNoEntry;

    int r = real(self, id, arr);
    if (r != kErrNoEntry || id < 0 || id >= kNumIds || arr == nullptr) return r;

    int64_t start = nowUs();
    pthread_mutex_lock(&gLock);
    if (!gId[id].pending || start - gId[id].freedAtUs > kRecentFreeUs) {
        pthread_mutex_unlock(&gLock);
        return r;
    }

    // The id was freed a moment ago and has not been registered again.
    // Wait for it, but ask BufMgr again every slice in case a notification
    // was missed (e.g. a register that did not go through this shim).
    int64_t deadline = start + kMaxWaitUs;
    while (true) {
        pthread_mutex_unlock(&gLock);
        r = real(self, id, arr);
        pthread_mutex_lock(&gLock);
        if (r != kErrNoEntry || nowUs() >= deadline) break;
        waitSlice();
    }
    pthread_mutex_unlock(&gLock);

    LOGI("getBuffers(id=%d) hit the free/register gap, waited %lld us, ret=%d", id,
         (long long)(nowUs() - start), r);
    return r;
}

// ---------------------------------------------------------------------------
// BufMgr::registerBuffers(BufMgr::BufId, Array<ImageBuf*>*)

extern "C" __attribute__((visibility("default"), used))
int camfix_registerBuffers(void* self, int id, void* arr) __asm__(SYM_REG);

extern "C" int camfix_registerBuffers(void* self, int id, void* arr) {
    RegFn real = resolve(gRealReg, SYM_REG);
    if (!real) return kErrNoEntry;

    int r = real(self, id, arr);
    if (id >= 0 && id < kNumIds && (r >= 0 || r == kErrAlreadyRegistered)) {
        pthread_mutex_lock(&gLock);
        gId[id].registered = true;
        gId[id].pending = false;
        pthread_cond_broadcast(&gCond);
        pthread_mutex_unlock(&gLock);
        if (kTraceAll) LOGI("registerBuffers(id=%d) -> %d", id, r);
    }
    return r;
}

// ---------------------------------------------------------------------------
// BufMgr::freeBuffers(BufMgr::BufId)

extern "C" __attribute__((visibility("default"), used))
int camfix_freeBuffers(void* self, int id) __asm__(SYM_FREE);

extern "C" int camfix_freeBuffers(void* self, int id) {
    FreeFn real = resolve(gRealFree, SYM_FREE);
    if (!real) return kErrNoEntry;

    // Mark before the real call, so a getBuffers that fails right after the
    // entry disappears can never see "not pending".
    if (id >= 0 && id < kNumIds) {
        pthread_mutex_lock(&gLock);
        if (gId[id].registered) {
            gId[id].registered = false;
            gId[id].pending = true;
            gId[id].freedAtUs = nowUs();
            if (kTraceAll) LOGI("freeBuffers(id=%d): tracking re-register", id);
        }
        pthread_mutex_unlock(&gLock);
    }
    return real(self, id);
}
