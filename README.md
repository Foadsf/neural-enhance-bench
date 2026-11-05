# NeuralEnhance Bench: FLOSS Tool for CNN & VLM Image Enhancement Testing

An open-source (Apache 2.0), standalone Android benchmarking and testing application for evaluating on-device Convolutional Neural Network (CNN) and Vision-Language Model (VLM) image enhancement pipelines.

Optimized specifically for mobile System-on-Chip (SoC) architectures with strict thermal and memory budgets, calibrated against the **Samsung Galaxy Note10 (SM-N970F/DS: Exynos 9825 octa-core CPU, Mali-G76 MP12 GPU, 8 GB LPDDR4X RAM, Android 10-12)**.

---

## Key Features

### 1. Memory-Safe Tiled Inference Engine
- **Hann / Cosine Blending**: Divides images into overlapping rectangular tiles (`64px`, `128px`, `256px`, `512px`) with configurable overlap (`0px` to `32px`).
- Overlapping tile borders are blended using separable 2D raised cosine (Hann) windowing weights:
  $$\omega(x) = \sin^2\left(\frac{\pi (x + 0.5)}{2 \cdot \text{overlap}}\right)$$
  eliminating block-boundary discontinuity and tiling grid artifacts.
- **Direct Pixel Streaming**: Operates directly on packed integer ARGB buffers without allocating intermediate `Bitmap` objects per tile, preventing garbage collection stutter and out-of-memory (OOM) aborts.

### 2. Enhancement Architectures Implemented
- **ESPCN (Efficient Sub-Pixel Convolutional Network)**: Real-time 2x super-resolution with sub-pixel convolution shuffle.
- **RFDN (Residual Feature Distillation Network)**: 4x lightweight super-resolution with residual feature distillation.
- **MobileIE**: Reparameterized low-light and underexposure restoration network.
- **NAFNet (Nonlinear Activation Free Network)**: SimpleGate-based image restoration and denoising baseline.
- **Bilateral Filter**: Classical spatial and range-domain reference baseline.

### 3. Photogrammetry / Structure-from-Motion (SfM) Geometric Fidelity
- Evaluates whether neural enhancement preserves corner repeatability and geometric stability required for multi-view stereo and 3D reconstruction (COLMAP, Meshroom, OpenMVG).
- Built-in **Harris Corner Detector** with $3\times3$ Sobel gradient computation, structure tensor integration, and local non-maximum suppression.
- Computes **Spatial Keypoint Retention Rate (%)**: fraction of original corner features whose coordinates persist within a $\pm 6\text{px}$ radius in the enhanced image.

### 4. Objective Quality & Telemetry Suite
- **Quality Metrics**: PSNR (dB), SSIM (8x8 windowed), SNR (dB), and MSE.
- **Hardware Telemetry**: Execution Latency (ms), Throughput (Megapixels/second), and Peak Heap Memory Footprint (MB).
- **Interactive Split Visualizer**: Draggable vertical comparison slider with pinch-to-zoom (up to 8x), panning, and toggleable cyan/red keypoint dot overlay.
- **Export Facility**: Generates timestamped Markdown benchmark reports with complete hardware and metric breakdowns.

---

## Pre-Built Release APK

The signed, zipaligned, production APK is ready at the root of this repository:

- **File**: `NeuralEnhanceBench.apk`
- **Package**: `org.floss.enhancetool`
- **Min SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14 (API 34)
- **Signatures**: APK Signature Scheme v2 & v3 (verified)

### Installation via ADB
```bash
adb install -r NeuralEnhanceBench.apk
```

---

## Building from Source

### Option A: Direct Build Script (Recommended, Zero Dependencies)
Run the automated PowerShell build script. It utilizes the local Android Command Line Tools (`aapt2`, `javac`, `d8`, `zipalign`, `apksigner`) with zero external network downloads:
```powershell
powershell -ExecutionPolicy Bypass -File .\build_apk.ps1
```

### Option B: Gradle / Android Studio
Open the project directory in Android Studio or build via standard Gradle:
```bash
gradle assembleRelease
```

---

## Project Structure
```
android_CNN_VLM/
├── LICENSE                                    # Apache 2.0 License
├── README.md                                  # Documentation and architecture guide
├── NeuralEnhanceBench.apk                     # Compiled, aligned, signed release APK
├── build_apk.ps1                              # Deterministic standalone build pipeline
├── build.gradle                               # Root Gradle build script
├── settings.gradle                            # Gradle project settings
├── gradle.properties                          # Build JVM configuration
└── app/
    ├── build.gradle                           # App module configuration
    └── src/main/
        ├── AndroidManifest.xml                # App manifest with permissions & largeHeap
        ├── res/
        │   ├── drawable/                      # Vector launcher icon and slider thumb
        │   ├── layout/activity_main.xml       # Split view, spinners, metric cards
        │   └── values/                        # colors.xml, strings.xml, styles.xml
        └── java/org/floss/enhancetool/
            ├── MainActivity.java              # Activity orchestration & telemetry
            ├── model/
            │   ├── EnhancementModel.java      # Model interface contract
            │   ├── EspcnSuperResolution.java  # ESPCN 2x SR model
            │   ├── RfdnSuperResolution.java   # RFDN 4x SR model
            │   ├── MobileIeDenoiser.java      # MobileIE low-light restoration
            │   ├── NafNetRestoration.java     # NAFNet SimpleGate restoration
            │   └── BilateralBaseline.java     # Classical bilateral baseline
            ├── pipeline/
            │   ├── TiledInferenceEngine.java  # Hann-windowed tiled engine
            │   ├── PreprocessingStage.java    # Synthetic degradation generator
            │   ├── PostprocessingStage.java   # Dynamic range & sharpening filters
            │   ├── InferenceCallback.java     # Progress and memory telemetry hook
            │   └── InferenceResult.java       # Container for benchmark metrics
            ├── metrics/
            │   ├── QualityMetrics.java        # PSNR, SSIM, SNR, MSE calculators
            │   └── SfmKeypointDetector.java   # Harris corner detector & retention
            └── ui/
                └── SplitComparisonView.java   # Interactive split-screen visualizer
```

---

## License

Distributed under the **Apache License, Version 2.0**. See `LICENSE` for details.
