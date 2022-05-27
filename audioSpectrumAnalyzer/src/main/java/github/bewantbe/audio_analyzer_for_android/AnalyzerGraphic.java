/* Copyright 2011 Google Inc.
 *
 *Licensed under the Apache License, Version 2.0 (the "License");
 *you may not use this file except in compliance with the License.
 *You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *Unless required by applicable law or agreed to in writing, software
 *distributed under the License is distributed on an "AS IS" BASIS,
 *WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *See the License for the specific language governing permissions and
 *limitations under the License.
 *
 * @author Stephen Uhler
 *
 * 2014 Eddy Xiao <bewantbe@gmail.com>
 * GUI extensively modified.
 * Add some naive auto refresh rate control logic.
 */

package github.bewantbe.audio_analyzer_for_android;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Custom view to draw the FFT graph
 */

public class AnalyzerGraphic extends View {
    private final String TAG = "AnalyzerGraphic:";
    private Context context;
    private double xZoom, yZoom;     // horizontal and vertical scaling
    private double xShift, yShift;   // horizontal and vertical translation, in unit 1 unit
    static final double minDB = -144f;    // hard lower bound for dB
    static final double maxDB = 12f;      // hard upper bound for dB

    private int canvasWidth, canvasHeight;   // size of my canvas
    private int[] myLocation = {0, 0}; // window location on screen
    private volatile static boolean isBusy = false;
    private double freq_lower_bound_for_log = 0f;
    private double[] savedDBSpectrum = new double[0];
    static final int VIEW_RANGE_DATA_LENGTH = 6;

    SpectrumPlot    spectrumPlot;
    SpectrogramPlot spectrogramPlot;

    private PlotMode showMode = PlotMode.SPECTRUM;

    enum PlotMode {  // java's enum type is inconvenient
        SPECTRUM(0), SPECTROGRAM(1);

