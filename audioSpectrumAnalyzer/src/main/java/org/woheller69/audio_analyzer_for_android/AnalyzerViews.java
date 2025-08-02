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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

/**
 * Operate the views in the UI here.
 * Should run on UI thread in general.
 */

class AnalyzerViews {
    final String TAG = "AnalyzerViews";
    private final AnalyzerActivity activity;
    final AnalyzerGraphic graphView;

    private float DPRatio;
    private float listItemTextSize = 20;        // see R.dimen.button_text_fontsize
    private float listItemTitleTextSize = 12;   // see R.dimen.button_text_small_fontsize
    private double fpsLimit = 8;

    private StringBuilder textCur = new StringBuilder("");  // for textCurChar
    private StringBuilder textRMS  = new StringBuilder("");
    private StringBuilder textPeak = new StringBuilder("");
    private StringBuilder textRec = new StringBuilder("");
    private char[] textRMSChar;   // for text in R.id.textview_RMS
    private char[] textCurChar;   // for text in R.id.textview_cur
    private char[] textPeakChar;  // for text in R.id.textview_peak
    private char[] textRecChar;   // for text in R.id.textview_rec

    PopupWindow popupMenuSampleRate;
    PopupWindow popupMenuFFTLen;
    PopupWindow popupMenuAverage;

    boolean bWarnOverrun = true;

    AnalyzerViews(AnalyzerActivity _activity) {
        activity = _activity;
        graphView = (AnalyzerGraphic) activity.findViewById(R.id.plot);

        Resources res = activity.getResources();
        listItemTextSize      = res.getDimension(R.dimen.button_text_fontsize);
        listItemTitleTextSize = res.getDimension(R.dimen.button_text_small_fontsize);
        DPRatio = res.getDisplayMetrics().density;

        textRMSChar  = new char[res.getString(R.string.textview_RMS_text).length()];
        textCurChar  = new char[res.getString(R.string.textview_cur_text).length()];
        textRecChar  = new char[res.getString(R.string.textview_rec_text).length()];
        textPeakChar = new char[res.getString(R.string.textview_peak_text).length()];

        /// initialize pop up window items list
        // http://www.codeofaninja.com/2013/04/show-listview-as-drop-down-android.html
        popupMenuSampleRate = popupMenuCreate( AnalyzerUtil.validateAudioRates(
                res.getStringArray(R.array.sample_rates)), R.id.button_sample_rate);
        popupMenuFFTLen = popupMenuCreate(
                res.getStringArray(R.array.fft_len), R.id.button_fftlen);
        popupMenuAverage = popupMenuCreate(
                res.getStringArray(R.array.fft_ave_num), R.id.button_average);

        setTextViewFontSize();
    }

    // Set text font size of textview_cur and textview_peak
    // according to space left
    //@SuppressWarnings("deprecation")
    private void setTextViewFontSize() {
        TextView tv = (TextView) activity.findViewById(R.id.textview_cur);
        // At this point tv.getWidth(), tv.getLineCount() will return 0

        Display display = activity.getWindowManager().getDefaultDisplay();
        // pixels left
        float px = display.getWidth() - activity.getResources().getDimension(R.dimen.textview_RMS_layout_width) - 5;

        float fs = tv.getTextSize();  // size in pixel

        // shrink font size if it can not fit in one line.
        final String text = activity.getString(R.string.textview_peak_text);
        // note: mTestPaint.measureText(text) do not scale like sp.
        Paint mTestPaint = new Paint();
        mTestPaint.setTextSize(fs);
        mTestPaint.setTypeface(Typeface.MONOSPACE);
        while (mTestPaint.measureText(text) > px && fs > 5) {
            fs -= 0.5;
            mTestPaint.setTextSize(fs);
        }

        ((TextView) activity.findViewById(R.id.textview_cur)).setTextSize(TypedValue.COMPLEX_UNIT_PX, fs);
        ((TextView) activity.findViewById(R.id.textview_peak)).setTextSize(TypedValue.COMPLEX_UNIT_PX, fs);
    }

    // Prepare the spectrum and spectrogram plot (from scratch or full reset)
    // Should be called before samplingThread starts.
    void setupView(AnalyzerParameters analyzerParam) {
        graphView.setupPlot(analyzerParam);
    }

