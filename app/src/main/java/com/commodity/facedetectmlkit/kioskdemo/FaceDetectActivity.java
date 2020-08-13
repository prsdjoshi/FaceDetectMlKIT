package com.commodity.facedetectmlkit.kioskdemo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.commodity.facedetectmlkit.R;
import com.commodity.facedetectmlkit.customview.AutoResizeTextView;
import com.commodity.facedetectmlkit.setting.ContantValues;
import com.commodity.facedetectmlkit.setting.resizablerectangle.ResizableRectangleActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
import static android.view.WindowManager.LayoutParams.TYPE_TOAST;

public class FaceDetectActivity extends AppCompatActivity {
    private static final String KEY_USE_FACING = "use_facing";
    private FaceDetectorPreview faceDetector;
    private HandlerThread handlerThread;
    private Handler handler;
    private static final int PERMISSIONS_REQUEST = 1;
    public static int useFacingView;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private FrameLayout frameLayout;
    private FloatingActionButton setting_fab;
    private WindowManager windowManager;
    private static final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor();
    private static final Size DESIRED_PREVIEW_SIZE = new Size(1920, 1080);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_detect);

        Intent intent = getIntent();
        useFacingView = intent.getIntExtra(KEY_USE_FACING, CameraCharacteristics.LENS_FACING_BACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Handler mainHandler = new Handler(this.getMainLooper());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            if (hasPermission()) {
                faceDetector = new FaceDetectorPreview(FaceDetectActivity.this, useFacingView);
                setTextBeforeFaceDetection();
            } else {
                requestPermission();
            }
        }
        setting_fab = findViewById(R.id.setting_fab);
        setting_fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //finish();
                faceDetector.dispose();

                startActivity(new Intent(getApplicationContext(), ResizableRectangleActivity.class));
            }
        });

    }

    @SuppressLint("LongLogTag")
    private void setTextBeforeFaceDetection() {
        try {
            DisplayMetrics metrics = new DisplayMetrics();
            Display display = getWindowManager().getDefaultDisplay();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                display.getRealMetrics(metrics);
            }
            int realWidth = metrics.widthPixels;
            int realHeight = metrics.heightPixels;

            AutoResizeTextView autoResizeTextView = ((AutoResizeTextView) findViewById(R.id.txtBeforeFaceDetection));
            autoResizeTextView.setVisibility(View.VISIBLE);
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT, (int) (realHeight * 0.1));
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            autoResizeTextView.setLayoutParams(layoutParams);
            autoResizeTextView.setPadding(5, 5, 5, 5);
            autoResizeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 150);
            autoResizeTextView.setText(ContantValues.BEFORE_CAMERA_TRIGGERING_MSG.getEventCodeString());
          //  autoResizeTextView.setTypeface(typeface);

        } catch (Exception e) {
            Log.e("Exception in setTextBeforeFaceDetection : ", e.toString());
        }
    }

    public static int getWindowTypeForOverlay(boolean allowSystemLayer) {
        if (allowSystemLayer) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return TYPE_APPLICATION_OVERLAY;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                return TYPE_SYSTEM_ALERT;
            } else {
                return TYPE_TOAST;
            }
        } else {
            // make layout of the window happens as that of a top-level window, not as a child of its container
            return TYPE_APPLICATION_ATTACHED_DIALOG;
        }
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
                    faceDetector = new FaceDetectorPreview(this, useFacingView);
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
