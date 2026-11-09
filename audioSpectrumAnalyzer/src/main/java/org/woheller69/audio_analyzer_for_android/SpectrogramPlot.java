/* Copyright 2014 Eddy Xiao <bewantbe@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.woheller69.audio_analyzer_for_android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;

/**
 * The spectrogram plot part of AnalyzerGraphic
 */

class SpectrogramPlot {
    private static final String TAG = "SpectrogramPlot:";
    boolean showFreqAlongX = false;

    // 1D+2D mode: fixed waterfall layout (frequency on X, time from top to bottom,
    // history length = canvas height). Otherwise the original standalone 2D behaviour.
    private boolean combined = false;
    // User settings, re-applied when leaving the combined mode.
    private boolean userShifting = false;
    private boolean userFreqAlongX = false;

    enum TimeAxisMode {  // java's enum type is inconvenient
        SHIFT(0), OVERWRITE(1);       // 0: moving (shifting) spectrogram, 1: overwriting in loop

        private final int value;
        TimeAxisMode(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    private TimeAxisMode showModeSpectrogram = TimeAxisMode.OVERWRITE;
    private boolean bShowTimeAxis = true;

    private double timeWatch = 4.0;  // TODO: a bit duplicated, use axisTime
    private volatile int timeMultiplier = 1;  // should be accorded with nFFTAverage in AnalyzerActivity
    int nFreqPoints;  // TODO: a bit duplicated, use BMP.nFreq
    int nTimePoints;  // TODO: a bit duplicated, use BMP.nTime
    double timeInc;
    int zeroPadFac = 1;

    private Matrix matrixSpectrogram = new Matrix();
    private Paint smoothBmpPaint;
    private Paint backgroundPaint;
    private Paint cursorPaint;
    private Paint gridPaint, rulerBrightPaint;
    private Paint labelPaint;
    private Paint cursorTimePaint;

    ScreenPhysicalMapping axisFreq;
    ScreenPhysicalMapping axisTime;
    private GridLabel fqGridLabel;
    private GridLabel tmGridLabel;
    double cursorFreq;
    private float DPRatio;
    private float gridDensity = 1/85f;  // every 85 pixel one grid line, on average
    private int canvasHeight=0, canvasWidth=0;
    float labelBeginX, labelBeginY;

    SpectrogramPlot(Context _context) {
        DPRatio = _context.getResources().getDisplayMetrics().density;
        gridPaint = new Paint();
        gridPaint.setColor(Color.DKGRAY);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(0.6f * DPRatio);

        cursorPaint = new Paint(gridPaint);
        cursorPaint.setColor(Color.parseColor("#00CD00"));
        cursorPaint.setStrokeWidth(2);

        cursorTimePaint = new Paint(cursorPaint);
        cursorTimePaint.setStyle(Paint.Style.STROKE);
        cursorTimePaint.setStrokeWidth(0);

        rulerBrightPaint = new Paint();
        rulerBrightPaint.setColor(Color.rgb(99, 99, 99));  // 99: between Color.DKGRAY and Color.GRAY
        rulerBrightPaint.setStyle(Paint.Style.STROKE);
        rulerBrightPaint.setStrokeWidth(1);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.GRAY);
        labelPaint.setTextSize(14.0f * DPRatio);
        labelPaint.setTypeface(Typeface.MONOSPACE);  // or Typeface.SANS_SERIF

        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.BLACK);

        cursorFreq = 0f;

        fqGridLabel = new GridLabel(GridLabel.Type.FREQ, canvasWidth  * gridDensity / DPRatio);
        tmGridLabel = new GridLabel(GridLabel.Type.TIME, canvasHeight * gridDensity / DPRatio);

        axisFreq = new ScreenPhysicalMapping(0, 0, 0, ScreenPhysicalMapping.Type.LINEAR);
        axisTime = new ScreenPhysicalMapping(0, 0, 0, ScreenPhysicalMapping.Type.LINEAR);

        Log.i(TAG, "SpectrogramPlot() initialized");
    }

