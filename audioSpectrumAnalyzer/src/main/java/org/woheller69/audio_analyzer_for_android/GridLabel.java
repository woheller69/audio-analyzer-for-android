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

import android.util.Log;

import static java.lang.Math.abs;
import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static java.lang.Math.log;
import static java.lang.Math.log10;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.Math.sqrt;

/**
 * Generate grid label (marker)
 */

class GridLabel {
    private final static String TAG = "GridLabel:";

    // Never null!
    double[] values = new double[0];  // TODO: use a better name?
    double[] ticks  = new double[0];  // TODO: use a better name?

    enum Type {  // java's enum type is inconvenient
        FREQ(0), DB(1), TIME(2), FREQ_LOG(3), FREQ_NOTE(4);

        private final int value;
        private Type(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    private Type gridType;
    private double gridDensity;

    GridLabel(Type _gridType, double _gridDensity) {
        gridType = _gridType;
        gridDensity = _gridDensity;
    }

    Type getGridType() { return gridType; }

    void setGridType(GridLabel.Type gt) { gridType = gt; }
    void setDensity(double _gridDensity) { gridDensity = _gridDensity; }

    // return position of grid lines, there are roughly gridDensity lines for the bigger grid
    private static int genLinearGridPoints(double[][] gridPointsArray, double startValue, double endValue,
                                            double gridDensity, Type scale_mode) {
        if (Double.isInfinite(startValue+endValue) || Double.isNaN(startValue+endValue)) {
            Log.e(TAG, "genLinearGridPoints(): value invalid");
            return 0;
        }
        if (startValue == endValue) {
            Log.e(TAG, "genLinearGridPoints(): startValue == endValue");
            return 0;
        }
        if (startValue > endValue) {
            double t = endValue;
            endValue = startValue;
            startValue = t;
        }
        if (scale_mode == Type.FREQ || scale_mode == Type.TIME) {
            if (gridDensity < 3.2) {
                // 3.2 >= 2 * 5/sqrt(2*5), so that there are at least 2 bigger grid.
                // The constant here is because: if gridIntervalGuess = sqrt(2*5), then gridIntervalBig = 5
                // i.e. grid size become larger by factor 5/sqrt(2*5).
                // By setting gridDensity = 3.2, we can make sure minimum gridDensity > 2
                gridDensity = 3.2;
            }
        } else {
            if (gridDensity < 3.5) {  // similar discussion as above
                gridDensity = 3.5;      // 3.5 >= 2 * 3/sqrt(1*3)
            }
        }
        double intervalValue = endValue - startValue;
        double gridIntervalGuess = intervalValue / gridDensity;
        double gridIntervalBig;
        double gridIntervalSmall;

        // Determine a suitable grid interval from guess
        if (scale_mode == Type.FREQ || scale_mode == Type.TIME || intervalValue <= 1) {  // Linear scale (Hz, Time)
            double exponent = pow(10, floor(log10(gridIntervalGuess)));
            double fraction = gridIntervalGuess / exponent;
            // grid interval is 1, 2, 5, 10, ...
            if (fraction < sqrt(1*2)) {
                gridIntervalBig   = 1;
                gridIntervalSmall = 0.2;
            } else if (fraction < sqrt(2*5)) {
                gridIntervalBig   = 2;
                gridIntervalSmall = 1.0;
            } else if (fraction < sqrt(5*10)) {
                gridIntervalBig   = 5;
                gridIntervalSmall = 1;
            } else {
                gridIntervalBig   = 10;
                gridIntervalSmall = 2;
            }
            gridIntervalBig   *= exponent;
            gridIntervalSmall *= exponent;
        } else {  // dB scale
            if (gridIntervalGuess > sqrt(36*12)) {
                gridIntervalBig   = 36;
                gridIntervalSmall = 12;
            } else if (gridIntervalGuess > sqrt(12*6)) {
                gridIntervalBig   = 12;
                gridIntervalSmall = 2;
            } else if (gridIntervalGuess > sqrt(6*3)) {
                gridIntervalBig   = 6;
                gridIntervalSmall = 1;
            } else if (gridIntervalGuess > sqrt(3*1)) {
                gridIntervalBig   = 3;
                gridIntervalSmall = 1;
            } else {
                gridIntervalBig   = 1;
                gridIntervalSmall = 1.0/6;
            }
        }

        if (gridPointsArray == null || gridPointsArray.length != 2) {
            Log.e(TAG, "genLinearGridPoints(): empty array!!");
            return 0;
        }

        // Reallocate if number of grid lines are different
        // Then fill in the gird line coordinates. Assuming the grid lines starting from 0
        double gridStartValueBig   = ceil(startValue / gridIntervalBig)   * gridIntervalBig;
        int nGrid = (int) floor((endValue - gridStartValueBig) / gridIntervalBig) + 1;
        if (nGrid != gridPointsArray[0].length) {
            gridPointsArray[0] = new double[nGrid];
        }
        double[] bigGridPoints = gridPointsArray[0];
        for (int i = 0; i < nGrid; i++) {
            bigGridPoints[i] = gridStartValueBig + i*gridIntervalBig;
        }

        double gridStartValueSmall = ceil(startValue / gridIntervalSmall) * gridIntervalSmall;
        nGrid = (int) floor((endValue - gridStartValueSmall) / gridIntervalSmall) + 1;
        if (nGrid != gridPointsArray[1].length) {    // reallocate space when need
            gridPointsArray[1] = new double[nGrid];
        }
        double[] smallGridPoints = gridPointsArray[1];
        for (int i = 0; i < nGrid; i++) {
            smallGridPoints[i] = gridStartValueSmall + i*gridIntervalSmall;
        }
        return (int)floor(log10(gridIntervalBig));
    }

    // TODO: might be merge to genLinearGridPoints.
    private static int genLogarithmicGridPoints(double[][] gridPointsArray, double startValue, double endValue,
                                                 double gridDensity) {
        if (Double.isInfinite(startValue + endValue) || Double.isNaN(startValue + endValue)) {
            Log.e(TAG, "genLogarithmicGridPoints(): value invalid");
            return 0;
        }
        if (startValue == endValue) {
            Log.e(TAG, "genLogarithmicGridPoints(): startValue == endValue");
            return 0;
        }
        if (startValue <=0 || endValue <= 0) {
            Log.e(TAG, "genLogarithmicGridPoints(): startValue <=0 || endValue <= 0 !!");
            return 0;
        }
        if (startValue > endValue) {
            double t = endValue;
            endValue = startValue;
            startValue = t;
        }

        int cntTick = 0;
        int cntVal = 0;
        if (endValue / startValue > 100) {
            // Major:  1, 10, 100, ...
            // Minor:  1, 2, 3, ... , 9, 10, 20, 30, ...
            double gapChangingPoint = pow(10, floor(log10(startValue)));
            double gridIntervalSmall = ceil(startValue / gapChangingPoint) * gapChangingPoint;
//            Log.i(TAG, "startValue = " + startValue + "  gapChangingPoint = " + gapChangingPoint + "  gridIntervalSmall = " + gridIntervalSmall);

            double b1 = pow(10, ceil(log10(startValue)));
            double b2 = pow(10, floor(log10(endValue)));
            int nGridBig   = (int)(floor(log10(endValue)) - ceil(log10(startValue)) + 1);
            int nGridSmall = (int)(floor((b1 - startValue) / gapChangingPoint)
                    + 9 * (nGridBig - 1)
                    + floor((endValue - b2) / b2)) + 1;
            if (nGridBig != gridPointsArray[0].length) {
                gridPointsArray[0] = new double[nGridBig];
            }
            if (nGridSmall != gridPointsArray[1].length) {
                gridPointsArray[1] = new double[nGridSmall];
            }
            while (gapChangingPoint <= endValue) {
                while (gridIntervalSmall < 10*gapChangingPoint && gridIntervalSmall <= endValue) {
                    gridPointsArray[1][cntTick++] = gridIntervalSmall;
                    gridIntervalSmall += gapChangingPoint;
                }
                if (gapChangingPoint >= startValue) {
                    gridPointsArray[0][cntVal++] = gapChangingPoint;
                }
                gapChangingPoint *= 10;
            }
            return Integer.MAX_VALUE;
        } else if (endValue / startValue > 10) {
            // Major:  1, 2, 3, ... , 9, 10, 20, 30, ...
            // Minor:  1, 1.5, 2, 2.5, ..., 9, 9.5, 10, 15, 20 25, ...
            double gapChangingPoint = pow(10, floor(log10(startValue)));
            double gapChangingPointd2 = gapChangingPoint / 2;
            double gridIntervalSmall = ceil(startValue / gapChangingPoint) * gapChangingPoint;
            double gridIntervalSmall2 = ceil(startValue / gapChangingPointd2) * gapChangingPointd2;

            double b1 = pow(10, ceil(log10(startValue)));
            double b2 = pow(10, floor(log10(endValue)));
            int nGridBig   = (int)(floor(log10(endValue)) - ceil(log10(startValue)) + 1);
            int nGridSmall = (int)(floor((b1 - startValue) / gapChangingPoint)
                    + 9 * (nGridBig - 1)
                    + floor((endValue - b2) / b2)) + 1;
            int nGridSmall2 = (int)(floor((b1 - startValue) / gapChangingPointd2)
                    + 18 * (nGridBig - 1)
                    + floor((endValue - b2) / (b2/2))) + 1;
            if (nGridSmall != gridPointsArray[0].length) {
                gridPointsArray[0] = new double[nGridSmall];
            }
            if (nGridSmall2 != gridPointsArray[1].length) {
                gridPointsArray[1] = new double[nGridSmall2];
            }
            while (gapChangingPoint <= endValue) {
                while (gridIntervalSmall < 10*gapChangingPoint && gridIntervalSmall <= endValue) {
                    gridPointsArray[0][cntVal++] = gridIntervalSmall;
                    gridIntervalSmall += gapChangingPoint;
                }
                while (gridIntervalSmall2 < 10*gapChangingPoint && gridIntervalSmall2 <= endValue) {
                    gridPointsArray[1][cntTick++] = gridIntervalSmall2;
                    gridIntervalSmall2 += gapChangingPointd2;
                }
                gapChangingPoint *= 10;
                gapChangingPointd2 = gapChangingPoint / 2;
            }
            return Integer.MAX_VALUE;
        } else {
            // Linear increment.
            // limit the largest major gap <= 1/3 screen width
            if (gridDensity < 3) {
//                Log.i(TAG, "genLogarithmicGridPoints(): low gridDensity = " + gridDensity);
                gridDensity = 3;
            }
            // reduce gridDensity when endValue/startValue is large
            gridDensity /= log(49 * endValue/startValue + 1) / log(50);
            double gridIntervalGuess = pow(endValue/startValue, 1/gridDensity);
//            Log.i(TAG, "  gridIntervalGuess = " + gridIntervalGuess + "  gridDensity = " + gridDensity + "  s = " + startValue + "  e = " + endValue);
            gridIntervalGuess = (gridIntervalGuess-1) * startValue;
//            Log.i(TAG, "  gridIntervalGuess2 = " + gridIntervalGuess);

            double gridIntervalBig, gridIntervalSmall;
            double exponent = pow(10, floor(log10(gridIntervalGuess)));
            double fraction = gridIntervalGuess / exponent;
            // grid interval is 1, 2, 5, 10, ...
            if (fraction < sqrt(1*2)) {
                gridIntervalBig   = 1;
                gridIntervalSmall = 0.2;
            } else if (fraction < sqrt(2*5)) {
                gridIntervalBig   = 2;
                gridIntervalSmall = 1.0;
            } else if (fraction < sqrt(5*10)) {
                gridIntervalBig   = 5;
                gridIntervalSmall = 1;
            } else {
                gridIntervalBig   = 10;
                gridIntervalSmall = 2;
            }
            gridIntervalBig   *= exponent;
            gridIntervalSmall *= exponent;

            double gridStartValueBig   = ceil(startValue / gridIntervalBig)   * gridIntervalBig;
            int nGrid = (int) floor((endValue - gridStartValueBig) / gridIntervalBig) + 1;
            if (nGrid != gridPointsArray[0].length) {
                gridPointsArray[0] = new double[nGrid];
            }
            double[] bigGridPoints = gridPointsArray[0];
            for (int i = 0; i < nGrid; i++) {
                bigGridPoints[i] = gridStartValueBig + i*gridIntervalBig;
            }

            double gridStartValueSmall = ceil(startValue / gridIntervalSmall) * gridIntervalSmall;
            nGrid = (int) floor((endValue - gridStartValueSmall) / gridIntervalSmall) + 1;
            if (nGrid != gridPointsArray[1].length) {    // reallocate space when need
                gridPointsArray[1] = new double[nGrid];
            }
            double[] smallGridPoints = gridPointsArray[1];
            for (int i = 0; i < nGrid; i++) {
                smallGridPoints[i] = gridStartValueSmall + i*gridIntervalSmall;
            }
            return (int)floor(log10(gridIntervalBig));
        }
    }

    private static double[] majorPitch = new double[]{0, 2, 4, 5, 7, 9, 11};

    // majorPitchCount[ceil(p)] is the number of notes should show (p is right boundary)
    // 7 - majorPitchCount[ceil(p)] for left boundary
    private static int[] majorPitchCount = new int[]{0, 1, 1, 2, 2, 3, 4, 4, 5, 5, 6, 6, 7, 8};
    private static int[] isMajorPitch = new int[]{1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 0, 1, 1};

    private static double mod(double v, double m) { return v - floor(v/m)*m; };

    private static int genMusicNoteGridPoints(double[][] gridPointsArray, double startValue, double endValue,
                                                double gridDensity, int start_note) {
        // For Type.FREQ_NOTE
        if (Double.isInfinite(startValue+endValue) || Double.isNaN(startValue+endValue)) {
            Log.e(TAG, "genLinearGridPoints(): value invalid");
            return 0;
        }
        if (startValue == endValue) {
            Log.e(TAG, "genLinearGridPoints(): startValue == endValue");
            return 0;
        }
        if (startValue > endValue) {
            double t = endValue;
            endValue = startValue;
            startValue = t;
        }

        startValue = AnalyzerUtil.freq2pitch(startValue);
        endValue   = AnalyzerUtil.freq2pitch(endValue);

        double intervalValue = endValue - startValue;
        double gridIntervalGuess = intervalValue / gridDensity;
        double gridIntervalBig = 0;
        double gridIntervalSmall = 0;

        boolean skipGridCal = false;

        if (gridIntervalGuess > 5) {
            gridIntervalBig = 12;
            gridIntervalSmall = 1;
        } else if (gridIntervalGuess > 1.2) {
            gridIntervalBig = 1;
            gridIntervalSmall = 0.5;
        } else {
            genLinearGridPoints(gridPointsArray, startValue, endValue, gridDensity, Type.FREQ);
            skipGridCal = true;
        }

        if (!skipGridCal) {
            int nGrid = 0;
            // start_note == 0 <=> C
            for (int k = 0; k < 2; k++) {
                double gridInterval = k == 0 ? gridIntervalBig : gridIntervalSmall;
                double[] val = gridPointsArray[k];
                if (gridInterval == 1) {
                    startValue += start_note;
                    endValue += start_note;
                    int startOctave = (int) floor(startValue / 12);
                    int endOctave   = (int) floor(endValue   / 12);
                    nGrid = 7 - majorPitchCount[(int) ceil(startValue - startOctave * 12)]
                            + 7 * (endOctave - startOctave - 1)
                            + majorPitchCount[(int) ceil(endValue - endOctave * 12)];
                    if (nGrid != val.length) {
                        val = gridPointsArray[k] = new double[nGrid];
                    }
                    int v = (int) ceil(startValue);
                    int cnt = 0;
                    while (v < endValue) {
                        if (isMajorPitch[(int)mod(v, 12)] == 1) {
                            val[cnt++] = v;
                        }
                        v++;
                    }
                    startValue -= start_note;
                    endValue -= start_note;
                    for (int i = 0; i < val.length; i++) {
                        val[i] -= start_note;
                    }
                } else {
                    // equal interval
                    double gridStartValue = ceil(startValue / gridInterval) * gridInterval;
                    nGrid = (int) floor((endValue - gridStartValue) / gridInterval) + 1;
                    if (nGrid != val.length) {
                        val = gridPointsArray[k] = new double[nGrid];
                    }
                    for (int i = 0; i < nGrid; i++) {
                        val[i] = gridStartValue + i * gridInterval;
                    }
                }
            }
        }

//        Log.i(TAG, "Note: " + startPitch + "(" + startValue + ") ~ " + endPitch + " (" + endValue + ") div " + gridPointsArray[0].length);
        for (int k = 0; k < 2; k++) {
            for (int i = 0; i < gridPointsArray[k].length; i++) {
                gridPointsArray[k][i] = AnalyzerUtil.pitch2freq(gridPointsArray[k][i]);
            }
        }
        return 0;
    }

    StringBuilder[] strings = new StringBuilder[0];
    char[][] chars = new char[0][];

    private double[] oldGridBoundary = new double[2];
    private double[][] gridPointsArray = new double[2][];

    // It's so ugly to write these StringBuffer stuff -- in order to reduce garbage
    // Also, since there is no "pass by reference", modify array is also ugly...
    void updateGridLabels(double startValue, double endValue) {
        gridPointsArray[0] = values;
        gridPointsArray[1] = ticks;
        int gapPrecision = 0;
        switch (gridType) {
            case FREQ_LOG:
                gapPrecision = genLogarithmicGridPoints(gridPointsArray, startValue, endValue, gridDensity);
                break;
            case FREQ_NOTE:
                gapPrecision = genMusicNoteGridPoints(gridPointsArray, startValue, endValue, gridDensity, 0);
                break;
            default:
                gapPrecision = genLinearGridPoints(gridPointsArray, startValue, endValue, gridDensity, gridType);
        }
        values = gridPointsArray[0];
        ticks  = gridPointsArray[1];
        boolean needUpdate = false;
        if (values.length != strings.length) {
            strings = new StringBuilder[values.length];
            for (int i = 0; i < values.length; i++) {
                strings[i] = new StringBuilder();
            }
            chars = new char[values.length][];
            for (int i = 0; i < values.length; i++) {
                chars[i] = new char[16];    // hand coded max char length...
            }
            needUpdate = true;
        }
        if (values.length > 0 && (needUpdate || values[0] != oldGridBoundary[0]
                || values[values.length-1] != oldGridBoundary[1])) {
            oldGridBoundary[0] = values[0];
            oldGridBoundary[1] = values[values.length-1];
            for (int i = 0; i < strings.length; i++) {
                strings[i].setLength(0);
                if (gridType == Type.FREQ_NOTE) {
                    double p = AnalyzerUtil.freq2pitch(values[i]);
                    AnalyzerUtil.pitch2Note(strings[i], p, gapPrecision, true);
                } else {
                    if (gapPrecision == Integer.MAX_VALUE) {  // 1000, 10000 -> 1k, 10k
                        if (values[i] >= 1000) {
                            SBNumFormat.fillInNumFixedFrac(strings[i], values[i] / 1000, 7, 0);
                            strings[i].append('k');
                        } else {
                            SBNumFormat.fillInNumFixedFrac(strings[i], values[i], 7, 0);
                        }
                    } else if (gapPrecision >= 3) {  // use 1k 2k ...
                        SBNumFormat.fillInNumFixedFrac(strings[i], values[i] / 1000, 7, 0);
                        strings[i].append('k');
                    } else if (gapPrecision >= 0) {
                        SBNumFormat.fillInNumFixedFrac(strings[i], values[i], 7, 0);
                    } else {
                        SBNumFormat.fillInNumFixedFrac(strings[i], values[i], 7, -gapPrecision);
                    }
                }
                strings[i].getChars(0, strings[i].length(), chars[i], 0);
            }
        }
    }

    boolean isImportantLabel(int j) {
        // For freq, time
        if (gridType == Type.FREQ_NOTE) {
            // assume C major
            return AnalyzerUtil.isAlmostInteger(AnalyzerUtil.freq2pitch(values[j])/12.0);
        } else {
            return AnalyzerUtil.isAlmostInteger(log10(values[j]));
        }
    }
}
