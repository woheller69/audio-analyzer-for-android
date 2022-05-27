package org.woheller69.audio_analyzer_for_android;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;

import static java.lang.Math.ceil;
import static java.lang.Math.floor;

/**
 * Ploting functions
 * Usage:
 *   Initialize and set axis, gridLabel, when necessary.
 *   Call plot* function to draw.
 */

class Plot2D {
    static String TAG = "Plot2D";
    private Matrix matrix = new Matrix();
    private float[] tmpLineXY = new float[0];  // cache line data for drawing
    private int[] index_range_cache = new int[2];
    // index_range_cache[0] == beginFreqPt
    // index_range_cache[1] == endFreqPt

    ScreenPhysicalMapping axisX, axisY;
    GridLabel gridLabelX, gridLabelY;

    private double gridDensity = 1/85.0;  // every 85 pixel one grid line, on average

    Plot2D(ScreenPhysicalMapping.Type axisTypeX, GridLabel.Type gridTypeX,
           ScreenPhysicalMapping.Type axisTypeY, GridLabel.Type gridTypeY,
           int _canvasWidth, int _canvasHeight, double DPRatio) {
        gridLabelX = new GridLabel(gridTypeX, _canvasWidth * gridDensity / DPRatio);
        gridLabelY = new GridLabel(gridTypeY, _canvasHeight * gridDensity / DPRatio);

        axisX = new ScreenPhysicalMapping(0, 0, 0, axisTypeX);
        axisY = new ScreenPhysicalMapping(0, 0, 0, axisTypeY);
    }

    void setCanvasBound(int _canvasWidth, int _canvasHeight, double[] axisBounds, double DPRatio) {
        gridLabelX.setDensity(_canvasWidth * gridDensity / DPRatio);
        gridLabelY.setDensity(_canvasHeight * gridDensity / DPRatio);
        axisX.setNCanvasPixel(_canvasWidth);
        axisY.setNCanvasPixel(_canvasHeight);
        if (axisBounds != null) {
            axisX.setBounds(axisBounds[0], axisBounds[2]);
            axisY.setBounds(axisBounds[1], axisBounds[3]);
        }
    }

    private double clampDB(double value) {
        if (value < AnalyzerGraphic.minDB || Double.isNaN(value)) {
            value = AnalyzerGraphic.minDB;
        }
        return value;
    }

    // Find the index range that the plot will appear inside view.
    // index_range[1] must never be reached.
    private void indexRangeFinder(double[] x_values, double x_step, int[] index_range) {
        if (index_range == null || index_range.length != 2) {
            // You are joking
            return ;
        }
        double viewMinX = axisX.vMinInView();
        double viewMaxX = axisX.vMaxInView();
        int max_index;
        if (x_values == null) {
            index_range[0] = (int)floor(viewMinX / x_step);    // pointer to tmpLineXY
            index_range[1] = (int)ceil (viewMaxX / x_step)+1;
            max_index = (int)floor(axisX.vUpperBound / x_step);
        } else {
            if (x_values.length == 0) {
                // mada joking
                return ;
            }
            // Assume x_values is in ascending order
            index_range[0] = AnalyzerUtil.binarySearchElem(x_values, viewMinX, true);
            index_range[1] = AnalyzerUtil.binarySearchElem(x_values, viewMaxX, false) + 1;
            max_index = x_values.length;
        }
        // Avoid log(0)
        if (((x_values == null && index_range[0] == 0)
                || (x_values != null && x_values[index_range[0]] <= 0))
                && axisX.mapType == ScreenPhysicalMapping.Type.LOG) {
            index_range[0]++;
        }
        // Avoid out-of-range access
        if (index_range[1] > max_index) {
            // just in case canvasMaxFreq / freqDelta > nFreqPointsTotal
            index_range[1] = max_index;
        }
    }

