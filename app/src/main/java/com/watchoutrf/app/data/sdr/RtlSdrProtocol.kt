package com.watchoutrf.app.data.sdr

import android.hardware.usb.UsbDeviceConnection
import android.util.Log

/**
 * Low-level USB protocol helpers for the RTL2832U chip.
 * Ported from librtlsdr (osmocom), GPLv2.
 *
 * RTL2832U uses vendor USB control transfers to access internal registers.
 * Registers are organised into named blocks accessed via wIndex.
 */
object RtlSdrProtocol {

    private const val TAG = "RtlSdrProtocol"
    private const val CTRL_TIMEOUT = 300  // ms

    // Block indices (used as high byte of wIndex)
    const val BLOCK_DEMOD = 0
    const val BLOCK_USB   = 1
    const val BLOCK_SYS   = 2
    const val BLOCK_TUN   = 3   // Tuner (I2C master)
    const val BLOCK_ROM   = 4
    const val BLOCK_IR    = 5
    const val BLOCK_IIC   = 6   // I2C

    // Common SYS block register addresses
    const val DEMOD_CTL   = 0x3000
    const val GPO         = 0x3001
    const val GPI         = 0x3002
    const val GPOE        = 0x3003
    const val GPD         = 0x3004
    const val SYSINTE     = 0x3005
    const val SYSINTS     = 0x3006
    const val GP_CFG0     = 0x300e
    const val GP_CFG1     = 0x300f
    const val SYSINTE_1   = 0x3019
    const val SYSINTS_1   = 0x301a
    const val DEMOD_CTL_1 = 0x300b

    // R820T2 I2C address
    const val R820T2_I2C_ADDR = 0x34

    /**
     * Write a single register in a named block.
     *
     * Maps to librtlsdr: rtlsdr_write_reg(dev, block, addr, val, 1)
     */
    fun writeReg(
        conn: UsbDeviceConnection,
        block: Int,
        addr: Int,
        value: Int,
    ): Boolean {
        val data = byteArrayOf((value and 0xFF).toByte())
        val index = (block shl 8) or 0x10
        val r = conn.controlTransfer(
            0x40, 0, addr, index, data, 1, CTRL_TIMEOUT
        )
        if (r < 0) Log.e(TAG, "writeReg block=$block addr=0x${addr.toString(16)} val=0x${value.toString(16)} FAILED ($r)")
        return r == 1
    }

    /**
     * Write a 16-bit value (big-endian) to a register.
     */
    fun writeReg16(
        conn: UsbDeviceConnection,
        block: Int,
        addr: Int,
        value: Int,
    ): Boolean {
        val data = byteArrayOf(
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte(),
        )
        val index = (block shl 8) or 0x10
        val r = conn.controlTransfer(
            0x40, 0, addr, index, data, 2, CTRL_TIMEOUT
        )
        return r == 2
    }

    /**
     * Read a single byte from a register.
     *
     * Maps to librtlsdr: rtlsdr_read_reg(dev, block, addr, 1)
     */
    fun readReg(
        conn: UsbDeviceConnection,
        block: Int,
        addr: Int,
    ): Int {
        val data = ByteArray(1)
        val index = block shl 8
        val r = conn.controlTransfer(
            0xC0, 0, addr, index, data, 1, CTRL_TIMEOUT
        )
        if (r < 0) {
            Log.e(TAG, "readReg block=$block addr=0x${addr.toString(16)} FAILED ($r)")
            return -1
        }
        return data[0].toInt() and 0xFF
    }

    /**
     * Write a byte array to a register (used for I2C burst writes).
     *
     * Maps to librtlsdr: rtlsdr_write_array(dev, block, addr, array, len)
     */
    fun writeArray(
        conn: UsbDeviceConnection,
        block: Int,
        addr: Int,
        data: ByteArray,
    ): Boolean {
        val index = (block shl 8) or 0x10
        val r = conn.controlTransfer(
            0x40, 0, addr, index, data, data.size, CTRL_TIMEOUT
        )
        return r == data.size
    }

    /**
     * Read a byte array from a register.
     */
    fun readArray(
        conn: UsbDeviceConnection,
        block: Int,
        addr: Int,
        length: Int,
    ): ByteArray? {
        val data = ByteArray(length)
        val index = block shl 8
        val r = conn.controlTransfer(
            0xC0, 0, addr, index, data, length, CTRL_TIMEOUT
        )
        return if (r == length) data else null
    }

    /**
     * Write a demodulator register (BLOCK_DEMOD, page-based).
     *
     * Maps to librtlsdr: rtlsdr_demod_write_reg(dev, page, addr, val, len)
     */
    fun writeDemodReg(
        conn: UsbDeviceConnection,
        page: Int,
        addr: Int,
        value: Int,
        len: Int = 1,
    ): Boolean {
        val data: ByteArray
        val realAddr = (addr shl 8) or 0x20
        data = if (len == 1) {
            byteArrayOf((value and 0xFF).toByte())
        } else {
            // librtlsdr uses BIG-endian for len=2 in demodulator writes as well!
            byteArrayOf(((value shr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
        }
        // RTL2832U requires the 0x10 bit set in wIndex for demodulator writes
        val index = 0x10 or page
        val r = conn.controlTransfer(
            0x40, 0, realAddr, index, data, len, CTRL_TIMEOUT
        )
        if (r < 0) Log.e(TAG, "writeDemodReg page=$page addr=0x${addr.toString(16)} val=0x${value.toString(16)} FAILED ($r)")
        
        // After every demod write, read page 0x0a addr 0x01 to flush (librtlsdr does this)
        readDemodReg(conn, 0x0a, 0x01, 1)
        return r == len
    }

    /**
     * Read a demodulator register (BLOCK_DEMOD, page-based).
     */
    fun readDemodReg(
        conn: UsbDeviceConnection,
        page: Int,
        addr: Int,
        len: Int = 1,
    ): Int {
        val data = ByteArray(len)
        val realAddr = (addr shl 8) or 0x20
        val index = page
        val r = conn.controlTransfer(
            0xC0, 0, realAddr, index, data, len, CTRL_TIMEOUT
        )
        if (r < 0) return -1
        if (len == 1) return data[0].toInt() and 0xFF
        return ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
    }

    /**
     * Write to the R820T2 tuner via I2C.
     *
     * @param regAddr  R820T2 register address (0x05 .. 0x1f)
     * @param value    Byte value to write
     */
    fun writeTunerReg(
        conn: UsbDeviceConnection,
        regAddr: Int,
        value: Int,
    ): Boolean {
        val data = byteArrayOf(regAddr.toByte(), (value and 0xFF).toByte())
        return writeArray(conn, BLOCK_IIC, R820T2_I2C_ADDR, data)
    }

    /**
     * Read from the R820T2 tuner via I2C.
     */
    fun readTunerRegs(
        conn: UsbDeviceConnection,
        regAddr: Int,
        length: Int,
    ): ByteArray? {
        // First write the register address
        val addrBuf = byteArrayOf(regAddr.toByte())
        writeArray(conn, BLOCK_IIC, R820T2_I2C_ADDR, addrBuf)
        // Then read back
        return readArray(conn, BLOCK_IIC, R820T2_I2C_ADDR, length)
    }

    /**
     * Enable or disable the I2C repeater to access the tuner.
     */
    fun setI2CRepeater(conn: UsbDeviceConnection, enable: Boolean): Boolean {
        return writeDemodReg(conn, 1, 0x01, if (enable) 0x18 else 0x10, 1)
    }
}
