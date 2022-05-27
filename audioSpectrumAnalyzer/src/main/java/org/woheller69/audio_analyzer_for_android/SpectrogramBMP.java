package org.woheller69.audio_analyzer_for_android;

import static java.lang.Math.log10;
import static java.lang.Math.pow;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;

import java.util.Arrays;

/**
 * Plot the raw spectrogram BMP.
 */

class SpectrogramBMP {
    final static String TAG = "SpectrogramBMP";

    private int[] cma = ColorMapArray.hot;
    double dBLowerBound = -120;
    double dBUpperBound = 0.0;

    SpectrumCompressStore spectrumStore = new SpectrumCompressStore();
    private PlainLinearSpamBMP linBmp = new PlainLinearSpamBMP();
    private LogFreqSpectrogramBMP logBmp = new LogFreqSpectrogramBMP();
    private LogSegFreqSpectrogramBMP logSegBmp = new LogSegFreqSpectrogramBMP();

    private int bmpWidthDefault = 1000;
    private int bmpWidthMax = 2000;
    private int bmpWidth = bmpWidthDefault;
    private boolean bNeedRebuildLogBmp = false;

    enum LogAxisPlotMode { REPLOT, SEGMENT }
    LogAxisPlotMode logAxisMode = LogAxisPlotMode.REPLOT;

    private ScreenPhysicalMapping axisF = null;

    void init(int _nFreq, int _nTime, ScreenPhysicalMapping _axis) {
        bmpWidth = calBmpWidth(_axis);
        synchronized (this) {
            spectrumStore.init(_nFreq, _nTime);
        }
        synchronized (this) {
            linBmp.init(_nFreq, _nTime);
        }
        if (logAxisMode == LogAxisPlotMode.REPLOT) {
            synchronized (this) {
                logBmp.init(_nFreq, _nTime, _axis, bmpWidth);
            }
        } else {
            synchronized (this) {
                logSegBmp.init(_nFreq, _nTime, _axis);
            }
        }
        axisF = _axis;
    }

    void rebuildLinearBMP() {  // For state restore
        linBmp.rebuild(spectrumStore);
    }

    void rebuildAllBMP() {
        if (spectrumStore.dbShortArray.length == 0) return;
        rebuildLinearBMP();
        if (logAxisMode == LogAxisPlotMode.REPLOT) {
            logBmp.rebuild(spectrumStore, logBmp.axis);
            bNeedRebuildLogBmp = false;
        } else {
            logSegBmp.rebuild(spectrumStore, axisF);
        }
    }

    void setLogAxisMode(LogAxisPlotMode _mode) {
        if (logAxisMode != _mode) {
            if (_mode == LogAxisPlotMode.REPLOT) {
                int nFreq = logSegBmp.nFreq;
                int nTime = logSegBmp.nTime;
                logSegBmp = new LogSegFreqSpectrogramBMP();  // Release
                logBmp.init(nFreq, nTime, axisF, bmpWidth);
                logBmp.rebuild(spectrumStore, logBmp.axis);
                bNeedRebuildLogBmp = false;
            } else {
                int nFreq = logBmp.nFreq;
                int nTime = logBmp.nTime;
                logBmp = new LogFreqSpectrogramBMP();  // Release
                logSegBmp.init(nFreq, nTime, axisF);
                logSegBmp.rebuild(spectrumStore, axisF);
            }
            logAxisMode = _mode;
        }
    }

    private int calBmpWidth(ScreenPhysicalMapping _axisFreq) {
        int tmpBmpWidth = (int) _axisFreq.nCanvasPixel;
        if (tmpBmpWidth <= 1) tmpBmpWidth = bmpWidthDefault;
        if (tmpBmpWidth > 2000) tmpBmpWidth = bmpWidthMax;
        return tmpBmpWidth;
    }

