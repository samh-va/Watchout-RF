package com.watchoutrf.app.ui.screens.spectrum

import android.content.Context
import android.hardware.usb.UsbManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchoutrf.app.data.dsp.PeakDetector
import com.watchoutrf.app.data.dsp.SpectrumAverager
import com.watchoutrf.app.data.dsp.SyntheticSignalGenerator
import com.watchoutrf.app.data.sdr.RtlSdrSource
import com.watchoutrf.app.data.usb.UsbConnectionState
import com.watchoutrf.app.data.usb.UsbDeviceManager
import com.watchoutrf.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Orchestrates the spectrum scan loop.
 *
 * Source selection:
 *  - If an RTL-SDR dongle is connected (UsbConnectionState.Connected) →
 *    opens the hardware via [RtlSdrSource] and reads real IQ data.
 *  - Otherwise → falls back to [SyntheticSignalGenerator] (demo mode).
 *
 * The switch happens automatically whenever the USB connection state changes.
 */
@HiltViewModel
class SpectrumViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbDeviceManager: UsbDeviceManager,
    private val sdrSource: RtlSdrSource,
    private val syntheticGenerator: SyntheticSignalGenerator,
    private val averager: SpectrumAverager,
    private val peakDetector: PeakDetector,
) : ViewModel() {

    private val _state = MutableStateFlow(SpectrumState())
    val state: StateFlow<SpectrumState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var usbObserverJob: Job? = null
    private var frameCount = 0
    private var fpsTimestamp = System.currentTimeMillis()

    init {
        observeUsbConnection()
    }

    // =========================================================================
    // USB connection observer — switches source automatically
    // =========================================================================

    private fun observeUsbConnection() {
        usbObserverJob = viewModelScope.launch {
            usbDeviceManager.connectionState.collect { usbState ->
                when (usbState) {
                    is UsbConnectionState.Connected -> {
                        _state.value = _state.value.copy(debugState = "GOT_CONN")
                        val deviceName = usbState.device.displayName
                        tryOpenHardware(deviceName)
                    }
                    is UsbConnectionState.Disconnected -> {
                        _state.value = _state.value.copy(
                            isDemoMode = true,
                            connectedDeviceName = null,
                            hwError = null,
                            debugState = "GOT_DISCONN"
                        )
                    }
                    is UsbConnectionState.PermissionDenied -> {
                        // Close hardware source if open; fall back to demo
                        if (sdrSource.isOpen) {
                            val wasScanning = _state.value.isScanning
                            stopScanning()
                            sdrSource.close()
                            _state.value = _state.value.copy(
                                isDemoMode = true,
                                connectedDeviceName = null,
                                hwError = null,
                            )
                            if (wasScanning) startScanning()
                        }
                    }
                    else -> {
                        _state.value = _state.value.copy(debugState = "GOT_OTHER")
                    }
                }
            }
        }
    }

    /**
     * Attempt to open the RTL-SDR hardware and switch to real data source.
     * Called when USB state transitions to Connected.
     */
    private fun tryOpenHardware(deviceName: String) {
        _state.value = _state.value.copy(debugState = "TRY_OPEN")
        viewModelScope.launch {
            _state.value = _state.value.copy(debugState = "STOP_SCAN")
            val wasScanning = _state.value.isScanning
            stopScanning()

            _state.value = _state.value.copy(debugState = "GET_USB")
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val usbDevice = usbManager.deviceList.values
                .firstOrNull { usbDeviceManager.isSupportedDevice(it) }

            if (usbDevice == null) {
                _state.value = _state.value.copy(hwError = "Device not found in USB list", debugState = "DEV_NULL")
                return@launch
            }

            _state.value = _state.value.copy(debugState = "SDR_OPEN")
            val errorString = sdrSource.open(usbManager, usbDevice)

            if (errorString == null) {
                _state.value = _state.value.copy(debugState = "SDR_OK")
                // Apply current frequency range and gain before starting the data stream
                // (Starting the stream before the tuner is configured can stall the USB hardware)
                sdrSource.setFrequency(_state.value.scanConfig.frequencyRange)
                sdrSource.setGain(
                    _state.value.scanConfig.gainMode,
                    _state.value.scanConfig.manualGainDb,
                )
                
                // Now start the streaming thread
                sdrSource.startStream()
                
                _state.value = _state.value.copy(
                    isDemoMode = false,
                    connectedDeviceName = deviceName,
                    hwError = null,
                )
                if (wasScanning) startScanning()
            } else {
                _state.value = _state.value.copy(
                    hwError = "Failed to initialise $deviceName. Try reconnecting.",
                    isDemoMode = true,
                    connectedDeviceName = null,
                    debugState = "FAIL: $errorString"
                )
            }
        }
    }

    // =========================================================================
    // Scan loop
    // =========================================================================

    fun startScanning() {
        if (scanJob?.isActive == true) return

        _state.value = _state.value.copy(isScanning = true, hwError = null)
        frameCount = 0
        fpsTimestamp = System.currentTimeMillis()

        scanJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val waterfallBuffer = mutableListOf<FloatArray>()
            waterfallBuffer.addAll(_state.value.waterfallHistory)
            var peakFrameCounter = 0

            while (isActive && _state.value.isScanning) {
                val config = _state.value.scanConfig
                val range  = config.frequencyRange

                // --- Acquire one spectrum frame ---
                val rawMagnitudes: FloatArray? = if (!_state.value.isDemoMode && sdrSource.isOpen) {
                    // Real hardware path
                    sdrSource.readSpectrum(config.numBins)
                } else {
                    // Synthetic / demo path
                    syntheticGenerator.generateSpectrum(
                        startFreqHz = range.startHz,
                        endFreqHz   = range.endHz,
                        numBins     = config.numBins,
                        noiseFloorDbm = (config.referenceLevel - config.dynamicRange).toDouble(),
                    ).magnitudes
                }

                if (rawMagnitudes == null) {
                    // Hardware read failure — surface error and fall back
                    _state.value = _state.value.copy(
                        hwError = "Read error from dongle. Falling back to demo.",
                        isDemoMode = true,
                    )
                    delay(FRAME_DELAY_MS)
                    continue
                }

                // --- DSP pipeline ---
                val averaged = averager.average(rawMagnitudes, config.averagingFactor)

                val maxHold = if (config.maxHoldEnabled) {
                    averager.updateMaxHold(averaged)
                } else {
                    _state.value.maxHoldSpectrum
                }

                // Waterfall history (newest at front, capped at MAX_WATERFALL_HISTORY)
                waterfallBuffer.add(0, averaged.copyOf())
                if (waterfallBuffer.size > MAX_WATERFALL_HISTORY)
                    waterfallBuffer.removeAt(waterfallBuffer.lastIndex)

                // Peak detection every N frames
                peakFrameCounter++
                val detectedPeaks = if (peakFrameCounter >= PEAK_DETECT_INTERVAL) {
                    peakFrameCounter = 0
                    val frameForDetection = com.watchoutrf.app.domain.model.SpectrumData(
                        frequencyStartHz = range.startHz,
                        frequencyEndHz   = range.endHz,
                        magnitudes       = averaged,
                    )
                    peakDetector.detectPeaks(
                        frameForDetection,
                        minPeakDbm    = config.referenceLevel - config.dynamicRange + 10f,
                        minDistanceBins = config.numBins / 50,
                        maxPeaks      = 8,
                    )
                } else {
                    _state.value.autoDetectedPeaks
                }

                // FPS tracking
                frameCount++
                val now     = System.currentTimeMillis()
                val elapsed = now - fpsTimestamp
                val fps = if (elapsed >= 1000L) {
                    val v = (frameCount * 1000L / elapsed).toInt()
                    frameCount = 0
                    fpsTimestamp = now
                    v
                } else {
                    _state.value.framesPerSecond
                }

                _state.value = _state.value.copy(
                    currentSpectrum   = averaged,
                    maxHoldSpectrum   = maxHold,
                    waterfallHistory  = waterfallBuffer.toList(),
                    autoDetectedPeaks = detectedPeaks,
                    framesPerSecond   = fps,
                )

                // Single-sweep mode stops after one frame
                if (config.scanMode == ScanMode.SINGLE) {
                    _state.value = _state.value.copy(isScanning = false)
                    break
                }

                delay(FRAME_DELAY_MS)
            }
        }
    }

    fun stopScanning() {
        _state.value = _state.value.copy(isScanning = false)
        scanJob?.cancel()
        scanJob = null
    }

    // =========================================================================
    // Configuration helpers
    // =========================================================================

    fun updateFrequencyRange(range: FrequencyRange) {
        averager.reset()
        // If hardware is active, retune immediately
        if (!_state.value.isDemoMode && sdrSource.isOpen) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { sdrSource.setFrequency(range) }
        }
        _state.value = _state.value.copy(
            scanConfig        = _state.value.scanConfig.copy(frequencyRange = range),
            currentSpectrum   = null,
            maxHoldSpectrum   = null,
            waterfallHistory  = emptyList(),
            autoDetectedPeaks = emptyList(),
            markers           = emptyList(),
        )
    }

    fun updateGainMode(mode: GainMode) {
        _state.value = _state.value.copy(
            scanConfig = _state.value.scanConfig.copy(gainMode = mode),
        )
        if (!_state.value.isDemoMode && sdrSource.isOpen) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                sdrSource.setGain(mode, _state.value.scanConfig.manualGainDb)
            }
        }
    }

    fun updateScanMode(mode: ScanMode) {
        _state.value = _state.value.copy(
            scanConfig = _state.value.scanConfig.copy(scanMode = mode),
        )
    }

    fun toggleMaxHold() {
        val config     = _state.value.scanConfig
        val newEnabled = !config.maxHoldEnabled
        _state.value = _state.value.copy(
            scanConfig       = config.copy(maxHoldEnabled = newEnabled),
            maxHoldSpectrum  = if (!newEnabled) null else _state.value.maxHoldSpectrum,
        )
    }

    fun resetMaxHold() {
        averager.reset()
        _state.value = _state.value.copy(maxHoldSpectrum = null)
    }

    fun addMarker(normalizedX: Float) {
        val config   = _state.value.scanConfig
        val range    = config.frequencyRange
        val freqHz   = range.startHz + ((range.endHz - range.startHz) * normalizedX.coerceIn(0f, 1f)).toLong()
        val spectrum = _state.value.currentSpectrum
        val ampDbm   = if (spectrum != null && spectrum.isNotEmpty()) {
            val bin = (normalizedX * (spectrum.size - 1)).toInt().coerceIn(0, spectrum.size - 1)
            spectrum[bin]
        } else {
            -100f
        }

        val existing    = _state.value.markers
        val markerIndex = existing.size + 1
        val color       = MarkerColor.entries[(markerIndex - 1) % MarkerColor.entries.size]

        _state.value = _state.value.copy(
            markers = existing + Marker(
                id           = markerIndex,
                frequencyHz  = freqHz.toDouble(),
                amplitudeDbm = ampDbm,
                label        = "M$markerIndex",
                color        = color,
            ),
        )
    }

    fun clearMarkers() {
        _state.value = _state.value.copy(markers = emptyList())
    }

    fun dismissHwError() {
        _state.value = _state.value.copy(hwError = null)
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
        sdrSource.close()
    }

    companion object {
        private const val FRAME_DELAY_MS        = 33L   // ~30 FPS
        private const val MAX_WATERFALL_HISTORY = 100
        private const val PEAK_DETECT_INTERVAL  = 10
    }
}
