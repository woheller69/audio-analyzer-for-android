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

import java.util.Arrays;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.corp.productivity.specialprojects.android.fft.RealDoubleFFT;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.asin;
import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.log10;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

// Short Time Fourier Transform
class STFT {
    // data for frequency Analysis
    private double[] spectrumAmpOutCum;
    private double[] spectrumAmpOutTmp;
    private double[] spectrumAmpOut;
    private double[] spectrumAmpOutDB;
    private double[] spectrumAmpIn;
    private double[] spectrumAmpInTmp;
    private double[] wnd;
    private int zeroPadFac = 1; //zero padding factor;
    private double wndEnergyFactor = 1;           // used to keep energy invariant under different window
    private int sampleRate;
    private int fftLen;
    private int hopLen;                           // control overlap of FFTs = (1 - lopLen/fftLen)*100%
    private int spectrumAmpPt;
//    private double[][] spectrumAmpOutArray;
//    private int spectrumAmpOutArrayPt = 0;        // Pointer for spectrumAmpOutArray
    private int nAnalysed = 0;
    private RealDoubleFFT spectrumAmpFFT;
    private boolean boolAWeighting = false;
    private double cumRMS = 0;
    private int    cntRMS = 0;
    private double outRMS = 0;

    private double[] dBAFactor;    // multiply to power spectrum to get A-weighting
    private double[] micGain;

    private double sqr(double x) { return x*x; }
  
    // Generate multiplier for A-weighting
    private void initDBAFactor(int fftlen, double sampleRate) {
        dBAFactor = new double[fftlen/2+1];
        for (int i = 0; i < fftlen/2+1; i++) {
            double f = (double)i/fftlen * sampleRate;
            double r = sqr(12200)*sqr(sqr(f)) / ((f*f+sqr(20.6)) * sqrt((f*f+sqr(107.7)) * (f*f+sqr(737.9))) * (f*f+sqr(12200)));
            dBAFactor[i] = r*r*1.58489319246111;  // 1.58489319246111 = 10^(1/5)
        }
    }