    void plotBarThin(Canvas c, double[] y_value, double[] x_values, int i_begin, int i_end, double x_inc, Paint linePainter) {
        if (tmpLineXY.length < 4*(y_value.length)) {
            Log.d(TAG, "plotBar(): new tmpLineXY");
            tmpLineXY = new float[4*(y_value.length)];
        }

        final double minYCanvas = axisY.pixelNoZoomFromV(AnalyzerGraphic.minDB);
        c.save();
        matrix.reset();
        matrix.setTranslate(0, (float)(-axisY.getShift() * axisY.nCanvasPixel));
        matrix.postScale(1, (float)axisY.getZoom());
        c.concat(matrix);
        //      float barWidthInPixel = 0.5f * freqDelta / (canvasMaxFreq - canvasMinFreq) * canvasWidth;
        //      if (barWidthInPixel > 2) {
        //        linePaint.setStrokeWidth(barWidthInPixel);
        //      } else {
        //        linePaint.setStrokeWidth(0);
        //      }
        // plot directly to the canvas
        for (int i = i_begin; i < i_end; i++) {
            float x = (float)axisX.pixelFromV(x_values == null ? i * x_inc : x_values[i]);
            float y = (float)axisY.pixelNoZoomFromV(clampDB(y_value[i]));
            tmpLineXY[4 * i] = x;
            tmpLineXY[4 * i + 1] = (float)minYCanvas;
            tmpLineXY[4 * i + 2] = x;
            tmpLineXY[4 * i + 3] = y;
        }
        c.drawLines(tmpLineXY, 4*i_begin, 4*(i_end-i_begin), linePainter);
        c.restore();
    }

    void plotBarThick(Canvas c, double[] y_value, double[] x_values, int i_begin, int i_end, double x_inc, Paint linePainter) {
        if (tmpLineXY.length < 4*(y_value.length)) {
            Log.d(TAG, "plotBar(): new tmpLineXY");
            tmpLineXY = new float[4*(y_value.length)];
        }

        final double minYCanvas = axisY.pixelNoZoomFromV(AnalyzerGraphic.minDB);
        int pixelStep = 2;  // each bar occupy this virtual pixel
        c.save();
        matrix.reset();
        double extraPixelAlignOffset = 0.0f;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//          // There is an shift for Android 4.4, while no shift for Android 2.3
//          // I guess that is relate to GL ES acceleration
//          if (c.isHardwareAccelerated()) {
//            extraPixelAlignOffset = 0.5f;
//          }
//        }
        matrix.setTranslate((float)(-axisX.getShift() * (y_value.length - 1) * pixelStep - extraPixelAlignOffset),
                (float)(-axisY.getShift() * axisY.nCanvasPixel));
        matrix.postScale((float)(axisX.nCanvasPixel / ((axisX.vMaxInView() - axisX.vMinInView()) / x_inc * pixelStep)), (float)axisY.getZoom());
        c.concat(matrix);
        // fill interval same as canvas pixel width.
        for (int i = i_begin; i < i_end; i++) {
            float x = x_values == null ? i * pixelStep : (float)(x_values[i] / x_inc * pixelStep);
            float y = (float)axisY.pixelNoZoomFromV(clampDB(y_value[i]));
            tmpLineXY[4*i  ] = x;
            tmpLineXY[4*i+1] = (float)minYCanvas;
            tmpLineXY[4*i+2] = x;
            tmpLineXY[4*i+3] = y;
        }
        c.drawLines(tmpLineXY, 4*i_begin, 4*(i_end-i_begin), linePainter);
        c.restore();
    }

    void plotBar(Canvas c, double[] y_value, double[] x_values, int i_begin, int i_end, double x_inc, Paint linePainter) {
        // If bars are very close to each other, draw bars as lines
        // Otherwise, zoom in so that lines look like bars.
        if (i_end - i_begin >= axisX.nCanvasPixel / 2
                || axisX.mapType != ScreenPhysicalMapping.Type.LINEAR) {
            plotBarThin(c, y_value, x_values, i_begin, i_end, x_inc, linePainter);
        } else {
            // for zoomed linear scale
            plotBarThick(c, y_value, x_values, i_begin, i_end, x_inc, linePainter);
        }
    }

