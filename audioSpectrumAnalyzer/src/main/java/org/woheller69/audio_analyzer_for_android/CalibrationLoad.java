package org.woheller69.audio_analyzer_for_android;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Load calibration file.
 */

class CalibrationLoad {
    final static String TAG = "CalibrationLoad";

    double[] freq = new double[0];
    double[] gain = new double[0];
    double centralFreq = 1000;
    double centralGain = -37.4;
    String name = null;

    void loadFile(Uri calibUri, Context context) {
        String calibPath = calibUri.getPath();
        BufferedReader br;
        InputStream inputStream;
        try {
            inputStream = context.getContentResolver().openInputStream(calibUri);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "no calib file found: " + calibPath);
            return;
        }
        if (inputStream == null) {
            Log.e(TAG, "open calib file fail: " + calibPath);
            return;
        }
        br = new BufferedReader(new InputStreamReader(inputStream));

        String line = null;
        try {
            int lineCount = 0;
            ArrayList<Double> freqList = new ArrayList<>();
            ArrayList<Double> amplitudeDBList = new ArrayList<>();
            while (true) {
                line = br.readLine();
                if (line == null) break;
                lineCount ++;
                line = line.trim();
                if (line.length() == 0) {  // skip empty line
                    continue;
                }
                char c = line.charAt(0);
                if ('0' <= c && c <= '9' || c == '.' || c == '-') {  // Should be a number
                    // 20.00	-4.2
                    String[] strs = line.split("[ \t]+");
                    if (strs.length != 2) {
                        Log.w(TAG, "Fail to parse line " + lineCount + " :" + line);
                        continue;
                    }
                    freqList       .add(Double.valueOf(strs[0]));
                    amplitudeDBList.add(Double.valueOf(strs[1]));
                } else if (line.charAt(0) == '*') {  // Dayton Audio txt/cal or miniDSP cal file
                    // parse Only the Dayton Audio header:
                    //*1000Hz	-37.4
                    String[] strs = line.substring(1).split("Hz[ \t]+");
                    if (strs.length == 2) {
                        Log.i(TAG, "Dayton Audio header");
                        centralFreq = Double.valueOf(strs[0]);
                        centralGain = Double.valueOf(strs[1]);
                    }
                    // miniDSP cal header:
                    //* miniDSP PMIK-1 calibration file, serial: 8000234, format: cal
                    // Skip
                } else if (line.charAt(0) == '"') {
                    // miniDSP txt header:
                    //"miniDSP PMIK-1 calibration file, serial: 8000234, format: frd"
                    // Skip
                    // miniDSP frd header:
                    //"miniDSP PMIK-1 calibration file, serial: 8000234, format: frd"
                    // Skip
                } else if (line.charAt(0) == '#') {
                    // Shell style comment line
                    // Skip
                } else {
                    Log.e(TAG, "Bad calibration file.");
                    freqList.clear();
                    amplitudeDBList.clear();
                    break;
                }
            }
            br.close();

            freq = new double[freqList.size()];
            gain = new double[freqList.size()];
            Iterator itr  = freqList.iterator();
            Iterator itr2 = amplitudeDBList.iterator();
            for (int j=0; itr.hasNext(); j++ ) {
                freq[j] = (double)itr.next();
                gain[j] = (double)itr2.next();
            }
        } catch (IOException e) {
            Log.e(TAG, "Fail to read file: " + calibPath);
        }
        name = calibPath.substring(calibPath.lastIndexOf("/")+1);
    }
}
