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
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import androidx.annotation.NonNull;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import static java.lang.Math.abs;
import static java.lang.Math.round;
import static java.util.Arrays.sort;

/**
 * Utility functions for audio analyzer.
 */

class AnalyzerUtil {
    private static String TAG = "AnalyzerUtil";
    private static final String[] LP = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    static double freq2pitch(double f) {
        return 69 + 12 * Math.log(f/440.0)/Math.log(2);  // MIDI pitch
    }

    static double pitch2freq(double p) {
        return Math.pow(2, (p - 69)/12) * 440.0;
    }

    static void pitch2Note(StringBuilder a, double p, int prec_frac, boolean tightMode) {
        int pi = (int) Math.round(p);
        int po = (int) Math.floor(pi/12.0);
        int pm = pi-po*12;
        a.append(LP[pm]);
        SBNumFormat.fillInInt(a, po-1);
        if (LP[pm].length() == 1 && !tightMode) {
            a.append(' ');
        }
        double cent = Math.round(100 * (p - pi) * Math.pow(10, prec_frac)) * Math.pow(10, -prec_frac);
        if (!tightMode) {
            SBNumFormat.fillInNumFixedWidthSignedFirst(a, cent, 2, prec_frac);
        } else {
            if (cent != 0) {
                a.append(cent < 0 ? '-' : '+');
                SBNumFormat.fillInNumFixedWidthPositive(a, Math.abs(cent), 2, prec_frac, '\0');
            }
        }
    }

    // Convert frequency to pitch
    // Fill with sFill until length is 6. If sFill=="", do not fill
    static void freq2Cent(StringBuilder a, double f, String sFill) {
        if (f<=0 || Double.isNaN(f) || Double.isInfinite(f)) {
            a.append("      ");
            return;
        }
        int len0 = a.length();
        // A4 = 440Hz
        double p = freq2pitch(f);
        pitch2Note(a, p, 0, false);
        while (a.length()-len0 < 6 && sFill!=null && sFill.length()>0) {
            a.append(sFill);
        }
    }

    // used to detect if the data is unchanged
    private double[] cmpDB;
    void sameTest(double[] data) {
        // test
        if (cmpDB == null || cmpDB.length != data.length) {
            Log.i(TAG, "sameTest(): new");
            cmpDB = new double[data.length];
        } else {
            boolean same = true;
            for (int i=0; i<data.length; i++) {
                if (!Double.isNaN(cmpDB[i]) && !Double.isInfinite(cmpDB[i]) && cmpDB[i] != data[i]) {
                    same = false;
                    break;
                }
            }
            if (same) {
                Log.i(TAG, "sameTest(): same data row!!");
            }
            System.arraycopy(data, 0, cmpDB, 0, data.length);
        }
    }

    static boolean isAlmostInteger(double x) {
        // return x % 1 == 0;
        double i = round(x);
        if (i == 0) {
            return abs(x) < 1.2e-7;  // 2^-23 = 1.1921e-07
        } else {
            return abs(x - i) / i < 1.2e-7;
        }
    }

    /**
     * Return a array of verified audio sampling rates.
     * @param requested: the sampling rates to be verified
     */
    static String[] validateAudioRates(String[] requested) {
        ArrayList<String> validated = new ArrayList<>();
        for (String s : requested) {
            int rate;
            String[] sv = s.split("::");
            if (sv.length == 1) {
                rate = Integer.parseInt(sv[0]);
            } else {
                rate = Integer.parseInt(sv[1]);
            }
            if (rate != 0) {
                int recBufferSize = AudioRecord.getMinBufferSize(rate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (recBufferSize != AudioRecord.ERROR_BAD_VALUE) {
                    // Extra exam to high sampling rate, by opening it.
                    if (rate > 48000) {
                        AudioRecord record;
                        try {
                            record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, rate,
                                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recBufferSize);
                        } catch (IllegalArgumentException e) {
                            continue;
                        }
                        if (record.getState() == AudioRecord.STATE_INITIALIZED) {
                            validated.add(s);
                        }
                    } else {
                        validated.add(s);
                    }
                }
            } else {
                validated.add(s);
            }
        }
        return validated.toArray(new String[0]);
    }

