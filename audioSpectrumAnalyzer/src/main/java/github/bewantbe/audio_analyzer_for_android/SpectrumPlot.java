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

package github.bewantbe.audio_analyzer_for_android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.Log;

import static java.lang.Math.abs;
import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static java.lang.Math.round;

/**
 * The spectrum plot part of AnalyzerGraphic
 */

class SpectrumPlot {
    private static final String TAG = "SpectrumPlot:";
    boolean showLines;
    private Paint linePaint, linePaintLight, linePeakPaint;
    private Paint cursorPaint;
    private Paint gridPaint;
    private Paint labelPaint;
    private Paint calibNamePaint;
    private Paint calibLinePaint;
    private int canvasHeight=0, canvasWidth=0;

    private float DPRatio;

    double cursorFreq, cursorDB;  // cursor location
    private Plot2D plot2D;
    ScreenPhysicalMapping axisX, axisY;

    SpectrumPlot(Context _context) {
        DPRatio = _context.getResources().getDisplayMetrics().density;

        linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#0D2C6D"));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1);

        linePaintLight = new Paint(linePaint);
        linePaintLight.setColor(Color.parseColor("#3AB3E2"));

        linePeakPaint = new Paint(linePaint);
        linePeakPaint.setColor(0xFF00A0A0);

        gridPaint = new Paint();
        gridPaint.setColor(Color.DKGRAY);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(0.6f * DPRatio);

        cursorPaint = new Paint(gridPaint);
        cursorPaint.setColor(Color.parseColor("#00CD00"));

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.GRAY);
        labelPaint.setTextSize(14.0f * DPRatio);
        labelPaint.setTypeface(Typeface.MONOSPACE);  // or Typeface.SANS_SERIF

        calibNamePaint = new Paint(labelPaint);
        calibNamePaint.setColor(Color.YELLOW & 0x66ffffff);
        calibLinePaint = new Paint(linePaint);
        calibLinePaint.setColor(Color.YELLOW);

        cursorFreq = cursorDB = 0f;

        plot2D = new Plot2D(
                ScreenPhysicalMapping.Type.LINEAR, GridLabel.Type.FREQ,
                ScreenPhysicalMapping.Type.LINEAR, GridLabel.Type.DB,
                canvasWidth, canvasHeight, DPRatio);
        axisX = plot2D.axisX;
        axisY = plot2D.axisY;
    }

    void setCanvas(int _canvasWidth, int _canvasHeight, double[] axisBounds) {
//        Log.i("SpectrumPlot", "setCanvas: W="+_canvasWidth+"  H="+_canvasHeight);
        canvasWidth  = _canvasWidth;
        canvasHeight = _canvasHeight;
        plot2D.setCanvasBound(_canvasWidth, _canvasHeight, axisBounds, DPRatio);
    }

    void setZooms(double xZoom, double xShift, double yZoom, double yShift) {
        plot2D.axisX.setZoomShift(xZoom, xShift);
        plot2D.axisY.setZoomShift(yZoom, yShift);
    }

    // Linear or Logarithmic frequency axis
    void setFreqAxisMode(ScreenPhysicalMapping.Type mapType, double freq_lower_bound_for_log, GridLabel.Type gridType) {
        plot2D.axisX.setMappingType(mapType, freq_lower_bound_for_log);
        plot2D.gridLabelX.setGridType(gridType);
        Log.i(TAG, "setFreqAxisMode(): set to mode " + mapType + " axisX.vL=" + plot2D.axisX.vLowerBound + "  freq_lower_bound_for_log = " + freq_lower_bound_for_log);
    }

    private double[] y_calib = null;
    private double[] x_calib = null;
    private String name_calib = null;

    void addCalibCurve(double[] y, double[] x, String name) {
        y_calib = y;
        x_calib = x;
        name_calib = name;
    }

    private double[] db_cache = null;
    private AnalyzerUtil.PeakHoldAndFall peakHold = new AnalyzerUtil.PeakHoldAndFall();
    private long timeLastCall;

    // Plot the spectrum into the Canvas c
    private void drawSpectrumOnCanvas(Canvas c, final double[] _db) {
        if (canvasHeight < 1 || _db == null || _db.length == 0) {
            return;
        }
        AnalyzerGraphic.setIsBusy(true);

        synchronized (_db) {  // TODO: need lock on savedDBSpectrum, but how?
            if (db_cache == null || db_cache.length != _db.length) {
                Log.d(TAG, "drawSpectrumOnCanvas(): new db_cache");
                db_cache = new double[_db.length];
            }
            System.arraycopy(_db, 0, db_cache, 0, _db.length);
        }

        long timeNow = SystemClock.uptimeMillis();
        peakHold.addCurrentValue(db_cache, (timeNow - timeLastCall)/1000.0);
        timeLastCall = timeNow;

        // Spectrum peak hold
        plot2D.plotLineBar(c, peakHold.v_peak, null, false, linePeakPaint, null);

        // Spectrum line and bar
        plot2D.plotLineBar(c, db_cache, null, !showLines, linePaintLight, linePaint);

        // Name of calibration curve.
        if (name_calib != null) {
            c.save();
            c.translate(30*DPRatio, (float)plot2D.axisY.pixelFromV(0));
            c.drawText(name_calib, 0, 0, calibNamePaint);
            c.restore();
        }
        plot2D.plotLineBar(c, y_calib, x_calib, false, calibLinePaint, null);

        AnalyzerGraphic.setIsBusy(false);
    }

    // x, y is in pixel unit
    void setCursor(double x, double y) {
        cursorFreq = plot2D.axisX.vFromPixel(x);  // frequency
        cursorDB   = plot2D.axisY.vFromPixel(y);  // decibel
    }

    // x, y is in frequency/DB units
    void setCursorFreq(double x, double y) {
        cursorFreq = x;
        cursorDB = y;
    }

    double getCursorFreq() {
        return  canvasWidth == 0 ? 0 : cursorFreq;
    }

    double getCursorDB() {
        return canvasHeight == 0 ?   0 : cursorDB;
    }

    void hideCursor() {
        cursorFreq = 0;
        cursorDB = 0;
    }

    // Plot spectrum with axis and ticks on the whole canvas c
    void drawSpectrumPlot(Canvas c, double[] savedDBSpectrum) {
        plot2D.updateGridLabels();
        plot2D.drawGridLines(c, gridPaint);
        drawSpectrumOnCanvas(c, savedDBSpectrum);
        if (cursorFreq != 0) {
            plot2D.plotCrossline(c, cursorFreq, cursorDB, cursorPaint);
        }

        plot2D.drawAxisLabels(c, labelPaint, gridPaint, gridPaint);
    }
}
