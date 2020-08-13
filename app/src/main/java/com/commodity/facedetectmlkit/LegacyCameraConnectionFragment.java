package com.commodity.facedetectmlkit;

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

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;


import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.commodity.facedetectmlkit.customview.AutoFitTextureView;
import com.commodity.facedetectmlkit.env.ImageUtils;
import com.commodity.facedetectmlkit.env.Logger;
import com.commodity.facedetectmlkit.kioskdemo.FaceDetectorPreview;
import com.commodity.facedetectmlkit.setting.ContantValues;

import java.io.IOException;
import java.util.List;

import static com.commodity.facedetectmlkit.kioskdemo.FaceDetectActivity.useFacingView;

@SuppressLint("ValidFragment")
public class LegacyCameraConnectionFragment extends Fragment {
    private static final Logger LOGGER = new Logger();
    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final String KEY_FACING = "camera_facing";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static int usefacing;

    private Camera camera;
    private Camera.PreviewCallback imageListener;
    private Size desiredSize;
    private int facing;
    /**
     * The layout identifier to inflate for this Fragment.
     */
    private int layout;
    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView textureView;
    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a {@link
     * TextureView}.
     */
    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onSurfaceTextureAvailable(
                        final SurfaceTexture texture, final int width, final int height) {


                    try {
                        try {
                            int index = getCameraId();
                            Log.d("LegacyCameraConnectionFragment face camera index: ", String.valueOf(index));

                            camera = Camera.open(index);
                            Camera.Parameters parameters = camera.getParameters();
                            List<String> focusModes = parameters.getSupportedFocusModes();
                            if (focusModes != null
                                    && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                            }
                            List<Camera.Size> cameraSizes = parameters.getSupportedPreviewSizes();
                            Size[] sizes = new Size[cameraSizes.size()];
                            int i = 0;
                            for (Camera.Size size : cameraSizes) {
                                sizes[i++] = new Size(size.width, size.height);
                            }
                            Size previewSize =
                                    CameraConnectionFragment.chooseOptimalSize(
                                            sizes, desiredSize.getWidth(), desiredSize.getHeight());
                            parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
                           camera.setDisplayOrientation(90);
                          //  camera.setDisplayOrientation(0);

                            camera.setParameters(parameters);
                            camera.setPreviewTexture(texture);
                        } catch (IOException exception) {
                            camera.release();
                        }

                        camera.setPreviewCallbackWithBuffer(imageListener);
                        Camera.Size s = camera.getParameters().getPreviewSize();
                        camera.addCallbackBuffer(new byte[ImageUtils.getYUVByteSize(s.height, s.width)]);

                        textureView.setAspectRatio(s.height, s.width);

                        if(thread == null)
                        {
                            thread = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        thread.sleep(2000);
                                        camera.startPreview();
                                    }
                                    catch (InterruptedException e)
                                    {
                                        e.printStackTrace();
                                    }
                                }
                            });
                            thread.start();
                        }


                    } catch (Exception e) {
                        stopCamera();
                        e.printStackTrace();
                    }
                }

                @Override
                public void onSurfaceTextureSizeChanged(
                        final SurfaceTexture texture, final int width, final int height) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    stopCamera();
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
                }
            };
    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread backgroundThread;
    private Thread thread;

    public static LegacyCameraConnectionFragment newInstance( final Camera.PreviewCallback imageListener, final int layout, final Size desiredSize, int facing) {
        LegacyCameraConnectionFragment f = new LegacyCameraConnectionFragment();
        f.imageListener = imageListener;
        f.layout = layout;
        f.desiredSize = desiredSize;
        f.facing = facing;
        usefacing = (facing == CameraCharacteristics.LENS_FACING_BACK) ?
                Camera.CameraInfo.CAMERA_FACING_BACK :
                Camera.CameraInfo.CAMERA_FACING_FRONT;

        return f;
    }

    public LegacyCameraConnectionFragment()
    {

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onSaveInstanceState(Bundle state) {
        state.putInt("layout", layout);
        state.putSize("desiredSize", desiredSize);
        state.putInt("facing", facing);
        super.onSaveInstanceState(state);    }

    public LegacyCameraConnectionFragment(
            final Camera.PreviewCallback imageListener, final int layout, final Size desiredSize, int facing) {

         Log.d("LegacyCameraConnectionFragment face camera id: ", String.valueOf(facing));

    }

    @Override
    public void setArguments(Bundle args) {
        super.setArguments(args);

        this.facing = args.getInt(KEY_FACING, CameraInfo.CAMERA_FACING_FRONT);

    }

    @Override
    public View onCreateView(
            final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            layout = savedInstanceState.getInt("layout");
            desiredSize = savedInstanceState.getSize("desiredSize");
            facing = savedInstanceState.getInt("facing");

        }
        return inflater.inflate(layout, container, false);

    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).

        if (textureView.isAvailable()) {
            if (camera != null) {
                try {
                    camera.startPreview();
                } catch (Exception e) {
                    e.printStackTrace();
                    stopBackgroundThread();
                    onDestroy();

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                FaceDetectorPreview faceDetector = new FaceDetectorPreview(getContext(), usefacing);
                                try {
                                    Intent ackIntent = new Intent(ContantValues.ACTIVITY_RESUMED.getEventCodeString());
                                    LocalBroadcastManager.getInstance(getContext()).sendBroadcast(ackIntent);
                                } catch (Exception e1) {
                                    Log.e("Error broadcasting " + ContantValues.ACTIVITY_RESUMED.getEventCodeString()
                                            + " : ", e1.toString());
                                }
                            }



                }
            } else {
                Toast.makeText(getContext(), "back", Toast.LENGTH_SHORT).show();
                Log.d("On resume facedetector camera nit avaialble", String.valueOf(textureView.isAvailable()));

                try {
                    Intent ackIntent = new Intent(ContantValues.ACTIVITY_PAUSED.getEventCodeString());
                    LocalBroadcastManager.getInstance(getContext()).sendBroadcast(ackIntent);
                } catch (Exception e) {
                    Log.e("Error broadcasting " + ContantValues.ACTIVITY_PAUSED.getEventCodeString()
                            + " : ", e.toString());
                }
                onDestroy();

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    FaceDetectorPreview faceDetector = new FaceDetectorPreview(getContext(), usefacing);
                    Log.d("On resume facedetector start again", String.valueOf(textureView.isAvailable()));
                    try {
                        Log.d("On resume  broadcast st./art again", String.valueOf(textureView.isAvailable()));
                        Intent ackIntent = new Intent(ContantValues.ACTIVITY_RESUMED.getEventCodeString());
                        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(ackIntent);
                    } catch (Exception e1) {
                        Log.e("Error broadcasting " + ContantValues.ACTIVITY_RESUMED.getEventCodeString()
                                + " : ", e1.toString());
                    }
                }

            }

        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        stopCamera();
//        if (camera != null) {
//            camera.stopPreview();
//        }
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCamera();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {

        try {
            backgroundThread.quitSafely();
            backgroundThread.join();
            backgroundThread = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }
    }

    protected void stopCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private int getCameraId() {
        CameraInfo ci = new CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, ci);
            if (ci.facing == this.facing) return i;
        }
        return -1; // No camera found
    }
}