    static double parseDouble(String st) {
        try {
            return Double.parseDouble(st);
        } catch (NumberFormatException e) {
            return 0.0/0.0;  // nan
        }
    }

    // Thanks http://stackoverflow.com/questions/16319237/cant-put-double-sharedpreferences
    static SharedPreferences.Editor putDouble(final SharedPreferences.Editor edit, final String key, final double value) {
        return edit.putLong(key, Double.doubleToRawLongBits(value));
    }
    static double getDouble(final SharedPreferences prefs, final String key, final double defaultValue) {
        return Double.longBitsToDouble(prefs.getLong(key, Double.doubleToLongBits(defaultValue)));
    }

    final int[]    stdSourceId;  // how to make it final?
    final int[]    stdSourceApi;
    final String[] stdSourceName;
    final String[] stdAudioSourcePermission;
    AnalyzerUtil(Context context) {
        stdSourceId   = context.getResources().getIntArray(R.array.std_audio_source_id);
        stdSourceApi  = context.getResources().getIntArray(R.array.std_audio_source_api_level);
        stdSourceName            = context.getResources().getStringArray(R.array.std_audio_source_name);
        stdAudioSourcePermission = context.getResources().getStringArray(R.array.std_audio_source_permission);
    }

    // filterLevel = 0: no filter
    //             & 1: leave only standard sources
    //             & 2: leave only permitted sources (&1)
    //             & 4: leave only sources coincide the API level (&1)
    int[] GetAllAudioSource(int filterLevel) {
        // Use reflection to get all possible audio source (in compilation environment)
        ArrayList<Integer> iList = new ArrayList<>();
        Class<MediaRecorder.AudioSource> clazz = MediaRecorder.AudioSource.class;
        Field[] arr = clazz.getFields();
        for (Field f : arr) {
            if (f.getType().equals(int.class)) {
                try {
                    int id = (int)f.get(null);
                    iList.add(id);
                    Log.i("Sources id:", "" + id);
                } catch (IllegalAccessException e) {}
            }
        }
        Collections.sort(iList);

        // Filter unnecessary audio source
        Iterator<Integer> iterator;
        ArrayList<Integer> iListRet = iList;
        if (filterLevel > 0) {
            iListRet = new ArrayList<>();
            iterator = iList.iterator();
            for (int i = 0; i < iList.size(); i++) {
                int id = iterator.next();
                int k = arrayContainInt(stdSourceId, id); // get the index to standard source if possible
                if (k < 0) continue;
                if ((filterLevel & 2) > 0 && !stdAudioSourcePermission[k].equals("")) continue;
                if ((filterLevel & 4) > 0 && stdSourceApi[k] > Build.VERSION.SDK_INT) continue;
                iListRet.add(id);
            }
        }

        // Return an int array
        int[] ids = new int[iListRet.size()];
        iterator = iListRet.iterator();
        for (int i = 0; i < ids.length; i++) {
            ids[i] = iterator.next();
        }
        return ids;
    }

    String getAudioSourceName(int id) {
        int k = arrayContainInt(stdSourceId, id);
        if (k >= 0) {
            return stdSourceName[k];
        } else {
            return "(unknown id=" + id +")";
        }
    }

