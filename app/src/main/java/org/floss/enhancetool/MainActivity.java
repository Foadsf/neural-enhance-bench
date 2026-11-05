package org.floss.enhancetool;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.floss.enhancetool.model.BilateralBaseline;
import org.floss.enhancetool.model.EnhancementModel;
import org.floss.enhancetool.model.EspcnSuperResolution;
import org.floss.enhancetool.model.MobileIeDenoiser;
import org.floss.enhancetool.model.NafNetRestoration;
import org.floss.enhancetool.model.RfdnSuperResolution;
import org.floss.enhancetool.pipeline.InferenceCallback;
import org.floss.enhancetool.pipeline.InferenceResult;
import org.floss.enhancetool.pipeline.PreprocessingStage;
import org.floss.enhancetool.pipeline.TiledInferenceEngine;
import org.floss.enhancetool.ui.SplitComparisonView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main Activity for FLOSS CNN and VLM Image Enhancement Testing.
 * Orchestrates models, tiled inference engine, real-time metrics telemetry,
 * interactive split-screen comparison, and benchmarking export.
 */
public class MainActivity extends Activity {

    private static final int REQUEST_PICK_IMAGE = 1001;

    private Spinner spinnerModel;
    private Spinner spinnerTileSize;
    private Spinner spinnerOverlap;
    private CheckBox chkSfmMode;

    private SplitComparisonView comparisonView;
    private ProgressBar progressBar;
    private TextView tvStatus;

    private Button btnLoadImage;
    private Button btnSynthetic;
    private Button btnRun;
    private Button btnToggleOverlay;
    private Button btnSaveEnhanced;
    private Button btnExportReport;

    private TextView tvMetricPsnr;
    private TextView tvMetricSsim;
    private TextView tvMetricSnr;
    private TextView tvMetricKeypoints;
    private TextView tvMetricLatency;
    private TextView tvMetricThroughput;
    private TextView tvMetricRam;

    private Bitmap sourceBitmap;
    private InferenceResult lastResult;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EnhancementModel[] availableModels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupSpinners();
        setupListeners();

