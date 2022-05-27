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

import static java.lang.Math.exp;
import static java.lang.Math.log;

/**
 * Mapping between physical value and screen pixel position
 * Use double or float ?
 */

//    | lower bound  ...  higher bound |  physical unit
//    | 0            ...             1 |  "unit 1" (Mapping can be linear or logarithmic)

// In LINEAR mode (default):
//    |lower value  ...    higher value|  physical unit
//    | shift       ... shift + 1/zoom |  "unit 1", 0=vLowerBound, 1=vUpperBound
//    | 0 | 1 |     ...          | n-1 |  pixel

// In LINEAR_ON mode (not implemented):
//      |lower value ...    higher value|     physical unit
//      | shift      ... shift + 1/zoom |     "unit 1" window
//    | 0 | 1 |      ...             | n-1 |  pixel

class ScreenPhysicalMapping {
    private final static String TAG = "ScreenPhysicalMapping";

    enum Type {  // java's enum type is inconvenient
        LINEAR(0), LINEAR_ON(1), LOG(2);

        private final int value;
        Type(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    Type mapType;                                     // Linear or Log
    double nCanvasPixel;
    double vLowerBound, vUpperBound;                  // Physical limits
    private double vLowerViewBound, vUpperViewBound;  // view bounds
    private double zoom = 1, shift = 0;               // zoom==1: no zooming, shift=0: no shift

    ScreenPhysicalMapping(double _nCanvasPixel, double _vLowerBound, double _vHigherBound, ScreenPhysicalMapping.Type _mapType) {
        nCanvasPixel = _nCanvasPixel;
        vLowerBound = _vLowerBound;
        vUpperBound = _vHigherBound;
        vLowerViewBound = vLowerBound;
        vUpperViewBound = vUpperBound;
        mapType      = _mapType;
    }

    ScreenPhysicalMapping(ScreenPhysicalMapping _axis) {
        mapType      = _axis.mapType;
        nCanvasPixel = _axis.nCanvasPixel;
        vLowerBound  = _axis.vLowerBound;
        vUpperBound  = _axis.vUpperBound;
        vLowerViewBound = _axis.vLowerViewBound;
        vUpperViewBound = _axis.vUpperViewBound;
        zoom         = _axis.zoom;
        shift        = _axis.shift;
    }

    void setNCanvasPixel(double _nCanvasPixel) {
        nCanvasPixel = _nCanvasPixel;
    }

    void setBounds(double _vL, double _vU) {
        vLowerBound = _vL;
        vUpperBound = _vU;
        // Reset view range, preserve zoom and shift
        setZoomShift(zoom, shift);
        if (AnalyzerUtil.isAlmostInteger(vLowerViewBound)) {  // dirty fix...
            vLowerViewBound = Math.round(vLowerViewBound);
        }
        if (AnalyzerUtil.isAlmostInteger(vUpperViewBound)) {
            vUpperViewBound = Math.round(vUpperViewBound);
        }
        setViewBounds(vLowerViewBound, vUpperViewBound);  // refine zoom shift
    }

    double getZoom() { return zoom; }
    double getShift() { return shift; }

    void setZoomShift(double _zoom, double _shift) {
        zoom = _zoom;
        shift = _shift;
        vLowerViewBound = vFromUnitPosition(0, zoom, shift);
        vUpperViewBound = vFromUnitPosition(1, zoom, shift);
    }

    // set zoom and shift from physical value bounds
    void setViewBounds(double vL, double vU) {
        if (vL == vU) {
            return;  // Or throw an exception?
        }
        double p1 = UnitPositionFromV(vL, vLowerBound, vUpperBound);
        double p2 = UnitPositionFromV(vU, vLowerBound, vUpperBound);
        zoom  = 1 / (p2 - p1);
        shift = p1;
        vLowerViewBound = vL;
        vUpperViewBound = vU;
    }

    double UnitPositionFromV(double v, double vL, double vU) {
        if (vL == vU) {
            return 0;
        }
        if (mapType == Type.LINEAR) {
            return (v - vL) / (vU - vL);
        } else {
            return log(v/vL) / log(vU/vL);
        }
    }

    double vFromUnitPosition(double u, double zoom, double shift) {
        if (zoom == 0) {
            return 0;
        }
        if (mapType == Type.LINEAR) {
            return (u / zoom + shift) * (vUpperBound - vLowerBound) + vLowerBound;
        } else {
            return exp((u / zoom + shift) * log(vUpperBound / vLowerBound)) * vLowerBound;
        }
    }

    double pixelFromV(double v) {
        return UnitPositionFromV(v, vLowerViewBound, vUpperViewBound) * nCanvasPixel;
    }

    double vFromPixel(double pixel) {
        if (nCanvasPixel == 0)
            return vLowerViewBound;
        return vFromUnitPosition(pixel / nCanvasPixel, zoom, shift);
    }

    double vMinInView() {
        return vLowerViewBound;
    }

    double vMaxInView() {
        return vUpperViewBound;
    }

    double pixelNoZoomFromV(double v) {
        return UnitPositionFromV(v, vLowerBound, vUpperBound) * nCanvasPixel;
    }

    double diffVBounds() { return vUpperBound - vLowerBound; }

    void reverseBounds() {
        double oldVL = vLowerViewBound;
        double oldVU = vUpperViewBound;
        setBounds(vUpperBound, vLowerBound);
        setViewBounds(oldVU, oldVL);
    }

    void setMappingType(ScreenPhysicalMapping.Type _mapType, double lower_bound_ref) {
        // Set internal variables if possible
        double vL = vMinInView();
        double vH = vMaxInView();
        if (_mapType == Type.LOG) {
            if (vLowerBound == 0) vLowerBound = lower_bound_ref;
            if (vUpperBound == 0) vUpperBound = lower_bound_ref;
        } else {
            if (vLowerBound == lower_bound_ref) vLowerBound = 0;
            if (vUpperBound == lower_bound_ref) vUpperBound = 0;
        }
        boolean bNeedUpdateZoomShift = mapType != _mapType;
        mapType = _mapType;
        if (!bNeedUpdateZoomShift || nCanvasPixel == 0 || vLowerBound == vUpperBound) {
            return;
        }

        // Update zoom and shift
        // lower_bound_ref is for how to map zero
        // Only support non-negative bounds
        if (_mapType == Type.LOG) {
            if (vL < 0 || vH < 0) {
                Log.e(TAG, "setMappingType(): negative bounds.");
                return;
            }
            if (vL  < lower_bound_ref) vL = lower_bound_ref;
            if (vH  < lower_bound_ref) vH = lower_bound_ref;
        } else {
            if (vL  <= lower_bound_ref) vL = 0;
            if (vH  <= lower_bound_ref) vH = 0;
        }
        setViewBounds(vL, vH);
    }
}