    void updateAxis(ScreenPhysicalMapping _axisFreq) {
        if (_axisFreq.mapType == ScreenPhysicalMapping.Type.LINEAR) {
            return;  // a linear axis, do not update
        }
        if (logAxisMode == LogAxisPlotMode.REPLOT) {
            synchronized (this) {
                bmpWidth = calBmpWidth(_axisFreq);
                logBmp.init(logBmp.nFreq, logBmp.nTime, _axisFreq, bmpWidth);
                bNeedRebuildLogBmp = true;
            }
        } else {
            synchronized (this) {
                logSegBmp.init(logSegBmp.nFreq, logSegBmp.nTime, _axisFreq);
            }
        }
        axisF = _axisFreq;
    }

    void updateZoom() {
        if (logAxisMode == LogAxisPlotMode.REPLOT) {
            bNeedRebuildLogBmp = true;
        }
    }

    void setColorMap(String colorMapName) {
        cma = ColorMapArray.selectColorMap(colorMapName);
        // Refresh if we have spectrogram data
        rebuildAllBMP();
    }

    // return value between 0 .. nLevel - 1
    private static int levelFromDB(double d, double lowerBound, double upperBound, int nLevel) {
        if (d >= upperBound) {
            return 0;
        }
        if (d <= lowerBound || Double.isInfinite(d) || Double.isNaN(d)) {
            return nLevel - 1;
        }
        return (int)(nLevel * (upperBound - d) / (upperBound - lowerBound));
    }

    private int colorFromDBLevel(short d) {  // 0 <= d <= 32767
        return colorFromDB(AnalyzerGraphic.maxDB - (AnalyzerGraphic.maxDB - AnalyzerGraphic.minDB) / 32768.0 * d);
//        return cma[(int)(cma.length / 32768.0 * d)];
    }

    private int colorFromDB(double d) {
        // Assume in ARGB format. Always set alpha=0xff for drawBitmap to work correctly.
        return cma[levelFromDB(d, dBLowerBound, dBUpperBound, cma.length)] + 0xff000000;
    }

    void fill(double[] db) {
        synchronized (this) {
            spectrumStore.fill(db);
            linBmp.fill(db);
            if (logAxisMode == LogAxisPlotMode.REPLOT) {
                logBmp.fill(db);
            } else {
                logSegBmp.fill(db);
            }
        }
    }

    void draw(Canvas c, ScreenPhysicalMapping.Type freqAxisType, SpectrogramPlot.TimeAxisMode showModeSpectrogram,
              Paint smoothBmpPaint, Paint cursorTimePaint) {
        // drawBitmap(int[] ...) was deprecated in API level 21.
        // public void drawBitmap (int[] colors, int offset, int stride, float x, float y,
        //                         int width, int height, boolean hasAlpha, Paint paint)
        // http://developer.android.com/reference/android/graphics/Canvas.html#drawBitmap(int[], int, int, float, float, int, int, boolean, android.graphics.Paint)
        // Consider use Bitmap
        // http://developer.android.com/reference/android/graphics/Bitmap.html#setPixels(int[], int, int, int, int, int, int)

        int pt;
        int lineLen;

        if (freqAxisType == ScreenPhysicalMapping.Type.LOG) {
            // Reference answer
//            c.save();
//            c.scale(1, 0.5f);
//            logBmp.draw(c);
//            if (showModeSpectrogram == TimeAxisMode.OVERWRITE) {
//                c.drawLine(0, logBmp.bmPt, logBmp.nFreq, logBmp.bmPt, cursorTimePaint);
//            }
//            c.restore();

//            c.save();
//            c.translate(0, nTimePoints/2);
//            c.scale((float)nFreqPoints / logSegBmp.bmpWidth, 0.5f);
//            logSegBmp.draw(c);
//            if (showModeSpectrogram == TimeAxisMode.OVERWRITE) {
//                c.drawLine(0, logSegBmp.bmPt, logSegBmp.bmpWidth, logSegBmp.bmPt, cursorTimePaint);
//            }
//            c.restore();

            synchronized (this) {
                if (logAxisMode == LogAxisPlotMode.REPLOT) {
                    // Draw in log, method: draw by axis
                    if (bNeedRebuildLogBmp) {
                        logBmp.rebuild(spectrumStore, axisF);
                        bNeedRebuildLogBmp = false;
                    }
                    logBmp.draw(c, showModeSpectrogram, smoothBmpPaint);
                    pt = logBmp.bmPt;
                    lineLen = logBmp.bmpWidth;
                } else {
                    // Draw in log, method: segmentation log freq BMP
                    c.scale((float) logSegBmp.nFreq / logSegBmp.bmpWidth, 1.0f);
                    logSegBmp.draw(c, showModeSpectrogram, axisF, smoothBmpPaint);
                    pt = logSegBmp.bmPt;
                    lineLen = logSegBmp.bmpWidth;
                }
            }
        } else {
            synchronized (this) {
                linBmp.draw(c, showModeSpectrogram, smoothBmpPaint);
            }
            pt = linBmp.iTimePointer;
            lineLen = linBmp.nFreq;
        }

        // new data line
        if (showModeSpectrogram == SpectrogramPlot.TimeAxisMode.OVERWRITE) {
            c.drawLine(0, pt, lineLen, pt, cursorTimePaint);
        }
    }

