/**
 * WatchoutRF Native Library
 *
 * Sprint 1: JNI version + NEON detection
 * Sprint 3: IQ sample processing + FFT pipeline via KissFFT
 */

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <math.h>
#include <android/log.h>
#include "kiss_fft.h"

#define LOG_TAG "WatchoutRF-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define WATCHOUTRF_VERSION "0.2.0"

/* Calibration offset in dB — accounts for RTL-SDR gain chain.
 * Adjust experimentally; -113 dBm is a reasonable starting point
 * for auto-gain mode on the R820T2. */
#define CALIBRATION_OFFSET_DB (-113.0f)

/* ======================================================================
 * Sprint 1 functions
 * ====================================================================== */

JNIEXPORT jstring JNICALL
Java_com_watchoutrf_app_NativeLib_getVersion(JNIEnv *env, jobject thiz)
{
    LOGI("WatchoutRF Native v%s", WATCHOUTRF_VERSION);
    return (*env)->NewStringUTF(env, WATCHOUTRF_VERSION);
}

JNIEXPORT jboolean JNICALL
Java_com_watchoutrf_app_NativeLib_isNeonSupported(JNIEnv *env, jobject thiz)
{
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
    LOGI("NEON SIMD: supported");
    return JNI_TRUE;
#else
    LOGI("NEON SIMD: not available");
    return JNI_FALSE;
#endif
}

#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>

JNIEXPORT jboolean JNICALL
Java_com_watchoutrf_app_NativeLib_resetUsbDevice(JNIEnv *env, jobject thiz, jint fd)
{
    if (fd < 0) return JNI_FALSE;
    int ret = ioctl(fd, USBDEVFS_RESET, 0);
    if (ret < 0) {
        LOGE("USBDEVFS_RESET failed with %d", ret);
        return JNI_FALSE;
    }
    LOGI("USBDEVFS_RESET successful");
    return JNI_TRUE;
}

/* ======================================================================
 * Sprint 3: Window functions
 * ====================================================================== */

typedef enum {
    WINDOW_RECTANGULAR = 0,
    WINDOW_HAMMING     = 1,
    WINDOW_HANN        = 2,
    WINDOW_BLACKMAN    = 3,
} WindowType;

static float apply_window(int i, int n, WindowType type)
{
    float x = (float)i / (float)(n - 1);
    switch (type) {
        case WINDOW_HAMMING:
            return 0.54f - 0.46f * cosf(2.0f * (float)M_PI * x);
        case WINDOW_HANN:
            return 0.5f * (1.0f - cosf(2.0f * (float)M_PI * x));
        case WINDOW_BLACKMAN:
            return 0.42f
                   - 0.50f * cosf(2.0f * (float)M_PI * x)
                   + 0.08f * cosf(4.0f * (float)M_PI * x);
        case WINDOW_RECTANGULAR:
        default:
            return 1.0f;
    }
}

/* Compute window normalisation factor (coherent gain) */
static float window_norm(int n, WindowType type)
{
    float sum = 0.0f;
    for (int i = 0; i < n; i++)
        sum += apply_window(i, n, type);
    return sum / (float)n;  /* mean value */
}

/* ======================================================================
 * Sprint 3: IQ → Spectrum
 *
 * JNI signature:
 *   FloatArray processIqToSpectrum(byte[] iqBytes, int numBins, int windowType)
 *
 * iqBytes  — raw RTL-SDR samples: unsigned 8-bit, interleaved I/Q
 *            (I0 Q0 I1 Q1 …).  Typically numBins*2 or more bytes.
 * numBins  — FFT size (must be power of two; typically 512, 1024, 2048)
 * windowType — see WindowType enum above
 *
 * Returns FloatArray of length numBins with power in dBm,
 *         DC-centered (index 0 = most negative frequency,
 *                       index numBins/2 = DC,
 *                       index numBins-1 = most positive frequency).
 * ====================================================================== */

JNIEXPORT jfloatArray JNICALL
Java_com_watchoutrf_app_NativeLib_processIqToSpectrum(
    JNIEnv  *env,
    jobject  thiz,
    jbyteArray iq_bytes,
    jint       num_bins,
    jint       window_type)
{
    if (num_bins <= 0 || (num_bins & (num_bins - 1)) != 0) {
        LOGE("numBins must be a positive power of two, got %d", num_bins);
        return NULL;
    }

    jsize iq_len = (*env)->GetArrayLength(env, iq_bytes);
    if (iq_len < num_bins * 2) {
        LOGE("IQ buffer too small: need %d, got %d", num_bins * 2, (int)iq_len);
        return NULL;
    }

    /* Pin the Java byte array */
    jbyte *iq = (*env)->GetByteArrayElements(env, iq_bytes, NULL);
    if (!iq) return NULL;

    /* Allocate KissFFT structures */
    kiss_fft_cfg  cfg = kiss_fft_alloc(num_bins, 0, NULL, NULL);
    kiss_fft_cpx *in  = (kiss_fft_cpx *)malloc(num_bins * sizeof(kiss_fft_cpx));
    kiss_fft_cpx *out = (kiss_fft_cpx *)malloc(num_bins * sizeof(kiss_fft_cpx));
    float        *mag = (float *)malloc(num_bins * sizeof(float));

    if (!cfg || !in || !out || !mag) {
        LOGE("OOM allocating FFT buffers (numBins=%d)", num_bins);
        free(cfg); free(in); free(out); free(mag);
        (*env)->ReleaseByteArrayElements(env, iq_bytes, iq, JNI_ABORT);
        return NULL;
    }

    WindowType wtype = (WindowType)window_type;
    float      norm  = window_norm(num_bins, wtype);
    if (norm < 1e-6f) norm = 1.0f;

    /* Convert unsigned-8 IQ → complex float with windowing
     * RTL-SDR: 0 = -1.0, 127 = 0.0, 255 = +1.0 */
    for (int i = 0; i < num_bins; i++) {
        float I = ((uint8_t)iq[i * 2    ] - 127.5f) / 128.0f;
        float Q = ((uint8_t)iq[i * 2 + 1] - 127.5f) / 128.0f;
        float w = apply_window(i, num_bins, wtype);
        in[i].r = I * w;
        in[i].i = Q * w;
    }

    /* Forward FFT */
    kiss_fft(cfg, in, out);

    /* Magnitude → dBm, with DC-centring (FFT shift)
     * bin 0..N/2-1  → positive freq half (stored at index N/2..N-1)
     * bin N/2..N-1  → negative freq half (stored at index 0..N/2-1) */
    for (int i = 0; i < num_bins; i++) {
        /* shift: output index maps to display bin */
        int src = (i + num_bins / 2) % num_bins;
        float re = out[src].r;
        float im = out[src].i;

        /* Power spectral density (normalised) */
        float power = (re * re + im * im) / ((float)num_bins * norm * norm);

        /* Convert to dBm */
        mag[i] = 10.0f * log10f(power + 1e-20f) + CALIBRATION_OFFSET_DB;
    }

    /* Return result */
    jfloatArray result = (*env)->NewFloatArray(env, num_bins);
    if (result)
        (*env)->SetFloatArrayRegion(env, result, 0, num_bins, mag);

    /* Cleanup */
    free(mag);
    free(out);
    free(in);
    kiss_fft_free(cfg);
    (*env)->ReleaseByteArrayElements(env, iq_bytes, iq, JNI_ABORT);

    return result;
}