    // Before calling this, axes should be initialized.
    void setupSpectrogram(AnalyzerParameters analyzerParam) {
        int sampleRate       = analyzerParam.sampleRate;
        int fftLen           = analyzerParam.fftLen;
        int hopLen           = analyzerParam.hopLen;
        int nAve             = analyzerParam.nFFTAverage;
        double timeDurationE = analyzerParam.spectrogramDuration;
        zeroPadFac = analyzerParam.zeroPadFac;
        timeMultiplier = nAve;
        timeInc     = (double)hopLen / sampleRate / zeroPadFac;  // time of each slice
        nFreqPoints = fftLen / 2;           // no direct current term
        if (combined) {
            nTimePoints = Math.max(1, (int)axisTime.nCanvasPixel);
            timeWatch = nTimePoints * timeInc;
            updateTimeAxis();
        } else {
            timeWatch = timeDurationE;
            nTimePoints = (int)Math.ceil(timeWatch / timeInc);
        }
        spectrogramBMP.newestOnTop = combined;
        spectrogramBMP.init(nFreqPoints, nTimePoints, axisFreq);
        Log.i(TAG, "setupSpectrogram() done" +
                "\n  sampleRate    = " + sampleRate +
                "\n  fftLen        = " + fftLen +
                "\n  historySeconds = " + timeWatch * nAve + "  (" + nTimePoints + " pixel rows)" +
                "\n  canvas size freq= " + axisFreq.nCanvasPixel + " time=" + axisTime.nCanvasPixel);
    }

    void setCanvas(int _canvasWidth, int _canvasHeight, double[] axisBounds) {
        Log.i(TAG, "setCanvas(): " + _canvasWidth + " x " + _canvasHeight);
        canvasWidth  = _canvasWidth;
        canvasHeight = _canvasHeight;
        if (canvasHeight > 1 && canvasWidth > 1) {
            updateDrawingWindowSize();
        }
        if (axisBounds != null) {
            if (showFreqAlongX) {
                axisFreq.setBounds(axisBounds[0], axisBounds[2]);
                axisTime.setBounds(axisBounds[1], axisBounds[3]);
            } else {
                axisTime.setBounds(axisBounds[0], axisBounds[2]);
                axisFreq.setBounds(axisBounds[1], axisBounds[3]);
            }
            if (!combined && showModeSpectrogram == TimeAxisMode.SHIFT) {
                double b1 = axisTime.vLowerBound;
                double b2 = axisTime.vUpperBound;
                axisTime.setBounds(b2, b1);
            }
        }
        // History is resized to the canvas in drawSpectrogramPlot()/setupSpectrogram(),
        // once nFreqPoints and timeInc are up to date.
        fqGridLabel.setDensity(axisFreq.nCanvasPixel * gridDensity / DPRatio);
        tmGridLabel.setDensity(axisTime.nCanvasPixel * gridDensity / DPRatio);

        spectrogramBMP.updateAxis(axisFreq);
    }

    void setZooms(double xZoom, double xShift, double yZoom, double yShift) {
        //Log.i(TAG, "setZooms():  xZ=" + xZoom + "  xS=" + xShift + "  yZ=" + yZoom + "  yS=" + yShift);
        if (showFreqAlongX) {
            axisFreq.setZoomShift(xZoom, xShift);
            if (combined) axisTime.setZoomShift(1, 0); else axisTime.setZoomShift(yZoom, yShift);
        } else {
            axisFreq.setZoomShift(yZoom, yShift);
            if (combined) axisTime.setZoomShift(1, 0); else axisTime.setZoomShift(xZoom, xShift);
        }
        spectrogramBMP.updateZoom();
    }

    // Linear or Logarithmic frequency axis
    void setFreqAxisMode(ScreenPhysicalMapping.Type mapType, double freq_lower_bound_for_log, GridLabel.Type gridType) {
        Log.i(TAG, "setFreqAxisMode(): set to mode " + mapType);
        axisFreq.setMappingType(mapType, freq_lower_bound_for_log);
        fqGridLabel.setGridType(gridType);
        spectrogramBMP.updateAxis(axisFreq);
    }

    void setColorMap(String colorMapName) { spectrogramBMP.setColorMap(colorMapName); }

    void setSpectrogramDBLowerBound(double b) { spectrogramBMP.dBLowerBound = b; }

    void setCursor(double x, double y) {
        if (showFreqAlongX) {
            //cursorFreq = axisBounds.width() * (xShift + (x-labelBeginX)/(canvasWidth-labelBeginX)/xZoom);  // frequency
            cursorFreq = axisFreq.vFromPixel(x - labelBeginX);
        } else {
            //cursorFreq = axisBounds.width() * (1 - yShift - y/labelBeginY/yZoom);  // frequency
            cursorFreq = axisFreq.vFromPixel(y);
        }
        if (cursorFreq < 0) {
            cursorFreq = 0;
        }
    }

    // x is in frequency units
    void setCursorFreq(double x) {
        cursorFreq = x;
    }

    TimeAxisMode getSpectrogramMode() {
        return showModeSpectrogram;
    }

    double getCursorFreq() {
        return canvasWidth == 0 ? 0 : cursorFreq;
    }