    // Save spectrum in a lower resolution short[] (0~32767) instead of double[]
    class SpectrumCompressStore {
        private final static String TAG = "SpectrumCompressStore:";
        int nFreq;
        int nTime;
        int iTimePointer;
        short[] dbShortArray = new short[0];  // java don't have unsigned short

        void init(int _nFreq, int _nTime) {
            // _nFreq == 2^n
            if (dbShortArray.length != (_nFreq + 1) * _nTime) {
                dbShortArray = new short[(_nFreq + 1) * _nTime];
            }
            if (nFreq != _nFreq || nTime != _nTime) {
                clear();
            }
            nFreq = _nFreq;
            nTime = _nTime;
        }

        void clear() {
            Arrays.fill(dbShortArray, (short) 32767);
            iTimePointer = 0;
        }

        void fill(double[] db) {
            if (db.length - 1 != nFreq) {
                Log.e(TAG, "fill(): WTF");
                return;
            }
            int p0 = (nFreq + 1) * iTimePointer;
            for (int i = 0; i <= nFreq; i++) {
                dbShortArray[p0 + i] = (short) levelFromDB(db[i], AnalyzerGraphic.minDB, AnalyzerGraphic.maxDB, 32768);
            }
            iTimePointer++;
            if (iTimePointer >= nTime) iTimePointer = 0;
        }
    }

    private class PlainLinearSpamBMP {
        private final static String TAG = "PlainLinearSpamBMP:";
        private int nFreq;
        private int nTime;

        int[] spectrogramColors = new int[0];  // int:ARGB, nFreqPoints columns, nTimePoints rows
        int[] spectrogramColorsShifting;       // temporarily of spectrogramColors for shifting mode
        int iTimePointer;          // pointer to the row to be filled (row major)

        void init(int _nFreq, int _nTime) {
            boolean bNeedClean = nFreq != _nFreq;
            if (spectrogramColors.length != _nFreq * _nTime) {
                spectrogramColors = new int[_nFreq * _nTime];
                spectrogramColorsShifting = new int[_nFreq * _nTime];
                bNeedClean = true;
            }
            if (!bNeedClean && iTimePointer >= _nTime) {
                Log.w(TAG, "setupSpectrogram(): Should not happen!!");
                Log.i(TAG, "setupSpectrogram(): iTimePointer=" + iTimePointer + "  nFreqPoints=" + _nFreq + "  nTimePoints=" + _nTime);
                bNeedClean = true;
            }
            if (bNeedClean) {
                clear();
            }
            nFreq = _nFreq;
            nTime = _nTime;
        }

        void clear() {
            Arrays.fill(spectrogramColors, 0);
            iTimePointer = 0;
        }

        void fill(double[] db) {
            if (db.length - 1 != nFreq) {
                Log.e(TAG, "fill(): WTF");
                return;
            }
            int pRef = iTimePointer * nFreq - 1;
            for (int i = 1; i < db.length; i++) {  // no DC term
                spectrogramColors[pRef + i] = colorFromDB(db[i]);
            }
            iTimePointer++;
            if (iTimePointer >= nTime) iTimePointer = 0;
        }

