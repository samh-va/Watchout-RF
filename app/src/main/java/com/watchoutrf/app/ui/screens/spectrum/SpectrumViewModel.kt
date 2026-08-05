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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import javax.inject.Inject

import com.watchoutrf.app.data.db.NamedFrequencyDao
import com.watchoutrf.app.data.export.WwbExporter

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
    private val namedFrequencyDao: NamedFrequencyDao,
) : ViewModel() {

    private val _state = MutableStateFlow(SpectrumState())
    val state: StateFlow<SpectrumState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var usbObserverJob: Job? = null
    private var frameCount = 0
    private var fpsTimestamp = System.currentTimeMillis()

    init {
        observeUsbConnection()
        viewModelScope.launch {
            Log.d("WatchoutRF", "Starting to collect namedFrequencies flow")
            try {
                namedFrequencyDao.getAllFrequencies().collect { frequencies ->
                    Log.d("WatchoutRF", "namedFrequencies updated: ${frequencies.size} items")
                    _state.value = _state.value.copy(namedFrequencies = frequencies)
                }
            } catch (e: Exception) {
                Log.e("WatchoutRF", "Error collecting namedFrequencies flow", e)
            }
        }
    }

    // CRUD for Named Frequencies
    fun addNamedFrequency(name: String, freqHz: Long, color: String = "#FF6B00") {
        Log.d("WatchoutRF", "addNamedFrequency called: name=$name freqHz=$freqHz")
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    namedFrequencyDao.insert(
                        NamedFrequency(name = name, frequencyHz = freqHz, colorHex = color)
                    )
                }
                Log.d("WatchoutRF", "addNamedFrequency: insert SUCCESS")
            } catch (e: Exception) {
                Log.e("WatchoutRF", "addNamedFrequency: insert FAILED", e)
            }
        }
    }

    fun deleteNamedFrequency(freq: NamedFrequency) {
        viewModelScope.launch {
            namedFrequencyDao.delete(freq)
        }
    }

    fun toggleFrequencyVisibility(freq: NamedFrequency) {
        viewModelScope.launch {
            namedFrequencyDao.update(freq.copy(isVisible = !freq.isVisible))
        }
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
                // (Starting the stream before the tuner is configured)
                // 3. Set initial state
                // Note: Frequency is tuned by the continuous sweep loop in startScanning()
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
                val spanHz = range.spanHz
                
                // ── FIXED SWEEP: use 80% of real SDR bandwidth as hop step ──
                // RTL-SDR real BW is 2,048,000 Hz. We use 80% (1,638,400 Hz)
                // as the hop advance so consecutive hops always overlap and
                // no frequency falls in a blind gap between hops.
                val sdrBwHz    = 2_048_000L
                val usableBwHz = (sdrBwHz * 0.80).toLong()  // 1,638,400 Hz
                val numHops    = maxOf(1, Math.ceil(spanHz.toDouble() / usableBwHz.toDouble()).toInt())
                
                val masterSpectrum = FloatArray(config.numBins) { -300f }
                val hzPerUiBin = spanHz.toDouble() / config.numBins
                
                var sweepFailed = false

                for (hopIndex in 0 until numHops) {
                    if (!isActive || !_state.value.isScanning) break
                    
                    // Break if user changed range while we were sweeping
                    if (_state.value.scanConfig.frequencyRange != range) break

                    // Center is the midpoint of the usable block for this hop
                    val hopCenterHz = range.startHz + (hopIndex * usableBwHz) + (usableBwHz / 2)
                    val hopStartHz  = range.startHz + (hopIndex * usableBwHz)
                    val hopEndHz    = (hopStartHz + usableBwHz).coerceAtMost(range.endHz)
                    
                    val rawMagnitudes: FloatArray? = if (!_state.value.isDemoMode && sdrSource.isOpen) {
                        // 1. Tune hardware to this hop
                        sdrSource.setFrequency(hopCenterHz)
                        
                        // 2. Wait for PLL lock and discard stale USB buffers
                        delay(10)
                        sdrSource.flush()
                        
                        // 3. Read raw spectrum for this chunk
                        val numReads = if (spanHz > 50_000_000L) 2 else 4
                        sdrSource.readSpectrum(1024, numReads)
                    } else {
                        // Demo mode
                        syntheticGenerator.generateSpectrum(
                            startFreqHz = hopStartHz,
                            endFreqHz   = hopEndHz,
                            numBins     = 1024,
                            noiseFloorDbm = (config.referenceLevel - config.dynamicRange).toDouble(),
                        ).magnitudes
                    }

                    if (rawMagnitudes == null) {
                        sweepFailed = true
                        break
                    }
                    
                    // 4. Map the 1024 high-res bins into the master UI array
                    // The SDR always captures ±sdrBwHz/2 around the center
                    val chunkStartHz  = hopCenterHz - (sdrBwHz / 2)
                    val chunkEndHz    = hopCenterHz + (sdrBwHz / 2)
                    val chunkHzPerBin = (chunkEndHz - chunkStartHz).toDouble() / rawMagnitudes.size
                    
                    // FIXED: 2% edge trim (was 5% = 102 kHz loss per side)
                    val edgeTrim = (rawMagnitudes.size * 0.02).toInt()
                    val dcCenter = rawMagnitudes.size / 2
                    val dcHalf   = (rawMagnitudes.size * 0.005).toInt().coerceAtLeast(3)
                    
                    for (i in edgeTrim until (rawMagnitudes.size - edgeTrim)) {
                        // Skip the DC zone (center of the FFT-shifted output)
                        if (i in (dcCenter - dcHalf)..(dcCenter + dcHalf)) continue
                        
                        val binFreqHz = chunkStartHz + (i * chunkHzPerBin)
                        if (binFreqHz >= range.startHz && binFreqHz <= range.endHz) {
                            val uiBinIndex = ((binFreqHz - range.startHz) / hzPerUiBin).toInt()
                                .coerceIn(0, config.numBins - 1)
                            
                            // Max-pooling: keep the highest peak if multiple raw bins map to one UI bin
                            if (rawMagnitudes[i] > masterSpectrum[uiBinIndex]) {
                                masterSpectrum[uiBinIndex] = rawMagnitudes[i]
                            }
                        }
                    }

                } // End of hop loop

                if (sweepFailed) {
                    // Attempt to re-open the hardware before giving up
                    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                    val usbDevice = usbManager.deviceList.values
                        .firstOrNull { usbDeviceManager.isSupportedDevice(it) }
                    if (usbDevice != null && !sdrSource.isOpen) {
                        _state.value = _state.value.copy(hwError = "Dongle disconnected. Reconnecting...")
                        sdrSource.close()
                        delay(500)
                        val err = sdrSource.open(usbManager, usbDevice)
                        if (err == null) {
                            sdrSource.setGain(_state.value.scanConfig.gainMode, _state.value.scanConfig.manualGainDb)
                            sdrSource.startStream()
                            _state.value = _state.value.copy(hwError = null, isDemoMode = false)
                        } else {
                            _state.value = _state.value.copy(
                                hwError = "Dongle disconnected. Falling back to demo mode.",
                                isDemoMode = true,
                            )
                        }
                    } else {
                        _state.value = _state.value.copy(
                            hwError = "Read error from dongle. Falling back to demo.",
                            isDemoMode = true,
                        )
                    }
                    delay(FRAME_DELAY_MS)
                    continue
                }
                
                // Gap-filling: linearly interpolate between valid bins
                // This smoothly bridges the DC-trimmed zones instead of
                // carrying a spike value forward.
                var gapStart = -1
                for (i in masterSpectrum.indices) {
                    if (masterSpectrum[i] <= -299.9f) {
                        if (gapStart < 0) gapStart = i
                    } else {
                        if (gapStart >= 0) {
                            // We found the end of a gap at index i
                            val leftVal = if (gapStart > 0) masterSpectrum[gapStart - 1] else masterSpectrum[i]
                            val rightVal = masterSpectrum[i]
                            val gapLen = i - gapStart
                            for (g in 0 until gapLen) {
                                val t = (g + 1).toFloat() / (gapLen + 1).toFloat()
                                masterSpectrum[gapStart + g] = leftVal + (rightVal - leftVal) * t
                            }
                            gapStart = -1
                        }
                    }
                }
                // Fill any trailing gap
                if (gapStart >= 0) {
                    val leftVal = if (gapStart > 0) masterSpectrum[gapStart - 1] else -100f
                    for (g in gapStart until masterSpectrum.size) {
                        masterSpectrum[g] = leftVal
                    }
                }
                
                // Spectral smoothing: 3-bin moving average to reduce visual noise
                val smoothed = FloatArray(masterSpectrum.size)
                for (i in masterSpectrum.indices) {
                    val left  = if (i > 0) masterSpectrum[i - 1] else masterSpectrum[i]
                    val right = if (i < masterSpectrum.size - 1) masterSpectrum[i + 1] else masterSpectrum[i]
                    smoothed[i] = (left + masterSpectrum[i] + right) / 3f
                }

                // --- DSP pipeline (Runs once per full sweep) ---
                val averaged = averager.average(smoothed, config.averagingFactor)

                val maxHold = if (config.maxHoldEnabled) {
                    averager.updateMaxHold(averaged)
                } else {
                    _state.value.maxHoldSpectrum
                }

                // Waterfall history
                waterfallBuffer.add(0, averaged.copyOf())
                if (waterfallBuffer.size > MAX_WATERFALL_HISTORY)
                    waterfallBuffer.removeAt(waterfallBuffer.lastIndex)

                // Peak detection
                peakFrameCounter++
                val detectedPeaks = if (peakFrameCounter >= PEAK_DETECT_INTERVAL) {
                    peakFrameCounter = 0
                    val frameForDetection = SpectrumData(
                        frequencyStartHz = range.startHz,
                        frequencyEndHz   = range.endHz,
                        magnitudes       = averaged,
                    )
                    peakDetector.detectPeaks(
                        frameForDetection,
                        minPeakDbm    = config.referenceLevel - config.dynamicRange + 10f,
                        minDistanceBins = config.numBins / 50,
                        maxPeaks      = 4,
                    )
                } else {
                    _state.value.autoDetectedPeaks
                }

                // FPS tracking (here it tracks Sweeps Per Second)
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

                // Single-sweep mode stops after one full sweep
                if (config.scanMode == ScanMode.SINGLE) {
                    _state.value = _state.value.copy(isScanning = false)
                    break
                }

                // Small delay to prevent burning CPU if sweep is instantaneous
                delay(10)
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
        // Hardware retuning will be handled automatically by the next iteration 
        // of the sweep loop in startScanning().
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

    fun updateActiveMarker(normalizedX: Float?) {
        _state.value = _state.value.copy(activeMarkerX = normalizedX)
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
        _state.value = _state.value.copy(
            markers = emptyList(),
            activeMarkerX = null
        )
    }

    fun updateNumBins(numBins: Int) {
        averager.reset()
        _state.value = _state.value.copy(
            scanConfig = _state.value.scanConfig.copy(numBins = numBins),
            currentSpectrum = null,
            maxHoldSpectrum = null,
        )
    }

    fun updateMarkerColor(markerId: Int, color: MarkerColor) {
        val existing = _state.value.markers.map {
            if (it.id == markerId) it.copy(color = color) else it
        }
        _state.value = _state.value.copy(markers = existing)
    }

    fun updateMarkerLabel(markerId: Int, newLabel: String) {
        val existing = _state.value.markers.map {
            if (it.id == markerId) it.copy(label = newLabel) else it
        }
        _state.value = _state.value.copy(markers = existing)
    }

    fun removeMarker(markerId: Int) {
        val existing = _state.value.markers.filter { it.id != markerId }
        _state.value = _state.value.copy(markers = existing)
    }

    fun addMarkerManual(freqHz: Long, label: String, color: MarkerColor? = null) {
        val config = _state.value.scanConfig
        val spectrum = _state.value.currentSpectrum

        // Estimate amplitude if it is within the current range
        val range = config.frequencyRange
        val ampDbm = if (spectrum != null && freqHz >= range.startHz && freqHz <= range.endHz) {
            val normalizedX = (freqHz - range.startHz).toFloat() / (range.endHz - range.startHz).toFloat()
            val bin = (normalizedX * (spectrum.size - 1)).toInt().coerceIn(0, spectrum.size - 1)
            spectrum[bin]
        } else {
            -100f
        }

        val existing = _state.value.markers
        val markerIndex = (existing.maxOfOrNull { it.id } ?: 0) + 1
        val finalColor = color ?: MarkerColor.entries[(markerIndex - 1) % MarkerColor.entries.size]

        _state.value = _state.value.copy(
            markers = existing + Marker(
                id = markerIndex,
                frequencyHz = freqHz.toDouble(),
                amplitudeDbm = ampDbm,
                label = if (label.isNotBlank()) label else "M$markerIndex",
                color = finalColor,
            )
        )
    }

    /**
     * Exports the current spectrum (or Max Hold if active) to a WWB7-compatible CSV file.
     * [uri] must come from an ActivityResultContracts.CreateDocument picker.
     * Returns null on success, or an error message on failure.
     */
    fun exportWwb(uri: android.net.Uri): String? {
        val config   = _state.value.scanConfig
        val range    = config.frequencyRange
        val spectrum = _state.value.maxHoldSpectrum?.takeIf { config.maxHoldEnabled }
            ?: _state.value.currentSpectrum
            ?: return "No spectrum data available to export."

        val spectrumData = com.watchoutrf.app.domain.model.SpectrumData(
            frequencyStartHz = range.startHz,
            frequencyEndHz   = range.endHz,
            magnitudes       = spectrum,
        )
        return WwbExporter.export(context, spectrumData, uri)
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