    // Will be called by SamplingLoop (in another thread)
    void update(final double[] spectrumDBcopy) {
        graphView.saveSpectrum(spectrumDBcopy);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // data will get out of synchronize here
                invalidateGraphView();
            }
        });
    }

    private double wavSecOld = 0;      // used to reduce frame rate
    void updateRec(double wavSec) {
        if (wavSecOld > wavSec) {
            wavSecOld = wavSec;
        }
        if (wavSec - wavSecOld < 0.1) {
            return;
        }
        wavSecOld = wavSec;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // data will get out of synchronize here
                invalidateGraphView(AnalyzerViews.VIEW_MASK_RecTimeLable);
            }
        });
    }

    void notifyWAVSaved(final String path) {
        String text = "WAV saved to " + path;
        notifyToast(text);
    }

    void notifyToast(final String st) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Context context = activity.getApplicationContext();
                Toast toast = Toast.makeText(context, st, Toast.LENGTH_SHORT);
                toast.show();
            }
        });
    }

    private long lastTimeNotifyOverrun = 0;
    void notifyOverrun() {
        if (!bWarnOverrun) {
            return;
        }
        long t = SystemClock.uptimeMillis();
        if (t - lastTimeNotifyOverrun > 6000) {
            lastTimeNotifyOverrun = t;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Context context = activity.getApplicationContext();
                    String text = "Recorder buffer overrun!\nYour cell phone is too slow.\nTry lower sampling rate or higher average number.";
                    Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
                    toast.show();
                }
            });
        }
    }

    void showInstructions() {
        TextView tv = new TextView(activity);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setText(fromHtml(activity.getString(R.string.instructions_text)));
        PackageInfo pInfo = null;
        String version = "\n" + activity.getString(R.string.app_name) + "  Version: ";
        try {
            pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            version += pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            version += "(Unknown)";
        }
        tv.append(version);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.instructions_title)
                .setView(tv)
                .setNegativeButton(R.string.dismiss, null)
                .create().show();
    }

    // Thanks http://stackoverflow.com/questions/37904739/html-fromhtml-deprecated-in-android-n
    @SuppressWarnings("deprecation")
    public static android.text.Spanned fromHtml(String source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY); // or Html.FROM_HTML_MODE_COMPACT
        } else {
            return Html.fromHtml(source);
        }
    }

    void enableSaveWavView(boolean bSaveWav) {
        if (bSaveWav) {
            ((TextView) activity.findViewById(R.id.textview_rec)).setHeight((int)(19*DPRatio));
        } else {
            ((TextView) activity.findViewById(R.id.textview_rec)).setHeight((int)(0*DPRatio));
        }
    }

    @SuppressWarnings("deprecation")
    void showPopupMenu(View view) {
        // popup menu position
        // In API 19, we can use showAsDropDown(View anchor, int xoff, int yoff, int gravity)
        // The problem in showAsDropDown (View anchor, int xoff, int yoff) is
        // it may show the window in wrong direction (so that we can't see it)
        int[] wl = new int[2];
        view.getLocationInWindow(wl);
        int x_left = wl[0];
        int y_bottom = activity.getWindowManager().getDefaultDisplay().getHeight() - wl[1];
        int gravity = android.view.Gravity.START | android.view.Gravity.BOTTOM;

        int itemId = view.getId();
        if (itemId == R.id.button_sample_rate){
            popupMenuSampleRate.showAtLocation(view, gravity, x_left, y_bottom);
        } else if (itemId == R.id.button_fftlen) {
            popupMenuFFTLen.showAtLocation(view, gravity, x_left, y_bottom);
        } else if (itemId == R.id.button_average) {
            popupMenuAverage.showAtLocation(view, gravity, x_left, y_bottom);
        }
    }

    // Maybe put this PopupWindow into a class
    private PopupWindow popupMenuCreate(String[] popUpContents, int resId) {

        // initialize a pop up window type
        PopupWindow popupWindow = new PopupWindow(activity);

        // the drop down list is a list view
        ListView listView = new ListView(activity);

        // set our adapter and pass our pop up window contents
        ArrayAdapter<String> aa = popupMenuAdapter(popUpContents);
        listView.setAdapter(aa);

        // set the item click listener
        listView.setOnItemClickListener(activity);

        // button resource ID, so we can trace back which button is pressed
        listView.setTag(resId);

        // get max text width
        Paint mTestPaint = new Paint();
        mTestPaint.setTextSize(listItemTextSize);
        float w = 0;  // max text width in pixel
        float wi;
        for (String popUpContent : popUpContents) {
            String sts[] = popUpContent.split("::");
            if (sts.length == 0) continue;
            String st = sts[0];
            if (sts.length == 2 && sts[1].equals("0")) {
                mTestPaint.setTextSize(listItemTitleTextSize);
                wi = mTestPaint.measureText(st);
                mTestPaint.setTextSize(listItemTextSize);
            } else {
                wi = mTestPaint.measureText(st);
            }
            if (w < wi) {
                w = wi;
            }
        }

        // left and right padding, at least +7, or the whole app will stop respond, don't know why
        w = w + 23 * DPRatio;
        if (w < 40 * DPRatio) {
            w = 40 * DPRatio;
        }

        // some other visual settings
        popupWindow.setFocusable(true);
        popupWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        // Set window width according to max text width
        popupWindow.setWidth((int)w);
        // also set button width
        ((Button) activity.findViewById(resId)).setWidth((int)(w + 5 * DPRatio));
        // Set the text on button in loadPreferenceForView()

        // set the list view as pop up window content
        popupWindow.setContentView(listView);

        return popupWindow;
    }

    /*
     * adapter where the list values will be set
     */
    private ArrayAdapter<String> popupMenuAdapter(String itemTagArray[]) {
        return new ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, itemTagArray) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                // setting the ID and text for every items in the list
                String item = getItem(position);
                String[] itemArr = item.split("::");
                String text = itemArr[0];
                String id = itemArr[1];

                // visual settings for the list item
                TextView listItem = new TextView(activity);

                if (id.equals("0")) {
                    listItem.setText(text);
                    listItem.setTag(id);
                    listItem.setTextSize(listItemTitleTextSize / DPRatio);
                    listItem.setPadding(5, 5, 5, 5);
                    listItem.setTextColor(Color.GREEN);
                    listItem.setGravity(android.view.Gravity.CENTER);
                } else {
                    listItem.setText(text);
                    listItem.setTag(id);
                    listItem.setTextSize(listItemTextSize / DPRatio);
                    listItem.setPadding(5, 5, 5, 5);
                    listItem.setTextColor(Color.WHITE);
                    listItem.setGravity(android.view.Gravity.CENTER);
                }

                return listItem;
            }
        };
    }

    private void refreshCursorLabel() {
        double f1 = graphView.getCursorFreq();

        textCur.setLength(0);
        textCur.append(activity.getString(R.string.text_cur));
        SBNumFormat.fillInNumFixedWidthPositive(textCur, f1, 5, 1);
        textCur.append("Hz(");
        AnalyzerUtil.freq2Cent(textCur, f1, " ");
        textCur.append(") ");
        SBNumFormat.fillInNumFixedWidth(textCur, graphView.getCursorDB(), 3, 1);
        textCur.append("dB");
        textCur.getChars(0, Math.min(textCur.length(), textCurChar.length), textCurChar, 0);

        ((TextView) activity.findViewById(R.id.textview_cur))
                .setText(textCurChar, 0, Math.min(textCur.length(), textCurChar.length));
    }

    private void refreshRMSLabel(double dtRMSFromFT) {
        textRMS.setLength(0);
        textRMS.append("RMS:dB \n");
        SBNumFormat.fillInNumFixedWidth(textRMS, 20*Math.log10(dtRMSFromFT), 3, 1);
        textRMS.getChars(0, Math.min(textRMS.length(), textRMSChar.length), textRMSChar, 0);

        TextView tv = (TextView) activity.findViewById(R.id.textview_RMS);
        tv.setText(textRMSChar, 0, textRMSChar.length);
        tv.invalidate();
    }

    private void refreshPeakLabel(double maxAmpFreq, double maxAmpDB) {
        textPeak.setLength(0);
        textPeak.append(activity.getString(R.string.text_peak));
        SBNumFormat.fillInNumFixedWidthPositive(textPeak, maxAmpFreq, 5, 1);
        textPeak.append("Hz(");
        AnalyzerUtil.freq2Cent(textPeak, maxAmpFreq, " ");
        textPeak.append(") ");
        SBNumFormat.fillInNumFixedWidth(textPeak, maxAmpDB, 3, 1);
        textPeak.append("dB");
        textPeak.getChars(0, Math.min(textPeak.length(), textPeakChar.length), textPeakChar, 0);

        TextView tv = (TextView) activity.findViewById(R.id.textview_peak);
        tv.setText(textPeakChar, 0, textPeakChar.length);
        tv.invalidate();
    }

    private void refreshRecTimeLable(double wavSec, double wavSecRemain) {
        // consist with @string/textview_rec_text
        textRec.setLength(0);
        textRec.append(activity.getString(R.string.text_rec));
        SBNumFormat.fillTime(textRec, wavSec, 1);
        textRec.append(activity.getString(R.string.text_remain));
        SBNumFormat.fillTime(textRec, wavSecRemain, 0);
        textRec.getChars(0, Math.min(textRec.length(), textRecChar.length), textRecChar, 0);
        ((TextView) activity.findViewById(R.id.textview_rec))
                .setText(textRecChar, 0, Math.min(textRec.length(), textRecChar.length));
    }

    private long timeToUpdate = SystemClock.uptimeMillis();
    private volatile boolean isInvalidating = false;

    // Invalidate graphView in a limited frame rate
    void invalidateGraphView() {
        invalidateGraphView(-1);
    }

    private static final int VIEW_MASK_graphView     = 1<<0;
    private static final int VIEW_MASK_textview_RMS  = 1<<1;
    private static final int VIEW_MASK_textview_peak = 1<<2;
    private static final int VIEW_MASK_CursorLabel   = 1<<3;
    private static final int VIEW_MASK_RecTimeLable  = 1<<4;

    private void invalidateGraphView(int viewMask) {
        if (isInvalidating) {
            return ;
        }
        isInvalidating = true;
        long frameTime;                      // time delay for next frame
        if (graphView.getShowMode() != AnalyzerGraphic.PlotMode.SPECTRUM) {
            frameTime = (long)(1000/fpsLimit);  // use a much lower frame rate for spectrogram
        } else {
            frameTime = 1000/60;
        }
        long t = SystemClock.uptimeMillis();
        //  && !graphView.isBusy()
        if (t >= timeToUpdate) {    // limit frame rate
            timeToUpdate += frameTime;
            if (timeToUpdate < t) {            // catch up current time
                timeToUpdate = t+frameTime;
            }
            idPaddingInvalidate = false;
            // Take care of synchronization of graphView.spectrogramColors and iTimePointer,
            // and then just do invalidate() here.
            if ((viewMask & VIEW_MASK_graphView) != 0)
                graphView.invalidate();
            // RMS
            if ((viewMask & VIEW_MASK_textview_RMS) != 0)
                refreshRMSLabel(activity.dtRMSFromFT);
            // peak frequency
            if ((viewMask & VIEW_MASK_textview_peak) != 0)
                refreshPeakLabel(activity.maxAmpFreq, activity.maxAmpDB);
            if ((viewMask & VIEW_MASK_CursorLabel) != 0)
                refreshCursorLabel();
            if ((viewMask & VIEW_MASK_RecTimeLable) != 0 && activity.samplingThread != null)
                refreshRecTimeLable(activity.samplingThread.wavSec, activity.samplingThread.wavSecRemain);
        } else {
            if (! idPaddingInvalidate) {
                idPaddingInvalidate = true;
                paddingViewMask = viewMask;
                paddingInvalidateHandler.postDelayed(paddingInvalidateRunnable, timeToUpdate - t + 1);
            } else {
                paddingViewMask |= viewMask;
            }
        }
        isInvalidating = false;
    }

    void setFpsLimit(double _fpsLimit) {
        fpsLimit = _fpsLimit;
    }

    private volatile boolean idPaddingInvalidate = false;
    private volatile int paddingViewMask = -1;
    private Handler paddingInvalidateHandler = new Handler();

    // Am I need to use runOnUiThread() ?
    private final Runnable paddingInvalidateRunnable = new Runnable() {
        @Override
        public void run() {
            if (idPaddingInvalidate) {
                // It is possible that t-timeToUpdate <= 0 here, don't know why
                invalidateGraphView(paddingViewMask);
            }
        }
    };
}