        double[] dbTmp = new double[0];

        void rebuild(SpectrumCompressStore dbLevelPic) {
            nFreq = dbLevelPic.nFreq;
            nTime = dbLevelPic.nTime;
            init(nFreq, nTime);  // reallocate

            if (dbTmp.length != nFreq + 1) {
                dbTmp = new double[nFreq + 1];
            }
            iTimePointer = 0;
            for (int k = 0; k < nTime; k++) {
                int p0 = (nFreq + 1) * k;
                for (int i = 0; i <= nFreq; i++) {  // See colorFromDBLevel
                    dbTmp[i] = AnalyzerGraphic.maxDB - (AnalyzerGraphic.maxDB - AnalyzerGraphic.minDB) / 32768.0 * dbLevelPic.dbShortArray[p0 + i];
                }
                fill(dbTmp);
            }
            iTimePointer = dbLevelPic.iTimePointer;
        }

        void draw(Canvas c, SpectrogramPlot.TimeAxisMode showModeSpectrogram, Paint smoothBmpPaint) {
            if (spectrogramColors.length == 0) return;
            if (showModeSpectrogram == SpectrogramPlot.TimeAxisMode.SHIFT) {
                System.arraycopy(spectrogramColors, 0, spectrogramColorsShifting,
                        (nTime - iTimePointer) * nFreq, iTimePointer * nFreq);
                System.arraycopy(spectrogramColors, iTimePointer * nFreq, spectrogramColorsShifting,
                        0, (nTime - iTimePointer) * nFreq);
                c.drawBitmap(spectrogramColorsShifting, 0, nFreq, 0, 0,
                        nFreq, nTime, false, smoothBmpPaint);
            } else {
                c.drawBitmap(spectrogramColors, 0, nFreq, 0, 0,
                        nFreq, nTime, false, smoothBmpPaint);
            }
        }
    }

    // Actually capable to show both Linear and Log spectrogram
    private class LogFreqSpectrogramBMP {
        final static String TAG = "LogFreqSpectrogramBMP:";
        int nFreq = 0;
        int nTime = 0;
        int[] bm = new int[0];   // elements are in "time major" order.
        int[] bmShiftCache = new int[0];
        int bmPt = 0;
        int bmpWidth = 1000;     // width in Frequency direction
        int[] mapFreqToPixL = new int[0];  // map that a frequency point should map to bm[]
        int[] mapFreqToPixH = new int[0];
        ScreenPhysicalMapping axis = null;

        LogFreqSpectrogramBMP() {
        }

        // like setupSpectrogram()
        void init(int _nFreq, int _nTime, ScreenPhysicalMapping _axis, int _bmpWidth) {
            // _nFreq == 2^n
            if (bm.length != _bmpWidth * _nTime) {
                bm = new int[_bmpWidth * _nTime];
                bmShiftCache = new int[bm.length];
            }
            if (mapFreqToPixL.length != _nFreq + 1) {
                Log.d(TAG, "init(): New");
                mapFreqToPixL = new int[_nFreq + 1];
                mapFreqToPixH = new int[_nFreq + 1];
            }
            if (bmpWidth != _bmpWidth || nTime != _nTime) {
                clear();
            }  // else only update axis
            bmpWidth = _bmpWidth;
            nFreq = _nFreq;
            nTime = _nTime;
            if (_axis == null) {
                Log.e(TAG, "init(): damn: axis == null");
                return;
            }
            if (axis != _axis) {  // not itself
                axis = new ScreenPhysicalMapping(_axis);
            }
            if (_axis.vLowerBound > _axis.vUpperBound) {  // ensure axis.vLowerBound < axis.vUpperBound
                axis.reverseBounds();
            }
            double dFreq = axis.vUpperBound / nFreq;
//            Log.v(TAG, "init(): axis.vL=" + axis.vLowerBound + "  axis.vU=" + axis.vUpperBound + "  axis.nC=" + axis.nCanvasPixel);
            for (int i = 0; i <= nFreq; i++) {  // freq = i * dFreq
                // do not show DC component (xxx - 1).
                mapFreqToPixL[i] = (int) Math.floor(axis.pixelFromV((i - 0.5) * dFreq) / axis.nCanvasPixel * bmpWidth);
                mapFreqToPixH[i] = (int) Math.floor(axis.pixelFromV((i + 0.5) * dFreq) / axis.nCanvasPixel * bmpWidth);
                if (mapFreqToPixH[i] >= bmpWidth) mapFreqToPixH[i] = bmpWidth - 1;
                if (mapFreqToPixH[i] < 0) mapFreqToPixH[i] = 0;
                if (mapFreqToPixL[i] >= bmpWidth) mapFreqToPixL[i] = bmpWidth - 1;
                if (mapFreqToPixL[i] < 0) mapFreqToPixL[i] = 0;
//                Log.i(TAG, "init(): [" + i + "]  L = " + axis.pixelNoZoomFromV((i-0.5f)*dFreq) + "  H = " + axis.pixelNoZoomFromV((i+0.5f)*dFreq));
            }
        }

