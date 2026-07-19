package com.watchoutrf.app.data.sdr

import android.hardware.usb.UsbDeviceConnection
import android.util.Log

/**
 * Controls the Realtek RTL2832U demodulator/USB-bridge chip.
 * Ported from librtlsdr.c (GPLv2).
 *
 * This class handles:
 * - Baseband init (FIR filter, demod configuration)
 * - Sample rate setting
 * - Crystal frequency configuration
 * - I2C repeater (gate to R820T2)
 */
class Rtl2832uChip(private val conn: UsbDeviceConnection) {

    companion object {
        private const val TAG = "Rtl2832u"
        private const val XTAL_FREQ = 28_800_000L

        /** FIR filter coefficients from librtlsdr (default low-pass). */
        private val FIR_COEFFS = intArrayOf(
            -54, -36, -41, -40, -32, -14, 14, 53,
            101, 156, 215, 273, 327, 372, 404, 421,
            421, 404, 372, 327,
        )
    }

    /**
     * Initialise the RTL2832U baseband.
     * Must be called after opening the USB device and before setting
     * sample rate or enabling the tuner.
     */
    fun initBaseband(): String {
        Log.i(TAG, "Initialising RTL2832U baseband")
        
        with(RtlSdrProtocol) {
            // === USB Init ===
            writeReg(conn, BLOCK_USB, 0x2000, 0x09)
            writeReg16(conn, BLOCK_USB, 0x2158, 0x0002)
            writeReg16(conn, BLOCK_USB, 0x2148, 0x1002)

            // === Power on Demodulator ===
            writeReg(conn, BLOCK_SYS, DEMOD_CTL_1, 0x22)
            writeReg(conn, BLOCK_SYS, DEMOD_CTL, 0xe8)

            // === Reset demod (bit 3, soft_rst) ===
            writeDemodReg(conn, 1, 0x01, 0x14, 1)
            writeDemodReg(conn, 1, 0x01, 0x10, 1)

            // === Disable spectrum inversion and adjacent channel rejection ===
            writeDemodReg(conn, 1, 0x15, 0x00, 1)
            writeDemodReg(conn, 1, 0x16, 0x0000, 2)

            // === Clear DDC shift and IF freq registers ===
            for (i in 0 until 6) {
                writeDemodReg(conn, 1, 0x16 + i, 0x00, 1)
            }

            // === Set FIR coefficients ===
            for (i in 0 until 8) {
                writeDemodReg(conn, 1, 0x1c + i, FIR_COEFFS[i] and 0xFF, 1)
            }
            
            // === Enable SDR mode, disable DAGC (bit 5) ===
            writeDemodReg(conn, 0, 0x19, 0x05, 1)

            // === Init FSM state-holding register ===
            writeDemodReg(conn, 1, 0x93, 0xf0, 1)
            writeDemodReg(conn, 1, 0x94, 0x0f, 1)

            // === Disable AGC loops ===
            writeDemodReg(conn, 1, 0x11, 0x00, 1)
            writeDemodReg(conn, 1, 0x04, 0x00, 1)

            // === Disable PID filter ===
            writeDemodReg(conn, 0, 0x61, 0x60, 1)

            // === Opt settings from librtlsdr ===
            writeDemodReg(conn, 0, 0x06, 0x80, 1)
            writeDemodReg(conn, 1, 0xb1, 0x1b, 1)

            // === disable 4.096 MHz clock output on pin TP_CK0 ===
            writeDemodReg(conn, 0, 0x0d, 0x83, 1)
        }
        Log.i(TAG, "RTL2832U baseband init OK")
        return "OK"
    }

    /**
     * Set the ADC sample rate.
     *
     * @param sampleRateHz  Desired sample rate in Hz.
     *                      Stable range: 225001 – 300000, 900001 – 3200000.
     *                      2 048 000 Hz (2.048 MS/s) is a good default.
     */
    fun setSampleRate(sampleRateHz: Int): Boolean {
        if (sampleRateHz < 225_001 || sampleRateHz > 3_200_000) {
            Log.e(TAG, "Invalid sample rate: $sampleRateHz Hz")
            return false
        }

        // Calculate tuning ratio
        val rsampRatio = (XTAL_FREQ * (1L shl 22) / sampleRateHz).toInt() and 0x0FFFFFFC
        val realRsampRatio = rsampRatio or ((rsampRatio and 0x04000000) shl 1)
        val realRate = (XTAL_FREQ * (1L shl 22) / realRsampRatio).toInt()

        Log.d(TAG, "setSampleRate: requested=${sampleRateHz} actual=${realRate}")

        with(RtlSdrProtocol) {
            writeDemodReg(conn, 1, 0x9f, rsampRatio shr 16, 2)
            writeDemodReg(conn, 1, 0xa1, rsampRatio and 0xFFFF, 2)
        }
        return true
    }

    /**
     * Set the Intermediate Frequency (IF) of the tuner so the digital down-converter (DDC)
     * can shift it to baseband (0 Hz).
     */
    fun setIfFreq(ifFreqHz: Long): Boolean {
        val ifFreq = -((ifFreqHz * (1L shl 22)) / XTAL_FREQ).toInt()
        with(RtlSdrProtocol) {
            writeDemodReg(conn, 1, 0x19, (ifFreq shr 16) and 0x3F, 1)
            writeDemodReg(conn, 1, 0x1a, (ifFreq shr 8) and 0xFF, 1)
            writeDemodReg(conn, 1, 0x1b, ifFreq and 0xFF, 1)
        }
        return true
    }

    /** Enable/disable I2C repeater (required to access the R820T2 tuner). */
    fun setI2cRepeater(on: Boolean) {
        RtlSdrProtocol.writeDemodReg(conn, 1, 0x01, if (on) 0x18 else 0x10, 1)
    }

    /**
     * Reset the USB FIFO buffer.
     * Call this just before starting bulk reads to clear any stale data.
     */
    fun resetBuffer(): Boolean {
        with(RtlSdrProtocol) {
            writeReg16(conn, BLOCK_USB, 0x2148, 0x1002)
            writeReg16(conn, BLOCK_USB, 0x2148, 0x0000)
        }
        return true
    }

    /**
     * Deinitialise the baseband (power down tuner section).
     */
    fun deinit() {
        RtlSdrProtocol.writeDemodReg(conn, 0, 0x19, 0x00, 1)
    }
}
