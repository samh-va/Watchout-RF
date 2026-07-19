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
    private val READS_PER_SPECTRUM = 4

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

    /** Set the hardware center frequency using a FrequencyRange */
    fun setFrequency(range: FrequencyRange): Boolean {
        return device?.setFrequency(range.centerHz) ?: false
    }

    /** Set the hardware center frequency directly in Hz */
    fun setFrequency(freqHz: Long): Boolean {
        return device?.setFrequency(freqHz) ?: false
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

    /** Flush the hardware buffer */
    fun flush() {
        device?.flush()
    }

    /**
     * Read one spectrum frame from the hardware.
     *
     * Reads [READS_PER_SPECTRUM] IQ blocks, processes each with KissFFT
     * (native), averages in **linear power** domain, then converts back
     * to dBm. This preserves narrow signal peaks that dB-domain averaging
     * would flatten.
     *
     * @return FloatArray of [numBins] dBm values, or null on I/O error.
     */
    fun readSpectrum(bins: Int = numBins): FloatArray? {
        numBins = bins
        val dev = device ?: return null

        val bytesPerBlock = bins * 2
        // Accumulate in linear power (milliwatts) to preserve peaks
        val linearAccum = FloatArray(bins) { 0f }
        var validReads = 0

        repeat(READS_PER_SPECTRUM) { i ->
            val iq = dev.readSamples(bytesPerBlock) ?: run {
                Log.w(tag, "readSamples returned null on block $i")
                return@repeat
            }
            val spectrum = nativeLib.processIqToSpectrum(iq, bins, windowType)
                ?: return@repeat
            // Convert dBm → linear power and accumulate
            for (j in 0 until bins) {
                // 10^(dBm/10) converts dBm to milliwatts
                linearAccum[j] += Math.pow(10.0, spectrum[j].toDouble() / 10.0).toFloat()
            }
            validReads++
        }

        if (validReads == 0) return null

        // Average in linear domain, then convert back to dBm
        val result = FloatArray(bins)
        for (j in 0 until bins) {
            val avgLinear = linearAccum[j] / validReads.toFloat()
            result[j] = (10.0 * Math.log10(avgLinear.toDouble().coerceAtLeast(1e-20))).toFloat()
        }

        return result
    }

    /** Close the dongle and release USB resources. */
    fun close() {
        device?.close()
        device = null
    }

    val isOpen: Boolean get() = device?.isOpen == true
    val currentFrequencyHz: Long get() = device?.currentFrequency ?: 0L
}