    void hideCursor() {
        cursorFreq = 0;
    }

    private void updateTimeAxis() {
        axisTime.setBounds(0, timeWatch * timeMultiplier);
        axisTime.setZoomShift(1, 0);
    }

    private void resizeHistoryToCanvas() {
        if (!combined || timeInc <= 0 || nFreqPoints <= 0) return;
        int rows = Math.max(1, (int)axisTime.nCanvasPixel);
        if (rows != nTimePoints) {
            spectrogramBMP.resizeTimeRows(rows, axisFreq);
            nTimePoints = rows;
        }
        timeWatch = rows * timeInc;
        updateTimeAxis();
    }

    void setTimeMultiplier(int nAve) {
        timeMultiplier = nAve;
        if (combined) {
            updateTimeAxis();
            return;
        }
        if (showModeSpectrogram == TimeAxisMode.SHIFT) {
            axisTime.vLowerBound = timeWatch * timeMultiplier;
        } else {
            axisTime.vUpperBound = timeWatch * timeMultiplier;
        }
        // keep zoom shift
        axisTime.setZoomShift(axisTime.getZoom(), axisTime.getShift());
    }

    void setShowTimeAxis(boolean bSTA) {
        bShowTimeAxis = bSTA;
    }

    // Switch between the standalone 2D layout (user settings) and the fixed 1D+2D layout.
    // The caller has to run setupAxes()/setupSpectrogram() afterwards.
    void setCombined(boolean c) {
        if (combined == c) return;
        combined = c;
        spectrogramBMP.newestOnTop = c;
        if (c) {
            applyShifting(true);
            applyFreqAlongX(true);
        } else {
            applyShifting(userShifting);
            applyFreqAlongX(userFreqAlongX);
        }
    }

    boolean isCombined() {
        return combined;
    }

    void setSpectrogramModeShifting(boolean b) {
        userShifting = b;
        if (!combined) applyShifting(b);
    }

    private void applyShifting(boolean b) {
        if (combined) {
            // newest slice stays at the top; the time axis is plain 0..T
            showModeSpectrogram = TimeAxisMode.SHIFT;
            updateTimeAxis();
            return;
        }
        if ((showModeSpectrogram == TimeAxisMode.SHIFT) != b) {
            // mode change, swap time bounds.
            double b1 = axisTime.vLowerBound;
            double b2 = axisTime.vUpperBound;
            axisTime.setBounds(b2, b1);
        }
        if (b) {
            showModeSpectrogram = TimeAxisMode.SHIFT;
            setPause(isPaused);  // update time estimation
        } else {
            showModeSpectrogram = TimeAxisMode.OVERWRITE;
        }
    }

    void setShowFreqAlongX(boolean b) {
        userFreqAlongX = b;
        if (!combined) applyFreqAlongX(b);
    }

    private void applyFreqAlongX(boolean b) {
        if (showFreqAlongX != b) {
            // Set (swap) canvas size
            double t = axisFreq.nCanvasPixel;
            axisFreq.setNCanvasPixel(axisTime.nCanvasPixel);
            axisTime.setNCanvasPixel(t);
            // swap bounds of freq axis
            axisFreq.reverseBounds();

            fqGridLabel.setDensity(axisFreq.nCanvasPixel * gridDensity / DPRatio);
            tmGridLabel.setDensity(axisTime.nCanvasPixel * gridDensity / DPRatio);
        }
        showFreqAlongX = b;
    }

    void setSmoothRender(boolean b) {
        if (b) {
            smoothBmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        } else {
            smoothBmpPaint = null;
        }
    }

    private double timeLastSample = 0;
    private boolean updateTimeDiff = false;

    void prepare() {
        if (!combined && showModeSpectrogram == TimeAxisMode.SHIFT)
            setPause(isPaused);
    }

    void setPause(boolean p) {
        if (! p) {
            timeLastSample = System.currentTimeMillis()/1000.0;
        }
        isPaused = p;
    }

    // Will be called in another thread (SamplingLoop)
    // db.length == 2^n + 1
    void saveRowSpectrumAsColor(final double[] db) {
        if (!combined) {
            // For time compensate in shifting mode
            double tNow = System.currentTimeMillis()/1000.0;
            updateTimeDiff = true;
            if (Math.abs(timeLastSample - tNow) > 0.5) {
                timeLastSample = tNow;
            } else {
                timeLastSample += timeInc * timeMultiplier;
                timeLastSample += (tNow - timeLastSample) * 1e-2;  // track current time
            }
        }
        // 1D+2D: each averaged FFT result advances the waterfall by exactly one physical pixel.
        spectrogramBMP.fill(db);
    }

