package com.commodity.facedetectmlkit.customview;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Build;

import androidx.annotation.RequiresApi;

import static com.commodity.facedetectmlkit.kioskdemo.FaceDetectorPreview.capturedPhoto;

/**
 * Created by Ryze on 2016-7-20.
 */
public class ScreenCaptureApplication extends Application {


    private Bitmap mScreenCaptureBitmap;

    @Override
    public void onCreate() {
        super.onCreate();
    }


    public Bitmap getmScreenCaptureBitmap() {
        return mScreenCaptureBitmap;
    }

    public void setmScreenCaptureBitmap(Bitmap mScreenCaptureBitmap) {
        this.mScreenCaptureBitmap = mScreenCaptureBitmap;

    }
}
