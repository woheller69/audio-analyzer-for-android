/* Copyright 2017 Eddy Xiao <bewantbe@gmail.com>
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.text.DecimalFormat;

/**
 * For showing and setting plot ranges,
 * including frequency (Hz) and loudness (dB).
 *
 * Ref. https://www.mkyong.com/android/android-prompt-user-input-dialog-example/
 */

class RangeViewDialogC {
    private static final String TAG = "RangeViewDialogC:";
    private AlertDialog rangeViewDialog = null;
    private View rangeViewView;

    private final AnalyzerActivity ct;
    private final AnalyzerGraphic graphView;

    RangeViewDialogC(AnalyzerActivity _ct, AnalyzerGraphic _graphView) {
        ct = _ct;
        graphView = _graphView;
        buildDialog(ct);
    }

    // Watch if there is change in the EditText
    private class MyTextWatcher implements TextWatcher {
        private EditText mEditText;

        MyTextWatcher(EditText editText) {
            mEditText = editText;
        }

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            mEditText.setTag(true);  // flag that indicate range been changed
        }

        @Override
        public void afterTextChanged(Editable editable) {}
    }

    private void SetRangeView(boolean loadSaved) {
        if (rangeViewDialog == null) {
            Log.d(TAG, "ShowRangeViewDialog(): rangeViewDialog is not prepared.");
            return;
        }
        double[] vals = graphView.getViewPhysicalRange();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ct);
        boolean isLock = sharedPref.getBoolean("view_range_lock", false);
        // If locked, load the saved value
        if (isLock || loadSaved) {
            double[] rr = new double[AnalyzerGraphic.VIEW_RANGE_DATA_LENGTH];
            for (int i = 0; i < rr.length; i++) {
                rr[i] = AnalyzerUtil.getDouble(sharedPref, "view_range_rr_" + i, 0.0 / 0.0);
                if (Double.isNaN(rr[i])) {  // not properly initialized
                    Log.w(TAG, "LoadPreferences(): rr is not properly initialized");
                    rr = null;
                    break;
                }
            }
            if (rr != null)
                System.arraycopy(rr, 0, vals, 0, rr.length);
        }

        DecimalFormat df = new DecimalFormat("#.##");
        ((EditText) rangeViewView.findViewById(R.id.et_freq_setting_lower_bound))
                .setText(df.format(vals[0]));
        ((EditText) rangeViewView.findViewById(R.id.et_freq_setting_upper_bound))
                .setText(df.format(vals[1]));
        ((EditText) rangeViewView.findViewById(R.id.et_db_setting_lower_bound))
                .setText(df.format(vals[2]));
        ((EditText) rangeViewView.findViewById(R.id.et_db_setting_upper_bound))
                .setText(df.format(vals[3]));
        ((TextView) rangeViewView.findViewById(R.id.show_range_tv_fL))
                .setText(ct.getString(R.string.show_range_tv_fL));
        ((TextView) rangeViewView.findViewById(R.id.show_range_tv_fH))
                .setText(ct.getString(R.string.show_range_tv_fH,vals[6],vals[7]));
        ((TextView) rangeViewView.findViewById(R.id.show_range_tv_dBL))
                .setText(ct.getString(R.string.show_range_tv_dBL));
        ((TextView) rangeViewView.findViewById(R.id.show_range_tv_dBH))
                .setText(ct.getString(R.string.show_range_tv_dBH,vals[8],vals[9]));

        ((CheckBox) rangeViewView.findViewById(R.id.show_range_lock)).setChecked(isLock);
    }

    void ShowRangeViewDialog() {
        SetRangeView(false);

        // Listener for test if a field is modified
        int[] resList = {R.id.et_freq_setting_lower_bound, R.id.et_freq_setting_upper_bound,
                R.id.et_db_setting_lower_bound,   R.id.et_db_setting_upper_bound};
        for (int id : resList) {
            EditText et = (EditText) rangeViewView.findViewById(id);
            et.setTag(false);                                     // false = no modified
            et.addTextChangedListener(new MyTextWatcher(et));     // Am I need to remove previous Listener first?
        }

        rangeViewDialog.show();
    }

    @SuppressLint("InflateParams")
    private void buildDialog(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        rangeViewView = inflater.inflate(R.layout.dialog_view_range, null);  // null because there is no parent. https://possiblemobile.com/2013/05/layout-inflation-as-intended/
        rangeViewView.findViewById(R.id.show_range_button_load).setOnClickListener(
            new View.OnClickListener() {
                public void onClick(View v) {
                    SetRangeView(true);
                }
            }
        );
        AlertDialog.Builder freqDialogBuilder = new AlertDialog.Builder(context);
        freqDialogBuilder
                .setView(rangeViewView)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        boolean isLock = ((CheckBox) rangeViewView.findViewById(R.id.show_range_lock)).isChecked();
                        double[] rangeDefault = graphView.getViewPhysicalRange();
                        double[] rr = new double[rangeDefault.length / 2];
                        int[] resList = {R.id.et_freq_setting_lower_bound, R.id.et_freq_setting_upper_bound,
                                R.id.et_db_setting_lower_bound,   R.id.et_db_setting_upper_bound};
                        for (int i = 0; i < resList.length; i++) {
                            EditText et = (EditText) rangeViewView.findViewById(resList[i]);
                            if (et == null) Log.v(TAG, "  EditText[" + i + "] == null");
                            if (et == null) continue;
                            if (et.getTag() == null) Log.v(TAG, "  EditText[" + i + "].getTag == null");
                            if (et.getTag() == null || (boolean)et.getTag() || isLock) {
                                rr[i] = AnalyzerUtil.parseDouble(et.getText().toString());
                            } else {
                                rr[i] = rangeDefault[i];
                                Log.v(TAG, "  EditText[" + i + "] not change. rr[i] = " + rr[i]);
                            }
                        }
                        // Save setting to preference, after sanitized.
                        rr = graphView.setViewRange(rr, rangeDefault);
                        SaveViewRange(rr, isLock);
                        if (isLock) {
                            ct.stickToMeasureMode();
                            ct.viewRangeArray = rr;
                        } else {
                            ct.stickToMeasureModeCancel();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Log.v(TAG, "rangeViewDialog: Canceled");
                    }
                });
//    freqDialogBuilder
//            .setTitle("dialog_title");
        rangeViewDialog = freqDialogBuilder.create();
    }

    private void SaveViewRange(double[] rr, boolean isLock) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ct);
        SharedPreferences.Editor editor = sharedPref.edit();
        for (int i = 0; i < rr.length; i++) {
            AnalyzerUtil.putDouble(editor, "view_range_rr_" + i, rr[i]);  // no editor.putDouble ? kidding me?
        }
        editor.putBoolean("view_range_lock", isLock);
        editor.apply();
    }
}
