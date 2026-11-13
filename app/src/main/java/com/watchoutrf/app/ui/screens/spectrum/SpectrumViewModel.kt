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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Serialises every access to the dongle (open / close / tune / read / gain).
     * The scan loop runs on an IO thread while USB attach/detach events arrive on
     * the main thread; without this they could close the device mid-read.
     */
    private val hwMutex = Mutex()

    private var scanJob: Job? = null
    private var usbObserverJob: Job? = null
    private var frameCount = 0
    private var fpsTimestamp = System.currentTimeMillis()

    init {
        observeUsbConnection()
        viewModelScope.launch {
            try {
                namedFrequencyDao.getAllFrequencies().collect { frequencies ->
                    _state.update { it.copy(namedFrequencies = frequencies) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting namedFrequencies flow", e)
            }
        }
    }

    // CRUD for Named Frequencies
    fun addNamedFrequency(name: String, freqHz: Long, color: String = "#FF6B00") {
        viewModelScope.launch {
            try {
                namedFrequencyDao.insert(
                    NamedFrequency(name = name, frequencyHz = freqHz, colorHex = color)
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "addNamedFrequency: insert failed", e)
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
                        val deviceName = usbState.device.displayName
                        _state.update { it.copy(debugState = "SDR_OPEN") }
                        val error = openHardware(deviceName)
                        if (error != null) {
                            _state.update {
                                it.copy(
                                    hwError = "Failed to initialise $deviceName. Try reconnecting.",
                                    isDemoMode = true,
                                    connectedDeviceName = null,
                                    debugState = "FAIL: $error",
                                )
                            }
                        }
                    }
                    is UsbConnectionState.Disconnected,
                    is UsbConnectionState.PermissionDenied -> {
                        // Switch the scan loop to demo data first, then release the device.
                        // (Leaving it open keeps the streaming thread spinning on a dead
                        // connection and holds the USB interface.)
                        _state.update {
                            it.copy(
                                isDemoMode = true,
                                connectedDeviceName = null,
                                hwError = null,
                                debugState = "GOT_DISCONN",
                            )
                        }
                        closeHardware()
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * Opens the RTL-SDR and switches the scan loop to real data.
     * All blocking USB work happens on [Dispatchers.IO] — tuner initialisation takes
     * hundreds of milliseconds and must never run on the main thread.
     *
     * @return null on success, otherwise a short error code.
     */
    private suspend fun openHardware(deviceName: String?): String? = hwMutex.withLock {
        withContext(Dispatchers.IO) {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val usbDevice = usbManager.deviceList.values
                .firstOrNull { usbDeviceManager.isSupportedDevice(it) }
                ?: return@withContext "DEV_NULL"
            if (!usbManager.hasPermission(usbDevice)) return@withContext "NO_PERMISSION"

            val error = sdrSource.open(usbManager, usbDevice)
            if (error != null) {
                sdrSource.close()
                return@withContext error
            }

            // Frequency is tuned per hop by the sweep loop; only gain is set here,
            // before the streaming thread starts.
            val config = _state.value.scanConfig
            sdrSource.setGain(config.gainMode, config.manualGainDb)
            sdrSource.startStream()

            _state.update {
                it.copy(
                    isDemoMode = false,
                    connectedDeviceName = deviceName ?: it.connectedDeviceName,
                    hwError = null,
                    debugState = "SDR_OK",
                )
            }
            null
        }
    }

    private suspend fun closeHardware() = hwMutex.withLock {
        withContext(Dispatchers.IO) { sdrSource.close() }
    }

    /**
     * Called by the scan loop after a failed read. Re-opens the dongle (USB reset +
     * full re-initialisation) a few times before giving up.
     */
    private suspend fun tryReconnect(maxAttempts: Int = 3): Boolean {
        val deviceName = _state.value.connectedDeviceName
        repeat(maxAttempts) { attempt ->
            // The dongle was unplugged: the USB observer handles that case.
            if (usbDeviceManager.connectionState.value !is UsbConnectionState.Connected) return false
            _state.update {
                it.copy(hwError = "Dongle not responding. Reconnecting (${attempt + 1}/$maxAttempts)...")
            }
            closeHardware()
            delay(500)
            if (openHardware(deviceName) == null) return true
            delay(1000)
        }
        closeHardware()
        return false
    }

    // =========================================================================
    // Scan loop
    // =========================================================================

    fun startScanning() {
        if (scanJob?.isActive == true) return

        _state.update { it.copy(isScanning = true, hwError = null) }
        frameCount = 0
        fpsTimestamp = System.currentTimeMillis()

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val waterfallBuffer = mutableListOf<FloatArray>()
            waterfallBuffer.addAll(_state.value.waterfallHistory)
            var peakFrameCounter = 0
            var lastRange: FrequencyRange? = null
            var lastNumBins = -1
            var lastDemoMode: Boolean? = null

            while (isActive && _state.value.isScanning) {
                val sweepStartMs = System.currentTimeMillis()
                val config = _state.value.scanConfig
                val range  = config.frequencyRange
                val spanHz = range.spanHz
                val demoMode = _state.value.isDemoMode

                // Range, resolution or data source changed since the previous sweep:
                // the running average, max hold and waterfall describe something else
                // and must not be blended with the new data.
                if (range != lastRange || config.numBins != lastNumBins || demoMode != lastDemoMode) {
                    if (lastRange != null) {
                        averager.reset()
                        waterfallBuffer.clear()
                    }
                    peakFrameCounter = 0
                    lastRange = range
                    lastNumBins = config.numBins
                    lastDemoMode = demoMode
                }
                
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
                var sweepAborted = false

                for (hopIndex in 0 until numHops) {
                    // Abort if the user stopped, or changed range / resolution, or the
                    // data source switched (dongle plugged / unplugged) mid-sweep.
                    val live = _state.value
                    if (!isActive || !live.isScanning ||
                        live.scanConfig.frequencyRange != range ||
                        live.scanConfig.numBins != config.numBins ||
                        live.isDemoMode != demoMode
                    ) {
                        sweepAborted = true
                        break
                    }

                    // Center is the midpoint of the usable block for this hop
                    val hopCenterHz = range.startHz + (hopIndex * usableBwHz) + (usableBwHz / 2)
                    val hopStartHz  = range.startHz + (hopIndex * usableBwHz)
                    val hopEndHz    = (hopStartHz + usableBwHz).coerceAtMost(range.endHz)
                    
                    val rawMagnitudes: FloatArray? = if (!demoMode) {
                        hwMutex.withLock {
                            if (!sdrSource.isOpen) {
                                null
                            } else {
                                // 1. Tune hardware to this hop
                                sdrSource.setFrequency(hopCenterHz)

                                // 2. Wait for PLL lock and discard stale USB buffers
                                delay(10)
                                sdrSource.flush()

                                // 3. Read raw spectrum for this chunk
                                val numReads = if (spanHz > 50_000_000L) 2 else 4
                                sdrSource.readSpectrum(1024, numReads)
                            }
                        }
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
                    // If the dongle was unplugged the USB observer has already switched
                    // (or is about to switch) to demo mode; otherwise try to recover it.
                    if (!_state.value.isDemoMode && !tryReconnect()) {
                        _state.update {
                            it.copy(
                                hwError = "Read error from dongle. Falling back to demo mode. " +
                                    "Unplug and reconnect it to retry.",
                                isDemoMode = true,
                                connectedDeviceName = null,
                            )
                        }
                    }
                    delay(FRAME_DELAY_MS)
                    continue
                }

                // An interrupted sweep is only partially filled. Publishing it would seed
                // the average and the max-hold trace with data from the previous settings.
                if (sweepAborted) continue

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

                val maxHold = if (_state.value.scanConfig.maxHoldEnabled) {
                    averager.updateMaxHold(averaged)
                } else {
                    null
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

                val history = waterfallBuffer.toList()
                _state.update { cur ->
                    // Settings may have changed while this sweep was being post-processed;
                    // in that case the frame no longer matches the displayed axes.
                    if (cur.scanConfig.frequencyRange != range || cur.scanConfig.numBins != config.numBins) {
                        cur
                    } else {
                        cur.copy(
                            currentSpectrum   = averaged,
                            maxHoldSpectrum   = if (cur.scanConfig.maxHoldEnabled) maxHold else null,
                            waterfallHistory  = history,
                            autoDetectedPeaks = detectedPeaks,
                            framesPerSecond   = fps,
                        )
                    }
                }

                // Single-sweep mode stops after one full sweep
                if (config.scanMode == ScanMode.SINGLE) {
                    _state.update { it.copy(isScanning = false) }
                    break
                }

                // Pace the loop: hardware sweeps take far longer than a frame, but demo
                // sweeps are instantaneous and would otherwise burn CPU and battery
                // re-rendering the UI ~100 times per second.
                val sweepMs = System.currentTimeMillis() - sweepStartMs
                delay((FRAME_DELAY_MS - sweepMs).coerceAtLeast(MIN_SWEEP_GAP_MS))
            }
        }
    }

    fun stopScanning() {
        _state.update { it.copy(isScanning = false) }
        scanJob?.cancel()
        scanJob = null
    }

    // =========================================================================
    // Configuration helpers
    // =========================================================================

    // NOTE: every mutation below goes through _state.update { }. The scan loop publishes
    // frames from a background thread, so a read-copy-assign on the state flow's value can
    // silently overwrite a change made in between (a saved frequency, a new marker…).

    fun updateFrequencyRange(range: FrequencyRange) {
        // Hardware retuning is handled by the next iteration of the sweep loop.
        _state.update {
            it.copy(
                scanConfig        = it.scanConfig.copy(frequencyRange = range),
                currentSpectrum   = null,
                maxHoldSpectrum   = null,
                waterfallHistory  = emptyList(),
                autoDetectedPeaks = emptyList(),
                markers           = emptyList(),
            )
        }
        averager.reset()
    }

    fun updateGainMode(mode: GainMode) {
        _state.update { it.copy(scanConfig = it.scanConfig.copy(gainMode = mode)) }
        if (!_state.value.isDemoMode) {
            viewModelScope.launch(Dispatchers.IO) {
                hwMutex.withLock {
                    if (sdrSource.isOpen) sdrSource.setGain(mode, _state.value.scanConfig.manualGainDb)
                }
            }
        }
    }

    fun updateScanMode(mode: ScanMode) {
        _state.update { it.copy(scanConfig = it.scanConfig.copy(scanMode = mode)) }
    }

    fun toggleMaxHold() {
        var enabled = false
        _state.update { cur ->
            enabled = !cur.scanConfig.maxHoldEnabled
            cur.copy(
                scanConfig      = cur.scanConfig.copy(maxHoldEnabled = enabled),
                maxHoldSpectrum = if (enabled) cur.maxHoldSpectrum else null,
            )
        }
        // Start from a clean trace next time it is enabled instead of resurrecting
        // maxima recorded before it was switched off.
        if (!enabled) averager.resetMaxHold()
    }

    fun resetMaxHold() {
        averager.resetMaxHold()
        _state.update { it.copy(maxHoldSpectrum = null) }
    }

    fun updateActiveMarker(normalizedX: Float?) {
        _state.update { it.copy(activeMarkerX = normalizedX) }
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

        _state.update { cur ->
            // max+1 (not size+1): ids must stay unique after a marker was removed,
            // otherwise editing/removing one marker would also hit its twin.
            val markerIndex = (cur.markers.maxOfOrNull { it.id } ?: 0) + 1
            val color       = MarkerColor.entries[(markerIndex - 1) % MarkerColor.entries.size]
            cur.copy(
                markers = cur.markers + Marker(
                    id           = markerIndex,
                    frequencyHz  = freqHz.toDouble(),
                    amplitudeDbm = ampDbm,
                    label        = "M$markerIndex",
                    color        = color,
                ),
            )
        }
    }

    fun clearMarkers() {
        _state.update { it.copy(markers = emptyList(), activeMarkerX = null) }
    }

    fun updateNumBins(numBins: Int) {
        _state.update {
            it.copy(
                scanConfig = it.scanConfig.copy(numBins = numBins),
                currentSpectrum = null,
                maxHoldSpectrum = null,
                // Rows of another width cannot be shown on the same waterfall
                waterfallHistory = emptyList(),
                autoDetectedPeaks = emptyList(),
            )
        }
        averager.reset()
    }

    fun updateMarkerColor(markerId: Int, color: MarkerColor) {
        _state.update { cur ->
            cur.copy(markers = cur.markers.map { if (it.id == markerId) it.copy(color = color) else it })
        }
    }

    fun updateMarkerLabel(markerId: Int, newLabel: String) {
        _state.update { cur ->
            cur.copy(markers = cur.markers.map { if (it.id == markerId) it.copy(label = newLabel) else it })
        }
    }

    fun removeMarker(markerId: Int) {
        _state.update { cur -> cur.copy(markers = cur.markers.filter { it.id != markerId }) }
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

        _state.update { cur ->
            val markerIndex = (cur.markers.maxOfOrNull { it.id } ?: 0) + 1
            val finalColor = color ?: MarkerColor.entries[(markerIndex - 1) % MarkerColor.entries.size]
            cur.copy(
                markers = cur.markers + Marker(
                    id = markerIndex,
                    frequencyHz = freqHz.toDouble(),
                    amplitudeDbm = ampDbm,
                    label = if (label.isNotBlank()) label else "M$markerIndex",
                    color = finalColor,
                )
            )
        }
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
        _state.update { it.copy(hwError = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
        sdrSource.close()
    }

    companion object {
        private const val TAG                   = "WatchoutRF"
        private const val FRAME_DELAY_MS        = 33L   // ~30 sweeps/s upper bound (demo mode)
        private const val MIN_SWEEP_GAP_MS      = 5L
        private const val MAX_WATERFALL_HISTORY = 100
        private const val PEAK_DETECT_INTERVAL  = 10
    }
}
