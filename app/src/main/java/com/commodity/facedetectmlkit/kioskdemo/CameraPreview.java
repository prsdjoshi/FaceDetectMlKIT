/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.commodity.facedetectmlkit.kioskdemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.commodity.facedetectmlkit.CameraConnectionFragment;
import com.commodity.facedetectmlkit.LegacyCameraConnectionFragment;
import com.commodity.facedetectmlkit.R;
import com.commodity.facedetectmlkit.env.ImageUtils;
import com.commodity.facedetectmlkit.env.Logger;
import com.commodity.facedetectmlkit.setting.ContantValues;
import com.commodity.facedetectmlkit.setting.resizablerectangle.ResizableRectangleActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.nio.ByteBuffer;


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public abstract class CameraPreview
        implements OnImageAvailableListener,
        Camera.PreviewCallback,
        CompoundButton.OnCheckedChangeListener {

    //variable of camera preview
    private static final Logger LOGGER = new Logger();
    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    protected int previewWidth = 0;
    protected int previewHeight = 0;
    private boolean debug = false;
    private Handler handler;
    private HandlerThread handlerThread;
    private boolean useCamera2API;
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    public Integer useFacing = null;
    private String cameraId = null;
    private Context context;
    private Activity activity;



    //variable of face detector


    protected Integer getCameraFacing() {
        return useFacing;
    }

    public CameraPreview (Context context,Integer Facing) {
        setupCamera();
        LOGGER.d("onCameraStart " + this);
        this.context = context;
        activity = (Activity) context;
        useFacing = Facing;
        LocalBroadcastManager.getInstance(context)
                .registerReceiver(
                        activtyPaused,
                        new IntentFilter(ContantValues.ACTIVITY_PAUSED
                                .getEventCodeString()));
        LocalBroadcastManager.getInstance(context)
                .registerReceiver(
                        activtyResumed,
                        new IntentFilter(ContantValues.ACTIVITY_RESUMED
                                .getEventCodeString()));
        setFragment();
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected int getLuminanceStride() {
        return yRowStride;
    }

    protected byte[] getLuminance() {
        return yuvBytes[0];
    }

    /**
     * Callback for android.hardware.Camera API
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onPreviewFrame(final byte[] bytes, final Camera camera) {
        if (isProcessingFrame) {
            LOGGER.w("Dropping frame!");
            return;
        }

        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                Camera.Size previewSize = camera.getParameters().getPreviewSize();
                previewHeight = previewSize.height;
                previewWidth = previewSize.width;
                rgbBytes = new int[previewWidth * previewHeight];
                onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
            }
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            return;
        }

        isProcessingFrame = true;
        yuvBytes[0] = bytes;
        yRowStride = previewWidth;

        imageConverter =
                new Runnable() {
                    @Override
                    public void run() {
                        ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
                    }
                };

        postInferenceCallback =
                new Runnable() {
                    @Override
                    public void run() {
                        camera.addCallbackBuffer(bytes);
                        isProcessingFrame = false;
                    }
                };
        processImage();
    }

    /**
     * Callback for Camera2 API
     */
    @Override
    public void onImageAvailable(final ImageReader reader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (isProcessingFrame) {
                image.close();
                return;
            }
            isProcessingFrame = true;
            Trace.beginSection("imageAvailable");
            final Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };

            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            image.close();
                            isProcessingFrame = false;
                        }
                    };

                processImage();

        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            Trace.endSection();
            return;
        }
        Trace.endSection();
    }






    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }


    // Returns true if the device supports the required hardware level, or better.
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean isHardwareLevelSupported(
            CameraCharacteristics characteristics, int requiredLevel) {
        int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return requiredLevel == deviceLevel;
        }
        // deviceLevel is not LEGACY, can use numerical sort
        return requiredLevel <= deviceLevel;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private String chooseCamera() {

        final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        try {

            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                final StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) {
                    continue;
                }

                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (useFacing != null &&
                        facing != null &&
                        !facing.equals(useFacing)
                ) {
                    continue;
                }


                useCamera2API = (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                        || isHardwareLevelSupported(
                        characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);


                LOGGER.i("Camera API lv2?: %s", useCamera2API);
                return cameraId;
            }
        } catch (CameraAccessException e) {
            LOGGER.e(e, "Not allowed to access camera");
        }

        return null;
    }


    protected void setFragment() {

        this.cameraId = chooseCamera();

        Fragment fragment;
        if (useCamera2API) {
            CameraConnectionFragment camera2Fragment =
                    CameraConnectionFragment.newInstance(
                            new CameraConnectionFragment.ConnectionCallback() {
                                @Override
                                public void onPreviewSizeChosen(final Size size, final int rotation) {
                                    previewHeight = size.getHeight();
                                    previewWidth = size.getWidth();
                                    CameraPreview.this.onPreviewSizeChosen(size, rotation);
                                }
                            },
                            this,
                            getLayoutId(),
                            getDesiredPreviewFrameSize());

            camera2Fragment.setCamera(cameraId);
            fragment = camera2Fragment;

        } else {

            int facing = (useFacing == CameraCharacteristics.LENS_FACING_BACK) ?
                    Camera.CameraInfo.CAMERA_FACING_BACK :
                    Camera.CameraInfo.CAMERA_FACING_FRONT;
            LegacyCameraConnectionFragment frag = LegacyCameraConnectionFragment.newInstance(this,
                    getLayoutId(),
                    getDesiredPreviewFrameSize(), facing);
            fragment = frag;

        }
        activity.getFragmentManager().beginTransaction().replace(R.id.rectcontainer, fragment).commit();
    }

    protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    public boolean isDebug() {
        return debug;
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int getScreenOrientation() {
        switch (activity.getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    public void dispose()
    {
        try {
            Intent ackIntent = new Intent(ContantValues.ACTIVITY_PAUSED.getEventCodeString());
            LocalBroadcastManager.getInstance(context).sendBroadcast(ackIntent);
        } catch (Exception e) {
            Log.e("Error broadcasting " + ContantValues.ACTIVITY_PAUSED.getEventCodeString()
                    + " : ", e.toString());
        }
    }

    private BroadcastReceiver activtyPaused = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                Log.d("Camera Preview: ","activtyPaused called");

                try {
                    if(handlerThread!=null)
                    {
                        handlerThread.quitSafely();
                        handlerThread.join();
                        handlerThread = null;

                    }
                    if(handler !=null)
                    {
                        handler = null;
                    }

                } catch (final InterruptedException e) {
                    //LOGGER.e(e, "Exception!");
                    Log.e(
                            "InterruptedException Error occured handling activtyPaused message : ", e.toString());

                }
            } catch (Exception e) {
                Log.e(
                        "Error occured handling activtyPaused message : ", e.toString());

            }
        }
    };

    private BroadcastReceiver activtyResumed = new BroadcastReceiver() {



        @SuppressLint("LongLogTag")
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                Log.d("Camera Preview: ", "activtyResumed called");
                if(handlerThread == null)
                {
                    handlerThread = new HandlerThread("inference");
                    handlerThread.start();
                }
                if(handler == null)
                {
                    handler = new Handler(handlerThread.getLooper());
                }

            } catch (Exception e) {
                Log.e("FaceDetection- Exception occured handling activtyResumed in CameraPreview: ", e.toString());



            }
        }
    };

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        setUseNNAPI(isChecked);
    }


    protected void showFrameInfo(String frameInfo) {
        LOGGER.e(frameInfo, "frameInfo : ");
    }

    protected void showCropInfo(String cropInfo) {
        LOGGER.e(cropInfo, "cropInfo : ");
    }

    protected void showInference(String inferenceTime) {
        LOGGER.e(inferenceTime, "InferenceTime : ");
    }

    protected abstract void processImage();

    protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

    protected abstract int getLayoutId();

    protected abstract Size getDesiredPreviewFrameSize();

    protected abstract void setupCamera();

    protected abstract void setNumThreads(int numThreads);

    protected abstract void setUseNNAPI(boolean isChecked);
}