        void clear() {
            Arrays.fill(bm, 0);
            bmPt = 0;
        }

        void fill(double[] db) {
            if (db.length - 1 != nFreq) {
                Log.e(TAG, "fill(): WTF");
                return;
            }
            int bmP0 = bmPt * bmpWidth;
            double maxDB;
            int i = 1;  // skip DC component(i = 0).
            while (i <= nFreq) {
                maxDB = db[i];
                int j = i + 1;
                while (j <= nFreq && mapFreqToPixL[i] + 1 == mapFreqToPixH[j]) {
                    // If multiple frequency points map to one pixel, show only the maximum.
                    if (db[j] > maxDB) maxDB = db[j];
                    j++;
                }
                int c = colorFromDB(maxDB);
                for (int k = mapFreqToPixL[i]; k < mapFreqToPixH[i]; k++) {
                    bm[bmP0 + k] = c;
                }
                i = j;
            }
            bmPt++;
            if (bmPt >= nTime) bmPt = 0;
        }

        private void fill(short[] dbLevel) {
            if (dbLevel.length - 1 != nFreq) {
                Log.e(TAG, "fill(): WTF");
                return;
            }
            int bmP0 = bmPt * bmpWidth;
            short maxDBLevel;
            int i = 1;  // skip DC component(i = 0).
            while (i <= nFreq) {
                maxDBLevel = dbLevel[i];
                int j = i + 1;
                while (j <= nFreq && mapFreqToPixL[i] + 1 == mapFreqToPixH[j]) {
                    // If multiple frequency points map to one pixel, show only the maximum.
                    if (dbLevel[j] < maxDBLevel) maxDBLevel = dbLevel[j];
                    j++;
                }
                int c = colorFromDBLevel(maxDBLevel);
                for (int k = mapFreqToPixL[i]; k < mapFreqToPixH[i]; k++) {
                    bm[bmP0 + k] = c;
                }
                i = j;
            }
            bmPt++;
            if (bmPt >= nTime) bmPt = 0;
        }

        short[] dbLTmp = new short[0];

        // re-calculate whole spectrogram, according to dbLevelPic under axis _axis
        void rebuild(SpectrumCompressStore dbLevelPic, ScreenPhysicalMapping _axisF) {
            nFreq = dbLevelPic.nFreq;
            nTime = dbLevelPic.nTime;
            init(nFreq, nTime, _axisF, bmpWidth);  // reallocate and rebuild index

            if (dbLTmp.length != nFreq + 1) {
                dbLTmp = new short[nFreq + 1];
            }
            bmPt = 0;
            for (int k = 0; k < nTime; k++) {
                System.arraycopy(dbLevelPic.dbShortArray, (nFreq + 1) * k, dbLTmp, 0, (nFreq + 1));
                fill(dbLTmp);
            }
            bmPt = dbLevelPic.iTimePointer;
        }

