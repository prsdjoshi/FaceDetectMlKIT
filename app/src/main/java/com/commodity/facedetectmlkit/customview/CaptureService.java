package com.commodity.facedetectmlkit.customview;


import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.commodity.facedetectmlkit.R;
import com.commodity.facedetectmlkit.setting.ContantValues;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
import static android.view.WindowManager.LayoutParams.TYPE_TOAST;
import static com.commodity.facedetectmlkit.kioskdemo.FaceDetectorPreview.capturedPhoto;

/**
 * Created by branch on 2016-5-25.
 * <p>
 * 启动悬浮窗界面
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CaptureService extends Service {


    public static Intent newIntent(Context context, Intent mResultData) {

        Intent intent = new Intent(context, CaptureService.class);

        if (mResultData != null) {
            intent.putExtras(mResultData);
        }
        return intent;
    }

    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;

    private static Intent mResultData = null;


    private ImageReader mImageReader;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayoutParams;


    private int mScreenWidth;
    private int mScreenHeight;
    private int mScreenDensity;


    @Override
    public void onCreate() {
        super.onCreate();

        createFloatView();

        createImageReader();
        LocalBroadcastManager.getInstance(getApplicationContext())
                .registerReceiver(
                        activtySS_CAPTURED,
                        new IntentFilter(ContantValues.SS_CAPTURED
                                .getEventCodeString()));
    }

    private BroadcastReceiver activtySS_CAPTURED = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                Log.d("Camera Preview SS captured: activtySS_CAPTURED","onReceive");
                startScreenShot();
            } catch (Exception e) {
                Log.e(
                        "Error occured handling activtySS_CAPTURED message : ", e.toString());
            }
        }
    };


    public static Intent getResultData() {
        return mResultData;
    }

    public static void setResultData(Intent mResultData) {
        CaptureService.mResultData = mResultData;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createFloatView() {
        mLayoutParams = new WindowManager.LayoutParams();
        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        DisplayMetrics metrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;

        mLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        mLayoutParams.format = PixelFormat.RGBA_8888;
        // 设置Window flag
        mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        mLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        mLayoutParams.x = mScreenWidth;
        mLayoutParams.y = 100;
        mLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;


        //  mFloatView = new ImageView(getApplicationContext());
        //  mFloatView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_imagetool_crop));
        //  mWindowManager.addView(mFloatView, mLayoutParams);




    }




    public void startScreenShot() {

        Handler handler1 = new Handler();
        handler1.postDelayed(new Runnable() {
                public void run() {
                    //start virtual
                    startVirtual();
                }
            }, 5);

        handler1.postDelayed(new Runnable() {
                public void run() {
                    //capture the screen
                    startCapture();

                }
        }, 30);
    }


    private void createImageReader() {

        mImageReader = ImageReader.newInstance(mScreenWidth, mScreenHeight, PixelFormat.RGBA_8888, 1);

    }

    public void startVirtual() {
        if (mMediaProjection != null) {
            virtualDisplay();
        } else {
            setUpMediaProjection();
            virtualDisplay();
        }
    }

    public void setUpMediaProjection() {
        if (mResultData == null) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            startActivity(intent);
        } else {
            mMediaProjection = getMediaProjectionManager().getMediaProjection(Activity.RESULT_OK, mResultData);
        }
    }

    private MediaProjectionManager getMediaProjectionManager() {

        return (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    private void virtualDisplay() {
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("screen-mirror",
                mScreenWidth, mScreenHeight, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);
    }

    private void startCapture() {

        Image image = mImageReader.acquireLatestImage();

        if (image == null) {
            startScreenShot();
        } else {
            SaveTask mSaveTask = new SaveTask();
            mSaveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,image);
        }
    }


    public class SaveTask extends AsyncTask<Image, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Image... params) {

            if (params == null || params.length < 1 || params[0] == null) {

                return null;
            }

            Image image = params[0];

            int width = image.getWidth();
            int height = image.getHeight();
            final Image.Plane[] planes = image.getPlanes();
            final ByteBuffer buffer = planes[0].getBuffer();
            //每个像素的间距
            int pixelStride = planes[0].getPixelStride();
            //总的间距
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);

            image.close();
//            File fileImage = null;
//            if (bitmap != null) {
//                try {
////                    fileImage = new File(FileUtil.getScreenShotsName(getApplicationContext()));
////                    if (!fileImage.exists()) {
////                        fileImage.createNewFile();
////                    }
//
//                    String path = new File(
//                            android.os.Environment.getExternalStorageDirectory(),
//                            "XM/logs") + "/screenshot.jpg";
//
//                    FileOutputStream out = new FileOutputStream(path);
//                    if (out != null) {
//                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
//                        out.flush();
//                        out.close();
//                        Intent media = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//                        Uri contentUri = Uri.fromFile(fileImage);
//                        media.setData(contentUri);
//                        sendBroadcast(media);
//                    }
//                } catch (FileNotFoundException e) {
//                    e.printStackTrace();
//                    fileImage = null;
//                } catch (IOException e) {
//                    e.printStackTrace();
//                    fileImage = null;
//                }
//            }
//
//            if (fileImage != null) {
//                return bitmap;
//            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);

            Log.d("Camera Preview SS captured: onPostExecute","bitmap null");

            if (bitmap != null) {
                Log.d("Camera Preview SS captured: onPostExecute","bitmap getting");
                ((ScreenCaptureApplication) getApplication()).setmScreenCaptureBitmap(bitmap);
                Intent ackIntent = new Intent(ContantValues.SS_CAPTURED_SET.getEventCodeString());
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(ackIntent);
            }


        }
    }


    private void tearDownMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private void stopVirtual() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
    }

    @Override
    public void onDestroy() {
        // to remove mFloatLayout from windowManager
        super.onDestroy();

        stopVirtual();

        tearDownMediaProjection();
    }


}