/*
    ROUNDWARE
    a participatory, location-aware media platform
    Android client library
       Copyright (C) 2008-2013 Halsey Solutions
    with contributions by Rob Knapen (shuffledbits.com) and Dan Latham
    http://roundware.org | contact@roundware.org

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

     This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.

     You should have received a copy of the GNU General Public License
     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.roundware.rwapp.utils;

import org.roundware.rwapp.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;


/**
 * Simple level meter to show audio recording levels.
 * 
 * @author Dan Latham
 * @author Rob Knapen
 */
public class LevelMeterView extends ImageView {
    double db;
    Bitmap meterBitmap;

    private static final String TAG = "LevelMeterView";

    // This may be different for different devices. It's the absolute value for
    // ambient
    // audio level on the device.
    //
    static final int AMBIENT_LOW_DB = 50;


    public LevelMeterView(Context context) {
        super(context, null);
        init();
    }


    public LevelMeterView(Context context, AttributeSet attr) {
        super(context, attr);
        init();
    }


    protected void init() {
        Log.d(TAG, "Initiailizing levelmeter...");
        meterBitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.level_meter);
        Log.d(TAG, "Initialized.");
    }


    public void setLevel(short[] samples) {
        db = calculatePowerDb(samples, 0, samples.length);
    }


    public void reset() {
        db = -1 * AMBIENT_LOW_DB;
        postInvalidate();
    }


    protected double calculatePowerDb(short[] sdata, int off, int samples) {
        // Calculate the sum of the values, and the sum of the squared values.
        // We need longs to avoid running out of bits.
        double sum = 0;
        double sqsum = 0;
        for (int i = 0; i < samples; i++) {
            final long v = sdata[off + i];
            sum += v;
            sqsum += v * v;
        }

        double power = (sqsum - sum * sum / samples) / samples;

        // Scale to the range 0 - 1.
        power /= MAX_16_BIT * MAX_16_BIT;

        // Convert to dB, with 0 being max power. Add a fudge factor to make
        // a "real" fully saturated input come to 0 dB.
        return Math.log10(power) * 10f + FUDGE;
    }

    // ******************************************************************** //
    // Constants.
    // ******************************************************************** //

    // Maximum signal amplitude for 16-bit data.
    private static final float MAX_16_BIT = 32768;

    // This fudge factor is added to the output to make a realistically
    // fully-saturated signal come to 0dB. Without it, the signal would
    // have to be solid samples of -32768 to read zero, which is not
    // realistic. This really is a fudge, because the best value depends
    // on the input frequency and sampling rate. We optimise here for
    // a 1kHz signal at 16,000 samples/sec.
    private static final float FUDGE = 0.6f;


    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        double level = (db + AMBIENT_LOW_DB) / AMBIENT_LOW_DB;
        assert level >= 0;

        Paint paint = new Paint();

        int wd = getWidth();
        int bottomY = getHeight() - 1;

        int rightX = (int) (wd * level);
        canvas.clipRect(0, 0, rightX, bottomY);

        // Hack!!!! - setting left = 20.0 - how do we get the lay'ed out
        // position?
        canvas.drawBitmap(meterBitmap, (float) 20.0, (float) 0.0, paint);
    }

}
