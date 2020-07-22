package com.commodity.facedetectmlkit.kioskdemo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.commodity.facedetectmlkit.R;
import com.commodity.facedetectmlkit.setting.ContantValues;

public class FaceDetectActivity extends AppCompatActivity {
    private static final String KEY_USE_FACING = "use_facing";
    private FaceDetectorPreview faceDetector;
    private HandlerThread handlerThread;
    private Handler handler;
    private static final int PERMISSIONS_REQUEST = 1;
    private int useFacing;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private FrameLayout frameLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_detect);

        Intent intent = getIntent();
        useFacing = intent.getIntExtra(KEY_USE_FACING, CameraCharacteristics.LENS_FACING_BACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            if (hasPermission()) {
                faceDetector = new FaceDetectorPreview(this,useFacing);
            } else {
                requestPermission();
            }
        }

        frameLayout =(FrameLayout) findViewById(R.id.rectcontainer);
        RelativeLayout.LayoutParams capturePhotoFrameLayout = new RelativeLayout.LayoutParams(1, 1);
        frameLayout.setLayoutParams(capturePhotoFrameLayout);


    }
    @Override
    public synchronized void onResume() {
        super.onResume();
        try {
            Intent ackIntent = new Intent(ContantValues.ACTIVITY_RESUMED.getEventCodeString());
            LocalBroadcastManager.getInstance(this).sendBroadcast(ackIntent);
        } catch (Exception e) {
            Log.e("Error broadcasting " + ContantValues.ACTIVITY_RESUMED.getEventCodeString()
                    + " : ", e.toString());
        }
    }

    @Override
    public synchronized void onPause() {
        try {
            Intent ackIntent = new Intent(ContantValues.ACTIVITY_PAUSED.getEventCodeString());
            LocalBroadcastManager.getInstance(this).sendBroadcast(ackIntent);
        } catch (Exception e) {
            Log.e("Error broadcasting " + ContantValues.ACTIVITY_PAUSED.getEventCodeString()
                    + " : ", e.toString());
        }
        super.onPause();
    }
    @Override
    public synchronized void onStop() {
        super.onStop();
    }

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
    }
    private static boolean allPermissionsGranted(final int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String[] permissions, final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST) {
            if (allPermissionsGranted(grantResults)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    faceDetector = new FaceDetectorPreview(this,useFacing);
                }
            } else {
                requestPermission();
            }
        }
    }


    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(
                        this,
                        "Camera permission is required for this demo",
                        Toast.LENGTH_LONG)
                        .show();
            }
            requestPermissions(new String[]{PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
        }
    }


}
