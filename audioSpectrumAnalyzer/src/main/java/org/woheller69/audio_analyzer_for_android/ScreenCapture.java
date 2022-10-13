package org.woheller69.audio_analyzer_for_android;

import static android.os.Environment.DIRECTORY_PICTURES;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenCapture {

    public static boolean ScreenCapture(Activity activity){

        File path = Environment.getExternalStoragePublicDirectory(DIRECTORY_PICTURES);
        if (!path.exists() && !path.mkdirs()) {
            Log.e("Screencapture", "Failed to make directory: " + path.toString());
            return false;
        }
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH'h'mm'm'ss.SS's'", Locale.US);
        String nowStr = df.format(new Date());
        File imageFile = new File(path, "sc" + nowStr + ".jpg");
        try {
            Log.e("Screencapture", String.valueOf(imageFile));
            // create bitmap screen capture
            View view = activity.getWindow().getDecorView().getRootView();
            view.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(view.getDrawingCache());
            view.setDrawingCacheEnabled(false);

            FileOutputStream outputStream = new FileOutputStream(imageFile);
            int quality = 95;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();
            Toast.makeText(activity,activity.getString(R.string.screenshot)+String.valueOf(imageFile),Toast.LENGTH_LONG).show();
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }
}
