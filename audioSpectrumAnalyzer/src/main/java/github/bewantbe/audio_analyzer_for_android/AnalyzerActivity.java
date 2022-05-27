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
 * Add spectrogram plot, smooth gesture view control and various settings.
 */

package github.bewantbe.audio_analyzer_for_android;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Audio "FFT" analyzer.
 * @author suhler@google.com (Stephen Uhler)
 */

public class AnalyzerActivity extends Activity
    implements OnLongClickListener, OnClickListener,
               OnItemClickListener, AnalyzerGraphic.Ready
{
    private static final String TAG="AnalyzerActivity:";

    AnalyzerViews analyzerViews;
    SamplingLoop samplingThread = null;
    private RangeViewDialogC rangeViewDialogC;
    private GestureDetectorCompat mDetector;
    private SetCursorFreqDialog setCursorFreqDialog;

    private AnalyzerParameters analyzerParam = null;

    double dtRMS = 0;
    double dtRMSFromFT = 0;
    double maxAmpDB;
    double maxAmpFreq;
    double[] viewRangeArray = null;

    private boolean isMeasure = false;
    private boolean isLockViewRange = false;
    volatile boolean bSaveWav = false;

    CalibrationLoad calibLoad = new CalibrationLoad();  // data for calibration of spectrum

    @Override
    public void onCreate(Bundle savedInstanceState) {
        //  Debug.startMethodTracing("calc");
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        Log.i(TAG, " max runtime mem = " + maxMemory + "k");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Resources res = getResources();
        analyzerParam = new AnalyzerParameters(res);

        // Initialized preferences by default values
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        // Read preferences and set corresponding variables
        loadPreferenceForView();

        analyzerViews = new AnalyzerViews(this);

        // travel Views, and attach ClickListener to the views that contain android:tag="select"
        visit((ViewGroup) analyzerViews.graphView.getRootView(), new Visit() {
          @Override
          public void exec(View view) {
            view.setOnLongClickListener(AnalyzerActivity.this);
            view.setOnClickListener(AnalyzerActivity.this);
            ((TextView) view).setFreezesText(true);
          }
        }, "select");

        rangeViewDialogC = new RangeViewDialogC(this, analyzerViews.graphView);
        setCursorFreqDialog = new SetCursorFreqDialog(this, analyzerViews.graphView);

        mDetector = new GestureDetectorCompat(this, new AnalyzerGestureListener());
    }

    /**
     * Run processClick() for views, transferring the state in the textView to our
     * internal state, then begin sampling and processing audio data
     */

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();

        LoadPreferences();
        analyzerViews.graphView.setReady(this);  // TODO: move this earlier?
        analyzerViews.enableSaveWavView(bSaveWav);

        // Used to prevent extra calling to restartSampling() (e.g. in LoadPreferences())
        bSamplingPreparation = true;

        // Start sampling
        restartSampling(analyzerParam);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        bSamplingPreparation = false;
        if (samplingThread != null) {
            samplingThread.finish();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
//    Debug.stopMethodTracing();
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Log.d(TAG, "onSaveInstanceState()");
        savedInstanceState.putDouble("dtRMS",       dtRMS);
        savedInstanceState.putDouble("dtRMSFromFT", dtRMSFromFT);
        savedInstanceState.putDouble("maxAmpDB",    maxAmpDB);
        savedInstanceState.putDouble("maxAmpFreq",  maxAmpFreq);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.d(TAG, "onRestoreInstanceState()");
        // will be called after the onStart()
        super.onRestoreInstanceState(savedInstanceState);

        dtRMS       = savedInstanceState.getDouble("dtRMS");
        dtRMSFromFT = savedInstanceState.getDouble("dtRMSFromFT");
        maxAmpDB    = savedInstanceState.getDouble("maxAmpDB");
        maxAmpFreq  = savedInstanceState.getDouble("maxAmpFreq");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.info, menu);
        return true;
    }

    static final int REQUEST_AUDIO_GET = 1;
    static final int REQUEST_CALIB_LOAD = 2;

    public void selectFile(int requestType) {
        // https://developer.android.com/guide/components/intents-common.html#Storage
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        if (requestType == REQUEST_AUDIO_GET) {
            intent.setType("audio/*");
        } else {
            intent.setType("*/*");
        }
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, requestType);
        } else {
            Log.e(TAG, "No file chooser found!.");

            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, "Please install a File Manager.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public static String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }

    void fillFftCalibration(AnalyzerParameters _analyzerParam, CalibrationLoad _calibLoad) {
        if (_calibLoad.freq == null || _calibLoad.freq.length == 0 || _analyzerParam == null) {
            return;
        }
        double[] freqTick = new double[_analyzerParam.fftLen/2 + 1];
        for (int i = 0; i < freqTick.length; i++) {
            freqTick[i] = (double)i / _analyzerParam.fftLen * _analyzerParam.sampleRate;
        }
        _analyzerParam.micGainDB = AnalyzerUtil.interpLinear(_calibLoad.freq, _calibLoad.gain, freqTick);
        _analyzerParam.calibName = _calibLoad.name;
//        for (int i = 0; i < _analyzerParam.micGainDB.length; i++) {
//            Log.i(TAG, "calib: " + freqTick[i] + "Hz : " + _analyzerParam.micGainDB[i]);
//        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CALIB_LOAD && resultCode == RESULT_OK) {
            final Uri uri = data.getData();
            calibLoad.loadFile(uri, this);
            Log.w(TAG, "mime:" + getContentResolver().getType(uri));
            fillFftCalibration(analyzerParam, calibLoad);
        } else if (requestCode == REQUEST_AUDIO_GET) {
            Log.w(TAG, "requestCode == REQUEST_AUDIO_GET");
        }
    }

    // for pass audioSourceIDs and audioSourceNames to MyPreferences
    public final static String MYPREFERENCES_MSG_SOURCE_ID = "AnalyzerActivity.SOURCE_ID";
    public final static String MYPREFERENCES_MSG_SOURCE_NAME = "AnalyzerActivity.SOURCE_NAME";

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "onOptionsItemSelected(): " + item.toString());
        switch (item.getItemId()) {
            case R.id.menu_manual:
                analyzerViews.showInstructions();
                return true;
            case R.id.menu_settings:
                Intent settings = new Intent(getBaseContext(), MyPreferences.class);
                settings.putExtra(MYPREFERENCES_MSG_SOURCE_ID, analyzerParam.audioSourceIDs);
                settings.putExtra(MYPREFERENCES_MSG_SOURCE_NAME, analyzerParam.audioSourceNames);
                startActivity(settings);
                return true;
            case R.id.menu_test_recorder:
                Intent int_info_rec = new Intent(this, InfoRecActivity.class);
                startActivity(int_info_rec);
                return true;
            case R.id.menu_view_range:
                rangeViewDialogC.ShowRangeViewDialog();
                return true;
            case R.id.menu_calibration:
                selectFile(REQUEST_CALIB_LOAD);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Popup menu click listener
    // Read chosen preference, save the preference, set the state.
    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        // get the tag, which is the value we are going to use
        String selectedItemTag = v.getTag().toString();
        // if tag() is "0" then do not update anything (it is a title)
        if (selectedItemTag.equals("0")) {
            return ;
        }

        // get the text and set it as the button text
        String selectedItemText = ((TextView) v).getText().toString();

        int buttonId = Integer.parseInt((parent.getTag().toString()));
        Button buttonView = (Button) findViewById(buttonId);
        buttonView.setText(selectedItemText);

        boolean b_need_restart_audio;

        // Save the choosen preference
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPref.edit();

        // so change of sample rate do not change view range
        if (! isLockViewRange) {
            viewRangeArray = analyzerViews.graphView.getViewPhysicalRange();
            // if range is align at boundary, extend the range.
            Log.i(TAG, "set sampling rate:a " + viewRangeArray[0] + " ==? " + viewRangeArray[6]);
            if (viewRangeArray[0] == viewRangeArray[6]) {
                viewRangeArray[0] = 0;
            }
        }

        // dismiss the pop up
        switch (buttonId) {
            case R.id.button_sample_rate:
                analyzerViews.popupMenuSampleRate.dismiss();
                if (! isLockViewRange) {
                    Log.i(TAG, "set sampling rate:b " + viewRangeArray[1] + " ==? " + viewRangeArray[6 + 1]);
                    if (viewRangeArray[1] == viewRangeArray[6 + 1]) {
                        viewRangeArray[1] = Integer.parseInt(selectedItemTag) / 2;
                    }
                    Log.i(TAG, "onItemClick(): viewRangeArray saved. " + viewRangeArray[0] + " ~ " + viewRangeArray[1]);
                }
                analyzerParam.sampleRate = Integer.parseInt(selectedItemTag);
                b_need_restart_audio = true;
                editor.putInt("button_sample_rate", analyzerParam.sampleRate);
                break;
            case R.id.button_fftlen:
                analyzerViews.popupMenuFFTLen.dismiss();
                analyzerParam.fftLen = Integer.parseInt(selectedItemTag);
                analyzerParam.hopLen = (int)(analyzerParam.fftLen*(1 - analyzerParam.overlapPercent/100) + 0.5);
                b_need_restart_audio = true;
                editor.putInt("button_fftlen", analyzerParam.fftLen);
                fillFftCalibration(analyzerParam, calibLoad);
                break;
            case R.id.button_average:
                analyzerViews.popupMenuAverage.dismiss();
                analyzerParam.nFFTAverage = Integer.parseInt(selectedItemTag);
                if (analyzerViews.graphView != null) {
                    analyzerViews.graphView.setTimeMultiplier(analyzerParam.nFFTAverage);
                }
                b_need_restart_audio = false;
                editor.putInt("button_average", analyzerParam.nFFTAverage);
                break;
            default:
                Log.w(TAG, "onItemClick(): no this button");
                b_need_restart_audio = false;
        }

        editor.commit();

        if (b_need_restart_audio) {
            restartSampling(analyzerParam);
        }
    }

    // Load preferences for Views
    // When this function is called, the SamplingLoop must not running in the meanwhile.
    private void loadPreferenceForView() {
        // load preferences for buttons
        // list-buttons
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        analyzerParam.sampleRate   = sharedPref.getInt("button_sample_rate", 8000);
        analyzerParam.fftLen       = sharedPref.getInt("button_fftlen",      1024);
        analyzerParam.nFFTAverage  = sharedPref.getInt("button_average",        1);
        // toggle-buttons
        analyzerParam.isAWeighting = sharedPref.getBoolean("dbA", false);
        if (analyzerParam.isAWeighting) {
            ((SelectorText) findViewById(R.id.dbA)).nextValue();
        }
        boolean isSpam = sharedPref.getBoolean("spectrum_spectrogram_mode", true);
        if (!isSpam) {
            ((SelectorText) findViewById(R.id.spectrum_spectrogram_mode)).nextValue();
        }
        String axisMode = sharedPref.getString("freq_scaling_mode", "linear");
        SelectorText st = (SelectorText) findViewById(R.id.freq_scaling_mode);
        st.setValue(axisMode);

        Log.i(TAG, "loadPreferenceForView():"+
                "\n  sampleRate  = " + analyzerParam.sampleRate +
                "\n  fftLen      = " + analyzerParam.fftLen +
                "\n  nFFTAverage = " + analyzerParam.nFFTAverage);
        ((Button) findViewById(R.id.button_sample_rate)).setText(Integer.toString(analyzerParam.sampleRate));
        ((Button) findViewById(R.id.button_fftlen     )).setText(Integer.toString(analyzerParam.fftLen));
        ((Button) findViewById(R.id.button_average    )).setText(Integer.toString(analyzerParam.nFFTAverage));
    }

    private void LoadPreferences() {
        // Load preferences for recorder and views, beside loadPreferenceForView()
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        boolean keepScreenOn = sharedPref.getBoolean("keepScreenOn", true);
        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        analyzerParam.audioSourceId = Integer.parseInt(sharedPref.getString("audioSource", Integer.toString(analyzerParam.RECORDER_AGC_OFF)));
        analyzerParam.wndFuncName = sharedPref.getString("windowFunction", "Hanning");
        analyzerParam.spectrogramDuration = Double.parseDouble(sharedPref.getString("spectrogramDuration",
                Double.toString(6.0)));
        analyzerParam.overlapPercent = Double.parseDouble(sharedPref.getString("fft_overlap_percent", "50.0"));
        analyzerParam.hopLen = (int)(analyzerParam.fftLen*(1 - analyzerParam.overlapPercent/100) + 0.5);

        // Settings of graph view
        // spectrum
        analyzerViews.graphView.setShowLines( sharedPref.getBoolean("showLines", false) );
        // set spectrum show range
        analyzerViews.graphView.setSpectrumDBLowerBound(
                Float.parseFloat(sharedPref.getString("spectrumRange", Double.toString(AnalyzerGraphic.minDB)))
        );

        // spectrogram
        analyzerViews.graphView.setSpectrogramModeShifting(sharedPref.getBoolean("spectrogramShifting", false));
        analyzerViews.graphView.setShowTimeAxis           (sharedPref.getBoolean("spectrogramTimeAxis", true));
        analyzerViews.graphView.setShowFreqAlongX         (sharedPref.getBoolean("spectrogramShowFreqAlongX", true));
        analyzerViews.graphView.setSmoothRender           (sharedPref.getBoolean("spectrogramSmoothRender", false));
        analyzerViews.graphView.setColorMap               (sharedPref.getString ("spectrogramColorMap", "Hot"));
        // set spectrogram show range
        analyzerViews.graphView.setSpectrogramDBLowerBound(Float.parseFloat(
                sharedPref.getString("spectrogramRange", Double.toString(analyzerViews.graphView.spectrogramPlot.spectrogramBMP.dBLowerBound))));
        analyzerViews.graphView.setLogAxisMode(
                sharedPref.getBoolean("spectrogramLogPlotMethod", true));

        analyzerViews.bWarnOverrun = sharedPref.getBoolean("warnOverrun", false);
        analyzerViews.setFpsLimit(Double.parseDouble(
                sharedPref.getString("spectrogramFPS", getString(R.string.spectrogram_fps_default))));

        // Apply settings by travel the views with android:tag="select".
        visit((ViewGroup) analyzerViews.graphView.getRootView(), new Visit() {
            @Override
            public void exec(View view) {
                processClick(view);
            }
        }, "select");

        // Get view range setting
        boolean isLock = sharedPref.getBoolean("view_range_lock", false);
        if (isLock) {
            Log.i(TAG, "LoadPreferences(): isLocked");
            // Set view range and stick to measure mode
            double[] rr = new double[AnalyzerGraphic.VIEW_RANGE_DATA_LENGTH];
            for (int i = 0; i < rr.length; i++) {
                rr[i] = AnalyzerUtil.getDouble(sharedPref, "view_range_rr_" + i, 0.0/0.0);
                if (Double.isNaN(rr[i])) {  // not properly initialized
                    Log.w(TAG, "LoadPreferences(): rr is not properly initialized");
                    rr = null;
                    break;
                }
            }
            if (rr != null) {
                viewRangeArray = rr;
            }
            stickToMeasureMode();
        } else {
            stickToMeasureModeCancel();
        }
    }

    void stickToMeasureMode() {
        isLockViewRange = true;
        switchMeasureAndScaleMode();  // Force set to Measure mode
    }

    void stickToMeasureModeCancel() {
        isLockViewRange = false;
        if (isMeasure) {
            switchMeasureAndScaleMode();  // Force set to ScaleMode
        }
    }

    private boolean isInGraphView(float x, float y) {
        analyzerViews.graphView.getLocationInWindow(windowLocation);
        return x >= windowLocation[0] && y >= windowLocation[1] &&
                x < windowLocation[0] + analyzerViews.graphView.getWidth() &&
                y < windowLocation[1] + analyzerViews.graphView.getHeight();
    }

    // Button processing
    public void showPopupMenu(View view) {
        analyzerViews.showPopupMenu(view);
    }

    public void showCursorFreqPopup(View view) {
        setCursorFreqDialog.ShowSetCursorFreqDialog();
    }

    /**
     * Gesture Listener for graphView (and possibly other views)
     * How to attach these events to the graphView?
     * @author xyy
     */
    private class AnalyzerGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent event) {  // enter here when down action happen
            flyingMoveHandler.removeCallbacks(flyingMoveRunnable);
            return true;
        }

        @Override
        public void onLongPress(MotionEvent event) {
            if (isInGraphView(event.getX(0), event.getY(0))) {
                if (!isMeasure) {  // go from "scale" mode to "cursor" mode
                    switchMeasureAndScaleMode();
                }
            }
            measureEvent(event);  // force insert this event
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            if (!isMeasure) {
                scaleEvent(event);            // ends scale mode
                analyzerViews.graphView.resetViewScale();
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                               float velocityX, float velocityY) {
            if (isMeasure) {
                // seems never reach here...
                return true;
            }
            // Log.d(TAG, "  AnalyzerGestureListener::onFling: " + event1.toString()+event2.toString());
            // Fly the canvas in graphView when in scale mode
            shiftingVelocity = Math.sqrt(velocityX*velocityX + velocityY*velocityY);
            shiftingComponentX = velocityX / shiftingVelocity;
            shiftingComponentY = velocityY / shiftingVelocity;
            float DPRatio = getResources().getDisplayMetrics().density;
            flyAcceleration = 1200 * DPRatio;
            timeFlingStart = SystemClock.uptimeMillis();
            flyingMoveHandler.postDelayed(flyingMoveRunnable, 0);
            return true;
        }

        Handler flyingMoveHandler = new Handler();
        long timeFlingStart;                     // Prevent from running forever
        double flyDt = 1/20.;                     // delta t of refresh
        double shiftingVelocity;                  // fling velocity
        double shiftingComponentX;                // fling direction x
        double shiftingComponentY;                // fling direction y
        double flyAcceleration = 1200.;           // damping acceleration of fling, pixels/second^2

        Runnable flyingMoveRunnable = new Runnable() {
            @Override
            public void run() {
                double shiftingVelocityNew = shiftingVelocity - flyAcceleration*flyDt;
                if (shiftingVelocityNew < 0) shiftingVelocityNew = 0;
                // Number of pixels that should move in this time step
                double shiftingPixel = (shiftingVelocityNew + shiftingVelocity)/2 * flyDt;
                shiftingVelocity = shiftingVelocityNew;
                if (shiftingVelocity > 0f
                        && SystemClock.uptimeMillis() - timeFlingStart < 10000) {
                    // Log.i(TAG, "  fly pixels x=" + shiftingPixelX + " y=" + shiftingPixelY);
                    AnalyzerGraphic graphView = analyzerViews.graphView;
                    graphView.setXShift(graphView.getXShift() - shiftingComponentX*shiftingPixel / graphView.getCanvasWidth() / graphView.getXZoom());
                    graphView.setYShift(graphView.getYShift() - shiftingComponentY*shiftingPixel / graphView.getCanvasHeight() / graphView.getYZoom());
                    // Am I need to use runOnUiThread() ?
                    analyzerViews.invalidateGraphView();
                    flyingMoveHandler.postDelayed(flyingMoveRunnable, (int)(1000*flyDt));
                }
            }
        };
    }

    private void switchMeasureAndScaleMode() {
        if (isLockViewRange) {
            isMeasure = true;
            return;
        }
        isMeasure = !isMeasure;
        //SelectorText st = (SelectorText) findViewById(R.id.graph_view_mode);
        //st.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isInGraphView(event.getX(0), event.getY(0))) {
            this.mDetector.onTouchEvent(event);
            if (isMeasure) {
                measureEvent(event);
            } else {
                scaleEvent(event);
            }
            analyzerViews.invalidateGraphView();
            // Go to scaling mode when user release finger in measure mode.
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (isMeasure) {
                    switchMeasureAndScaleMode();
                }
            }
        } else {
            // When finger is outside the plot, hide the cursor and go to scaling mode.
            if (isMeasure) {
                analyzerViews.graphView.hideCursor();
                switchMeasureAndScaleMode();
            }
        }
        return super.onTouchEvent(event);
    }

    /**
     *  Manage cursor for measurement
     */
    private void measureEvent(MotionEvent event) {
        switch (event.getPointerCount()) {
            case 1:
                analyzerViews.graphView.setCursor(event.getX(), event.getY());
                // TODO: if touch point is very close to boundary for a long time, move the view
                break;
            case 2:
                if (isInGraphView(event.getX(1), event.getY(1))) {
                    switchMeasureAndScaleMode();
                }
        }
    }

    /**
     *  Manage scroll and zoom
     */
    final private static double INIT = Double.MIN_VALUE;
    private boolean isPinching = false;
    private double xShift0 = INIT, yShift0 = INIT;
    private double x0, y0;
    private int[] windowLocation = new int[2];

    private void scaleEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_MOVE) {
            xShift0 = INIT;
            yShift0 = INIT;
            isPinching = false;
            // Log.i(TAG, "scaleEvent(): Skip event " + event.getAction());
            return;
        }
        // Log.i(TAG, "scaleEvent(): switch " + event.getAction());
        AnalyzerGraphic graphView = analyzerViews.graphView;
        switch (event.getPointerCount()) {
            case 2 :
                if (isPinching)  {
                    graphView.setShiftScale(event.getX(0), event.getY(0), event.getX(1), event.getY(1));
                } else {
                    graphView.setShiftScaleBegin(event.getX(0), event.getY(0), event.getX(1), event.getY(1));
                }
                isPinching = true;
                break;
            case 1:
                float x = event.getX(0);
                float y = event.getY(0);
                graphView.getLocationInWindow(windowLocation);
                // Log.i(TAG, "scaleEvent(): xy=" + x + " " + y + "  wc = " + wc[0] + " " + wc[1]);
                if (isPinching || xShift0 == INIT) {
                    xShift0 = graphView.getXShift();
                    x0 = x;
                    yShift0 = graphView.getYShift();
                    y0 = y;
                } else {
                    // when close to the axis, scroll that axis only
                    if (x0 < windowLocation[0] + 50) {
                        graphView.setYShift(yShift0 + (y0 - y) / graphView.getCanvasHeight() / graphView.getYZoom());
                    } else if (y0 < windowLocation[1] + 50) {
                        graphView.setXShift(xShift0 + (x0 - x) / graphView.getCanvasWidth() / graphView.getXZoom());
                    } else {
                        graphView.setXShift(xShift0 + (x0 - x) / graphView.getCanvasWidth() / graphView.getXZoom());
                        graphView.setYShift(yShift0 + (y0 - y) / graphView.getCanvasHeight() / graphView.getYZoom());
                    }
                }
                isPinching = false;
                break;
            default:
                Log.i(TAG, "Invalid touch count");
                break;
        }
    }

    @Override
    public boolean onLongClick(View view) {
        vibrate(300);
        Log.i(TAG, "long click: " + view.toString());
        return true;
    }

    // Responds to layout with android:tag="select"
    // Called from SelectorText.super.performClick()
    @Override
    public void onClick(View v) {
        if (processClick(v)) {
            restartSampling(analyzerParam);
        }
        analyzerViews.invalidateGraphView();
    }

    private final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 1;  // just a number
    private final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 2;
    Thread graphInit;
    private boolean bSamplingPreparation = false;

    private void restartSampling(final AnalyzerParameters _analyzerParam) {
        // Stop previous sampler if any.
        if (samplingThread != null) {
            samplingThread.finish();
            try {
                samplingThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            samplingThread = null;
        }

        if (viewRangeArray != null) {
            analyzerViews.graphView.setupAxes(analyzerParam);
            double[] rangeDefault = analyzerViews.graphView.getViewPhysicalRange();
            Log.i(TAG, "restartSampling(): setViewRange: " + viewRangeArray[0] + " ~ " + viewRangeArray[1]);
            analyzerViews.graphView.setViewRange(viewRangeArray, rangeDefault);
            if (! isLockViewRange) viewRangeArray = null;  // do not conserve
        }

        // Set the view for incoming data
        graphInit = new Thread(new Runnable() {
            public void run() {
                analyzerViews.setupView(_analyzerParam);
            }
        });
        graphInit.start();

        // Check and request permissions
        if (! checkAndRequestPermissions())
            return;

        if (! bSamplingPreparation)
            return;

        // Start sampling
        samplingThread = new SamplingLoop(this, _analyzerParam);
        samplingThread.start();
    }

    // For call requestPermissions() after each showPermissionExplanation()
    private int count_permission_explanation = 0;

    // For preventing infinity loop: onResume() -> requestPermissions() -> onRequestPermissionsResult() -> onResume()
    private int count_permission_request = 0;

    // Test and try to gain permissions.
    // Return true if it is OK to proceed.
    // Ref.
    //   https://developer.android.com/training/permissions/requesting.html
    //   https://developer.android.com/guide/topics/permissions/requesting.html
    private boolean checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(AnalyzerActivity.this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission RECORD_AUDIO denied. Trying  to request...");
            if (ActivityCompat.shouldShowRequestPermissionRationale(AnalyzerActivity.this, Manifest.permission.RECORD_AUDIO) &&
                    count_permission_explanation < 1) {
                Log.w(TAG, "  Show explanation here....");
                analyzerViews.showPermissionExplanation(R.string.permission_explanation_recorder);
                count_permission_explanation++;
            } else {
                Log.w(TAG, "  Requesting...");
                if (count_permission_request < 3) {
                    ActivityCompat.requestPermissions(AnalyzerActivity.this,
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
                    count_permission_explanation = 0;
                    count_permission_request++;
                } else {
                    this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Context context = getApplicationContext();
                            String text = "Permission denied.";
                            Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
                            toast.show();
                        }
                    });
                }
            }
            return false;
        }
        if (bSaveWav &&
                ContextCompat.checkSelfPermission(AnalyzerActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission WRITE_EXTERNAL_STORAGE denied. Trying  to request...");
            ((SelectorText) findViewById(R.id.button_recording)).nextValue();
            bSaveWav = false;
            analyzerViews.enableSaveWavView(bSaveWav);
//      ((SelectorText) findViewById(R.id.button_recording)).performClick();
            ActivityCompat.requestPermissions(AnalyzerActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            // Still possible to proceed with bSaveWav == false
            // simulate a view click, so that bSaveWav = false
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "RECORD_AUDIO Permission granted by user.");
                } else {
                    Log.w(TAG, "RECORD_AUDIO Permission denied by user.");
                }
                break;
            }
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "WRITE_EXTERNAL_STORAGE Permission granted by user.");
                    if (! bSaveWav) {
                        Log.w(TAG, "... bSaveWav == true");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ((SelectorText) findViewById(R.id.button_recording)).nextValue();
                                bSaveWav = true;
                                analyzerViews.enableSaveWavView(bSaveWav);
                            }
                        });
                    } else {
                        Log.w(TAG, "... bSaveWav == false");
                    }
                } else {
                    Log.w(TAG, "WRITE_EXTERNAL_STORAGE Permission denied by user.");
                }
                break;
            }
        }
        // Then onResume() will be called.
    }

    /**
     * Process a click on one of our selectors.
     * @param v   The view that was clicked
     * @return    true if we need to update the graph
     */

    public boolean processClick(View v) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPref.edit();
        String value;
        if (v instanceof SelectorText) {
            value = ((SelectorText) v).getValue();
        } else {
            value = ((TextView) v).getText().toString();
        }
        switch (v.getId()) {
            case R.id.button_recording:
                bSaveWav = value.equals("Rec");
                //  SelectorText st = (SelectorText) findViewById(R.id.run);
                //  if (bSaveWav && ! st.getText().toString().equals("stop")) {
                //    st.nextValue();
                //    if (samplingThread != null) {
                //      samplingThread.setPause(true);
                //    }
                //  }
                analyzerViews.enableSaveWavView(bSaveWav);
                return true;
            case R.id.run:
                boolean pause = value.equals("stop");
                if (samplingThread != null && samplingThread.getPause() != pause) {
                    samplingThread.setPause(pause);
                }
                analyzerViews.graphView.spectrogramPlot.setPause(pause);
                return false;
                //case R.id.graph_view_mode:
                //  isMeasure = !value.equals("scale");
                //  return false;
            case R.id.freq_scaling_mode: {
                Log.d(TAG, "processClick(): freq_scaling_mode = " + value);
                analyzerViews.graphView.setAxisModeLinear(value);
                editor.putString("freq_scaling_mode", value);
                editor.commit();
                return false;
            }
            case R.id.dbA:
                analyzerParam.isAWeighting = !value.equals("dB");
                if (samplingThread != null) {
                    samplingThread.setAWeighting(analyzerParam.isAWeighting);
                }
                editor.putBoolean("dbA", analyzerParam.isAWeighting);
                editor.commit();
                return false;
            case R.id.spectrum_spectrogram_mode:
                if (value.equals("spum")) {
                    analyzerViews.graphView.switch2Spectrum();
                } else {
                    analyzerViews.graphView.switch2Spectrogram();
                }
                editor.putBoolean("spectrum_spectrogram_mode", value.equals("spum"));
                editor.commit();
                return false;
            default:
                return true;
        }
    }

    private void vibrate(int ms) {
        //((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(ms);
    }

    /**
     * Visit all subviews of this view group and run command
     * @param group   The parent view group
     * @param cmd     The command to run for each view
     * @param select  The tag value that must match. Null implies all views
     */

    private void visit(ViewGroup group, Visit cmd, String select) {
        exec(group, cmd, select);
        for (int i = 0; i < group.getChildCount(); i++) {
            View c = group.getChildAt(i);
            if (c instanceof ViewGroup) {
                visit((ViewGroup) c, cmd, select);
            } else {
                exec(c, cmd, select);
            }
        }
    }

    private void exec(View v, Visit cmd, String select) {
        if (select == null || select.equals(v.getTag())) {
            cmd.exec(v);
        }
    }

    /**
     * Interface for view hierarchy visitor
     */
    interface Visit {
        void exec(View view);
    }

    /**
     * The graph view size has been determined - update the labels accordingly.
     */
    @Override
    public void ready() {
        // put code here for the moment that graph size just changed
        Log.v(TAG, "ready()");
        analyzerViews.invalidateGraphView();
    }
}
