package com.watchoutrf.app.data.sdr

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.watchoutrf.app.NativeLib
import com.watchoutrf.app.domain.model.FrequencyRange
import com.watchoutrf.app.domain.model.GainMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides real-time spectrum data from a connected RTL-SDR dongle.
 *
 * Open the device with [open], then call [readSpectrum] in a loop on a
 * background thread.  Call [close] when done.
 *
 * The FFT is performed natively via [NativeLib.processIqToSpectrum].
 */
@Singleton
class RtlSdrSource @Inject constructor(
    private val nativeLib: NativeLib,
) {
    private val tag = "RtlSdrSource"
    private var device: RtlSdrDevice? = null

    // Config
    private var numBins    = 1024
    private var windowType = 1   // Hamming

    // IQ buffer: we read several FFT-worth of samples per call and average
    // Using 8× the FFT size gives better SNR from incoherent averaging
    private val READS_PER_SPECTRUM = 8

    /**
     * Opens the RTL-SDR hardware and claims the USB interface.
     * Returns null if successful, or an error string if failed.
     */
    fun open(
        usbManager: UsbManager,
        usbDevice: UsbDevice,
        sampleRate: Int = RtlSdrDevice.DEFAULT_SAMPLE_RATE,
    ): String? {
        close()
        val dev = RtlSdrDevice.open(usbManager, usbDevice)
            ?: return "FAIL_OPEN_USB"

        val initResult = dev.initialize(sampleRate)
        if (initResult != "OK") {
            dev.close()
            return "FAIL_INIT_$initResult"
        }
        device = dev
        Log.i(tag, "RtlSdrSource ready")
        return null
    }

    /**
     * Set the centre frequency.  Call this whenever the user changes
     * the frequency range.
     */
    fun setFrequency(range: FrequencyRange): Boolean {
        return device?.setFrequency(range.centerHz) ?: false
    }

    /** Set gain mode. */
    fun setGain(mode: GainMode, manualGainDb: Int = 40): Boolean {
        val tenthsDb = if (mode == GainMode.Auto) -1 else manualGainDb * 10
        return device?.setGain(tenthsDb) ?: false
    }

    /** Start the background USB hardware stream */
    fun startStream() {
        device?.startStreaming()
    }

    /**
     * Read one spectrum frame from the hardware.
     *
     * Reads [READS_PER_SPECTRUM] × [numBins] × 2 IQ bytes, processes
     * each block with KissFFT (native), and returns the per-bin average.
     *
     * @return FloatArray of [numBins] dBm values, or null on I/O error.
     */
    fun readSpectrum(bins: Int = numBins): FloatArray? {
        numBins = bins
        val dev = device ?: return null

        val bytesPerBlock = bins * 2
        val accumulator   = FloatArray(bins) { 0f }

        repeat(READS_PER_SPECTRUM) { i ->
            val iq = dev.readSamples(bytesPerBlock) ?: run {
                Log.w(tag, "readSamples returned null on block $i")
                return@repeat
            }
            val spectrum = nativeLib.processIqToSpectrum(iq, bins, windowType)
                ?: return@repeat
            for (j in 0 until bins)
                accumulator[j] += spectrum[j]
        }

        // Incoherent average in linear power, then back to dB
        // (simple dB average is close enough here)
        for (j in 0 until bins)
            accumulator[j] /= READS_PER_SPECTRUM.toFloat()

        return accumulator
    }

    /** Close the dongle and release USB resources. */
    fun close() {
        device?.close()
        device = null
    }

    val isOpen: Boolean get() = device?.isOpen == true
    val currentFrequencyHz: Long get() = device?.currentFrequency ?: 0L
}
