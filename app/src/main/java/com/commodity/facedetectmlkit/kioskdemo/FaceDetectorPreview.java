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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;
import android.media.ImageReader.OnImageAvailableListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.commodity.facedetectmlkit.CameraActivity;
import com.commodity.facedetectmlkit.R;
import com.commodity.facedetectmlkit.customview.AutoResizeTextView;
import com.commodity.facedetectmlkit.customview.OverlayView;
import com.commodity.facedetectmlkit.env.BorderedText;
import com.commodity.facedetectmlkit.env.ImageUtils;
import com.commodity.facedetectmlkit.env.Logger;
import com.commodity.facedetectmlkit.setting.ContantValues;
import com.commodity.facedetectmlkit.setting.FixedValues;
import com.commodity.facedetectmlkit.tflite.Classifier;
import com.commodity.facedetectmlkit.tflite.TFLiteObjectDetectionAPIModel;
import com.commodity.facedetectmlkit.tracking.MultiBoxTracker;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static android.media.MediaExtractor.MetricsConstants.FORMAT;
import static android.view.View.VISIBLE;
import static com.commodity.facedetectmlkit.tracking.MultiBoxTracker.frameToCanvasMatrix;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class FaceDetectorPreview extends CameraPreview implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    private static final int TF_OD_API_INPUT_SIZE = 224;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "mask_detector.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/mask_labelmap.txt";

    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;

    private static Size DESIRED_PREVIEW_SIZE = null;
    //private static final int CROP_SIZE = 320;
    //private static final Size CROP_SIZE = new Size(320, 320);


    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    private final Activity activity;
    private Handler previewHandler;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Classifier detector;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;

    // Face detector
    private FaceDetector faceDetector;

    // here the preview image is drawn in portrait way
    private Bitmap portraitBmp = null;
    // here the face is cropped and drawn
    private Bitmap faceBmp = null;
    private Bitmap newrgbFrameBitmap = null;
    private LinearLayout lay_bottom;
    private LinearLayout lay_mask;
    private LinearLayout lay_msg;
    private TextView txt_msg,txt_mask;
    TextView txt_temp;
    private ImageView imagemsg;

    int cnt_face=0;
    boolean isNoface_inRect = true;
    boolean isNoface_outRect = true;
    ArrayList<String> array_probability = new ArrayList<>();
    Context context;
    private FrameLayout frameLayout;
    private boolean isPreviewVisible = false;
    private String temperature =null;
    private boolean isNormalTemperature = false;
    private VideoView videoview;
    private final float THRESHOLD = 0.75f;
    private ImageView capturedPhoto;
    private CountDownTimer countDownTimer;
    private AutoResizeTextView txtBeforeAfterFaceDetection;
    private static final String FORMAT = "%02d:%02d";
    public boolean isStartDetection = true;

    @Override
    protected void setupCamera() {
        if (true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                DESIRED_PREVIEW_SIZE= new Size(1920, 1080);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public FaceDetectorPreview(Context context, Integer Facing) {
        super(context, Facing);
        this.context = context;
        activity = (Activity) context;

        Log.d("window width: ", String.valueOf(activity.getWindow().getWindowManager().getDefaultDisplay().getWidth()));
        Log.d("window height: ", String.valueOf(activity.getWindow().getWindowManager().getDefaultDisplay().getHeight()));
        // Real-time contour detection of multiple faces
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        //.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                      //  .setContourMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                       // .setMinFaceSize(0.30f)
                    //    .setMinFaceSize(0.15f)
                        .enableTracking()
                        .build();

//        FaceDetectorOptions realTimeOpts =
//                new FaceDetectorOptions.Builder()
//                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
//                        .build();

        FaceDetector detector = FaceDetection.getClient(options);

        faceDetector = detector;

        try {
            if (previewHandler == null)
                previewHandler = new Handler();
        } catch (Exception e) {
            Log.e("Exception in creating handler in FaceDetectorPreview ", e.toString());
        }
        //checkWritePermission();

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());

        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(context);


        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            context.getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            //cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            activity.getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            activity.finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);


        int targetW, targetH;
        if (sensorOrientation == 90 || sensorOrientation == 270) {
            targetH = previewWidth;
            targetW = previewHeight;
        } else {
            targetW = previewWidth;
            targetH = previewHeight;
        }
        int cropW = (int) (targetW / 2.0);
        int cropH = (int) (targetH / 2.0);

        croppedBitmap = Bitmap.createBitmap(cropW, cropH, Config.ARGB_8888);

        portraitBmp = Bitmap.createBitmap(targetW, targetH, Config.ARGB_8888);
        faceBmp = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropW, cropH,
                        sensorOrientation, MAINTAIN_ASPECT);

//    frameToCropTransform =
//            ImageUtils.getTransformationMatrix(
//                    previewWidth, previewHeight,
//                    previewWidth, previewHeight,
//                    sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        Log.d("Desired Width: ",DESIRED_PREVIEW_SIZE.getWidth()+" Height: "+DESIRED_PREVIEW_SIZE.getHeight());
        trackingOverlay = (OverlayView) activity.findViewById(R.id.tracking_overlay);
        capturedPhoto = (ImageView)activity.findViewById(R.id.faceimage);
        frameLayout =(FrameLayout) activity.findViewById(R.id.rectcontainer);
        videoview =(VideoView) activity.findViewById(R.id.videoview);
        Uri uri = Uri.parse("android.resource://"+activity.getPackageName()+"/"+R.raw.full_screen_google);
        videoview.setVideoURI(uri);
        videoview.setMediaController(new MediaController(context));
        videoview.requestFocus();
        videoview.setLayoutParams(new RelativeLayout.LayoutParams(1920, 1080));
        videoview.start();

        RelativeLayout.LayoutParams capturePhotoFrameLayout = new RelativeLayout.LayoutParams(1, 1);
        frameLayout.setLayoutParams(capturePhotoFrameLayout);
        txtBeforeAfterFaceDetection = (AutoResizeTextView) activity.findViewById(R.id.txtBeforeFaceDetection);

        txt_temp = (TextView) activity.findViewById(R.id.txt_temp);
        lay_bottom = (LinearLayout) activity.findViewById(R.id.lay_bottom);
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (DESIRED_PREVIEW_SIZE.getWidth()*0.2));
        params.weight =1f;
        linearLayout.setLayoutParams(params);

        lay_mask = new LinearLayout(activity);
        LinearLayout.LayoutParams params1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,0.6f);
        lay_mask.setLayoutParams(params1);
        lay_mask.setBackgroundColor(activity.getResources().getColor(R.color.white_semi_transparent));
        linearLayout.addView(lay_mask);

        lay_msg = new LinearLayout(activity);
        LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,0.4f);
        lay_msg.setLayoutParams(params2);
        lay_msg.setBackgroundColor(activity.getResources().getColor(R.color.black_semi_transparent));
        lay_msg.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.addView(lay_msg);

        lay_bottom.addView(linearLayout);

        txt_mask = new TextView(activity.getApplicationContext());
        txt_mask.setText(ContantValues.MASK_NOTFOUND_LABEL.getEventCodeString());
        txt_mask.setTextColor(activity.getResources().getColor(R.color.black));
        txt_mask.setPadding(5,5,5,5);
        txt_mask.setLayoutParams(getTextParams());
        txt_mask.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL);
        lay_mask.addView(txt_mask);

        txt_msg = new TextView(activity.getApplicationContext());
        txt_msg.setText(ContantValues.NOTPERMIT_LABEL.getEventCodeString());
        txt_msg.setGravity(Gravity.CENTER);
        txt_msg.setTextColor(activity.getResources().getColor(R.color.white));
        txt_msg.setPadding(5,5,5,5);
        txt_msg.setLayoutParams(getTextParams());

        imagemsg =new ImageView(activity);
        int img_size= (int) (DESIRED_PREVIEW_SIZE.getWidth()*0.2/2);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(img_size, img_size);
        layoutParams.gravity=Gravity.CENTER_VERTICAL;
        imagemsg.setLayoutParams(layoutParams);

        imagemsg.setPadding(5,5,5,5);
        lay_msg.addView(imagemsg);
        lay_msg.addView(txt_msg);


        txt_mask.setTextSize(TypedValue.COMPLEX_UNIT_PX, 150);
        txt_msg.setTextSize(TypedValue.COMPLEX_UNIT_PX, 150);
        txt_temp.setTextSize(TypedValue.COMPLEX_UNIT_PX, 150);
        lay_mask.measure(0, 0);
        lay_msg.measure(0, 0);
        float mskTextsize = getAutoResize(txt_mask,ContantValues.MASK_NOTFOUND_LABEL.getEventCodeString(),lay_mask.getMeasuredWidth()/2,lay_mask.getMeasuredHeight(),1.0f);
        float msgTextsize = getAutoResize(txt_msg,ContantValues.NOTPERMIT_LABEL.getEventCodeString(), (int) (lay_msg.getMeasuredWidth() /2),lay_msg.getMeasuredHeight(),1.0f);