    private void initWindowFunction(int fftlen, String wndName) {
        wnd = new double[fftlen];
        switch (wndName) {
            case "Bartlett":
                for (int i=0; i<wnd.length; i++) {  // Bartlett
                    wnd[i] = asin(sin(PI*i/wnd.length))/PI*2;
                }
                break;
            case "Hanning":
                for (int i=0; i<wnd.length; i++) {  // Hanning, hw=1
                    wnd[i] = 0.5*(1-cos(2*PI*i/(wnd.length-1.))) *2;
                }
                break;
            case "Blackman":
                for (int i=0; i<wnd.length; i++) {  // Blackman, hw=2
                    wnd[i] = 0.42-0.5*cos(2*PI*i/(wnd.length-1))+0.08*cos(4*PI*i/(wnd.length-1));
                }
                break;
            case "Blackman Harris":
                for (int i=0; i<wnd.length; i++) {  // Blackman_Harris, hw=3
                    wnd[i] = (0.35875-0.48829*cos(2*PI*i/(wnd.length-1))+0.14128*cos(4*PI*i/(wnd.length-1))-0.01168*cos(6*PI*i/(wnd.length-1))) *2;
                }
                break;
            case "Kaiser, a=2.0": {
                double a = 2.0;
                double dn = besselCal.i0(PI * a);
                for (int i=0; i<wnd.length; i++) {  // Kaiser, a=2.0
                    wnd[i] = besselCal.i0(PI*a*sqrt(1-(2.0*i/(wnd.length-1)-1.0)*(2.0*i/(wnd.length-1)-1.0))) / dn;
                }
                break;
            }
            case "Kaiser, a=3.0": {
                double a = 3.0;
                double dn = besselCal.i0(PI * a);
                for (int i=0; i<wnd.length; i++) {  // Kaiser, a=3.0
                    wnd[i] = besselCal.i0(PI*a*sqrt(1-(2.0*i/(wnd.length-1)-1.0)*(2.0*i/(wnd.length-1)-1.0))) / dn;
                }
                break;
            }
            case "Kaiser, a=4.0": {
                double a = 4.0;
                double dn = besselCal.i0(PI * a);
                for (int i=0; i<wnd.length; i++) {  // Kaiser, a=4.0
                    wnd[i] = besselCal.i0(PI*a*sqrt(1-(2.0*i/(wnd.length-1)-1.0)*(2.0*i/(wnd.length-1)-1.0))) / dn;
                }
                break;
            }
            // 7 more window functions (by james34602, https://github.com/bewantbe/audio-analyzer-for-android/issues/14 )
            case "Flat-top": {
                for (int i=0; i<wnd.length; i++) {
                    double f = 2 * PI * i / (wnd.length - 1);
                    wnd[i] = 1 - 1.93 * cos(f) + 1.29 * cos(2 * f) - 0.388 * cos(3 * f) + 0.028 * cos(4 * f);
                }
                break;
            }
            case "Nuttall": {
                double a0 = 0.355768;
                double a1 = 0.487396;
                double a2 = 0.144232;
                double a3 = 0.012604;
                for (int i=0; i<wnd.length; i++)
                {
                    double scale = PI * i / (wnd.length - 1);
                    wnd[i] = a0 - a1 * cos(2.0 * scale) + a2 * cos(4.0 * scale) - a3 * cos(6.0 * scale);
                }
                break;
            }
            case "Gaussian, b=3.0": {
                double Beta = 3.0;
                double Arg;
                for (int i=0; i<wnd.length; i++) {
                    Arg = (Beta * (1.0 - ((double)i / (double)wnd.length) * 2.0));
                    wnd[i] = exp(-0.5 * (Arg * Arg));
                }
                break;
            }
            case "Gaussian, b=5.0": {
                double Beta = 5.0;
                double Arg;
                for (int i=0; i<wnd.length; i++) {
                    Arg = (Beta * (1.0 - ((double)i / (double)wnd.length) * 2.0));
                    wnd[i] = exp(-0.5 * (Arg * Arg));
                }
                break;
            }
            case "Gaussian, b=6.0": {
                double Beta = 6.0;
                double Arg;
                for (int i=0; i<wnd.length; i++) {
                    Arg = (Beta * (1.0 - ((double)i / (double)wnd.length) * 2.0));
                    wnd[i] = exp(-0.5 * (Arg * Arg));
                }
                break;
            }
            case "Gaussian, b=7.0": {
                double Beta = 7.0;
                double Arg;
                for (int i=0; i<wnd.length; i++) {
                    Arg = (Beta * (1.0 - ((double)i / (double)wnd.length) * 2.0));
                    wnd[i] = exp(-0.5 * (Arg * Arg));
                }
                break;
            }
            case "Gaussian, b=8.0": {
                double Beta = 8.0;
                double Arg;
                for (int i=0; i<wnd.length; i++) {
                    Arg = (Beta * (1.0 - ((double)i / (double)wnd.length) * 2.0));
                    wnd[i] = exp(-0.5 * (Arg * Arg));
                }
                break;
            }
            default:
                for (int i=0; i<wnd.length; i++) {
                    wnd[i] = 1;
                }
                break;
        }
        double normalizeFactor = 0;
        for (int i=0; i<wnd.length; i++) {
            normalizeFactor += wnd[i];
        }
        normalizeFactor = wnd.length / normalizeFactor;
        wndEnergyFactor = 0;
        for (int i=0; i<wnd.length; i++) {
            wnd[i] *= normalizeFactor;
            wndEnergyFactor += wnd[i]*wnd[i];
        }
        wndEnergyFactor = wnd.length / wndEnergyFactor;
    }

    void setAWeighting(boolean e_isAWeighting) {
        boolAWeighting = e_isAWeighting;
    }

    boolean getAWeighting() {
        return boolAWeighting;
    }

    private void init(int fftlen, int _hopLen, int sampleRate, int minFeedSize, String wndName) {
        if (minFeedSize <= 0) {
            throw new IllegalArgumentException("STFT::init(): should minFeedSize >= 1.");
        }
        if (((-fftlen)&fftlen) != fftlen) {
            // error: fftlen should be power of 2
            throw new IllegalArgumentException("STFT::init(): Currently, only power of 2 are supported in fftlen");
        }
        this.sampleRate = sampleRate;
        fftLen = fftlen;
        hopLen = _hopLen;                          // 50% overlap by default
        spectrumAmpOutCum= new double[fftlen/2+1];
        spectrumAmpOutTmp= new double[fftlen/2+1];
        spectrumAmpOut   = new double[fftlen/2+1];
        spectrumAmpOutDB = new double[fftlen/2+1];
        spectrumAmpIn    = new double[fftlen];
        spectrumAmpInTmp = new double[fftlen];
        spectrumAmpFFT   = new RealDoubleFFT(spectrumAmpIn.length);
//        spectrumAmpOutArray = new double[(int)ceil((double)minFeedSize / (fftlen/2))][]; // /2 since half overlap
//        for (int i = 0; i < spectrumAmpOutArray.length; i++) {
//            spectrumAmpOutArray[i] = new double[fftlen/2+1];
//        }

        initWindowFunction(fftlen, wndName);
        initDBAFactor(fftlen, sampleRate);
        clear();
        boolAWeighting = false;
    }