    private float getLabelBeginY() {
        float textHeigh     = labelPaint.getFontMetrics(null);
        float labelLargeLen = 0.5f * textHeigh;
        if (!showFreqAlongX && !bShowTimeAxis) {
            return canvasHeight;
        } else {
            float h = canvasHeight - 0.6f*labelLargeLen - textHeigh;
            return combined ? Math.max(1, (float)Math.floor(h)) : h;
        }
    }

    // Left margin for ruler
    private float getLabelBeginX() {
        float textHeight = labelPaint.getFontMetrics(null);
        float labelLaegeLen = 0.5f * textHeight;
        if (showFreqAlongX) {
            if (bShowTimeAxis) {
                int j = combined ? 4 : 3;
                for (int i = 0; i < tmGridLabel.strings.length; i++) {
                    if (j < tmGridLabel.strings[i].length()) {
                        j = tmGridLabel.strings[i].length();
                    }
                }
                return 0.6f*labelLaegeLen + j*0.5f*textHeight;
            } else {
                return combined ? 0.6f*labelLaegeLen + 2*textHeight : 0;
            }
        } else {
            return 0.6f*labelLaegeLen + 2.5f*textHeight;
        }
    }

    private float labelBeginXOld = 0;
    private float labelBeginYOld = 0;

    private void updateDrawingWindowSize() {
        labelBeginX = getLabelBeginX();  // this seems will make the scaling gesture inaccurate
        labelBeginY = getLabelBeginY();
        if (labelBeginX != labelBeginXOld || labelBeginY != labelBeginYOld) {
            if (showFreqAlongX) {
                axisFreq.setNCanvasPixel(canvasWidth - labelBeginX);
                axisTime.setNCanvasPixel(labelBeginY);
            } else {
                axisTime.setNCanvasPixel(canvasWidth - labelBeginX);
                axisFreq.setNCanvasPixel(labelBeginY);
            }
            labelBeginXOld = labelBeginX;
            labelBeginYOld = labelBeginY;
        }
    }

    private void drawFreqCursor(Canvas c) {
        if (cursorFreq == 0) return;
        float cX, cY;
        // Show only the frequency cursor
        if (showFreqAlongX) {
            cX = (float)axisFreq.pixelFromV(cursorFreq) + labelBeginX;
            c.drawLine(cX, 0, cX, labelBeginY, cursorPaint);
        } else {
            cY = (float)axisFreq.pixelFromV(cursorFreq);
            c.drawLine(labelBeginX, cY, canvasWidth, cY, cursorPaint);
        }
    }

    // Draw time axis for spectrogram
    // Working in the original canvas frame
    private void drawTimeAxis(Canvas c, float labelBeginX, float labelBeginY, boolean drawOnXAxis) {
        if (drawOnXAxis) {
            AxisTickLabels.draw(c, axisTime, tmGridLabel,
                    labelBeginX, labelBeginY, 0, 1,
                    labelPaint, gridPaint, rulerBrightPaint);
        } else {
            AxisTickLabels.draw(c, axisTime, tmGridLabel,
                    labelBeginX, 0, 1, -1,
                    labelPaint, gridPaint, rulerBrightPaint);
        }
    }

    // Draw frequency axis for spectrogram
    // Working in the original canvas frame
    private void drawFreqAxis(Canvas c, float labelBeginX, float labelBeginY, boolean drawOnXAxis) {
        if (drawOnXAxis) {
            AxisTickLabels.draw(c, axisFreq, fqGridLabel,
                    labelBeginX, labelBeginY, 0, 1,
                    labelPaint, gridPaint, rulerBrightPaint);
        } else {
            AxisTickLabels.draw(c, axisFreq, fqGridLabel,
                    labelBeginX, 0, 1, -1,
                    labelPaint, gridPaint, rulerBrightPaint);
        }
    }


    private double pixelTimeCompensate = 0;
    private volatile boolean isPaused = false;