        private final int value;
        PlotMode(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    public AnalyzerGraphic(Context context, AttributeSet attrs, int defStyle) {
        // https://developer.android.com/training/custom-views/create-view.html
        super(context, attrs, defStyle);
        setup(context);
    }

    public AnalyzerGraphic(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup(context);
    }

    public AnalyzerGraphic(Context context) {
        super(context);
        setup(context);
    }

    private void setup(Context _context) {
        context = _context;
        Log.i(TAG, "in setup()");

        xZoom  = 1f;
        xShift = 0f;
        yZoom  = 1f;
        yShift = 0f;
        canvasWidth = canvasHeight = 0;

        // Demo of full initialization
        spectrumPlot    = new SpectrumPlot(context);
        spectrogramPlot = new SpectrogramPlot(context);

        spectrumPlot   .setCanvas(canvasWidth, canvasHeight, null);
        spectrogramPlot.setCanvas(canvasWidth, canvasHeight, null);

        spectrumPlot   .setZooms(xZoom, xShift, yZoom, yShift);
        spectrogramPlot.setZooms(xZoom, xShift, yZoom, yShift);

        Resources res = _context.getResources();
        spectrumPlot.axisY.vLowerBound = Float.parseFloat(res.getString(R.string.max_DB_range));
    }

    AnalyzerParameters analyzerParamCache;

    void setupAxes(AnalyzerParameters analyzerParam) {
        int sampleRate       = analyzerParam.sampleRate;
        int fftLen           = analyzerParam.fftLen;
        int nAve             = analyzerParam.nFFTAverage;
        double timeDurationE = analyzerParam.spectrogramDuration;

        freq_lower_bound_for_log = (double)sampleRate/fftLen;
        double freq_lower_bound_local = 0;
        if (spectrumPlot.axisX.mapType == ScreenPhysicalMapping.Type.LOG) {
            freq_lower_bound_local = freq_lower_bound_for_log;
        }
        // Spectrum
        double axisBounds[] = new double[]{freq_lower_bound_local, 0.0, sampleRate/2.0, spectrumPlot.axisY.vUpperBound};
        Log.i(TAG, "setupAxes(): W=" + canvasWidth + "  H=" + canvasHeight + "  dB=" + spectrumPlot.axisY.vUpperBound);
        spectrumPlot.setCanvas(canvasWidth, canvasHeight, axisBounds);

        // Spectrogram
        freq_lower_bound_local = 0;
        if (spectrogramPlot.axisFreq.mapType == ScreenPhysicalMapping.Type.LOG) {
            freq_lower_bound_local = freq_lower_bound_for_log;
        }
        if (spectrogramPlot.showFreqAlongX) {
            axisBounds = new double[]{freq_lower_bound_local, 0.0, sampleRate/2.0, timeDurationE * nAve};
        } else {
            axisBounds = new double[]{0.0, sampleRate/2.0, timeDurationE * nAve, freq_lower_bound_local};
        }
        spectrogramPlot.setCanvas(canvasWidth, canvasHeight, axisBounds);

        analyzerParamCache = analyzerParam;
    }

    // Call this when settings changed.
    void setupPlot(AnalyzerParameters analyzerParam) {
        setupAxes(analyzerParam);
        spectrogramPlot.setupSpectrogram(analyzerParam);
    }

    void setAxisModeLinear(String mode) {
        boolean b = mode.equals("linear");  // see also layout/*.xml
        ScreenPhysicalMapping.Type mapType;
        GridLabel.Type gridType;
        if (b) {
            mapType = ScreenPhysicalMapping.Type.LINEAR;
            gridType = GridLabel.Type.FREQ;
        } else {
            mapType = ScreenPhysicalMapping.Type.LOG;
            gridType = mode.equals("note") ? GridLabel.Type.FREQ_NOTE : GridLabel.Type.FREQ_LOG;
        }
        spectrumPlot   .setFreqAxisMode(mapType, freq_lower_bound_for_log, gridType);
        spectrogramPlot.setFreqAxisMode(mapType, freq_lower_bound_for_log, gridType);
        if (showMode == PlotMode.SPECTRUM) {
            xZoom  = spectrumPlot.axisX.getZoom();
            xShift = spectrumPlot.axisX.getShift();
        } else if (showMode == PlotMode.SPECTROGRAM) {
            if (spectrogramPlot.showFreqAlongX) {
                xZoom  = spectrogramPlot.axisFreq.getZoom();
                xShift = spectrogramPlot.axisFreq.getShift();
            } else {
                yZoom  = spectrogramPlot.axisFreq.getZoom();
                yShift = spectrogramPlot.axisFreq.getShift();
            }
        }
    }

    public void setShowFreqAlongX(boolean b) {
        spectrogramPlot.setShowFreqAlongX(b);

        if (showMode == PlotMode.SPECTRUM) return;

        if (spectrogramPlot.showFreqAlongX) {
            xZoom  = spectrogramPlot.axisFreq.getZoom();
            xShift = spectrogramPlot.axisFreq.getShift();
            yZoom  = spectrogramPlot.axisTime.getZoom();
            yShift = spectrogramPlot.axisTime.getShift();
        } else {
            xZoom  = spectrogramPlot.axisTime.getZoom();
            xShift = spectrogramPlot.axisTime.getShift();
            yZoom  = spectrogramPlot.axisFreq.getZoom();
            yShift = spectrogramPlot.axisFreq.getShift();
        }
    }

    // Note: Assume setupPlot() was called once.
    public void switch2Spectrum() {
        if (showMode == PlotMode.SPECTRUM) {
            return;
        }
        Log.v(TAG, "switch2Spectrum()");
        // execute when switch from Spectrogram mode to Spectrum mode
        showMode = PlotMode.SPECTRUM;
        xZoom  = spectrogramPlot.axisFreq.getZoom();
        xShift = spectrogramPlot.axisFreq.getShift();
        if (! spectrogramPlot.showFreqAlongX) {
            xShift = 1 - 1/xZoom - xShift;
        }
        spectrumPlot.axisX.setZoomShift(xZoom, xShift);

        yZoom  = spectrumPlot.axisY.getZoom();
        yShift = spectrumPlot.axisY.getShift();
    }

    // Note: Assume setupPlot() was called once.
    public void switch2Spectrogram() {
        if (showMode == PlotMode.SPECTRUM && canvasHeight > 0) { // canvasHeight==0 means the program is just start
            Log.v(TAG, "switch2Spectrogram()");
            if (spectrogramPlot.showFreqAlongX) {
                // no need to change x scaling
                yZoom  = spectrogramPlot.axisTime.getZoom();
                yShift = spectrogramPlot.axisTime.getShift();
                spectrogramPlot.axisFreq.setZoomShift(xZoom, xShift);
            } else {
                //noinspection SuspiciousNameCombination
                yZoom = xZoom;
                yShift = 1 - 1/xZoom - xShift;         // axisFreq is reverted
                xZoom  = spectrogramPlot.axisTime.getZoom();
                xShift = spectrogramPlot.axisTime.getShift();
                spectrogramPlot.axisFreq.setZoomShift(yZoom, yShift);
            }
        }
        spectrogramPlot.spectrogramBMP.updateAxis(spectrogramPlot.axisFreq);
        spectrogramPlot.prepare();
        showMode = PlotMode.SPECTROGRAM;
    }

    double[] setViewRange(double[] _ranges, double[] rangesDefault) {
        // See AnalyzerActivity::getViewPhysicalRange() for ranges[]
        if (_ranges.length < VIEW_RANGE_DATA_LENGTH) {
            Log.i(TAG, "setViewRange(): invalid input.");
            return null;
        }

        // do not modify input parameter
        double[] ranges = new double[VIEW_RANGE_DATA_LENGTH];
        System.arraycopy(_ranges, 0, ranges, 0, VIEW_RANGE_DATA_LENGTH);

        if (rangesDefault != null) {
            // Sanity check
            if (rangesDefault.length != 2 * VIEW_RANGE_DATA_LENGTH) {
                Log.i(TAG, "setViewRange(): invalid input.");
                return null;
            }
            for (int i = 0; i < 6; i += 2) {
                if (ranges[i  ] > ranges[i+1]) {                     // order reversed
                    double t = ranges[i]; ranges[i] = ranges[i+1]; ranges[i+1] = t;
                }
                if (ranges[i  ] < rangesDefault[i+6]) ranges[i  ] = rangesDefault[i+6];  // lower  than lower bound
                if (ranges[i+1] < rangesDefault[i+6]) ranges[i+1] = rangesDefault[i+7];  // all lower  than lower bound?
                if (ranges[i  ] > rangesDefault[i+7]) ranges[i  ] = rangesDefault[i+6];  // all higher than upper bound?
                if (ranges[i+1] > rangesDefault[i+7]) ranges[i+1] = rangesDefault[i+7];  // higher than upper bound
                if (ranges[i    ] == ranges[i + 1] || Double.isNaN(ranges[i]) || Double.isNaN(ranges[i + 1])) {  // invalid input value
                    ranges[i    ] = rangesDefault[i];
                    ranges[i + 1] = rangesDefault[i + 1];
                }
            }
        }

        // Set range
        if (showMode == PlotMode.SPECTRUM) {
            spectrumPlot.axisX.setViewBounds(ranges[0], ranges[1]);
            spectrumPlot.axisY.setViewBounds(ranges[3], ranges[2]);  // reversed
        } else if (showMode == PlotMode.SPECTROGRAM) {
            if (spectrogramPlot.getSpectrogramMode() == SpectrogramPlot.TimeAxisMode.SHIFT) {
                spectrogramPlot.axisTime.setViewBounds(ranges[5], ranges[4]);
            } else {
                spectrogramPlot.axisTime.setViewBounds(ranges[4], ranges[5]);
            }
            if (spectrogramPlot.showFreqAlongX) {
                spectrogramPlot.axisFreq.setViewBounds(ranges[0], ranges[1]);
            } else {
                spectrogramPlot.axisFreq.setViewBounds(ranges[1], ranges[0]);
            }
            spectrogramPlot.spectrogramBMP.updateAxis(spectrogramPlot.axisFreq);
        }

        // Set zoom shift for view
        if (showMode == PlotMode.SPECTRUM) {
            xZoom  = spectrumPlot.axisX.getZoom();
            xShift = spectrumPlot.axisX.getShift();
            yZoom  = spectrumPlot.axisY.getZoom();
            yShift = spectrumPlot.axisY.getShift();
        } else if (showMode == PlotMode.SPECTROGRAM) {
            if (spectrogramPlot.showFreqAlongX) {
                xZoom  = spectrogramPlot.axisFreq.getZoom();
                xShift = spectrogramPlot.axisFreq.getShift();
                yZoom  = spectrogramPlot.axisTime.getZoom();
                yShift = spectrogramPlot.axisTime.getShift();
            } else {
                yZoom  = spectrogramPlot.axisFreq.getZoom();
                yShift = spectrogramPlot.axisFreq.getShift();
                xZoom  = spectrogramPlot.axisTime.getZoom();
                xShift = spectrogramPlot.axisTime.getShift();
            }
        }

        return ranges;
    }

    private void updateAxisZoomShift() {
        if (showMode == PlotMode.SPECTRUM) {
            spectrumPlot   .setZooms(xZoom, xShift, yZoom, yShift);
        } else {
            spectrogramPlot.setZooms(xZoom, xShift, yZoom, yShift);
        }
    }

    public void setSmoothRender(boolean b) {
        spectrogramPlot.setSmoothRender(b);
    }

    public PlotMode getShowMode() {
        return showMode;
    }

    public void setTimeMultiplier(int nAve) {
        spectrogramPlot.setTimeMultiplier(nAve);
    }

    public void setShowTimeAxis(boolean bSTA) {
        spectrogramPlot.setShowTimeAxis(bSTA);
    }

    public void setSpectrogramModeShifting(boolean b) {
        spectrogramPlot.setSpectrogramModeShifting(b);
    }

    void setLogAxisMode(boolean b) {
        SpectrogramBMP.LogAxisPlotMode mode = SpectrogramBMP.LogAxisPlotMode.REPLOT;
        if (!b) {
            mode = SpectrogramBMP.LogAxisPlotMode.SEGMENT;
        }
        spectrogramPlot.spectrogramBMP.setLogAxisMode(mode);
    }

//    void setSpectrogramBMPWidth(int w) {
//        spectrogramPlot.spectrogramBMP.setBmpWidth(w);
//    }

    void setColorMap(String colorMapName) {
        spectrogramPlot.setColorMap(colorMapName);
    }

    static boolean isBusy() {
        return isBusy;
    }

    static void setIsBusy(boolean b) { isBusy = b; }

    private final FPSCounter fpsCounter = new FPSCounter("AnalyzerGraphic");
//  long t_old;

    @Override
    protected void onDraw(Canvas c) {
        fpsCounter.inc();
        isBusy = true;
        if (showMode == PlotMode.SPECTRUM) {
            spectrumPlot.addCalibCurve(analyzerParamCache.micGainDB, null, analyzerParamCache.calibName);
            spectrumPlot.drawSpectrumPlot(c, savedDBSpectrum);
        } else {
            spectrogramPlot.drawSpectrogramPlot(c);
        }
        isBusy = false;
    }

    // All FFT data will enter this view through this interface
    // Will be called in another thread (SamplingLoop)
    public void saveSpectrum(double[] db) {
        synchronized (savedDBSpectrum) {  // TODO: need lock on savedDBSpectrum, but how?
            if (savedDBSpectrum == null || savedDBSpectrum.length != db.length) {
                savedDBSpectrum = new double[db.length];
            }
            System.arraycopy(db, 0, savedDBSpectrum, 0, db.length);
        }
        // TODO: Run on another thread? Lock on data ? Or use CompletionService?
        if (showMode == PlotMode.SPECTROGRAM) {
            spectrogramPlot.saveRowSpectrumAsColor(savedDBSpectrum);
        }
    }

    void setSpectrumDBLowerBound(double b) {
        spectrumPlot.axisY.vUpperBound = b;
    }

    void setSpectrogramDBLowerBound(double b) {
        spectrogramPlot.setSpectrogramDBLowerBound(b);
    }

    public void setShowLines(boolean b) {
        spectrumPlot.showLines = b;
    }

    private boolean intersects(float x, float y) {
        getLocationOnScreen(myLocation);
        return x >= myLocation[0] && y >= myLocation[1] &&
                x < myLocation[0] + getWidth() && y < myLocation[1] + getHeight();
    }

    // return true if the coordinate (x,y) is inside graphView
    public boolean setCursor(float x, float y) {
        if (intersects(x, y)) {
            x = x - myLocation[0];
            y = y - myLocation[1];
            // Convert to coordinate in axis
            if (showMode == PlotMode.SPECTRUM) {
                spectrumPlot.setCursor(x, y);
            } else {
                spectrogramPlot.setCursor(x, y);
            }
            return true;
        } else {
            return false;
        }
    }

    public void setCursorFreq(double f) {
        spectrumPlot.setCursorFreq(f,1000);
        spectrogramPlot.setCursorFreq(f);
    }

    public double getCursorFreq() {
        if (showMode == PlotMode.SPECTRUM) {
            return spectrumPlot.getCursorFreq();
        } else {
            return spectrogramPlot.getCursorFreq();
        }
    }

    public double getCursorDB() {
        if (showMode == PlotMode.SPECTRUM) {
            return spectrumPlot.getCursorDB();
        } else {
            return 0;
        }
    }

    public void hideCursor() {
        spectrumPlot.hideCursor();
        spectrogramPlot.hideCursor();
    }

    double[] getViewPhysicalRange() {
        double[] r = new double[12];
        if (getShowMode() == AnalyzerGraphic.PlotMode.SPECTRUM) {
            // fL, fU, dBL dBU, time L, time U
            r[0] = spectrumPlot.axisX.vMinInView();
            r[1] = spectrumPlot.axisX.vMaxInView();
            r[2] = spectrumPlot.axisY.vMaxInView(); // reversed
            r[3] = spectrumPlot.axisY.vMinInView();
            r[4] = 0;
            r[5] = 0;

            // Limits of fL, fU, dBL dBU, time L, time U
            r[6] = spectrumPlot.axisX.vLowerBound;
            r[7] = spectrumPlot.axisX.vUpperBound;
            r[8] = AnalyzerGraphic.minDB;
            r[9] = AnalyzerGraphic.maxDB;
            r[10]= 0;
            r[11]= 0;
        } else {
            r[0] = spectrogramPlot.axisFreq.vMinInView();
            r[1] = spectrogramPlot.axisFreq.vMaxInView();
            if (r[0] > r[1]) { double t=r[0]; r[0]=r[1]; r[1]=t; }
            r[2] = spectrogramPlot.spectrogramBMP.dBLowerBound;
            r[3] = spectrogramPlot.spectrogramBMP.dBUpperBound;
            r[4] = spectrogramPlot.axisTime.vMinInView();
            r[5] = spectrogramPlot.axisTime.vMaxInView();

            r[6] = spectrogramPlot.axisFreq.vLowerBound;
            r[7] = spectrogramPlot.axisFreq.vUpperBound;
            if (r[6] > r[7]) { double t=r[6]; r[6]=r[7]; r[7]=t; }
            r[8] = AnalyzerGraphic.minDB;
            r[9] = AnalyzerGraphic.maxDB;
            r[10]= spectrogramPlot.axisTime.vLowerBound;
            r[11]= spectrogramPlot.axisTime.vUpperBound;
        }
        for (int i=6; i<r.length; i+=2) {
            if (r[i] > r[i+1]) {
                double t = r[i]; r[i] = r[i+1]; r[i+1] = t;
            }
        }
        return r;
    }

    public double getXZoom() {
        return xZoom;
    }

    public double getYZoom() {
        return yZoom;
    }

    public double getXShift() {
        return xShift;
    }

    public double getYShift() {
        return yShift;
    }

    public double getCanvasWidth() {
        if (showMode == PlotMode.SPECTRUM) {
            return canvasWidth;
        } else {
            return canvasWidth - spectrogramPlot.labelBeginX;
        }
    }

    public double getCanvasHeight() {
        if (showMode == PlotMode.SPECTRUM) {
            return canvasHeight;
        } else {
            return spectrogramPlot.labelBeginY;
        }
    }

    private double clamp(double x, double min, double max) {
        if (x > max) {
            return max;
        } else if (x < min) {
            return min;
        } else {
            return x;
        }
    }

    private double clampXShift(double offset) {
        return clamp(offset, 0f, 1 - 1 / xZoom);
    }

    private double clampYShift(double offset) {
        if (showMode == PlotMode.SPECTRUM) {
            // limit view to minDB ~ maxDB, assume linear in dB scale
            return clamp(offset, (maxDB - spectrumPlot.axisY.vLowerBound) / spectrumPlot.axisY.diffVBounds(),
                    (minDB - spectrumPlot.axisY.vLowerBound) / spectrumPlot.axisY.diffVBounds() - 1 / yZoom);
        } else {
            // strict restrict, y can be frequency or time.
            return clamp(offset, 0f, 1 - 1 / yZoom);
        }
    }

    public void setXShift(double offset) {
        xShift = clampXShift(offset);
        updateAxisZoomShift();
    }

    public void setYShift(double offset) {
        yShift = clampYShift(offset);
        updateAxisZoomShift();
    }

    public void resetViewScale() {
        xShift = 0;
        xZoom = 1;
        yShift = 0;
        yZoom = 1;
        updateAxisZoomShift();
    }

    private double xMidOld = 100;
    private double xDiffOld = 100;
    private double xZoomOld = 1;
    private double xShiftOld = 0;
    private double yMidOld = 100;
    private double yDiffOld = 100;
    private double yZoomOld = 1;
    private double yShiftOld = 0;

    // record the coordinate frame state when starting scaling
    public void setShiftScaleBegin(double x1, double y1, double x2, double y2) {
        xMidOld = (x1+x2)/2f;
        xDiffOld = Math.abs(x1-x2);
        xZoomOld  = xZoom;
        xShiftOld = xShift;
        yMidOld = (y1+y2)/2f;
        yDiffOld = Math.abs(y1-y2);
        yZoomOld  = yZoom;
        yShiftOld = yShift;
    }

    // Do the scaling according to the motion event getX() and getY() (getPointerCount()==2)
    public void setShiftScale(double x1, double y1, double x2, double y2) {
        double limitXZoom;
        double limitYZoom;
        if (showMode == PlotMode.SPECTRUM) {
            limitXZoom =  spectrumPlot.axisX.diffVBounds()/200f;  // limit to 200 Hz a screen
            limitYZoom = -spectrumPlot.axisY.diffVBounds()/6f;    // limit to 6 dB a screen
        } else {
            int nTimePoints = spectrogramPlot.nTimePoints;
            if (spectrogramPlot.showFreqAlongX) {
                limitXZoom = spectrogramPlot.axisFreq.diffVBounds()/200f;
                limitYZoom = nTimePoints>10 ? nTimePoints / 10 : 1;
            } else {
                limitXZoom = nTimePoints>10 ? nTimePoints / 10 : 1;
                limitYZoom = spectrogramPlot.axisFreq.diffVBounds()/200f;
            }
        }
        limitXZoom = Math.abs(limitXZoom);
        limitYZoom = Math.abs(limitYZoom);
//        Log.i(TAG, "setShiftScale: limit: xZ="+limitXZoom+"  yZ="+limitYZoom);
        if (canvasWidth*0.13f < xDiffOld) {  // if fingers are not very close in x direction, do scale in x direction
            xZoom  = clamp(xZoomOld * Math.abs(x1-x2)/xDiffOld, 1f, limitXZoom);
        }
        xShift = clampXShift(xShiftOld + (xMidOld/xZoomOld - (x1+x2)/2f/xZoom) / canvasWidth);
        if (canvasHeight*0.13f < yDiffOld) {  // if fingers are not very close in y direction, do scale in y direction
            yZoom  = clamp(yZoomOld * Math.abs(y1-y2)/yDiffOld, 1f, limitYZoom);
        }
        yShift = clampYShift(yShiftOld + (yMidOld/yZoomOld - (y1+y2)/2f/yZoom) / canvasHeight);
//        Log.i(TAG, "setShiftScale: xZ="+xZoom+"  xS="+xShift+"  yZ="+yZoom+"  yS="+yShift);
        updateAxisZoomShift();
    }

    private Ready readyCallback = null;      // callback to caller when rendering is complete

    public void setReady(Ready ready) {
        this.readyCallback = ready;
    }

    interface Ready {
        void ready();
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
        Log.i(TAG, "onSizeChanged(): canvas (" + oldw + "," + oldh + ") -> (" + w + "," + h + ")");
        isBusy = true;
        this.canvasWidth = w;
        this.canvasHeight = h;
        spectrumPlot   .setCanvas(w, h, null);
        spectrogramPlot.setCanvas(w, h, null);
        if (h > 0 && readyCallback != null) {
            readyCallback.ready();
        }
        isBusy = false;
    }

  /*
   * Save freqAxisAlongX, cursors, zooms, and current spectrogram/spectrum.
   * All other properties will be set in onCreate() and onResume().
   * Will be called after onPause() or between onCreat() and on onResume()
   * Ref. https://developer.android.com/guide/topics/ui/settings.html#CustomSaveState
   */

    @Override
    protected Parcelable onSaveInstanceState() {
        Log.i(TAG, "onSaveInstanceState(): xShift = " + xShift + "  xZoom = " + xZoom + "  yShift = " + yShift + "  yZoom = " + yZoom);
        Parcelable parentState = super.onSaveInstanceState();
        SavedState state = new SavedState(parentState);

        state.freqAxisAlongX = spectrogramPlot.showFreqAlongX ? 1 : 0;

        state.cFreqSpum  = spectrumPlot.cursorFreq;
        state.cFreqSpam  = spectrogramPlot.cursorFreq;
        state.cDb  = spectrumPlot.cursorDB;
        state.xZ  = xZoom;
        state.xS  = xShift;
        state.yZ  = yZoom;
        state.yS  = yShift;
        state.SpumXZ = spectrumPlot.axisX.getZoom();
        state.SpumXS = spectrumPlot.axisX.getShift();
        state.SpumYZ = spectrumPlot.axisY.getZoom();
        state.SpumYS = spectrumPlot.axisY.getShift();
        state.SpamFZ = spectrogramPlot.axisFreq.getZoom();
        state.SpamFS = spectrogramPlot.axisFreq.getShift();
        state.SpamTZ = spectrogramPlot.axisTime.getZoom();
        state.SpamTS = spectrogramPlot.axisTime.getShift();

        state.tmpS     = savedDBSpectrum;

        state.nFreq    = spectrogramPlot.nFreqPoints;
        state.nTime    = spectrogramPlot.nTimePoints;
        state.iTimePinter = spectrogramPlot.spectrogramBMP.spectrumStore.iTimePointer;

        final short[] tmpSC    = spectrogramPlot.spectrogramBMP.spectrumStore.dbShortArray;

        // Get byte[] representation of short[]
        // Note no ByteBuffer view of ShortBuffer, see
        //   http://bugs.java.com/bugdatabase/view_bug.do?bug_id=4489356
        byte[] input = new byte[tmpSC.length * 2];
        for (int i = 0; i < tmpSC.length; i++) {
            input[2*i  ] = (byte)(tmpSC[i] & 0xff);
            input[2*i+1] = (byte)(tmpSC[i] >> 8);
        }

        // Save spectrumStore.dbShortArray to a file.
        File tmpSCPath = new File(context.getCacheDir(), "spectrogram_short.raw");
        try {
            OutputStream fout = new FileOutputStream(tmpSCPath);
            fout.write(input);
            fout.close();
        } catch (IOException e) {
            Log.w("SavedState:", "writeToParcel(): Fail to save state to file.");
        }

        return state;
    }

    // Will be called after setup(), during AnalyzerActivity.onCreate()?
    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            SavedState s = (SavedState) state;
            super.onRestoreInstanceState(s.getSuperState());

            spectrogramPlot.showFreqAlongX = s.freqAxisAlongX == 1;

            spectrumPlot.cursorFreq    = s.cFreqSpum;
            spectrogramPlot.cursorFreq = s.cFreqSpam;
            spectrumPlot.cursorDB      = s.cDb;
            xZoom  = s.xZ;
            xShift = s.xS;
            yZoom  = s.yZ;
            yShift = s.yS;
            spectrumPlot.axisX.setZoomShift(s.SpumXZ, s.SpumXS);
            spectrumPlot.axisY.setZoomShift(s.SpumYZ, s.SpumYS);
            spectrogramPlot.axisFreq.setZoomShift(s.SpamFZ, s.SpamFS);
            spectrogramPlot.axisTime.setZoomShift(s.SpamTZ, s.SpamTS);

            savedDBSpectrum = s.tmpS;

            spectrogramPlot.nFreqPoints = s.nFreq;
            spectrogramPlot.nTimePoints = s.nTime;

            final SpectrogramBMP sBMP = spectrogramPlot.spectrogramBMP;
            final SpectrogramBMP.SpectrumCompressStore sBMPS = sBMP.spectrumStore;
            sBMPS.nFreq = s.nFreq;  // prevent reinitialize of LogFreqSpectrogramBMP
            sBMPS.nTime = s.nTime;
            sBMPS.iTimePointer = s.iTimePinter;

            // Read temporary saved spectrogram
            byte[] input = new byte[(s.nFreq+1) * s.nTime * 2]; // length of spectrumStore.dbShortArray
            int bytesRead = -1;
            File tmpSCPath = new File(context.getCacheDir(), "spectrogram_short.raw");
            try {
                InputStream fin = new FileInputStream(tmpSCPath);
                bytesRead = fin.read(input);
                fin.close();
            } catch (IOException e) {
                Log.w("SavedState:", "writeToParcel(): Fail to save state to file.");
            }

            if (bytesRead != input.length) {  // fail to get saved spectrogram, have a new start
                sBMPS.nFreq = 0;
                sBMPS.nTime = 0;
                sBMPS.iTimePointer = 0;
            } else {  // we have data!
                short[] tmpSC = new short[input.length/2];
                for (int i = 0; i < tmpSC.length; i++) {
                    tmpSC[i] = (short)(input[2*i] + (input[2*i+1] << 8));
                }
                sBMPS.dbShortArray = tmpSC;
                sBMP.rebuildLinearBMP();
            }

            Log.i(TAG, "onRestoreInstanceState(): xShift = " + xShift + "  xZoom = " + xZoom + "  yShift = " + yShift + "  yZoom = " + yZoom);
        } else {
            Log.i(TAG, "onRestoreInstanceState(): not SavedState?!");
            super.onRestoreInstanceState(state);
        }
    }

