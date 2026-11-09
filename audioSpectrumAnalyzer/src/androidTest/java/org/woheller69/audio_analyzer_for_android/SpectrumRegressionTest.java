package org.woheller69.audio_analyzer_for_android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;

/** Device regression checks using the real View, axis mappings and Canvas renderer. */
@RunWith(AndroidJUnit4.class)
public class SpectrumRegressionTest {
    private android.content.Context getTargetContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void spectrumRegression() throws Throwable {
        final Throwable[] error = new Throwable[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { checkSpectrumVisibility(); }
            catch (Throwable failure) { error[0] = failure; }
        });
        if (error[0] != null) throw error[0];
    }

    private void checkSpectrumVisibility() {
        AnalyzerGraphic graph = new AnalyzerGraphic(getTargetContext());
        require(graph.getViewPhysicalRange()[2] < 0,
                "Uninitialized dB bounds can be saved as a positive-only locked range");
        graph.setShowFreqAlongX(true);
        graph.setSpectrumDBLowerBound(-144);
        graph.layout(0, 0, 1080, 1200);
        AnalyzerParameters parameters = new AnalyzerParameters(getTargetContext().getResources());
        graph.setupPlot(parameters);
        graph.setShowLines(true);
        double[] spectrum = new double[parameters.fftLen / 2 + 1];
        Arrays.fill(spectrum, -65);
        spectrum[128] = -20;
        graph.saveSpectrum(spectrum);
        assertVisible(graph);
        double[] legacy = {0, parameters.sampleRate / 2.0, 0, 12, 0, 0};
        require(AnalyzerGraphic.repairLegacyLockedDBRange(legacy, -120),
                "Legacy positive-only locked range was not repaired");
        for (int mode = 0; mode < 2; mode++) {
            if (mode == 0) graph.switch2Spectrogram(); else graph.switch2Spectrum();
            graph.setViewRange(legacy, graph.getViewPhysicalRange());
            require(graph.getViewPhysicalRange()[2] == -120
                    && graph.getViewPhysicalRange()[3] == 0,
                    "Range semantics changed with gesture mode");
            require(graph.spectrogramPlot.spectrogramBMP.dBLowerBound == -120
                    && graph.spectrogramPlot.spectrogramBMP.dBUpperBound == 0,
                    "Waterfall was clipped by a different dB range");
            assertVisible(graph);
        }

        double beforeTop = graph.spectrumPlot.axisY.vMinInView();
        double beforeBottom = graph.spectrumPlot.axisY.vMaxInView();
        graph.setShiftScaleBegin(200, 500, 800, 900);
        graph.setShiftScale(100, 200, 900, 1000);
        require(graph.spectrumPlot.axisY.vMinInView() == beforeTop
                && graph.spectrumPlot.axisY.vMaxInView() == beforeBottom,
                "Waterfall pinch changed the spectrum dB range");
        graph.beginPan(500);
        graph.setYShift(0.7);
        require(graph.spectrumPlot.axisY.vMinInView() == beforeTop,
                "Waterfall pan moved the spectrum");
        assertVisible(graph);

        graph.setShiftScaleBegin(200, 50, 800, 200);
        graph.setShiftScale(200, 20, 800, 250);
        require(graph.spectrumPlot.axisY.getZoom() > 1, "Spectrum pinch stopped working");
        graph.switch2Spectrogram();
        android.os.Parcelable saved = graph.onSaveInstanceState();
        AnalyzerGraphic restored = new AnalyzerGraphic(getTargetContext());
        restored.setShowFreqAlongX(true);
        restored.setSpectrumDBLowerBound(-144);
        restored.layout(0, 0, 1080, 1200);
        restored.setupPlot(parameters);
        restored.onRestoreInstanceState(saved);
        double restoredZoom = restored.spectrumPlot.axisY.getZoom();
        double restoredShift = restored.spectrumPlot.axisY.getShift();
        restored.setXShift(0);
        require(restored.spectrumPlot.axisY.getZoom() == restoredZoom
                && restored.spectrumPlot.axisY.getShift() == restoredShift,
                "Horizontal pan after restore replaced spectrum dB scale with Time state");
        assertVisible(restored);

        graph.resetViewScale();
        require(graph.spectrumPlot.axisY.getZoom() == 1
                && graph.spectrumPlot.axisY.getShift() == 0,
                "Reset in Time mode did not restore spectrum");
        assertVisible(graph);

        graph.layout(0, 0, 1080, 1600);
        assertVisible(graph);
        graph.onRestoreInstanceState(graph.onSaveInstanceState());
        graph.switch2Spectrum();
        graph.setXShift(0);
        assertVisible(graph);
    }

    private void assertVisible(AnalyzerGraphic graph) {
        Bitmap bitmap = Bitmap.createBitmap(graph.getWidth(), graph.getHeight(), Bitmap.Config.ARGB_8888);
        graph.draw(new Canvas(bitmap));
        int[] pixels = new int[bitmap.getWidth() * (bitmap.getHeight() / 4)];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight() / 4);
        int spectrumPixels = 0;
        for (int color : pixels) {
            // Fractional pixel coverage blends the live line with the peak-hold line.
            int red = (color >> 16) & 255, green = (color >> 8) & 255, blue = color & 255;
            if (blue > red + 60 && blue > green + 10) spectrumPixels++;
        }
        int colourfulPixels = 0;
        int top = bitmap.getHeight() / 4;
        for (int y = top; y < top + 5; y++) {
            for (int x = 200; x < bitmap.getWidth() - 20; x++) {
                int color = bitmap.getPixel(x, y);
                int red = (color >> 16) & 255, green = (color >> 8) & 255, blue = color & 255;
                if (Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) > 20)
                    colourfulPixels++;
            }
        }
        if (spectrumPixels <= 20 || colourfulPixels <= 20) {
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(
                    new java.io.File(getTargetContext().getCacheDir(), "spectrum-regression.png"))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
        }
        bitmap.recycle();
        require(colourfulPixels > 20, "Waterfall's latest rows are missing or clipped");
        require(spectrumPixels > 20, "Live spectrum missing: pixels=" + spectrumPixels
                + " bounds=" + graph.spectrumPlot.axisY.vMinInView() + ","
                + graph.spectrumPlot.axisY.vMaxInView() + " size="
                + graph.spectrumPlot.axisX.nCanvasPixel + "x" + graph.spectrumPlot.axisY.nCanvasPixel);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