    // Plot spectrogram with axis and ticks on the whole canvas c
    void drawSpectrogramPlot(Canvas c) {
        if (canvasWidth == 0 || canvasHeight == 0) {
            return;
        }
        updateDrawingWindowSize();
        resizeHistoryToCanvas();
        fqGridLabel.setDensity(axisFreq.nCanvasPixel * gridDensity / DPRatio);
        tmGridLabel.setDensity(axisTime.nCanvasPixel * gridDensity / DPRatio);
        fqGridLabel.updateGridLabels(axisFreq.vMinInView(), axisFreq.vMaxInView());
        tmGridLabel.updateGridLabels(axisTime.vMinInView(), axisTime.vMaxInView());

        // show Spectrogram
        double halfFreqResolutionShift;  // move the color patch to match the center frequency
        if (axisFreq.mapType == ScreenPhysicalMapping.Type.LINEAR) {
            halfFreqResolutionShift = axisFreq.getZoom() * axisFreq.nCanvasPixel / nFreqPoints / 2;
        } else {
            halfFreqResolutionShift = 0;  // the correction is included in log axis render algo.
        }
        matrixSpectrogram.reset();
        if (showFreqAlongX) {
            // when xZoom== 1: nFreqPoints -> canvasWidth; 0 -> labelBeginX
            matrixSpectrogram.postScale((float)(axisFreq.getZoom() * axisFreq.nCanvasPixel / nFreqPoints),
                    (float)(axisTime.getZoom() * axisTime.nCanvasPixel / nTimePoints));
            matrixSpectrogram.postTranslate((float)(labelBeginX - axisFreq.getShift() * axisFreq.getZoom() * axisFreq.nCanvasPixel + halfFreqResolutionShift),
                    (float)(-axisTime.getShift() * axisTime.getZoom() * axisTime.nCanvasPixel));
        } else {
            // postRotate() will make c.drawBitmap about 20% slower, don't know why
            matrixSpectrogram.postRotate(-90);
            matrixSpectrogram.postScale((float)(axisTime.getZoom() * axisTime.nCanvasPixel / nTimePoints),
                    (float)(axisFreq.getZoom() * axisFreq.nCanvasPixel / nFreqPoints));
            // (1-yShift) is relative position of shift (after rotation)
            // yZoom*labelBeginY is canvas length in frequency direction in pixel unit
            matrixSpectrogram.postTranslate((float)(labelBeginX - axisTime.getShift() * axisTime.getZoom() * axisTime.nCanvasPixel),
                    (float)((1-axisFreq.getShift()) * axisFreq.getZoom() * axisFreq.nCanvasPixel - halfFreqResolutionShift));
        }
        c.save();
        c.concat(matrixSpectrogram);

        // Time compensate to make it smoother shifting (standalone 2D only).
        // But if user pressed pause, stop compensate.
        if (!combined) {
            if (!isPaused && updateTimeDiff) {
                double timeCurrent = System.currentTimeMillis() / 1000.0;
                pixelTimeCompensate = (timeLastSample - timeCurrent) / (timeInc * timeMultiplier * nTimePoints) * nTimePoints;
                updateTimeDiff = false;
            }
            if (showModeSpectrogram == TimeAxisMode.SHIFT) {
                c.translate(0.0f, (float)pixelTimeCompensate);
            }
        }
        // 1D+2D: whole-pixel steps keep every FFT slice sharp and the newest at row zero.
        if (axisFreq.mapType == ScreenPhysicalMapping.Type.LOG &&
                spectrogramBMP.logAxisMode == SpectrogramBMP.LogAxisPlotMode.REPLOT) {
            // Revert the effect of axisFreq.getZoom() axisFreq.getShift() for the mode REPLOT
            c.scale((float)(1 / axisFreq.getZoom()), 1f);
            if (showFreqAlongX) {
                c.translate((float)(nFreqPoints * axisFreq.getShift() * axisFreq.getZoom()), 0.0f);
            } else {
                c.translate((float)(nFreqPoints * (1f - axisFreq.getShift() - 1f / axisFreq.getZoom()) * axisFreq.getZoom()), 0.0f);
            }
        }

        spectrogramBMP.draw(c, axisFreq.mapType, showModeSpectrogram, combined ? null : smoothBmpPaint, cursorTimePaint);
        c.restore();

        drawFreqCursor(c);

        if (showFreqAlongX) {
            c.drawRect(0, labelBeginY, canvasWidth, canvasHeight, backgroundPaint);
            drawFreqAxis(c, labelBeginX, labelBeginY, showFreqAlongX);
            if (labelBeginX > 0) {
                c.drawRect(0, 0, labelBeginX, labelBeginY, backgroundPaint);
                drawTimeAxis(c, labelBeginX, labelBeginY, !showFreqAlongX);
            }
        } else {
            c.drawRect(0, 0, labelBeginX, labelBeginY, backgroundPaint);
            drawFreqAxis(c, labelBeginX, labelBeginY, showFreqAlongX);
            if (labelBeginY != canvasHeight) {
                c.drawRect(0, labelBeginY, canvasWidth, canvasHeight, backgroundPaint);
                drawTimeAxis(c, labelBeginX, labelBeginY, !showFreqAlongX);
            }
        }
    }

    SpectrogramBMP spectrogramBMP = new SpectrogramBMP();

}