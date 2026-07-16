package com.watchoutrf.app.data.sdr

import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import kotlin.math.abs

/**
 * Controls the R820T2 (Rafael Micro) tuner chip via I2C through the RTL2832U.
 * Ported from librtlsdr/tuner_r82xx.c (GPLv2).
 *
 * Crystal frequency: 28.8 MHz
 * I2C address: 0x34
 * IF frequency: 3.57 MHz
 */
class R820T2Tuner(private val conn: UsbDeviceConnection) {

    companion object {
        private const val TAG = "R820T2Tuner"
        private const val XTAL_FREQ = 28_800_000L   // 28.8 MHz
        private const val IF_FREQ   =  3_570_000L   //  3.57 MHz
        private const val VCO_POWER_REF = 2

        /** Initial register values for R820T2 (registers 0x05 .. 0x1f) */
        private val INIT_REGS = intArrayOf(
            // reg: 0x05  0x06  0x07  0x08  0x09  0x0a  0x0b  0x0c
                   0x90, 0x80, 0x60, 0x80, 0x40, 0xda, 0x4a, 0x0f,
            // reg: 0x0d  0x0e  0x0f  0x10  0x11  0x12  0x13  0x14
                   0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x50, 0x00,
            // reg: 0x15  0x16  0x17  0x18  0x19  0x1a  0x1b  0x1c
                   0x00, 0xc0, 0x30, 0x48, 0xec, 0x60, 0x00, 0x24,
            // reg: 0x1d  0x1e  0x1f
                   0xdd, 0x0e, 0x00,
        )
    }

    // Shadow copy of registers 0x05..0x1f for read-modify-write
    private val regs = INIT_REGS.copyOf()
    
    // Discovered I2C address
    private var i2cAddr: Int = 0x34

    private fun regIndex(reg: Int) = reg - 0x05

    private fun writeReg(reg: Int, value: Int): Boolean {
        regs[regIndex(reg)] = value and 0xFF
        val data = byteArrayOf(reg.toByte(), value.toByte())
        val r = RtlSdrProtocol.writeArray(conn, RtlSdrProtocol.BLOCK_IIC, i2cAddr, data)
        return r
    }

    private fun writeRegMask(reg: Int, value: Int, bitMask: Int): Boolean {
        val idx = regIndex(reg)
        val newVal = (regs[idx] and bitMask.inv()) or (value and bitMask)
        return writeReg(reg, newVal)
    }

    /**
     * Initialise the R820T2 tuner. Must be called once after RTL2832U init
     * with the I2C repeater enabled.
     */
    fun init(): String {
        Log.i(TAG, "Initialising R820T2 tuner")
        
        // Check that the tuner is present by reading register 0x00 (should be 0x69)
        val candidateAddrs = intArrayOf(0x34, 0x74) // R820T and R828D
        var foundAddr = -1
        var tunerId = -1

        for (addr in candidateAddrs) {
            val regZero = byteArrayOf(0x00)
            RtlSdrProtocol.writeArray(conn, RtlSdrProtocol.BLOCK_IIC, addr, regZero)
            
            val check = RtlSdrProtocol.readArray(conn, RtlSdrProtocol.BLOCK_IIC, addr, 1)
            if (check != null && (check[0].toInt() and 0xFF) == 0x69) {
                foundAddr = addr
                tunerId = check[0].toInt() and 0xFF
                break
            }
        }

        if (foundAddr == -1) {
            Log.e(TAG, "R820T2/R828D not detected on I2C bus at any known address")
            return "I2C_PROBE_READ_FAIL"
        }

        i2cAddr = foundAddr
        Log.i(TAG, "Rafael Micro Tuner found at I2C address: 0x${foundAddr.toString(16)} with ID: 0x${tunerId.toString(16)}")

        // Write all initial register values
        for (i in INIT_REGS.indices) {
            val reg = i + 0x05
            writeReg(reg, INIT_REGS[i])
        }

        Log.i(TAG, "R820T2 init OK")
        return "OK"
    }

