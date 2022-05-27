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
 */

package org.woheller69.audio_analyzer_for_android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.RotateAnimation;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * Text view that toggles through a set of values.
 * @author suhler@google.com (Stephen Uhler)
 */

public class SelectorText extends AppCompatTextView {
  static final String TAG = "SelectorText:";
  private static float DPRatio;
  private static final int ANIMATION_DELAY = 70;
  private int value_id = 0;
  private String[] values = new String[0];
  private String[] valuesDisplay = new String[0];
  private Paint paint, bg;
  private RectF rect = new RectF();
  private RectF bgRect = new RectF();
  private float r;
  
  public SelectorText(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    setup(context, attrs);
  }
  public SelectorText(Context context, AttributeSet attrs) {
    super(context, attrs);
    setup(context, attrs);
  }
  public SelectorText(Context context) {
    super(context);
    setup(context, null);
  }
  
  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean performClick() {
    setText(getText());  // fix the no-animation bug
    //setText(valuesDisplay[value_id]);
    Animation an = createAnimation(true, ANIMATION_DELAY);
    an.setAnimationListener(new AnimationListener() {
      @Override
      public void onAnimationEnd(Animation animation) {
        nextValue();
        SelectorText.super.performClick();
        createAnimation(false, ANIMATION_DELAY).start();
      }
      @Override public void onAnimationRepeat(Animation animation) {}
      @Override public void onAnimationStart(Animation animation) {}
    });
    an.start();
    return true;
  }
  
  /**
   * Choose an arbitrary animation for the text view.
   * @param start   If true, animate the old value "out", otherwise animate the old value in
   * @param millis  Animation time for this step, ms
   */
  
  private Animation createAnimation(boolean start, int millis) {
    RotateAnimation ra = new RotateAnimation(start?0f:180f, start?180f:360f, getWidth()/2, getHeight()/2);
//    Log.d("SelectorText", "  createAnimation(): ");
    ra.setDuration(millis);
    setAnimation(ra);
    return ra;
  }
  
  /**
   * Compute the value of our "select" indicator.
   */
  
  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    rect.set(2f*DPRatio, h/2 - 5f*DPRatio, 12f*DPRatio, h/2 + 7f*DPRatio);
    bgRect.set(1f*DPRatio, 1f*DPRatio, w - 2f*DPRatio, h - 1f*DPRatio);
  }
  
  /**
   * Draw the selector, then the selected text.
   */
  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.drawRoundRect(rect, r, r, paint);
    c.drawRoundRect(bgRect, r, r, bg);
  }
  
  /**
   * Initialize our selector.  We could make most of the features customizable via XML.
   */
  
  private void setup(Context context, AttributeSet attrs) {
    DPRatio = getResources().getDisplayMetrics().density;
    r = 3 * DPRatio;
    
    bg = new Paint();
    bg.setStrokeWidth(2*DPRatio);
    bg.setColor(Color.GRAY);
    bg.setStyle(Paint.Style.STROKE);
    paint = new Paint(bg);
    paint.setColor(Color.GREEN);
    
    setClickable(true);
    if (attrs != null) {
      TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SelectorText);
      String items = a.getString(R.styleable.SelectorText_items);
      String delim = getValue(a, R.styleable.SelectorText_itemDelim, " ");
      String itemsDisplay = a.getString(R.styleable.SelectorText_itemsDisplay);
      if (items != null) {
//        Log.i(AnalyzerActivity.TAG, "items: " + items);
        if (itemsDisplay != null && itemsDisplay.length() > 0) {
          setValues(items.split(delim), itemsDisplay.split("::"));
        } else {
          setValues(items.split(delim), items.split(delim));
        }
      }
      a.recycle();
    }
    if (valuesDisplay.length > 0) {
      setText(valuesDisplay[0]);
    }
  }
  
  private static String getValue(TypedArray a, int index, String dflt) {
    String result = a.getString(index);
    return result == null ? dflt : result;
  }
  
  public void setValues(String[] values, String[] valuesDisplay) {
    this.values = values;
    if (values.length == valuesDisplay.length) {
      this.valuesDisplay = valuesDisplay;
    } else {
      Log.w(TAG, "values.length != valuesDisplay.length");
      this.valuesDisplay = values;
    }
    adjustWidth();
    invalidate();
  }

  public String getValue() { return values[value_id]; }

  public String[] getValues() {
    return values;
  }

  public void setValue(String v) {
    for (int i = 0; i < values.length; i++) {
      if (! v.equals(values[value_id])) {
        nextValue();
      }
    }
  }

  public String nextValue() {
    if (values.length != 0) {
      value_id++;
      if (value_id >= values.length) {
        value_id = 0;
      }
      setText(valuesDisplay[value_id]);
      return valuesDisplay[value_id];
    }
    return getText().toString();
  }
  
  private void adjustWidth() {
    Paint p = getPaint();
    int adj = getPaddingLeft() + getPaddingRight();
    int width = 0;
    for (String s : valuesDisplay) {
      width = Math.max(width, Math.round(p.measureText(s)));
    }
    setMinWidth(width + adj + (int)(4*DPRatio));
  }
}