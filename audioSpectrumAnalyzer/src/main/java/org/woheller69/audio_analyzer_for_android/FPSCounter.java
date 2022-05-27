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

// Frames Per Second Counter

class FPSCounter {
  private long frameCount;
  private long timeOld, timeUpdateInterval;  // in ms
  private double fps;
  private String TAG_OUTSIDE;
  
  FPSCounter(String TAG) {
    timeUpdateInterval = 2000;
    TAG_OUTSIDE = TAG;
    frameCount = 0;
    timeOld = SystemClock.uptimeMillis();
  }
  
  // call this when number of frames plus one
  void inc() {
    frameCount++;
    long timeNow = SystemClock.uptimeMillis();
    if (timeOld + timeUpdateInterval <= timeNow) {
      fps = 1000 * (double) frameCount / (timeNow - timeOld);
      Log.d(TAG_OUTSIDE, "FPS: " + Math.round(100*fps)/100.0 +
            " (" + frameCount + "/" + (timeNow - timeOld) + "ms)");
      timeOld = timeNow;
      frameCount = 0;
    }
  }

  public double getFPS() {
    return fps;
  }
}