    void plotLine(Canvas c, double[] y_value, double[] x_values, int i_begin, int i_end, double x_inc, Paint linePainter) {
        if (tmpLineXY.length < 4*(y_value.length)) {
            Log.d(TAG, "plotLine(): new tmpLineXY");
            tmpLineXY = new float[4*(y_value.length)];
        }
        c.save();
        matrix.reset();
        matrix.setTranslate(0, (float)(-axisY.getShift() * axisY.nCanvasPixel));
        matrix.postScale(1, (float)axisY.getZoom());
        c.concat(matrix);
        if (x_values == null) {
            float o_x = (float)axisX.pixelFromV(i_begin * x_inc);
            float o_y = (float)axisY.pixelNoZoomFromV(clampDB(y_value[i_begin]));
            for (int i = i_begin+1; i < i_end; i++) {
                float x = (float)axisX.pixelFromV(i * x_inc);
                float y = (float)axisY.pixelNoZoomFromV(clampDB(y_value[i]));
                tmpLineXY[4*i  ] = o_x;
                tmpLineXY[4*i+1] = o_y;
                tmpLineXY[4*i+2] = x;
                tmpLineXY[4*i+3] = y;
                o_x = x;
                o_y = y;
            }
        } else {
            float o_x = (float)axisX.pixelFromV(x_values[i_begin]);
            float o_y = (float)axisY.pixelNoZoomFromV(clampDB(y_value[i_begin]));
            for (int i = i_begin+1; i < i_end; i++) {
                float x = (float)axisX.pixelFromV(x_values[i]);
                float y = (float)axisY.pixelNoZoomFromV(clampDB(y_value[i]));
                tmpLineXY[4*i  ] = o_x;
                tmpLineXY[4*i+1] = o_y;
                tmpLineXY[4*i+2] = x;
                tmpLineXY[4*i+3] = y;
                o_x = x;
                o_y = y;
            }
        }
        c.drawLines(tmpLineXY, 4*(i_begin+1), 4*(i_end-i_begin-1), linePainter);
        c.restore();
    }

    void plotLineBar(Canvas c, double[] db_cache, double[] x_values, boolean drawBar, Paint linePaint, Paint barPaint) {
        if (db_cache == null || db_cache.length == 0) return;

        // There are db.length frequency points, including DC component
        int nFreqPointsTotal = db_cache.length - 1;
        double freqDelta = axisX.vUpperBound / nFreqPointsTotal;

        indexRangeFinder(x_values, freqDelta, index_range_cache);

        if (drawBar) {
            plotBar(c, db_cache, x_values, index_range_cache[0], index_range_cache[1], freqDelta, barPaint);
        }
        plotLine(c, db_cache, x_values, index_range_cache[0], index_range_cache[1], freqDelta, linePaint);
    }

    void updateGridLabels() {
        gridLabelX.updateGridLabels(axisX.vMinInView(), axisX.vMaxInView());
        gridLabelY.updateGridLabels(axisY.vMinInView(), axisY.vMaxInView());
    }

    void drawGridLines(Canvas c, Paint gridPaint) {
        for(int i = 0; i < gridLabelX.values.length; i++) {
            float xPos = (float)axisX.pixelFromV(gridLabelX.values[i]);
            c.drawLine(xPos, 0, xPos, (float)axisY.nCanvasPixel, gridPaint);
        }
        for(int i = 0; i < gridLabelY.values.length; i++) {
            float yPos = (float)axisY.pixelFromV(gridLabelY.values[i]);
            c.drawLine(0, yPos, (float)axisX.nCanvasPixel, yPos, gridPaint);
        }
    }

    void drawAxisLabels(Canvas c, Paint labelPaint, Paint gridPaint, Paint rulerBrightPaint) {
        AxisTickLabels.draw(c, axisX, gridLabelX,
                0f, 0f, 0, 1,
                labelPaint, gridPaint, rulerBrightPaint);
        AxisTickLabels.draw(c, axisY, gridLabelY,
                0f, 0f, 1, 1,
                labelPaint, gridPaint, rulerBrightPaint);
    }

    void plotCrossline(Canvas c, double x, double y, Paint crossPaint) {
        float cX, cY;
        cX = (float)axisX.pixelFromV(x);  // cursorFreq
        cY = (float)axisY.pixelFromV(y);  // cursorDB
        c.drawLine(cX, 0, cX, (float)axisY.nCanvasPixel, crossPaint);  //canvasHeight
        c.drawLine(0, cY, (float)axisX.nCanvasPixel, cY, crossPaint);  // cursorPaint
    }
}