    // Java s**ks
    static int arrayContainInt(int[] arr, int v) {
        if (arr == null) return -1;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == v) return i;
        }
        return -1;
    }

    static int arrayContainString(String[] arr, String v) {
        if (arr == null) return -1;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(v)) return i;
        }
        return -1;
    }

    static boolean isSorted(double[] a) {
        if (a == null || a.length <= 1) {
            return true;
        }
        double d = a[0];
        for (int i = 1; i < a.length; i++) {
            if (d > a[i]) {
                return false;
            }
            d = a[i];
        }
        return true;
    }

    static double[] interpLinear(double[] xFixed, double[] yFixed, double[] xInterp) {
        if (xFixed == null || yFixed == null || xInterp == null) {
            return null;
        }
        if (xFixed.length == 0 || yFixed.length == 0 || xInterp.length == 0) {
            return new double[0];
        }
        if (xFixed.length != yFixed.length) {
            Log.e(TAG, "Input data length mismatch");
        }

//        if (!isSorted(xFixed)) {
//            sort(xFixed);
//            yFixed = yFixed(x_id);
//        }
        if (!isSorted(xInterp)) {
            sort(xInterp);
        }
        double[] yInterp = new double[xInterp.length];

        int xiId = 0;
        while (xiId < xInterp.length && xInterp[xiId] < xFixed[0]) {
            yInterp[xiId] = yFixed[0];
            xiId++;
        }

        for (int xfId = 1; xfId < xFixed.length; xfId++) {
            while (xiId < xInterp.length && xInterp[xiId] < xFixed[xfId]) {
                // interpolation using (xFixed(x_id - 1), yFixed(xfId - 1)) and (xFixed(x_id), yFixed(xfId))
                yInterp[xiId] = (yFixed[xfId] - yFixed[xfId - 1]) / (xFixed[xfId] - xFixed[xfId - 1]) * (xInterp[xiId] - xFixed[xfId - 1]) + yFixed[xfId - 1];
                xiId++;
            }
        }

        while (xiId < xInterp.length) {
            yInterp[xiId] = yFixed[yFixed.length - 1];
            xiId++;
        }

        return yInterp;
    }

    // Binary search for nearest element.
    // bLower == true : return lower  index if no exact match.
    // bLower == false: return higher index if no exact match.
    // x must be sorted (ascending).
    static int binarySearchElem(double[] x, double v, boolean bLower) {
        if (x == null || x.length == 0) return -1;
        int l = 0;
        int h = x.length - 1;
        if (v == x[l]) {
            return l;
        }
        if (v < x[l]) {
            if (bLower) {
                return l;  // or? -1;
            } else {
                return l;
            }
        }
        if (v == x[h]) {
            return h;
        }
        if (v > x[h]) {
            if (bLower) {
                return h;
            } else {
                return h;  // or? -1;
            }
        }
        // x[0] < v < x[len-1]
        int m;
        while (l+1 < h) {
            m = (l + h) / 2;
            if (v == x[m]) {
                return m;
            }
            if (v < x[m]) {
                h = m;
            } else {
                l = m;
            }
        }
        // Now we must have l+1 == h, and no exact match was found.
        if (bLower) {
            return l;
        } else {
            return h;
        }
    }

    static class PeakHoldAndFall {
        double[] v_peak = new double[0];
        double[] t_peak = new double[0];
        double t_hold = 2.0;
        double drop_speed = 20.0;

        PeakHoldAndFall() {}

        PeakHoldAndFall(double hold_time, double _drop_speed) {
            t_hold = hold_time;
            drop_speed = _drop_speed;
        }

        void addCurrentValue(@NonNull double[] v_curr, double dt) {
            if (v_curr.length != v_peak.length) {
                v_peak = new double[v_curr.length];
                System.arraycopy(v_curr, 0, v_peak, 0, v_curr.length);
                t_peak = new double[v_curr.length];
                // t_peak initialized to 0.0 by default (Java).
            } else {
                for (int k = 0; k < v_peak.length; k++) {
                    double t_now = t_peak[k] + dt;
                    double v_drop = 0;
                    // v_peak falling in quadratic speed.
                    if (t_peak[k] > t_hold) {
                        v_drop = (t_peak[k] - t_hold + t_now - t_hold) / 2 * dt;
                    } else if (t_now > t_hold) {
                        v_drop = (t_now - t_hold) / 2 * dt;
                    }
                    t_peak[k] = t_now;
                    v_peak[k] -= drop_speed * v_drop;
                    // New peak arrived.
                    if (v_peak[k] < v_curr[k]) {
                        v_peak[k] = v_curr[k];
                        t_peak[k] = 0;
                    }
                }
            }
        }
    }
}