        // Default initial test target: 512x512 Siemens resolution target
        generateTestPattern();
    }

    private void initViews() {
        spinnerModel = findViewById(R.id.spinner_model);
        spinnerTileSize = findViewById(R.id.spinner_tile_size);
        spinnerOverlap = findViewById(R.id.spinner_overlap);
        chkSfmMode = findViewById(R.id.chk_sfm_mode);

        comparisonView = findViewById(R.id.comparison_view);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);

        btnLoadImage = findViewById(R.id.btn_load_image);
        btnSynthetic = findViewById(R.id.btn_synthetic);
        btnRun = findViewById(R.id.btn_run);
        btnToggleOverlay = findViewById(R.id.btn_toggle_overlay);
        btnSaveEnhanced = findViewById(R.id.btn_save_enhanced);
        btnExportReport = findViewById(R.id.btn_export_report);

        tvMetricPsnr = findViewById(R.id.tv_metric_psnr);
        tvMetricSsim = findViewById(R.id.tv_metric_ssim);
        tvMetricSnr = findViewById(R.id.tv_metric_snr);
        tvMetricKeypoints = findViewById(R.id.tv_metric_keypoints);
        tvMetricLatency = findViewById(R.id.tv_metric_latency);
        tvMetricThroughput = findViewById(R.id.tv_metric_throughput);
        tvMetricRam = findViewById(R.id.tv_metric_ram);
    }

    private void setupSpinners() {
        availableModels = new EnhancementModel[]{
                new EspcnSuperResolution(),
                new RfdnSuperResolution(),
                new MobileIeDenoiser(),
                new NafNetRestoration(),
                new BilateralBaseline()
        };

        String[] modelNames = new String[availableModels.length];
        for (int i = 0; i < availableModels.length; i++) {
            modelNames[i] = availableModels[i].getName();
        }

        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, modelNames);
        spinnerModel.setAdapter(modelAdapter);

        // Tile sizes
        String[] tileSizes = new String[]{"64 px", "128 px", "256 px", "512 px"};
        ArrayAdapter<String> tileAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, tileSizes);
        spinnerTileSize.setAdapter(tileAdapter);
        spinnerTileSize.setSelection(1); // Default: 128 px for optimal mobile throughput

        // Overlap pixels
        String[] overlaps = new String[]{"0 px", "8 px", "16 px", "32 px"};
        ArrayAdapter<String> overlapAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, overlaps);
        spinnerOverlap.setAdapter(overlapAdapter);
        spinnerOverlap.setSelection(2); // Default: 16 px
    }

    private void setupListeners() {
        btnLoadImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_PICK_IMAGE);
            }
        });

        btnSynthetic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateTestPattern();
            }
        });

        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runEnhancement();
            }
        });

        btnToggleOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean nextState = !comparisonView.isShowingKeypoints();
                comparisonView.setShowKeypoints(nextState);
                btnToggleOverlay.setText(nextState ? "Hide Keypoints" : "SfM Keypoints");
            }
        });

        btnSaveEnhanced.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveEnhancedImage();
            }
        });

        btnExportReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportBenchmarkReport();
            }
        });
    }

    private void generateTestPattern() {
        sourceBitmap = PreprocessingStage.generateSyntheticBenchmarkTarget(512, 512);
        comparisonView.setBitmaps(sourceBitmap, sourceBitmap);
        tvStatus.setText("Generated 512x512 Siemens Star & Calibration Target.");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(imageUri);
                    Bitmap loaded = BitmapFactory.decodeStream(inputStream);
                    if (inputStream != null) inputStream.close();

                    if (loaded != null) {
                        // Max dimension clamp to 1024 to maintain mobile thermal budget
                        int maxDim = Math.max(loaded.getWidth(), loaded.getHeight());
                        if (maxDim > 1024) {
                            float scale = 1024f / maxDim;
                            int targetW = Math.round(loaded.getWidth() * scale);
                            int targetH = Math.round(loaded.getHeight() * scale);
                            sourceBitmap = Bitmap.createScaledBitmap(loaded, targetW, targetH, true);
                            loaded.recycle();
                        } else {
                            sourceBitmap = loaded;
                        }
                        comparisonView.setBitmaps(sourceBitmap, sourceBitmap);
                        tvStatus.setText("Loaded " + sourceBitmap.getWidth() + "x" + sourceBitmap.getHeight() + " image.");
                        Toast.makeText(this, "Loaded " + sourceBitmap.getWidth() + "x" + sourceBitmap.getHeight(),
                                Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to load image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void runEnhancement() {
        if (sourceBitmap == null) {
            Toast.makeText(this, "Please load an image or generate a test pattern first", Toast.LENGTH_SHORT).show();
            return;
        }

        final EnhancementModel model = availableModels[spinnerModel.getSelectedItemPosition()];

        int[] tileSizes = {64, 128, 256, 512};
        final int tileSize = tileSizes[spinnerTileSize.getSelectedItemPosition()];

        int[] overlaps = {0, 8, 16, 32};
        final int overlap = overlaps[spinnerOverlap.getSelectedItemPosition()];

        setControlsEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        tvStatus.setText("Running " + model.getName() + " on " + sourceBitmap.getWidth() + "x" + sourceBitmap.getHeight() + "...");

        executor.execute(new Runnable() {
            @Override
            public void run() {
                TiledInferenceEngine engine = new TiledInferenceEngine(tileSize, overlap);

                final InferenceResult result = engine.execute(sourceBitmap, model, new InferenceCallback() {
                    @Override
                    public void onProgress(final int currentTile, final int totalTiles, final long memoryBytes) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                int percent = (int) ((currentTile / (float) totalTiles) * 100);
                                progressBar.setProgress(percent);
                                tvStatus.setText(String.format(Locale.US,
                                        "Tile %d/%d (%d%%) | Mem: %.1f MB",
                                        currentTile, totalTiles, percent, memoryBytes / (1024f * 1024f)));
                            }
                        });
                    }
                });

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        lastResult = result;
                        displayResult(result);
                        setControlsEnabled(true);
                        progressBar.setVisibility(View.GONE);
                        tvStatus.setText(String.format(Locale.US,
                                "Done! Latency: %d ms | Throughput: %.2f MP/s | Ret: %.1f%%",
                                result.latencyMs, result.throughputMpPerSec, result.keypointRetention * 100f));
                    }
                });
            }
        });
    }

    private void displayResult(InferenceResult res) {
        if (res == null) return;

        comparisonView.setBitmaps(res.originalBitmap, res.enhancedBitmap);
        comparisonView.setKeypoints(res.originalKeypoints, res.enhancedKeypoints);

        tvMetricLatency.setText(String.format(Locale.US, "Time: %d ms", res.latencyMs));
        tvMetricThroughput.setText(String.format(Locale.US, "Rate: %.2f MP/s", res.throughputMpPerSec));
        tvMetricRam.setText(String.format(Locale.US, "Peak RAM: %.1f MB", res.getPeakMemoryMb()));

        tvMetricPsnr.setText(String.format(Locale.US, "%.1f dB", res.psnr));
        tvMetricSsim.setText(String.format(Locale.US, "%.4f", res.ssim));
        tvMetricSnr.setText(String.format(Locale.US, "%.1f dB", res.snr));

        tvMetricKeypoints.setText(String.format(Locale.US, "%d (%.0f%%)",
                res.enhancedKeypoints.size(), res.keypointRetention * 100f));
    }

    private void setControlsEnabled(boolean enabled) {
        btnRun.setEnabled(enabled);
        btnLoadImage.setEnabled(enabled);
        btnSynthetic.setEnabled(enabled);
        spinnerModel.setEnabled(enabled);
        spinnerTileSize.setEnabled(enabled);
        spinnerOverlap.setEnabled(enabled);
    }

    private void saveEnhancedImage() {
        if (lastResult == null || lastResult.enhancedBitmap == null) {
            Toast.makeText(this, "No enhanced image available. Run test first.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File exportDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (exportDir == null) exportDir = getFilesDir();

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File outFile = new File(exportDir, "Enhanced_" + timeStamp + ".png");

            FileOutputStream fos = new FileOutputStream(outFile);
            lastResult.enhancedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            Toast.makeText(this, "Saved: " + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportBenchmarkReport() {
        if (lastResult == null) {
            Toast.makeText(this, "No benchmark run to export. Run enhancement first.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File exportDir = getExternalFilesDir(null);
            if (exportDir == null) exportDir = getFilesDir();

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File reportFile = new File(exportDir, "Benchmark_" + timeStamp + ".md");

            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(reportFile), StandardCharsets.UTF_8);

            writer.write("# FLOSS CNN & VLM Mobile Enhancement Benchmark Report\n\n");
            writer.write("- **Date**: " + new Date().toString() + "\n");
            writer.write("- **Model**: " + lastResult.modelName + "\n");
            writer.write("- **Tile Size**: " + lastResult.tileSize + " px\n");
            writer.write("- **Overlap**: " + lastResult.overlap + " px\n");
            writer.write("- **Input Resolution**: " + lastResult.originalBitmap.getWidth() + "x" + lastResult.originalBitmap.getHeight() + "\n");
            writer.write("- **Output Resolution**: " + lastResult.enhancedBitmap.getWidth() + "x" + lastResult.enhancedBitmap.getHeight() + "\n\n");

            writer.write("## 1. Hardware & Execution Telemetry\n");
            writer.write("| Metric | Measured Value |\n");
            writer.write("| :--- | :--- |\n");
            writer.write(String.format(Locale.US, "| Latency | %d ms |\n", lastResult.latencyMs));
            writer.write(String.format(Locale.US, "| Throughput | %.2f MP/s |\n", lastResult.throughputMpPerSec));
            writer.write(String.format(Locale.US, "| Peak Heap Footprint | %.2f MB |\n\n", lastResult.getPeakMemoryMb()));

            writer.write("## 2. Objective Quality Metrics\n");
            writer.write("| Metric | Score |\n");
            writer.write("| :--- | :--- |\n");
            writer.write(String.format(Locale.US, "| PSNR | %.2f dB |\n", lastResult.psnr));
            writer.write(String.format(Locale.US, "| SSIM | %.4f |\n", lastResult.ssim));
            writer.write(String.format(Locale.US, "| SNR | %.2f dB |\n", lastResult.snr));
            writer.write(String.format(Locale.US, "| MSE | %.4f |\n\n", lastResult.mse));

            writer.write("## 3. Photogrammetry / SfM Feature Integrity\n");
            writer.write("| Metric | Value |\n");
            writer.write("| :--- | :--- |\n");
            writer.write(String.format(Locale.US, "| Original Keypoints | %d |\n", lastResult.originalKeypoints.size()));
            writer.write(String.format(Locale.US, "| Enhanced Keypoints | %d |\n", lastResult.enhancedKeypoints.size()));
            writer.write(String.format(Locale.US, "| Geometric Retention Rate | %.2f%% |\n\n", lastResult.keypointRetention * 100f));

            writer.write("## 4. Architectural Summary\n");
            writer.write("Processed using on-device Hann-windowed overlapping tiled convolution pipeline.\n");
            writer.write("All algorithms 100% FLOSS under Apache 2.0 license.\n");

            writer.close();

            Toast.makeText(this, "Exported: " + reportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();

            // Share Intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Mobile Enhancement Benchmark: " + lastResult.modelName);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Benchmark Report saved at: " + reportFile.getAbsolutePath());
            startActivity(Intent.createChooser(shareIntent, "Share Benchmark Report"));

        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