//        txt_mask.setTextSize(mskTextsize);
//        txt_msg.setTextSize(msgTextsize);
//        txt_temp.setTextSize(mskTextsize);
        txt_mask.setTextSize(TypedValue.COMPLEX_UNIT_PX, mskTextsize);
        txt_msg.setTextSize(TypedValue.COMPLEX_UNIT_PX, msgTextsize);
        txt_temp.setTextSize(TypedValue.COMPLEX_UNIT_PX, mskTextsize);


//        ImageView imageView = (ImageView)activity.findViewById(R.id.faceimage);
//        imageView.setImageDrawable(context.getResources().getDrawable(R.drawable.face_cut));
//        LinearLayout.LayoutParams facecutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
//        facecutParams.setMargins((int) FixedValues.RECT_POS_LEFT,
//                (int) FixedValues.RECT_POS_TOP,(int) FixedValues.RECT_POS_RIGHT,
//                (int) FixedValues.RECT_POS_BOTTOM);
//        imageView.setLayoutParams(params);

        setValue("");

        trackingOverlay.addCallback(
            new OverlayView.DrawCallback() {
                @Override
                public void drawCallback(final Canvas canvas) {
                    tracker.draw(canvas);
                    if (isDebug()) {
                        tracker.drawDebug(canvas);
                    }
                }
            });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }
    public float getAutoResize(TextView textView, String text, int width, int pheight, double resizePercent) {
        double height=pheight * resizePercent;
        float targetTextSize = 0;
        int textHeight = 0;
        TextPaint textPaint = textView.getPaint();
        float lower = 1;
        float upper = 300;
        int loop_counter = 1;
        targetTextSize = (lower + upper) / 2;
        textHeight = getTextHeight(text, textPaint, width, targetTextSize);
        while (loop_counter < 30 && upper - lower > 1) {
            targetTextSize = (lower + upper) / 2;
            textHeight = getTextHeight(text, textPaint, width, targetTextSize);
            if (textHeight > height)
                upper = targetTextSize;
            else
                lower = targetTextSize;
            loop_counter++;
        }

        return targetTextSize;
    }
    private int getTextHeight(CharSequence source, TextPaint paint, int width,
                              float textSize) {
        // modified: make a copy of the original TextPaint object for measuring
        // (apparently the object gets modified while measuring, see also the
        // docs for TextView.getPaint() (which states to access it read-only)
        TextPaint paintCopy = new TextPaint(paint);
        // Update the text paint object
        paintCopy.setTextSize(textSize);
        // Measure using a static layout
        StaticLayout layout = new StaticLayout(source, paintCopy, width,
                Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
        return layout.getHeight();
    }

    @NonNull
    private TableRow.LayoutParams getTextParams() {
        TableRow.LayoutParams params = new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.MATCH_PARENT);

        return params;
    }
    boolean isNormal(double x, double max) {
        return x <= max;
    }
    @SuppressLint("LongLogTag")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setValue(String ispermit)
    {
        try {
            if(ispermit.equalsIgnoreCase(""))
            {
                isNoface_inRect = false;
                isNoface_outRect = false;
                txt_temp.setVisibility(View.GONE);
                lay_bottom.setVisibility(View.GONE);
                return;
            }
            String detected_temp_value =
                    temperature != null && !temperature.equalsIgnoreCase("")
                            ? String.format("Temperature: %s °F", temperature)
                            : "";

            Log.d("FaceDetection- setValueToView detected_temp_value: ", detected_temp_value);
            Log.d("FaceDetection- setValueToView isMask: ", ispermit);

            if(!detected_temp_value.equalsIgnoreCase(""))
            {
                isNormalTemperature = isNormal(Double.parseDouble(temperature), Double.parseDouble(String.valueOf("98.5")));
            }
            else {
                isNormalTemperature = false;
            }


            if(detected_temp_value.equalsIgnoreCase(""))
            {
                if (ispermit.equalsIgnoreCase("mask")) {
                    imagemsg.setImageDrawable(activity.getResources().getDrawable(R.drawable.ic_checked));
                    lay_msg.setBackgroundColor(activity.getResources().getColor(R.color.green));
                    txt_msg.setText(ContantValues.PERMIT_LABEL.getEventCodeString());
                    txt_mask.setText(ContantValues.MASK_FOUND_LABEL.getEventCodeString());
                    txt_temp.setBackgroundTintList(ColorStateList.valueOf(activity.getResources().getColor(R.color.yellow_semi_transparent)));
                    txt_temp.setTextColor(activity.getResources().getColor(R.color.black));
                    txt_temp.setVisibility(View.GONE);
                    lay_bottom.setVisibility(View.VISIBLE);
                }
                else if (ispermit.equalsIgnoreCase("no mask")) {
                    imagemsg.setImageDrawable(activity.getResources().getDrawable(R.drawable.ic_stop));
                    lay_msg.setBackgroundColor(activity.getResources().getColor(R.color.red));
                    txt_msg.setText(ContantValues.NOTPERMIT_LABEL.getEventCodeString());
                    txt_mask.setText(ContantValues.MASK_NOTFOUND_LABEL.getEventCodeString());
                    txt_temp.setBackgroundTintList(ColorStateList.valueOf(activity.getResources().getColor(R.color.red)));
                    txt_temp.setTextColor(activity.getResources().getColor(R.color.white));
                    txt_temp.setVisibility(View.GONE);
                    lay_bottom.setVisibility(View.VISIBLE);
                }else {
                    isNoface_inRect = false;
                    isNoface_outRect = false;
                    txt_temp.setVisibility(View.GONE);
                    lay_bottom.setVisibility(View.GONE);
                }
            }
            else if(isNormalTemperature && ispermit.equalsIgnoreCase("mask"))
            {
                imagemsg.setImageDrawable(activity.getResources().getDrawable(R.drawable.ic_checked));
                lay_msg.setBackgroundColor(activity.getResources().getColor(R.color.green));
                txt_msg.setText(ContantValues.PERMIT_LABEL.getEventCodeString());
                txt_mask.setText(ContantValues.MASK_FOUND_LABEL.getEventCodeString());
                txt_temp.setBackgroundTintList(ColorStateList.valueOf(activity.getResources().getColor(R.color.yellow_semi_transparent)));
                txt_temp.setTextColor(activity.getResources().getColor(R.color.black));
                txt_temp.setText(detected_temp_value);
                txt_temp.setVisibility(View.VISIBLE);
                lay_bottom.setVisibility(View.VISIBLE);

            }
            else if (!isNormalTemperature && ispermit.equalsIgnoreCase("mask")) {
                imagemsg.setImageDrawable(activity.getResources().getDrawable(R.drawable.ic_stop));
                lay_msg.setBackgroundColor(activity.getResources().getColor(R.color.red));
                txt_msg.setText(ContantValues.NOTPERMIT_LABEL.getEventCodeString());
                txt_mask.setText(ContantValues.MASK_NOTFOUND_LABEL.getEventCodeString());
                txt_temp.setBackgroundTintList(ColorStateList.valueOf(activity.getResources().getColor(R.color.red)));
                txt_temp.setTextColor(activity.getResources().getColor(R.color.white));
                txt_temp.setText(detected_temp_value);
                txt_temp.setVisibility(View.VISIBLE);
                lay_bottom.setVisibility(View.VISIBLE);
            }
            else if (isNormalTemperature && ispermit.equalsIgnoreCase("no mask")) {
                imagemsg.setImageDrawable(activity.getResources().getDrawable(R.drawable.ic_stop));
                lay_msg.setBackgroundColor(activity.getResources().getColor(R.color.red));
                txt_msg.setText(ContantValues.NOTPERMIT_LABEL.getEventCodeString());
                txt_mask.setText(ContantValues.MASK_NOTFOUND_LABEL.getEventCodeString());
                txt_temp.setBackgroundTintList(ColorStateList.valueOf(activity.getResources().getColor(R.color.red)));
                txt_temp.setTextColor(activity.getResources().getColor(R.color.white));
                txt_temp.setText(detected_temp_value);
                txt_temp.setVisibility(View.VISIBLE);
                lay_bottom.setVisibility(View.VISIBLE);
            }
            else if (!isNormalTemperature && ispermit.equalsIgnoreCase("no mask")) {
                imagemsg.setImageDrawable(activity.getResources().getDrawable(R.drawable.ic_stop));
                lay_msg.setBackgroundColor(activity.getResources().getColor(R.color.red));
                txt_msg.setText(ContantValues.NOTPERMIT_LABEL.getEventCodeString());
                txt_mask.setText(ContantValues.MASK_NOTFOUND_LABEL.getEventCodeString());
                txt_temp.setBackgroundTintList(ColorStateList.valueOf(activity.getResources().getColor(R.color.red)));
                txt_temp.setTextColor(activity.getResources().getColor(R.color.white));
                txt_temp.setText(detected_temp_value);
                txt_temp.setVisibility(View.VISIBLE);
                lay_bottom.setVisibility(View.VISIBLE);
            }
            else {
                isNoface_inRect = false;
                isNoface_outRect = false;
                txt_temp.setVisibility(View.GONE);
                lay_bottom.setVisibility(View.GONE);
                isNormalTemperature = false;
            }
        } catch (Resources.NotFoundException e) {
            e.printStackTrace();
        }

    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");
        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
        if (getCameraFacing() == CameraCharacteristics.LENS_FACING_FRONT) {
            Matrix rgbmatrix = new Matrix();
            rgbmatrix.postRotate(180);
            newrgbFrameBitmap = Bitmap.createBitmap(rgbFrameBitmap, 0, 0, rgbFrameBitmap.getWidth(), rgbFrameBitmap.getHeight(), rgbmatrix, true);
            rgbFrameBitmap = newrgbFrameBitmap;
        }
        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }


        InputImage image = InputImage.fromBitmap(croppedBitmap, 0);
        faceDetector
                    .process(image)
                    .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                        @Override
                        public void onSuccess(List<Face> faces) {
                            Log.d("Face Detection isStartDetection: ", String.valueOf(isStartDetection));

                            if (faces.size() == 0) {
                                if(isNoface_inRect)
                                {
                                    Log.d("Face Detection: ", String.valueOf(faces.size()));

                                    activity.runOnUiThread(
                                            new Runnable() {
                                                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                                                @Override
                                                public void run() {
                                                    setValue("");
                                                    array_probability.clear();
                                                }});
                                }
                                activity.runOnUiThread(
                                        new Runnable() {
                                            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                                            @Override
                                            public void run() {
                                                if(isPreviewVisible)
                                                {
                                                    int duration = (1000 * 3);
                                                    previewHandler.postDelayed(runnableFaceDetectorPreview, duration);
                                                }
                                            }
                                        });
                                updateResults(currTimestamp, new LinkedList<>());
                                return;
                            }else {

                                activity.runOnUiThread(
                                        new Runnable() {
                                            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                                            @Override
                                            public void run() {
                                                if (previewHandler != null) {
                                                    previewHandler.removeCallbacks(runnableFaceDetectorPreview);
                                                }
                                                boolean isopen =isEyeOpen(faces);
                                                if(!isPreviewVisible && isopen)
                                                {
                                                    LOGGER.i("FACE EyeOpenProb isEyeOpen" + isopen);
                                                    setFullCameraPreview();
                                                }
                                            }});

                            }
                            runInBackground(
                                    new Runnable() {
                                        @Override
                                        public void run() {

                                            Log.d("Face Detection: ", String.valueOf(faces.size()));
                                            onFacesDetected(currTimestamp, faces);
                                        }
                                    });
                        }

                    });




    }
    public boolean isEyeOpen(List<Face> faceList)
    {
        boolean isOpen = false;
        if(faceList.size()>0)
        {
            try {
                Face face = faceList.get(0);
                LOGGER.i("FACE EyeOpenProb right" + face.getRightEyeOpenProbability());
                LOGGER.i("FACE EyeOpenProb left" + face.getRightEyeOpenProbability());
                float lefteye = face.getLeftEyeOpenProbability();
                float righteye = face.getRightEyeOpenProbability();
                if (Float.compare(lefteye, THRESHOLD) > 0 && Float.compare(righteye, THRESHOLD) > 0)
                {
                    isOpen= true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
//            if (face.getLeftEyeOpenProbability() > 0.75f || face.getRightEyeOpenProbability() > 0.75f) {
//            }
        }
        return isOpen;
    }
    private Runnable runnableFaceDetectorPreview = new Runnable() {

        @SuppressLint("LongLogTag")
        @Override
        public void run() {
            try {
                setInvisibleCameraPreview();

            } catch (Exception e) {
                Log.e("Exception in Face Detection Preview Interval runnable ",
                        e.toString());
            }

        }
    };

    private void setFullCameraPreview() {
        isStartDetection = true;
        isPreviewVisible=true;
        RelativeLayout.LayoutParams capturePhotoFrameLayout = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        frameLayout.setLayoutParams(capturePhotoFrameLayout);
        changeMessage(true);
    }

    private void setInvisibleCameraPreview() {
        isPreviewVisible=false;
        RelativeLayout.LayoutParams capturePhotoFrameLayout = new RelativeLayout.LayoutParams(1, 1);
        frameLayout.setLayoutParams(capturePhotoFrameLayout);
        changeMessage(false);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.lagacy_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        Log.d("DESIRED_PREVIEW_SIZE: ", String.valueOf(DESIRED_PREVIEW_SIZE));
        return DESIRED_PREVIEW_SIZE;
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }


    // Face Mask Processing
    private Matrix createTransform(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight,
            final int applyRotation) {

        Matrix matrix = new Matrix();
        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
                LOGGER.w("Rotation of %d % 90 != 0", applyRotation);
            }

            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

            // Rotate around origin.
            matrix.postRotate(applyRotation);
        }

//        // Account for the already applied rotation, if any, and then determine how
//        // much scaling is needed for each axis.
//        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;
//
//        final int inWidth = transpose ? srcHeight : srcWidth;
//        final int inHeight = transpose ? srcWidth : srcHeight;

        if (applyRotation != 0) {

            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
        }

        return matrix;

    }

    private void updateResults(long currTimestamp, final List<Classifier.Recognition> mappedRecognitions) {

        tracker.trackResults(mappedRecognitions, currTimestamp);
        trackingOverlay.postInvalidate();
        computingDetection = false;


        activity.runOnUiThread(
                new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void run() {
                        if(mappedRecognitions.size()>0)
                        {
                            array_probability.add(mappedRecognitions.get(0).getTitle());
                            if(array_probability.size()>3)
                            {
                                isNoface_inRect =true;
                                Log.d("count yes:",String.valueOf(Collections.max(array_probability)));
                                setValue(Collections.max(array_probability));
                                capturePreviewVisible();
                                array_probability.clear();
                            }
                        }
                        showFrameInfo(previewWidth + "x" + previewHeight);
                        showCropInfo(croppedBitmap.getWidth() + "x" + croppedBitmap.getHeight());
                        showInference(lastProcessingTimeMs + "ms");
                    }
                });

    }

    public void capturePreviewVisible()
    {
        isStartDetection = false;
        capturedPhoto.setImageBitmap(croppedBitmap);
        capturedPhoto.setVisibility(View.VISIBLE);
        txtBeforeAfterFaceDetection.setVisibility(View.GONE);
        countDownTimerStart();
    }
    private void countDownTimerStart() {
        txt_temp.setVisibility(VISIBLE);
        countDownTimer = new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                txt_temp.setText("Please Move Hand to measure temperature " + String.format(FORMAT,
                        TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) - TimeUnit.HOURS.toMinutes(
                                TimeUnit.MILLISECONDS.toHours(millisUntilFinished)),
                        TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) - TimeUnit.MINUTES.toSeconds(
                                TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished))));
            }

            @SuppressLint("ResourceAsColor")
            @Override
            public void onFinish() {

                capturedPhoto.setImageBitmap(null);
                capturedPhoto.setVisibility(View.GONE);
                txt_temp.setVisibility(View.GONE);
                setFullCameraPreview();
                setValue("");

            }
        };
        countDownTimer.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void onFacesDetected(long currTimestamp, List<Face> faces) {
        cnt_face=faces.size();
        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
        final Canvas canvas = new Canvas(cropCopyBitmap);
        final Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Style.STROKE);
        paint.setStrokeWidth(2.0f);

        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
        switch (MODE) {
            case TF_OD_API:
                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                break;
        }

        final List<Classifier.Recognition> mappedRecognitions =
                new LinkedList<Classifier.Recognition>();

        //final List<Classifier.Recognition> results = new ArrayList<>();

        // Note this can be done only once
        int sourceW = rgbFrameBitmap.getWidth();
        int sourceH = rgbFrameBitmap.getHeight();
        int targetW = portraitBmp.getWidth();
        int targetH = portraitBmp.getHeight();
        Matrix transform = createTransform(
                sourceW,
                sourceH,
                targetW,
                targetH,
                sensorOrientation);
         final Canvas cv = new Canvas(portraitBmp);
        // draws the original image in portrait mode.
        cv.drawBitmap(rgbFrameBitmap, transform, null);

        final Canvas cvFace = new Canvas(faceBmp);

        boolean saved = false;
        RectF rect_contain = new RectF(260,200,820,800);

        int inRect=0;
        for (Face face : faces) {

            LOGGER.i("FACE" + face.toString());
               // face.getBoundingBox().bottom
            LOGGER.i("Running detection on face " + currTimestamp);
            //results = detector.recognizeImage(croppedBitmap);

            //if(Right2 < Right1 && Left2 > Left1 && Top2 > Top1 && Bottom2 < Bottom1

            final RectF boundingBox = new RectF(face.getBoundingBox());


            //final boolean goodConfidence = result.getConfidence() >= minimumConfidence;
                final boolean goodConfidence = true; //face.get;
                if (boundingBox != null && goodConfidence) {

                    // maps crop coordinates to original
                    cropToFrameTransform.mapRect(boundingBox);

                    // maps original coordinates to portrait coordinates
                    RectF faceBB = new RectF(boundingBox);
                    transform.mapRect(faceBB);

                    // translates portrait to origin and scales to fit input inference size
                    //cv.drawRect(faceBB, paint);
                    float sx = ((float) TF_OD_API_INPUT_SIZE) / faceBB.width();
                    float sy = ((float) TF_OD_API_INPUT_SIZE) / faceBB.height();
                    Matrix matrix = new Matrix();
                    matrix.postTranslate(-faceBB.left, -faceBB.top);
                    matrix.postScale(sx, sy);

                    cvFace.drawBitmap(portraitBmp, matrix, null);


                    String label = "";
                    float confidence = -1f;
                    Integer color = Color.BLUE;

                    final long startTime = SystemClock.uptimeMillis();
                    final List<Classifier.Recognition> resultsAux = detector.recognizeImage(faceBmp);
                    lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                    if (resultsAux.size() > 0) {

                        Classifier.Recognition result = resultsAux.get(0);

                        float conf = result.getConfidence();
                        if (conf >= 0.6f) {

                            confidence = conf;
                            label = result.getTitle();
                            if (result.getId().equals("0")) {
                                color = Color.GREEN;
                            } else {
                                color = Color.RED;
                            }
                        }

                    }

                    if (getCameraFacing() == CameraCharacteristics.LENS_FACING_FRONT) {

                        // camera is frontal so the image is flipped horizontally
                        // flips horizontally
                        Matrix flip = new Matrix();
                        if (sensorOrientation == 90 || sensorOrientation == 270) {
                            flip.postScale(1, -1, previewWidth / 2.0f, previewHeight / 2.0f);
                        } else {
                            flip.postScale(-1, 1, previewWidth / 2.0f, previewHeight / 2.0f);
                        }
                        //flip.postScale(1, -1, targetW / 2.0f, targetH / 2.0f);
                        flip.mapRect(boundingBox);

                    }

                    RectF rect_check = new RectF(FixedValues.RECT_POS_LEFT,
                            FixedValues.RECT_POS_TOP,FixedValues.RECT_POS_RIGHT,
                            FixedValues.RECT_POS_BOTTOM);
                    RectF rect_face = new RectF(boundingBox);
                    getFrameToCanvasMatrix().mapRect(rect_face);
                    //if(true)
                  //  if(rect.contains(boundingBox))
                    if(rect_face.right < FixedValues.RECT_POS_RIGHT && rect_face.left > FixedValues.RECT_POS_LEFT && rect_face.top > FixedValues.RECT_POS_TOP && rect_face.bottom < FixedValues.RECT_POS_BOTTOM)
                    {
                        Log.d("rectangle left: ", String.valueOf(rect_check.left)+", right: "+String.valueOf(rect_check.right)+", Top: "+
                                String.valueOf(rect_check.top)+", bottom: "+String.valueOf(rect_check.bottom));
                        Log.d("boundingBox left: ", String.valueOf(rect_face.left)+", right: "+String.valueOf(rect_face.right)+", Top: "+
                                String.valueOf(rect_face.top)+", bottom: "+String.valueOf(rect_face.bottom));

                        inRect++;

                        int value_rectCheck = (int) (rect_check.width() * 0.5);
                        if(value_rectCheck < rect_face.width())
                        {
                            final Classifier.Recognition result = new Classifier.Recognition(
                                    "0", label, confidence, boundingBox,face);
                            result.setColor(color);
                            result.setLocation(boundingBox);
                            result.setFace(face);
                            mappedRecognitions.add(result);
                            txtBeforeAfterFaceDetection.setText("Please stand steady to detect your face properly ");

                        }
                        else {
                            txtBeforeAfterFaceDetection.setText("Please come closer to the circle");
                        }

                    }
                    else {
                      //  txtBeforeAfterFaceDetection.setText(ContantValues.AFTER_CAMERA_TRIGGERING_MSG.getEventCodeString());
                    }

            }

        }


        if(inRect==0)
        {
            activity.runOnUiThread(
                    new Runnable() {
                        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                        @Override
                        public void run() {
                            setValue("");
                            array_probability.clear();
                        }});
        }
        updateResults(currTimestamp, mappedRecognitions);
    }
    private Matrix getFrameToCanvasMatrix() {

        return frameToCanvasMatrix;
    }



    private void changeMessage(boolean isFullScreen) {
        try {
            if(isFullScreen)
            {
                txtBeforeAfterFaceDetection.setText(ContantValues.AFTER_CAMERA_TRIGGERING_MSG.getEventCodeString());

            }else {
                txtBeforeAfterFaceDetection.setText(ContantValues.BEFORE_CAMERA_TRIGGERING_MSG.getEventCodeString());

            }
        } catch (Exception e) {
            Log.e("Exception in changeMessage in FaceDetectorPreview : ", e.toString());
        }
    }
}
