package org.roundware.rwapp.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Button;

/**
 * Custom button that support irregular shaped touch areas based on the background image set for
 * it. Based on source code shared on the Internet.
 * <p/>
 * Created by Rob Knapen on 24/08/15.
 */
public class ShapedButton extends Button {

    public ShapedButton(Context context) {
        super(context);
    }


    public ShapedButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    public ShapedButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {

        Drawable drawable = this.getBackground();

        if (drawable != null) {
            Bitmap bitmap = drawableToBitmap(drawable);

            int iX = (int) event.getX();
            int iY = (int) event.getY();

            if (iX >= 0 & iY >= 0 & iX <= (bitmap.getWidth()) & iY <= (bitmap.getHeight())) {
                if (bitmap.getPixel(iX, iY) == 0) {
                    return false;
                }
            }
        }

        return super.dispatchTouchEvent(event);
    }


    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        int width = drawable.getIntrinsicWidth();
        width = width > 0 ? width : 1;
        int height = drawable.getIntrinsicHeight();
        height = height > 0 ? height : 1;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }
}