        // draw to a regime size nFreq * nTime
        void draw(Canvas c, SpectrogramPlot.TimeAxisMode showModeSpectrogram, Paint smoothBmpPaint) {
            if (bm.length == 0) return;
            c.scale((float) nFreq / bmpWidth, 1f);
            if (showModeSpectrogram == SpectrogramPlot.TimeAxisMode.SHIFT) {
                System.arraycopy(bm, 0, bmShiftCache, (nTime - bmPt) * bmpWidth, bmPt * bmpWidth);
                System.arraycopy(bm, bmPt * bmpWidth, bmShiftCache, 0, (nTime - bmPt) * bmpWidth);
                c.drawBitmap(bmShiftCache, 0, bmpWidth, 0.0f, 0.0f,
                        bmpWidth, nTime, false, smoothBmpPaint);
            } else {
                c.drawBitmap(bm, 0, bmpWidth, 0.0f, 0.0f,
                        bmpWidth, nTime, false, smoothBmpPaint);
            }
        }
    }

    private class LogSegFreqSpectrogramBMP {
        final static String TAG = "LogSeg..:";
        int nFreq = 0;
        int nTime = 0;
        int[] bm = new int[0];   // elements are in "time major" order.
        int[] bmShiftCache = new int[0];
        int bmPt = 0;
        double[] iFreqToPix = new double[0];
        double[] pixelAbscissa = new double[0];
        double[] freqAbscissa = new double[0];
        int bmpWidth = 0;
        final double incFactor = 2;
        double interpolationFactor = 2.0; // the extra factor (1.0~2.0) here is for sharper image.

        void init(int _nFreq, int _nTime, ScreenPhysicalMapping _axis) {
            if (_nFreq == 0 || _nTime == 0 || Math.max(_axis.vLowerBound, _axis.vUpperBound) == 0) {
                return;
            }
            // Note that there is limit for bmpWidth, i.e. Canvas.getMaximumBitmapHeight
            // https://developer.android.com/reference/android/graphics/Canvas.html#getMaximumBitmapHeight%28%29
            // Seems that this limit is at about 4096.
            // In case that this limit is reached, we might break down pixelAbscissa[] to smaller pieces.
            bmpWidth = (int) (_nFreq * incFactor * interpolationFactor);
            if (bm.length != bmpWidth * _nTime) {
                bm = new int[bmpWidth * _nTime];
                bmShiftCache = new int[bm.length];
            }
            if (nFreq != _nFreq || nTime != _nTime) {
                clear();
            }  // else only update axis and mapping
            nFreq = _nFreq;
            nTime = _nTime;

            double maxFreq = Math.max(_axis.vLowerBound, _axis.vUpperBound);
            double minFreq = maxFreq / nFreq;
            double dFreq = maxFreq / nFreq;

            int nSegment = (int) (Math.log((maxFreq + 0.1) / minFreq) / Math.log(incFactor)) + 1;
            Log.d(TAG, "nFreq = " + nFreq + "  dFreq = " + dFreq + "  nSegment = " + nSegment + "  bmpWidth = " + bmpWidth);

            pixelAbscissa = new double[nSegment + 1];
            freqAbscissa = new double[nSegment + 1];
            pixelAbscissa[0] = 0;
            freqAbscissa[0] = minFreq;  // should be minFreq
            //Log.v(TAG, "pixelAbscissa[" + 0 + "] = " + pixelAbscissa[0] + "  freqAbscissa[i] = " + freqAbscissa[0]);
            Log.v(TAG, "pixelAbscissa[" + 0 + "] = " + pixelAbscissa[0]);
            for (int i = 1; i <= nSegment; i++) {
                /**  Mapping [0, 1] -> [fmin, fmax]
                 *   /  fmax  \ x
                 *   | ------ |   * fmin
                 *   \  fmin  /
                 *   This makes the "pixels"(x) more uniformly map to frequency points in logarithmic scale.
                 */
                pixelAbscissa[i] = (pow(maxFreq / minFreq, (double) i / nSegment) * minFreq - minFreq) / (maxFreq - minFreq);
                pixelAbscissa[i] = Math.floor(pixelAbscissa[i] * bmpWidth);   // align to pixel boundary
                freqAbscissa[i] = pixelAbscissa[i] / bmpWidth * (maxFreq - minFreq) + minFreq;
                Log.v(TAG, "pixelAbscissa[" + i + "] = " + pixelAbscissa[i] + "  freqAbscissa[i] = " + freqAbscissa[i]);
            }

            // Map between [pixelAbscissa[i-1]..pixelAbscissa[i]] and [freqAbscissa[i-1]..freqAbscissa[i]]
            iFreqToPix = new double[nFreq + 1];
            iFreqToPix[0] = 0;
            double eps = 1e-7;  // 7 + log10(8192) < 15
            int iF = 1;
            ScreenPhysicalMapping axisSeg = new ScreenPhysicalMapping(1.0, minFreq, maxFreq, ScreenPhysicalMapping.Type.LOG);
            for (int i = 1; i <= nSegment; i++) {
                axisSeg.setNCanvasPixel(Math.round(pixelAbscissa[i] - pixelAbscissa[i - 1]));  // should work without round()
                axisSeg.setBounds(freqAbscissa[i - 1], freqAbscissa[i]);
                Log.v(TAG, "axisSeg[" + i + "] .nC = " + axisSeg.nCanvasPixel + "  .vL = " + axisSeg.vLowerBound + "  .vU = " + axisSeg.vUpperBound);
                while ((iF + 0.5) * dFreq <= freqAbscissa[i] + eps) {
                    // upper bound of the pixel position of frequency point iF
                    iFreqToPix[iF] = axisSeg.pixelFromV((iF + 0.5) * dFreq) + pixelAbscissa[i - 1];
//                    Log.d(TAG, "seg = " + i + "  iFreqToPix[" + iF + "] = " + iFreqToPix[iF]);
                    iF++;
                }
            }
            if (iF < nFreq) {  // last point
                iFreqToPix[nFreq] = pixelAbscissa[nSegment];
            }
        }