    private static class SavedState extends BaseSavedState {
        int freqAxisAlongX;
        double cFreqSpum;
        double cFreqSpam;
        double cDb;
        double xZ;
        double xS;
        double yZ;
        double yS;
        double SpumXZ;
        double SpumXS;
        double SpumYZ;
        double SpumYS;
        double SpamFZ;
        double SpamFS;
        double SpamTZ;
        double SpamTS;

        double[] tmpS;

        int nFreq;
        int nTime;
        int iTimePinter;

        SavedState(Parcelable state) {
            super(state);
        }

        private SavedState(Parcel in) {
            super(in);
            freqAxisAlongX = in.readInt();
            cFreqSpum  = in.readDouble();
            cFreqSpam  = in.readDouble();
            cDb  = in.readDouble();
            xZ   = in.readDouble();
            xS   = in.readDouble();
            yZ   = in.readDouble();
            yS   = in.readDouble();
            SpumXZ = in.readDouble();
            SpumXS = in.readDouble();
            SpumYZ = in.readDouble();
            SpumYS = in.readDouble();
            SpamFZ = in.readDouble();
            SpamFS = in.readDouble();
            SpamTZ = in.readDouble();
            SpamTS = in.readDouble();

            tmpS = in.createDoubleArray();

            nFreq       = in.readInt();
            nTime       = in.readInt();
            iTimePinter = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(freqAxisAlongX);
            out.writeDouble(cFreqSpum);
            out.writeDouble(cFreqSpam);
            out.writeDouble(cDb);
            out.writeDouble(xZ);
            out.writeDouble(xS);
            out.writeDouble(yZ);
            out.writeDouble(yS);
            out.writeDouble(SpumXZ);
            out.writeDouble(SpumXS);
            out.writeDouble(SpumYZ);
            out.writeDouble(SpumYS);
            out.writeDouble(SpamFZ);
            out.writeDouble(SpamFS);
            out.writeDouble(SpamTZ);
            out.writeDouble(SpamTS);

            out.writeDoubleArray(tmpS);

            out.writeInt(nFreq);
            out.writeInt(nTime);
            out.writeInt(iTimePinter);
        }

        // Standard creator object using an instance of this class
        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