    /**
     * Set tuner centre frequency.
     *
     * @param freqHz Target RF frequency in Hz (e.g. 100_000_000 for 100 MHz)
     */
    fun setFrequency(freqHz: Long): Boolean {
        // LO frequency = RF + IF
        val loFreqHz = freqHz + IF_FREQ

        // Choose PLL divider so that VCO is in 1.77 – 3.9 GHz
        var divNum = 2
        val vcoMin = 1_770_000_000L
        while (divNum < 64 && loFreqHz * divNum < vcoMin) divNum *= 2

        val vcoFreqHz = loFreqHz * divNum

        // Integer part of PLL
        val nint  = (vcoFreqHz / (2L * XTAL_FREQ)).toInt()
        val vcoFracInput = vcoFreqHz - nint * 2L * XTAL_FREQ

        // Fractional part (16-bit)
        val nfrac = (vcoFracInput * 65536L / (2L * XTAL_FREQ)).toInt()

        Log.d(TAG, "setFrequency ${freqHz/1e6} MHz  LO=${loFreqHz/1e6} MHz  div=$divNum  Nint=$nint  Nfrac=$nfrac")

        if (nint > 63) {
            Log.e(TAG, "Frequency out of range: nint=$nint > 63")
            return false
        }

        // PLL registers
        val divider_msb = when {
            divNum >= 64 -> 0b110
            divNum >= 32 -> 0b101
            divNum >= 16 -> 0b100
            divNum >=  8 -> 0b011
            divNum >=  4 -> 0b010
            divNum >=  2 -> 0b001
            else         -> 0b000
        }

        // reg 0x10: frac[15:8]
        writeRegMask(0x10, nfrac shr 8, 0x3f)
        // reg 0x11: frac[7:0]
        writeReg(0x11, nfrac and 0xFF)
        // reg 0x12: int + flag
        writeRegMask(0x12, nint, 0x3f)
        // reg 0x13: SDM (set to 0 for integer mode when frac is 0)
        writeReg(0x13, if (nfrac == 0) 0x08 else 0x00)
        // reg 0x0f: divider selection
        writeRegMask(0x0f, (divider_msb shl 5), 0xe0)

        // Set mixer top / tracking filter for frequency range
        // (simplified version of r82xx_set_mux from librtlsdr)
        val trackFilter: Int
        val lna_top: Int
        when {
            loFreqHz < 50_000_000 -> { trackFilter = 0x60; lna_top = 0xE5 }
            loFreqHz < 100_000_000 -> { trackFilter = 0x60; lna_top = 0xE5 }
            loFreqHz < 200_000_000 -> { trackFilter = 0x60; lna_top = 0xE5 }
            loFreqHz < 500_000_000 -> { trackFilter = 0x40; lna_top = 0xE5 }
            loFreqHz < 1_000_000_000 -> { trackFilter = 0x20; lna_top = 0xE5 }
            else -> { trackFilter = 0x00; lna_top = 0xE5 }
        }
        writeRegMask(0x07, trackFilter, 0x60)   // IMR/tracking filter
        writeRegMask(0x1d, lna_top and 0xC7, 0xC7)

        // Wait for PLL lock — poll reg 0x0 bit 6
        // (simplified: just write a small delay equivalent via no-op reads)
        Thread.sleep(5)

        return true
    }

    /**
     * Set tuner gain.
     *
     * @param gainTenthsDb  Gain in tenths of dB, or -1 for auto-gain (AGC).
     *                      Valid manual range: 0 .. 496 (0.0 .. 49.6 dB).
     */
    fun setGain(gainTenthsDb: Int): Boolean {
        return if (gainTenthsDb < 0) {
            // Auto gain: enable both LNA AGC and mixer AGC
            writeRegMask(0x05, 0x00, 0x10)  // LNA AGC on
            writeRegMask(0x07, 0x10, 0x10)  // Mixer AGC on
            true
        } else {
            // Manual gain
            writeRegMask(0x05, 0x10, 0x10)  // LNA AGC off
            writeRegMask(0x07, 0x00, 0x10)  // Mixer AGC off

            // LNA gain: 0-15 steps (~3.5 dB each)
            val lna_gain = (gainTenthsDb / 35).coerceIn(0, 15)
            writeRegMask(0x05, lna_gain, 0x0f)

            // Mixer gain: 0-15 steps (~1 dB each)
            val mix_gain = (gainTenthsDb / 10 - lna_gain * 35 / 10).coerceIn(0, 15)
            writeRegMask(0x07, mix_gain, 0x0f)
            true
        }
    }

    /** Set IF bandwidth / filter BW. 2.4 MHz default for ~2 MS/s. */
    fun setIfBandwidth(bwHz: Int = 2_400_000): Boolean {
        // Simplified: use pre-calculated values for common BWs
        val filtCode = when {
            bwHz <= 200_000   -> 0b11110
            bwHz <= 400_000   -> 0b11010
            bwHz <= 1_000_000 -> 0b10110
            bwHz <= 2_400_000 -> 0b01010
            bwHz <= 3_400_000 -> 0b00110
            else              -> 0b00000
        }
        writeRegMask(0x0b, filtCode, 0x1f)
        return true
    }
}
