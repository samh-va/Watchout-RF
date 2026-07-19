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
            0x83, 0x32, 0x75,             // 05 to 07
            0xc0, 0x40, 0xd6, 0x6c,       // 08 to 0b
            0xf5, 0x63, 0x75, 0x68,       // 0c to 0f
            0x6c, 0x83, 0x80, 0x00,       // 10 to 13
            0x0f, 0x00, 0xc0, 0x30,       // 14 to 17
            0x48, 0xcc, 0x60, 0x00,       // 18 to 1b
            0x54, 0xae, 0x4a, 0xc0        // 1c to 1f
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

    private fun readRegs(reg: Int, length: Int): ByteArray? {
        val regByte = byteArrayOf(reg.toByte())
        RtlSdrProtocol.writeArray(conn, RtlSdrProtocol.BLOCK_IIC, i2cAddr, regByte)
        return RtlSdrProtocol.readArray(conn, RtlSdrProtocol.BLOCK_IIC, i2cAddr, length)
    }

    private fun writeRegs(startReg: Int, values: ByteArray): Boolean {
        for (i in values.indices) {
            regs[regIndex(startReg + i)] = values[i].toInt() and 0xFF
        }
        val data = ByteArray(values.size + 1)
        data[0] = startReg.toByte()
        System.arraycopy(values, 0, data, 1, values.size)
        return RtlSdrProtocol.writeArray(conn, RtlSdrProtocol.BLOCK_IIC, i2cAddr, data)
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

        // Essential librtlsdr startup sequences
        writeRegMask(0x1a, 0x00, 0x0c)
        writeRegMask(0x12, 0x80, 0x80)
        writeRegMask(0x19, 0x20, 0x20) // R820T2 only
        writeRegMask(0x1d, 0x00, 0x38)

        // Force AGC on
        setGain(-1)

        Log.i(TAG, "R820T2 init OK")
        return "OK"
    }

    private fun setMux(freqHz: Long): Boolean {
        val freqMhz = (freqHz / 1_000_000).toInt()
        var rangeIndex = 0
        for (i in 0 until FREQ_RANGES.size - 1) {
            if (freqMhz < FREQ_RANGES[i + 1].freq) {
                rangeIndex = i
                break
            }
            rangeIndex = i + 1
        }
        val range = FREQ_RANGES[rangeIndex]

        // Open Drain
        writeRegMask(0x17, range.openD, 0x08)
        // RF_MUX, Polymux
        writeRegMask(0x1a, range.rfMuxPloy, 0xc3)
        // TF BAND
        writeReg(0x1b, range.tfC)
        // XTAL CAP & Drive (assume XTAL_HIGH_CAP_0P for RTL-SDR TCXO)
        writeRegMask(0x10, range.xtalCap0p or 0x00, 0x0b)
        writeRegMask(0x08, 0x00, 0x3f)
        writeRegMask(0x09, 0x00, 0x3f)
        return true
    }

    /**
     * Tune the R820T2 to a specific frequency.
     */
    fun setFrequency(freqHz: Long): Boolean {
        setMux(freqHz)

        val loFreqHz = freqHz + IF_FREQ

        // set pll autotune = 128kHz
        writeRegMask(0x1a, 0x00, 0x0c)

        // We will build a burst payload for 0x10 to 0x16 (7 bytes)
        val pllRegs = ByteArray(7)
        for (i in 0..6) {
            pllRegs[i] = regs[regIndex(0x10 + i)].toByte()
        }

        // refdiv2 = 0
        pllRegs[0] = ((pllRegs[0].toInt() and 0xef) or 0x00).toByte()
        // set VCO current = 100
        pllRegs[2] = ((pllRegs[2].toInt() and 0x1f) or 0x80).toByte()

        var divNum = 0
        var mixDiv = 2
        val loFreqKhz = (loFreqHz + 500) / 1000

        val vcoMin = 1_770_000L // kHz
        val vcoMax = vcoMin * 2

        while (mixDiv <= 64) {
            if ((loFreqKhz * mixDiv >= vcoMin) && (loFreqKhz * mixDiv < vcoMax)) {
                var divBuf = mixDiv
                while (divBuf > 2) {
                    divBuf = divBuf shr 1
                    divNum++
                }
                break
            }
            mixDiv = mixDiv shl 1
        }

        val check = readRegs(0x00, 5)
        val vcoFineTune = if (check != null && check.size >= 5) {
            (check[4].toInt() and 0x30) shr 4
        } else 0

        if (vcoFineTune > VCO_POWER_REF) {
            divNum--
        } else if (vcoFineTune < VCO_POWER_REF) {
            divNum++
        }

        pllRegs[0] = ((pllRegs[0].toInt() and 0x1f) or (divNum shl 5)).toByte()

        /* ── PLL divider calculation (matches librtlsdr tuner_r82xx.c) ──
         *
         *  vcoFreq = loFreqHz × mixDiv
         *  nint    = floor(vcoFreq / (2 × pllRef))
         *  vcoFra  = vcoFreq − nint × 2 × pllRef         (remainder in Hz)
         *  sdm     = floor(vcoFra × 65536 / (2 × pllRef)) (16-bit fractional part)
         */
        val vcoFreq = loFreqHz * mixDiv.toLong()
        val pllRef  = XTAL_FREQ
        val twoPllRef = 2L * pllRef

        val nint = (vcoFreq / twoPllRef).toInt()
        val vcoFra = vcoFreq - nint.toLong() * twoPllRef
        val sdm = ((vcoFra * 65536L) / twoPllRef).toInt()

        val ni = (nint - 13) / 4
        val si = nint - 4 * ni - 13
        pllRegs[4] = (ni + (si shl 6)).toByte()

        val pwSdm = if (sdm == 0) 0x08 else 0x00
        pllRegs[2] = ((pllRegs[2].toInt() and 0xf7) or pwSdm).toByte()

        pllRegs[5] = (sdm and 0xFF).toByte()
        pllRegs[6] = ((sdm shr 8) and 0xFF).toByte()

        // Burst write all 7 PLL registers
        writeRegs(0x10, pllRegs)

        var locked = false
        for (i in 0..2) {
            Thread.sleep(5)
            val lockCheck = readRegs(0x00, 3)
            if (lockCheck != null && lockCheck.size >= 3) {
                if ((lockCheck[2].toInt() and 0x40) != 0) {
                    locked = true
                    break
                }
            }
            if (i == 0) {
                // Didn't lock. Increase VCO current
                writeRegMask(0x12, 0x60, 0xe0)
            }
        }

        if (!locked) {
            Log.e(TAG, "PLL NOT LOCKED for ${freqHz / 1_000_000.0} MHz (nint=$nint, sdm=$sdm, mixDiv=$mixDiv)")
        } else {
            Log.d(TAG, "PLL locked for ${freqHz / 1_000_000.0} MHz (nint=$nint, sdm=$sdm, mixDiv=$mixDiv)")
        }

        // set pll autotune = 8kHz
        writeRegMask(0x1a, 0x08, 0x08)

        // set mixer top
        writeRegMask(0x1c, 0x00, 0x04)
        // set LNA top
        writeRegMask(0x1d, 0x00, 0x38)

        return locked
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
