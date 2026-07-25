# WatchoutRF — Documentación Técnica y de Usuario

> Versión 1.0 · Desktop (macOS) y Android  
> Hardware compatible: **Nooelec NESDR SMART** (RTL2832U + R820T2)

---

## Tabla de Contenidos

1. [El Hardware: Nooelec NESDR SMART](#1-el-hardware-nooelec-nesdr-smart)
2. [Cómo funciona un SDR — IQ Sampling](#2-cómo-funciona-un-sdr--iq-sampling)
3. [Del IQ a la gráfica — FFT y Espectro](#3-del-iq-a-la-gráfica--fft-y-espectro)
4. [Pipeline DSP dentro de WatchoutRF](#4-pipeline-dsp-dentro-de-watchoutrf)
5. [Guía de la App Desktop (macOS)](#5-guía-de-la-app-desktop-macos)
6. [Guía de la App Android](#6-guía-de-la-app-android)
7. [Rangos de Frecuencia y Presets](#7-rangos-de-frecuencia-y-presets)
8. [Glosario](#8-glosario)

---

## 1. El Hardware: Nooelec NESDR SMART

### ¿Qué es el NESDR SMART?

El **Nooelec NESDR SMART** es un receptor de Software-Defined Radio (SDR) de bajo costo que se conecta vía USB. Físicamente es un dongle similar a una memoria USB, pero en su interior contiene dos chips fundamentales:

| Chip | Función | Descripción |
|------|---------|-------------|
| **RTL2832U** | Demodulator / ADC | Convierte la señal analógica del tuner en bytes digitales y los envía al host por USB |
| **R820T2** | Tuner RF | Sintoniza la antena a la frecuencia deseada y hace la primera conversión de frecuencia |

### Especificaciones clave

| Parámetro | Valor |
|-----------|-------|
| Rango de frecuencia | **25 MHz – 1766 MHz** |
| Tasa de muestreo | 2.048 MS/s (megamuestras por segundo) |
| Ancho de banda instantáneo | ~2 MHz |
| Resolución ADC | 8 bits |
| Conector de antena | SMA hembra |
| Interfaz | USB 2.0 (High Speed) |
| Oscilador | TCXO de alta estabilidad (±0.5 ppm) |

### Flujo de señal interno

```
Antena
  │
  ▼
R820T2 Tuner (LNA + Mixer + IF Filter)
  │  Convierte la señal RF a frecuencia intermedia (IF)
  ▼
RTL2832U ADC (Analog-to-Digital Converter)
  │  Muestrea la IF a 28.8 MHz, luego decimación digital
  ▼
USB Bulk Transfer → Host (Android / macOS)
  │  Envía bloques de bytes IQ crudos
  ▼
WatchoutRF (DSP en software)
```

### Ganancia

El R820T2 tiene un amplificador de bajo ruido (LNA) configurable:

- **Modo Auto:** El chip ajusta la ganancia automáticamente para minimizar saturación
- **Modo Manual:** Valores en décimas de dB (e.g., 400 = 40.0 dB)

> **Tip:** En entornos con señales fuertes (cerca de transmisores), baja la ganancia para evitar saturación (clipping). En ambientes débiles, sube la ganancia pero ten cuidado con el ruido.

---

## 2. Cómo funciona un SDR — IQ Sampling

### Muestreo Cuadratura (IQ)

El RTL-SDR no captura directamente la señal de radio — en cambio captura dos componentes:

- **I (In-phase):** La componente real de la señal
- **Q (Quadrature):** La componente imaginaria, desfasada 90°

Juntos forman un número complejo:

$$s(t) = I(t) + j \cdot Q(t)$$

Esto es equivalente a representar la señal en el plano complejo. La ventaja es que con una sola captura de 2 MS/s se obtienen **2 MHz de ancho de banda**, con información completa de amplitud y fase.

### Relación con la frecuencia real

El tuner R820T2 convierte la señal RF a una frecuencia central llamada **frecuencia de centro** ($f_c$). Los datos IQ capturados representan el espectro simétrico alrededor de $f_c$:

$$f_{\text{real}} = f_c + f_{\text{baseband}}$$

donde $f_{\text{baseband}} \in [-1024\ \text{kHz},\ +1024\ \text{kHz}]$ para 2048 bins a 2 MS/s.

### Sweeping (barrido de frecuencia)

Como el ancho de banda instantáneo es solo ~2 MHz, para cubrir rangos más amplios (por ejemplo, IEM: 470–698 MHz = 228 MHz de span), la app hace un **barrido secuencial** (sweep):

```
[470-472 MHz] → [472-474 MHz] → [474-476 MHz] → ... → [696-698 MHz]
     cada "hop" = una captura de 2 MHz de ancho de banda
```

El número total de hops para el rango IEM es ~114. El sistema los concatena para formar el espectro completo.

---

## 3. Del IQ a la gráfica — FFT y Espectro

### La Transformada Rápida de Fourier (FFT)

Para cada bloque de muestras IQ, WatchoutRF aplica la **FFT** (Fast Fourier Transform). La FFT descompone la señal del tiempo al dominio de la frecuencia:

$$X[k] = \sum_{n=0}^{N-1} x[n] \cdot e^{-j 2\pi kn / N}$$

donde:
- $x[n]$: Muestra IQ compleja número $n$
- $N$: Número de bins (512, 1024, o 2048 — seleccionable en la app)
- $X[k]$: Magnitud compleja en el bin de frecuencia $k$
- $k$: Índice de bin ($0$ a $N-1$)

### De coeficientes FFT a dBm

El resultado de la FFT es un número complejo por bin. Para obtener la magnitud:

$$|X[k]| = \sqrt{\text{Re}(X[k])^2 + \text{Im}(X[k])^2}$$

Luego se convierte a **dBm** (decibelios referenciados a 1 mW):

$$P_{\text{dBm}}[k] = 20 \cdot \log_{10}(|X[k]|) + C$$

donde $C$ es una constante de calibración dependiente del sistema.

> **¿Por qué dBm?** Porque las señales de RF abarcan muchos órdenes de magnitud de potencia. La escala logarítmica en dB permite ver simultáneamente señales que difieren en millones de veces en potencia (e.g., −20 dBm vs −80 dBm).

### Resolución de frecuencia

Cada bin representa un ancho de banda de:

$$\Delta f = \frac{f_{\text{sample}}}{N} = \frac{2{,}048{,}000\ \text{Hz}}{N}$$

| Bins (N) | Resolución ($\Delta f$) | Uso recomendado |
|----------|------------------------|-----------------|
| 512 | **4.0 kHz / bin** | Rápido, menos resolución |
| 1024 | **2.0 kHz / bin** | Balance (default) |
| 2048 | **1.0 kHz / bin** | Máxima resolución, más lento |

> **Tradeoff:** Más bins = más resolución de frecuencia, pero más tiempo de cómputo por sweep y menor velocidad de actualización.

### Funciones de Ventana (Windowing)

Un problema práctico de la FFT es el **spectral leakage** (derrame espectral): si la señal no es perfectamente periódica en la ventana de captura, la energía "se derrama" hacia bins vecinos, ensuciando la gráfica.

La solución es multiplicar las muestras por una **función de ventana** antes de la FFT:

$$x_w[n] = x[n] \cdot w[n]$$

WatchoutRF soporta las siguientes ventanas (implementadas en código nativo):

| Ventana | Característica | Mejor para |
|---------|---------------|------------|
| **Rectangular** | Sin ponderación (todas las muestras = 1) | Señales estrictamente periódicas |
| **Hamming** | Reduce leakage con buena resolución (default) | Uso general, IEM/wireless |
| **Hann** | Similar a Hamming, lóbulo lateral más suave | Audio RF |
| **Blackman** | Máxima supresión de lóbulos laterales | Señales débiles junto a fuertes |
| **Flat-Top** | Máxima precisión de amplitud | Medición de potencia absoluta |

---

## 4. Pipeline DSP dentro de WatchoutRF

### Vista general del pipeline

```
Hardware IQ     KissFFT (nativo)     Promediado EMA      Max-Hold
  (USB)     ──►  FFT → dBm      ──►   exponencial   ──►  paralelo   ──►  Pantalla
                                      (α = 0.5)
                                             │
                                             ▼
                                      Peak Detector
                                      (cada 10 frames)
```

### 4.1 Promediado Exponencial (EMA)

Para suavizar el ruido entre frames consecutivos, se aplica un **Exponential Moving Average**:

$$S_{\text{avg}}[k] = \alpha \cdot S_{\text{avg,prev}}[k] + (1 - \alpha) \cdot S_{\text{new}}[k]$$

donde $\alpha = 0.5$ (configurable en código como `averagingFactor`).

- $\alpha \to 0$: Sin suavizado — cada frame se muestra raw
- $\alpha \to 1$: Suavizado máximo — la gráfica cambia muy lentamente

> **En código:** `SpectrumAverager.average(newData, alpha)` — clase en `data/dsp/SpectrumAverager.kt`

### 4.2 Max-Hold

El Max-Hold mantiene el **valor máximo histórico** de cada bin:

$$S_{\text{hold}}[k] = \max(S_{\text{hold,prev}}[k],\ S_{\text{new}}[k])$$

Esta traza se muestra en **ámbar** superpuesta a la traza activa en **cyan**. Es ideal para detectar señales intermitentes o transitorias que podrían perderse en tiempo real.

> **En código:** `SpectrumAverager.updateMaxHold(newData)` — mismo archivo.

### 4.3 Promediado en dominio lineal

Cuando el hardware hace múltiples lecturas por sweep (`numReads = 4`), el promediado se hace en **dominio lineal** (milliwatts), no en dB:

$$\bar{P}_{\text{lineal}}[k] = \frac{1}{N} \sum_{i=1}^{N} 10^{P_{\text{dBm},i}[k] / 10}$$

$$P_{\text{avg,dBm}}[k] = 10 \cdot \log_{10}(\bar{P}_{\text{lineal}}[k])$$

> **¿Por qué?** Promediar en dB subestimaría sistemáticamente las señales con variación rápida (como frecuencia modulada). El promediado en potencia lineal preserva los picos correctamente.

### 4.4 Detección automática de picos

El `PeakDetector` implementa un algoritmo simple pero efectivo:

**Paso 1 — Maxima locales:**
Un bin $k$ es candidato a pico si:
$$S[k] > S[k-1] \quad \text{y} \quad S[k] > S[k+1] \quad \text{y} \quad S[k] \geq S_{\text{min}}$$

donde $S_{\text{min}} = \text{referenceLevel} - \text{dynamicRange} + 10\ \text{dBm}$

**Paso 2 — Ordenar por amplitud:** Los candidatos se ordenan de mayor a menor.

**Paso 3 — Filtro de distancia mínima:** Se descarta cualquier pico que esté a menos de $D_{\text{min}}$ bins de uno ya seleccionado:
$$D_{\text{min}} = \frac{N_{\text{bins}}}{50}$$

Por ejemplo, con 1024 bins: $D_{\text{min}} = 20$ bins = 40 kHz mínimo de separación entre picos.

**Paso 4 — Top-8:** Se retienen máximo 8 picos. Se ejecuta cada 10 frames para no sobrecargar el CPU.

> **En código:** `PeakDetector.detectPeaks()` — `data/dsp/PeakDetector.kt`

### 4.5 Modo Demo (sin dongle)

Cuando no hay dongle conectado, la app usa `SyntheticSignalGenerator` que genera un espectro sintético con:

- Piso de ruido: −95 dBm con variación Gaussiana ($\sigma = 3\ \text{dB}$)
- Señales modeladas como curvas Gaussianas en escala de potencia lineal:
  $$S_{\text{señal}}[k] = S_{\text{peak}} \cdot e^{-\frac{(f_k - f_c)^2}{2\sigma_f^2}}$$
- Una componente oscilante en el tiempo para dar vida a la animación: $+1.5 \cdot \sin(t \cdot 0.5 + k \cdot 0.01)\ \text{dB}$

---

## 5. Guía de la App Desktop (macOS)

### Pantalla principal

```
┌─────────────────────────────────────────────────────────┐
│  [Panel Lateral]  │        Área de Espectro             │
│                   │  ┌─────────────────────────────┐   │
│  • Rangos         │  │  Gráfica FFT (cyan)          │   │
│  • Max Hold       │  │  Max Hold (ámbar)            │   │
│  • Marcadores     │  │  Marcadores con etiquetas    │   │
│  • Resolución     │  └─────────────────────────────┘   │
│                   │  [Regla de frecuencia]               │
│                   │  ┌─────────────────────────────┐   │
│                   │  │  Waterfall (histórico)       │   │
│                   │  └─────────────────────────────┘   │
│  • Frecuencias    ├─────────────────────────────────────┤
│    (abre diálogo) │   [Barra inferior: peaks auto]      │
└─────────────────────────────────────────────────────────┘
```

### Panel lateral — Controles

#### Selector de Rango de Frecuencia
Chips horizontales con los presets disponibles. Al seleccionar uno, el sweep se reinicia y se borran los marcadores actuales.

#### Max Hold (switch)
- **ON (verde):** La traza ámbar aparece y va registrando el máximo histórico de cada bin
- **Reset Hold:** Borra la traza ámbar y reinicia la acumulación

#### Resolución (512 / 1024 / 2048)
Cambia el número de bins FFT. Afecta directamente la resolución de frecuencia y la velocidad de barrido.

#### Panel de Marcadores Manuales
- Los marcadores se agregan haciendo clic en la gráfica de espectro
- Cada marcador aparece listado con:
  - Su **etiqueta** (editable con el ícono ✏️)
  - Su **frecuencia** en MHz
  - Una **paleta de colores** (8 opciones: cyan, amarillo, magenta, verde, rojo, azul, naranja, blanco)
- El marcador se dibuja en la gráfica como una línea vertical + diamante en la amplitud + etiqueta en la parte superior

#### Frecuencias Nombradas (botón 📡)
Abre un diálogo donde puedes guardar frecuencias de interés con nombre y color personalizado. Estas se muestran como líneas verticales naranjas en la gráfica y persisten entre sesiones.

### Área de espectro

| Elemento | Color | Descripción |
|----------|-------|-------------|
| Traza actual | **Cyan** | FFT del sweep más reciente, suavizada con EMA |
| Max Hold | **Ámbar** | Máximo histórico por bin |
| Marcadores manuales | **Color elegido** | Marcadores colocados por el usuario |
| Picos automáticos | **Varios colores** | Top-8 picos detectados automáticamente |
| Frecuencias nombradas | **Naranja** | Líneas de referencia guardadas |
| Crosshair activo | **Naranja** | Cursor de drag mostrando freq + dBm |

### Barra inferior
Muestra exclusivamente los **picos automáticos detectados** (hasta 8), con su etiqueta, frecuencia y amplitud en dBm.

### Waterfall
El waterfall es un historial visual de 100 frames. El eje horizontal es frecuencia, el eje vertical es tiempo (el presente está arriba, el pasado abajo). El color indica potencia:

| Color | Potencia relativa |
|-------|------------------|
| Azul oscuro | Baja (ruido) |
| Azul claro → Cyan | Media-baja |
| Cyan → Amarillo | Media-alta |
| Naranja → Rojo | Alta (señal fuerte) |

---

## 6. Guía de la App Android

### Pantalla principal

La app Android tiene la misma arquitectura que el desktop, adaptada para pantalla táctil:

```
┌──────────────────────────────────────────────────────────┐
│ ← WatchoutRF  [DEMO] DBG:... RES: 512 1024 2048  freq─● │  ← TopBar
├──────────────────┬───────────────────────────────────────┤
│  Panel           │  Escala dBm │  Espectro FFT            │
│  Lateral         │             │  (cyan + ámbar Max Hold)  │
│  (180dp)         │             │                          │
│                  │             │  Marcadores + Overlays   │
│  • Rangos        │             │                          │
│                  ├─────────────┴──────────────────────────┤
│  • Modos         │      Regla de frecuencia               │
│  • Max Hold      ├───────────────────────────────────────┤
│                  │         Waterfall                      │
│  ─────────────   ├───────────────────────────────────────┤
│  [marcadores     │                                        │
│   scrollables]   │                                        │
│                  │                                        │
│  ─────────────   │                                        │
│  📡 Frequencies  │                                        │
│  [STOP/SCAN]     │                                        │
│  [Reset Hold]    │                                        │
│  [Clear Markers] │                                        │
├──────────────────┴───────────────────────────────────────┤
│  [cursor: freq | dBm]  │  Peak1 ● label: freq  dBm ...   │  ← BottomBar
└──────────────────────────────────────────────────────────┘
```

### TopBar

| Elemento | Descripción |
|----------|-------------|
| **← Atrás** | Navega a la pantalla anterior |
| **DEMO MODE** (ámbar) / **DEVICE NAME** (verde) | Estado de conexión del dongle |
| **DBG:** | Estado interno del sistema (debug) |
| **RES: 512 / 1024 / 2048** | Selector de resolución FFT — toca el número para cambiar |
| **freq range** | Rango activo en MHz |
| **●** verde/rojo | Indicador de escaneo activo |

### Panel lateral

#### Selector de rangos (chips horizontales)
Desliza horizontalmente para ver todos los presets. El chip activo se resalta en verde.

#### Modos de escaneo
- **Continuous:** Sweep continuo en bucle
- **Single Sweep:** Un solo barrido y para
- **Max Hold:** (switch) activa la traza ámbar

#### Panel de Marcadores (scrollable)
Al añadir marcadores tocando la gráfica, aparecen en esta lista. Para cada marcador:

- **Nombre** (en su color actual) + ícono ✏️ para editar
  - Al tocar ✏️ aparece un campo de texto con botón ✓ para confirmar
- **Frecuencia** a la derecha
- **Fila de colores** (8 círculos de colores) — toca el que quieras, se resalta con borde blanco

#### Botones de acción (abajo del panel)
| Botón | Acción |
|-------|--------|
| **📡 Frequencies** | Abre el gestor de frecuencias nombradas |
| **SCAN / STOP** | Inicia o detiene el barrido |
| **Reset Hold** | Borra la traza Max Hold (solo visible cuando está activo) |
| **Clear Markers** | Borra todos los marcadores manuales (solo visible cuando hay marcadores) |

### Gestos en la gráfica de espectro

| Gesto | Acción |
|-------|--------|
| **Toque simple** | Coloca el crosshair naranja en esa frecuencia |
| **Arrastrar** | Mueve el crosshair en tiempo real mostrando freq + dBm |
| **Levantar el dedo** | El crosshair permanece en la última posición |

> **Nota:** Para agregar un marcador permanente, mantén el crosshair en la posición deseada — el marcador se crea automáticamente al hacer tap.

### Gestor de Frecuencias Nombradas

Abre una hoja modal (bottom sheet) donde puedes:

1. Ver todas las frecuencias guardadas (nombre, MHz, punto de color, ícono ojo para visibilidad)
2. Agregar nuevas frecuencias con nombre y MHz
3. Togglear visibilidad (la línea en el espectro aparece/desaparece)
4. Eliminar frecuencias

Las frecuencias se guardan en una base de datos **Room (SQLite)** local y persisten entre sesiones.

### Diferencias clave Desktop vs Android

| Feature | Desktop | Android |
|---------|---------|---------|
| Conexión dongle | USB directo / nativo | USB-OTG con permisos del sistema |
| Panel de control | Sidebar fijo | Sidebar 180dp con scroll |
| Resolución | En el panel lateral | En el TopBar |
| Marcadores | Panel scrollable en sidebar | Panel scrollable en sidebar |
| Edición de marcador | ícono lápiz + tecla Enter/Esc | ícono lápiz + botón ✓ |
| Persistencia de frecuencias | Base de datos local | Room (SQLite) |
| Modo demo | Generador sintético | Generador sintético (mismo algoritmo) |

---

## 7. Rangos de Frecuencia y Presets

| Preset | Inicio | Fin | Span | Uso principal |
|--------|--------|-----|------|---------------|
| **FM Broadcast** | 88 MHz | 108 MHz | 20 MHz | Radio FM comercial |
| **VHF TV** | 174 MHz | 230 MHz | 56 MHz | Televisión VHF, micrófonos VHF |
| **IEM/Wireless Mic** ⭐ | 470 MHz | 698 MHz | 228 MHz | **In-ear monitors, micrófonos inalámbricos** |
| **UHF TV** | 470 MHz | 862 MHz | 392 MHz | Televisión digital terrestre |
| **Cellular 700** | 698 MHz | 806 MHz | 108 MHz | LTE Banda 12/17 |
| **ISM 900** | 902 MHz | 928 MHz | 26 MHz | IoT, ZigBee, algunos walkie-talkies |
| **Full Range** | 24 MHz | 1766 MHz | 1742 MHz | Exploración completa (lento) |

⭐ = Rango predeterminado al iniciar la app

### Rango IEM — ¿Por qué 470-698 MHz?

Este rango es el espectro de **televisión digital libre** en muchos países, que coincide con las frecuencias asignadas legalmente para sistemas de audio inalámbrico profesional. En él operan:

- Micrófonos inalámbricos (200 kHz de ancho de banda típico)
- In-Ear Monitors / IEM (200 kHz)
- Canales de televisión UHF (6 MHz cada uno, actúan como "obstáculos")
- Intermodulación entre sistemas adyacentes

---

## 8. Glosario

| Término | Definición |
|---------|-----------|
| **ADC** | Analog-to-Digital Converter. Convierte voltaje analógico en números digitales |
| **Bin** | Un canal de frecuencia individual en la salida de la FFT |
| **dBm** | Decibelios referenciados a 1 miliWatt. $0\ \text{dBm} = 1\ \text{mW}$ |
| **EMA** | Exponential Moving Average. Promedio exponencial que suaviza el ruido |
| **FFT** | Fast Fourier Transform. Algoritmo que convierte señal en el tiempo a frecuencias |
| **IEM** | In-Ear Monitor. Sistema de monitoreo de audio inalámbrico in-ear |
| **IF** | Intermediate Frequency. Frecuencia intermedia de conversión del tuner |
| **IQ** | In-phase / Quadrature. Par de componentes ortogonales que describen una señal |
| **KissFFT** | Librería C de FFT usada en el backend nativo de la app Android |
| **LNA** | Low Noise Amplifier. Amplificador de bajo ruido del tuner |
| **Max-Hold** | Modo que registra el valor máximo visto en cada bin de frecuencia |
| **MS/s** | Megamuestras por segundo. Tasa de muestreo del ADC |
| **Peak** | Pico: bin con mayor amplitud que sus vecinos; indica presencia de señal |
| **ppm** | Partes por millón. Medida de precisión del oscilador (el NESDR SMART usa ±0.5 ppm) |
| **R820T2** | Chip tuner de Rafael Micro. Sintoniza 25–1766 MHz |
| **RTL2832U** | Chip demodulador de Realtek. Fue diseñado para DVB-T pero se usa como ADC SDR |
| **SDR** | Software-Defined Radio. Radio cuyas funciones se implementan en software |
| **Spectral Leakage** | Derrame espectral: energía de una señal que contamina bins vecinos en la FFT |
| **Sweep** | Barrido: el proceso de sintonizar múltiples frecuencias secuencialmente para cubrir un rango |
| **TCXO** | Temperature-Compensated Crystal Oscillator. Oscilador de alta precisión |
| **Waterfall** | Historial visual del espectro a lo largo del tiempo |
| **Windowing** | Aplicar una función de ventana a las muestras antes de la FFT para reducir leakage |

---

*Documentación generada el 2026-07-25 · WatchoutRF v1.0*