        void clear() {
            Arrays.fill(bm, 0);
            bmPt = 0;
        }

        double[] dbPixelMix = new double[0];

        void fill(double[] db) {
            if (db.length - 1 != nFreq) {
                Log.e(TAG, "full(): WTF");
                return;
            }
            if (dbPixelMix.length != bmpWidth) {
                dbPixelMix = new double[bmpWidth];
            }
            Arrays.fill(dbPixelMix, 0.0);
            double b0 = iFreqToPix[0];
            for (int i = 1; i <= nFreq; i++) {
                // assign color to pixel iFreqToPix[i-1] .. iFreqToPix[i]
                double b1 = iFreqToPix[i];
                if ((int) b0 == (int) b1) {
                    dbPixelMix[(int) b0] += db[i] * (b1 - b0);
                    continue;
                }
                if (b0 % 1 != 0) {  // mix color
                    //dbPixelMix[(int)b0] += db[i] * (1 - b0 % 1);  // dB mean
                    double db0 = db[i - 1];  // i should > 1
                    double db1 = db[i];
                    dbPixelMix[(int) b0] = 10 * log10(pow(10, db0 / 10) * (b0 % 1) + pow(10, db1 / 10) * (1 - b0 % 1));  // energy mean
                }
                for (int j = (int) Math.ceil(b0); j < (int) b1; j++) {
                    dbPixelMix[j] = db[i];
                }
//                if (b1 % 1 > 0) {  // avoid out of bound (b1 == bmpWidth)
//                    dbPixelMix[(int) b1] += db[i] * (b1 % 1);
//                }
                b0 = b1;
            }
            int bmP0 = bmPt * bmpWidth;
            for (int i = 0; i < bmpWidth; i++) {
                bm[bmP0 + i] = colorFromDB(dbPixelMix[i]);
            }
            bmPt++;
            if (bmPt >= nTime) bmPt = 0;
        }

