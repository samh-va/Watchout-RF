package com.watchoutrf.app

/**
 * JNI bridge to the WatchoutRF native library (watchoutrf_native.so).
 *
 * Sprint 1: Version info, NEON detection.
 * Sprint 3: IQ sample → spectrum (dBm) processing via KissFFT.
 */
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeLib @Inject constructor() {

    /** Returns the native library version string (e.g. "0.2.0"). */
    external fun getVersion(): String

    /** Returns true if the device supports ARM NEON SIMD instructions. */
    external fun isNeonSupported(): Boolean

    /** Resets the USB device using ioctl USBDEVFS_RESET */
    external fun resetUsbDevice(fd: Int): Boolean

    /**
     * Converts raw RTL-SDR IQ samples to a power spectrum in dBm.
     *
     * @param iqBytes  Raw unsigned-8 interleaved samples (I0 Q0 I1 Q1 …)
     *                 from the RTL-SDR bulk endpoint.  Must contain at least
     *                 [numBins] * 2 bytes.
     * @param numBins  FFT size — must be a positive power of two (e.g. 1024).
     * @param windowType  Window function:
     *                    0 = Rectangular, 1 = Hamming (default),
     *                    2 = Hann,        3 = Blackman
     * @return FloatArray of length [numBins] with dBm values, DC-centred
     *         (index 0 = lowest frequency, index numBins-1 = highest),
     *         or null on error.
     */
    external fun processIqToSpectrum(
        iqBytes: ByteArray,
        numBins: Int,
        windowType: Int = 1,
    ): FloatArray?

    companion object {
        init {
            System.loadLibrary("watchoutrf_native")
        }
    }
}
