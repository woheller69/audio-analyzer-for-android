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

import android.os.SystemClock;
import android.util.Log;

/**
 * For checking Recorder running status.
 * Especially buffer overrun, i.e. too few data from the recorder.
 */
class RecorderMonitor {
    private static final String TAG0 = "RecorderMonitor:";
    private final String TAG;
    private long timeUpdateOld, timeUpdateInterval, timeStarted;  // in ms
    private long lastOverrunTime;
    private long nSamplesRead;
    private int sampleRate;
    private int bufferSampleSize;
    private double sampleRateReal;
    private boolean lastCheckOverrun = false;

    RecorderMonitor(int sampleRateIn, int bufferSampleSizeIn, String TAG1) {
        sampleRate = sampleRateIn;
        bufferSampleSize = bufferSampleSizeIn;
        timeUpdateInterval = 2000;
        TAG = TAG1 + TAG0;
    }

    // When start recording, call this
    void start() {
        nSamplesRead = 0;
        lastOverrunTime = 0;
        timeStarted = SystemClock.uptimeMillis();
        timeUpdateOld = timeStarted;
        sampleRateReal = sampleRate;
    }

    // Input number of audio frames that read
    // Return true if an overrun check is performed, otherwise false.
    boolean updateState(int numOfReadShort) {
        long timeNow = SystemClock.uptimeMillis();
        if (nSamplesRead == 0) {      // get overrun checker synchronized
            timeStarted = timeNow - numOfReadShort*1000/sampleRate;
        }
        nSamplesRead += numOfReadShort;
        if (timeUpdateOld + timeUpdateInterval > timeNow) {
            return false;  // do the checks below every timeUpdateInterval ms
        }
        timeUpdateOld += timeUpdateInterval;
        if (timeUpdateOld + timeUpdateInterval <= timeNow) {
            timeUpdateOld = timeNow;  // catch up the time (so that at most one output per timeUpdateInterval)
        }
        long nSamplesFromTime = (long)((timeNow - timeStarted) * sampleRateReal / 1000);
        double f1 = (double) nSamplesRead / sampleRateReal;
        double f2 = (double) nSamplesFromTime / sampleRateReal;
//    Log.i(TAG, "Buffer"
//        + " should read " + nSamplesFromTime + " (" + Math.round(f2*1000)/1000.0 + "s),"
//        + " actual read " + nSamplesRead + " (" + Math.round(f1*1000)/1000.0 + "s)\n"
//        + " diff " + (nSamplesFromTime-nSamplesRead) + " (" + Math.round((f2-f1)*1000)/1e3 + "s)"
//        + " sampleRate = " + Math.round(sampleRateReal*100)/100.0);
        // Check if buffer overrun occur
        if (nSamplesFromTime > bufferSampleSize + nSamplesRead) {
            Log.w(TAG, "SamplingLoop::run(): Buffer Overrun occurred !\n"
              + " should read " + nSamplesFromTime + " (" + Math.round(f2*1000)/1000.0 + "s),"
              + " actual read " + nSamplesRead + " (" + Math.round(f1*1000)/1000.0 + "s)\n"
              + " diff " + (nSamplesFromTime-nSamplesRead) + " (" + Math.round((f2-f1)*1000)/1e3 + "s)"
              + " sampleRate = " + Math.round(sampleRateReal*100)/100.0
              + "\n Overrun counter reset.");
            lastOverrunTime = timeNow;
            nSamplesRead = 0;  // start over
        }
        // Update actual sample rate
        if (nSamplesRead > 10*sampleRate) {
            sampleRateReal = 0.9*sampleRateReal + 0.1*(nSamplesRead * 1000.0 / (timeNow - timeStarted));
            if (Math.abs(sampleRateReal-sampleRate) > 0.0145*sampleRate) {  // 0.0145 = 25 cent
                Log.w(TAG, "SamplingLoop::run(): Sample rate inaccurate, possible hardware problem !\n"
                    + " should read " + nSamplesFromTime + " (" + Math.round(f2*1000)/1000.0 + "s),"
                    + " actual read " + nSamplesRead + " (" + Math.round(f1*1000)/1000.0 + "s)\n"
                    + " diff " + (nSamplesFromTime-nSamplesRead) + " (" + Math.round((f2-f1)*1000)/1e3 + "s)"
                    + " sampleRate = " + Math.round(sampleRateReal*100)/100.0
                    + "\n Overrun counter reset.");
                nSamplesRead = 0;
            }
        }
        lastCheckOverrun = lastOverrunTime == timeNow;
        return true;  // state updated during this check
    }

    boolean getLastCheckOverrun() {
    return lastCheckOverrun;
  }

    long getLastOverrunTime() {
    return lastOverrunTime;
  }

    double getSampleRate() {
    return sampleRateReal;
  }
}