        double[] dbTmp = new double[0];

        void rebuild(SpectrumCompressStore dbLevelPic, ScreenPhysicalMapping _axisF) {
            nFreq = dbLevelPic.nFreq;
            nTime = dbLevelPic.nTime;
            init(nFreq, nTime, _axisF);  // reallocate and rebuild index

            if (dbTmp.length != nFreq + 1) {
                dbTmp = new double[nFreq + 1];
            }
            bmPt = 0;
            for (int k = 0; k < nTime; k++) {
                int p0 = (nFreq + 1) * k;
                for (int i = 0; i <= nFreq; i++) {  // See colorFromDBLevel
                    dbTmp[i] = AnalyzerGraphic.maxDB - (AnalyzerGraphic.maxDB - AnalyzerGraphic.minDB) / 32768.0 * dbLevelPic.dbShortArray[p0 + i];
                }
                fill(dbTmp);
            }
            bmPt = dbLevelPic.iTimePointer;
        }

        String st1old;  // for debug
        String st2old;  // for debug

        void draw(Canvas c, SpectrogramPlot.TimeAxisMode showModeSpectrogram, ScreenPhysicalMapping axisFreq, Paint smoothBmpPaint) {
            if (bm.length == 0 || axisFreq.nCanvasPixel == 0) {
                Log.d(TAG, "draw(): what.....");
                return;
            }
            int i1 = pixelAbscissa.length - 1;
            String st1 = "draw():  pixelAbscissa[" + (i1 - 1) + "]=" + pixelAbscissa[i1 - 1] + "  pixelAbscissa[" + i1 + "]=" + pixelAbscissa[i1] + "  bmpWidth=" + bmpWidth;
            String st2 = "draw():  axis.vL=" + axisFreq.vLowerBound + "  axis.vU=" + axisFreq.vUpperBound + "  axisFreq.nC=" + axisFreq.nCanvasPixel + "  nTime=" + nTime;
            if (!st1.equals(st1old)) {
                Log.v(TAG, st1);
                Log.v(TAG, st2);
                st1old = st1;
                st2old = st2;
            }
            int[] bmTmp = bm;
            if (showModeSpectrogram == SpectrogramPlot.TimeAxisMode.SHIFT) {
                System.arraycopy(bm, 0, bmShiftCache, (nTime - bmPt) * bmpWidth, bmPt * bmpWidth);
                System.arraycopy(bm, bmPt * bmpWidth, bmShiftCache, 0, (nTime - bmPt) * bmpWidth);
                bmTmp = bmShiftCache;
            }
            for (int i = 1; i < pixelAbscissa.length; i++) {  // draw each segmentation
                c.save();
                double f1 = freqAbscissa[i - 1];
                double f2 = freqAbscissa[i];
                double p1 = axisFreq.pixelNoZoomFromV(f1);
                double p2 = axisFreq.pixelNoZoomFromV(f2);
                if (axisFreq.vLowerBound > axisFreq.vUpperBound) {
                    p1 = axisFreq.nCanvasPixel - p1;
                    p2 = axisFreq.nCanvasPixel - p2;
                }
                double widthFactor = (p2 - p1) / (pixelAbscissa[i] - pixelAbscissa[i - 1]) * (bmpWidth / axisFreq.nCanvasPixel);
                // Log.v(TAG, "draw():  f1=" + f1 + "  f2=" + f2 + "  p1=" + p1 + "  p2=" + p2 + "  widthFactor=" + widthFactor + "  modeInt=" + axisFreq.mapType);
                c.scale((float) widthFactor, 1);
                c.drawBitmap(bmTmp, (int) pixelAbscissa[i - 1], bmpWidth, (float)(p1 / axisFreq.nCanvasPixel * bmpWidth / widthFactor), 0.0f,
                        (int) (pixelAbscissa[i] - pixelAbscissa[i - 1]), nTime, false, smoothBmpPaint);
                c.restore();
            }
        }
    }
}
