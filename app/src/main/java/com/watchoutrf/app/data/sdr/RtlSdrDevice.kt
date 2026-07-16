package com.watchoutrf.app.data.sdr

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * High-level facade for an open RTL-SDR device.
 *
 * Combines:
 * - Rtl2832uChip  — demodulator / USB-bridge init & sample-rate
 * - R820T2Tuner   — tuner frequency & gain
 * - Bulk endpoint  — raw IQ sample reads
 *
 * Usage:
 *   val dev = RtlSdrDevice.open(usbManager, usbDevice) ?: return
 *   dev.setFrequency(98_000_000)
 *   val iq = dev.readSamples(numBins * 2)
 *   dev.close()
 */
class RtlSdrDevice private constructor(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val bulkEndpoint: UsbEndpoint,
) {
    private val demod  = Rtl2832uChip(connection)
    private val tuner  = R820T2Tuner(connection)

    private var currentFreqHz:    Long = 100_000_000L
    private var currentSampleRate: Int = DEFAULT_SAMPLE_RATE
    private var initialized = false
    
    // Continuous streaming to prevent hardware FIFO overflow (which causes STALL)
    @Volatile private var isStreaming = false
    private var streamThread: Thread? = null
    @Volatile private var latestBuffer: ByteArray? = null
    private val streamLock = Object()

    companion object {
        private const val TAG = "RtlSdrDevice"
        const val DEFAULT_SAMPLE_RATE = 2_048_000   // 2.048 MS/s
        private const val BULK_TIMEOUT_MS = 1_000

        /**
         * Open the RTL-SDR device and claim the USB interface.
         * Returns null on failure.
         */
        fun open(usbManager: UsbManager, device: UsbDevice): RtlSdrDevice? {
            if (!usbManager.hasPermission(device)) {
                Log.e(TAG, "No USB permission for ${device.deviceName}")
                return null
            }

            val connection = usbManager.openDevice(device)
            if (connection == null) {
                Log.e(TAG, "Failed to open USB device")
                return null
            }

            // Find interface 0 and its bulk IN endpoint
            val usbInterface = device.getInterface(0)
            if (!connection.claimInterface(usbInterface, true)) {
                Log.e(TAG, "Failed to claim USB interface 0")
                connection.close()
                return null
            }

            // Dummy write (librtlsdr does this before anything else)
            // On Android, this might be necessary to "wake up" the pipe before initBaseband
            val r = RtlSdrProtocol.writeReg(connection, RtlSdrProtocol.BLOCK_USB, 0x2000, 0x09)
            if (!r) {
                Log.i(TAG, "Dummy write failed, resetting device...")
                val fd = connection.fileDescriptor
                if (fd >= 0) {
                    com.watchoutrf.app.NativeLib().resetUsbDevice(fd)
                    try { Thread.sleep(100) } catch (e: Exception) {}
                }
            } else {
                // If it didn't fail, we'll still reset it just to be absolutely sure the hardware is fresh
                // as Android sometimes leaves the port powered and the chip in a bad state from a previous run.
                Log.i(TAG, "Resetting device unconditionally for fresh state...")
                val fd = connection.fileDescriptor
                if (fd >= 0) {
                    com.watchoutrf.app.NativeLib().resetUsbDevice(fd)
                    try { Thread.sleep(100) } catch (e: Exception) {}
                }
            }

            var bulkEndpoint: UsbEndpoint? = null
            for (i in 0 until usbInterface.endpointCount) {
                val ep = usbInterface.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    ep.direction == UsbConstants.USB_DIR_IN
                ) {
                    bulkEndpoint = ep
                    break
                }
            }

            if (bulkEndpoint == null) {
                Log.e(TAG, "No bulk IN endpoint found")
                connection.releaseInterface(usbInterface)
                connection.close()
                return null
            }

            Log.i(TAG, "USB device opened: ${device.productName} (VID=${device.vendorId.toString(16)} PID=${device.productId.toString(16)})")
            return RtlSdrDevice(connection, usbInterface, bulkEndpoint)
        }
    }

    /**
     * Full device initialisation sequence.
     * Must be called once before any data can be read.
     */
    fun initialize(sampleRateHz: Int = DEFAULT_SAMPLE_RATE): String {
        if (initialized) return "OK"
        Log.i(TAG, "Initialising RTL-SDR device…")

        // 1. Init RTL2832U baseband
        val basebandRes = demod.initBaseband()
        if (basebandRes != "OK") return "ERR_BASEBAND_$basebandRes"

        // 2. Enable I2C repeater to reach the tuner
        demod.setI2cRepeater(true)

        // 3. Init R820T2
        val tunerInitResult = tuner.init()
        if (tunerInitResult != "OK") {
            demod.setI2cRepeater(false)
            return "ERR_TUNER_$tunerInitResult"
        }

        // 4. Set IF bandwidth
        tuner.setIfBandwidth(sampleRateHz / 2)

        // 5. Disable I2C repeater
        demod.setI2cRepeater(false)

        // 6. Set sample rate
        if (!demod.setSampleRate(sampleRateHz)) return "ERR_SAMPLE_RATE"
        currentSampleRate = sampleRateHz

        // 7. Reset USB FIFO
        demod.resetBuffer()

        initialized = true
        Log.i(TAG, "RTL-SDR initialisation complete.")
        
        return "OK"
    }
    
    fun startStreaming() {
        if (isStreaming) return
        isStreaming = true
        streamThread = Thread {
            Log.i(TAG, "Streaming thread started with UsbRequest (async)")
            
            // Critical: Reset the hardware FIFO right before starting the stream
            demod.resetBuffer()
            
            val numRequests = 4
            val bufferSize = 16384
            val requests = Array(numRequests) { android.hardware.usb.UsbRequest() }
            val buffers = Array(numRequests) { java.nio.ByteBuffer.allocateDirect(bufferSize) }
            
            // Initialize and queue all requests
            var allInitialized = true
            for (i in 0 until numRequests) {
                if (!requests[i].initialize(connection, bulkEndpoint)) {
                    Log.e(TAG, "Failed to initialize UsbRequest $i")
                    allInitialized = false
                    break
                }
                requests[i].clientData = buffers[i]
                
                // queue handles position/limit on older APIs if we just use the extension
                buffers[i].clear()
                // Use the older queue API that takes a length, for compatibility
                if (!requests[i].queue(buffers[i], bufferSize)) {
                    Log.e(TAG, "Failed to queue initial UsbRequest $i")
                }
            }
            
            if (!allInitialized) {
                isStreaming = false
                return@Thread
            }
            
            while (isStreaming) {
                // Wait for the next completed request
                val response = connection.requestWait()
                if (response == null) {
                    Log.e(TAG, "requestWait returned null! Hardware might have stalled.")
                    demod.resetBuffer()
                    try { Thread.sleep(10) } catch (e: Exception) {}
                    continue
                }
                
                val buffer = response.clientData as java.nio.ByteBuffer
                
                // UsbRequest.queue(ByteBuffer, int) does NOT update the buffer's position
                // on older Android APIs. Since the RTL-SDR streams continuously, we can 
                // assume the URB was filled completely (bufferSize).
                val bytesRead = bufferSize
                
                if (bytesRead > 0) {
                    synchronized(streamLock) {
                        val chunk = ByteArray(bytesRead)
                        buffer.position(0)
                        buffer.get(chunk)
                        latestBuffer = chunk
                        streamLock.notifyAll()
                    }
                }
                
                // Re-queue the request for the next chunk of data
                if (isStreaming) {
                    buffer.clear()
                    response.queue(buffer, bufferSize)
                }
            }
            
            // Cleanup
            for (req in requests) {
                req.cancel()
                req.close()
            }
            Log.i(TAG, "Streaming thread stopped")
        }
        streamThread?.start()
    }

    private fun stopStreaming() {
        isStreaming = false
        streamThread?.interrupt()
        streamThread?.join(500)
        streamThread = null
    }

    /**
     * Tune to a centre frequency.
     *
     * @param freqHz  Centre frequency in Hz.
     */
    fun setFrequency(freqHz: Long): Boolean {
        demod.setI2cRepeater(true)
        val ok = tuner.setFrequency(freqHz)
        demod.setI2cRepeater(false)
        if (ok) {
            currentFreqHz = freqHz
            Log.i(TAG, "Tuned to ${freqHz / 1_000_000.0} MHz")
        }
        return ok
    }

    /**
     * Set gain.
     *
     * @param gainTenthsDb  Gain in tenths-of-dB, or -1 for auto.
     */
    fun setGain(gainTenthsDb: Int): Boolean {
        demod.setI2cRepeater(true)
        val ok = tuner.setGain(gainTenthsDb)
        demod.setI2cRepeater(false)
        return ok
    }

    /**
     * Read [length] raw IQ bytes.
     * Since the hardware continuously streams, this fetches the latest available data
     * from the streaming thread to prevent hardware stalls.
     *
     * @param length  Number of bytes to read.
     * @return ByteArray of unsigned-8 interleaved IQ samples, or null on error.
     */
    fun readSamples(length: Int): ByteArray? {
        if (!isStreaming) return null
        
        synchronized(streamLock) {
            // Wait for new data if we don't have any
            if (latestBuffer == null) {
                try { streamLock.wait(500) } catch (e: Exception) {}
            }
            
            val buf = latestBuffer
            if (buf == null) {
                Log.e(TAG, "readSamples timeout waiting for streaming thread")
                return null
            }
            
            // Clear it so we wait for fresh data next time
            latestBuffer = null
            
            // Return exactly the requested length (truncate or pad as necessary)
            return if (buf.size >= length) {
                buf.copyOfRange(0, length)
            } else {
                // If the hardware returned a smaller chunk, just return what we have padded
                val result = ByteArray(length)
                System.arraycopy(buf, 0, result, 0, buf.size)
                result
            }
        }
    }

    /** Close the device and release all resources. */
    fun close() {
        try {
            stopStreaming()
            demod.deinit()
            connection.releaseInterface(usbInterface)
            connection.close()
            initialized = false
            Log.i(TAG, "RTL-SDR device closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing device", e)
        }
    }

    val currentFrequency: Long get() = currentFreqHz
    val sampleRate: Int get() = currentSampleRate
    val isOpen: Boolean get() = initialized
}