    STFT(Context context, AnalyzerParameters analyzerParam) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        zeroPadFac=Integer.parseInt(sharedPref.getString("zeroPadding",context.getString(R.string.zeropadding_default))); //Zero padding factor
        init(analyzerParam.fftLen, analyzerParam.hopLen, analyzerParam.sampleRate, analyzerParam.nFFTAverage, analyzerParam.wndFuncName);
        if (analyzerParam.micGainDB != null) {
            if (micGain == null || micGain.length != analyzerParam.micGainDB.length) {
                micGain = new double[analyzerParam.micGainDB.length];
            }
            Log.w("STFT:", "calib loaded. micGain.length = " + micGain.length);
            for (int i = 0; i < micGain.length; i++) {
                micGain[i] = pow(10, analyzerParam.micGainDB[i] / 10.0);
            }
        } else {
            Log.w("STFT:", "no calib");
        }
    }


    public void feedData(short[] ds, int dsLen) {
        if (dsLen > ds.length) {
            Log.e("STFT", "dsLen > ds.length !");
            dsLen = ds.length;
        }
        int inLen = spectrumAmpIn.length;
        int outLen = spectrumAmpOut.length;
        int dsPt = 0;           // input data point to be read
        while (dsPt < dsLen) {
            while (spectrumAmpPt < 0 && dsPt < dsLen) {  // skip data when hopLen > fftLen
                double s = ds[dsPt++] / 32768.0;
                spectrumAmpPt++;
                cumRMS += s*s;
                cntRMS++;
            }
            while (spectrumAmpPt < inLen && dsPt < dsLen) {
                double s = ds[dsPt++] / 32768.0;
                spectrumAmpIn[spectrumAmpPt++] = s;
                cumRMS += s*s;
                cntRMS++;
            }


            if (spectrumAmpPt == inLen) {    // enough data for one FFT
                for (int i = 0; i < inLen; i++) {
                    if (i<inLen/zeroPadFac) {
                        spectrumAmpInTmp[i] = spectrumAmpIn[i] * wnd[(int) (i*zeroPadFac)] * zeroPadFac; // in case of zero padding make jumps in window function so that the window is over the measured data. Multiply with factor to keep energy constant
                    }
                    else
                        spectrumAmpInTmp[i] = 0;  //in case of zero padding only use a fraction of measured data and fill rest with zeros
                }
                spectrumAmpFFT.ft(spectrumAmpInTmp);
                fftToAmp(spectrumAmpOutTmp, spectrumAmpInTmp);
//                System.arraycopy(spectrumAmpOutTmp, 0, spectrumAmpOutArray[spectrumAmpOutArrayPt], 0,
//                                 spectrumAmpOutTmp.length);
//                spectrumAmpOutArrayPt = (spectrumAmpOutArrayPt+1) % spectrumAmpOutArray.length;
                for (int i = 0; i < outLen; i++) {
                    spectrumAmpOutCum[i] += spectrumAmpOutTmp[i];
                }
                nAnalysed++;
                if (hopLen/zeroPadFac < fftLen) {  //Copy the remaining data that will be (re-)used to begin of array. In case of zero padding only consider the part not replaced with zeros as used
                    System.arraycopy(spectrumAmpIn, hopLen/zeroPadFac, spectrumAmpIn, 0, fftLen - hopLen/zeroPadFac);
                }
                spectrumAmpPt = fftLen - hopLen/zeroPadFac;  // can be positive and negative
            }
        }
    }

    // Convert complex amplitudes to absolute amplitudes.
    private void fftToAmp(double[] dataOut, double[] data) {
        // data.length should be a even number
        double scaler = 2.0*2.0 / (data.length * data.length);  // *2 since there are positive and negative frequency part
        dataOut[0] = data[0]*data[0] * scaler / 4.0;
        int j = 1;
        for (int i = 1; i < data.length - 1; i += 2, j++) {
            dataOut[j] = (data[i]*data[i] + data[i+1]*data[i+1]) * scaler;
        }
        dataOut[j] = data[data.length-1]*data[data.length-1] * scaler / 4.0;
    }

    final double[] getSpectrumAmp() {
        if (nAnalysed != 0) {    // no new result
            int outLen = spectrumAmpOut.length;
            double[] sAOC = spectrumAmpOutCum;
            for (int j = 0; j < outLen; j++) {
                sAOC[j] /= nAnalysed;
            }
            if (micGain != null && micGain.length == sAOC.length) {
                // No correction to phase.
                // Correction to DC is fake.
                for (int j = 0; j < outLen; j++) {
                    sAOC[j] /= micGain[j];
                }
            }
            if (boolAWeighting) {
                for (int j = 0; j < outLen; j++) {
                    sAOC[j] *= dBAFactor[j];
                }
            }
            System.arraycopy(sAOC, 0, spectrumAmpOut, 0, outLen);
            Arrays.fill(sAOC, 0.0);
            nAnalysed = 0;
            for (int i = 0; i < outLen; i++) {
                spectrumAmpOutDB[i] = 10.0 * log10(spectrumAmpOut[i]);
            }
        }
        return spectrumAmpOut;
    }

    final double[] getSpectrumAmpDB() {
        getSpectrumAmp();
        return spectrumAmpOutDB;
    }

    double getRMS() {
        if (cntRMS > 8000/30) {
            outRMS = sqrt(cumRMS / cntRMS * 2.0);  // "* 2.0" normalize to sine wave.
            cumRMS = 0;
            cntRMS = 0;
        }
        return outRMS;
    }

    double getRMSFromFT() {
        getSpectrumAmpDB();
        double s = 0;
        for (int i = 1; i < spectrumAmpOut.length; i++) {
            s += spectrumAmpOut[i];
        }
        return sqrt(s * wndEnergyFactor/ zeroPadFac); //correct for zero padding level
    }

    int nElemSpectrumAmp() {
      return nAnalysed;
    }

    double maxAmpFreq = Double.NaN, maxAmpDB = Double.NaN;

    void calculatePeak() {
        getSpectrumAmpDB();
        // Find and show peak amplitude
        maxAmpDB  = 20 * log10(0.125/32768);
        maxAmpFreq = 0;
        for (int i = 1; i < spectrumAmpOutDB.length; i++) {  // skip the direct current term
            if (spectrumAmpOutDB[i] > maxAmpDB) {
                maxAmpDB  = spectrumAmpOutDB[i];
                maxAmpFreq = i;
            }
        }
        maxAmpFreq = maxAmpFreq * sampleRate / fftLen;

        // Slightly better peak finder
        // The peak around spectrumDB should look like quadratic curve after good window function
        // a*x^2 + b*x + c = y
        // a - b + c = x1
        //         c = x2
        // a + b + c = x3
        if (sampleRate / fftLen < maxAmpFreq && maxAmpFreq < sampleRate/2 - sampleRate / fftLen) {
            int id = (int)(round(maxAmpFreq/sampleRate*fftLen));
            double x1 = spectrumAmpOutDB[id-1];
            double x2 = spectrumAmpOutDB[id];
            double x3 = spectrumAmpOutDB[id+1];
            double c = x2;
            double a = (x3+x1)/2 - x2;
            double b = (x3-x1)/2;
            if (a < 0) {
                double xPeak = -b/(2*a);
                if (abs(xPeak) < 1) {
                    maxAmpFreq += xPeak * sampleRate / fftLen;
                    maxAmpDB = (4*a*c - b*b)/(4*a);
                }
            }
        }
    }

    void clear() {
        spectrumAmpPt = 0;
        Arrays.fill(spectrumAmpOut, 0.0);
        Arrays.fill(spectrumAmpOutDB, log10(0));
        Arrays.fill(spectrumAmpOutCum, 0.0);
//        for (int i = 0; i < spectrumAmpOutArray.length; i++) {
//            Arrays.fill(spectrumAmpOutArray[i], 0.0);
//        }
    }

}
