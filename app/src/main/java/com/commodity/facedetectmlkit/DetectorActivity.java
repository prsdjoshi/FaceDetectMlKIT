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

package com.commodity.facedetectmlkit;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.commodity.facedetectmlkit.tracking.MultiBoxTracker.frameToCanvasMatrix;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    // Configuration values for the prepackaged SSD model.
    //private static final int TF_OD_API_INPUT_SIZE = 300;
    //private static final boolean TF_OD_API_IS_QUANTIZED = true;
    //private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    //private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";

    //private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

    // Face Mask
    private static final int TF_OD_API_INPUT_SIZE = 224;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "mask_detector.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/mask_labelmap.txt";

    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(1920, 1080);
    //private static final int CROP_SIZE = 320;
    //private static final Size CROP_SIZE = new Size(320, 320);


    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d("window width: ", String.valueOf(getWindow().getWindowManager().getDefaultDisplay().getWidth()));
        Log.d("window height: ", String.valueOf(getWindow().getWindowManager().getDefaultDisplay().getHeight()));
        // Real-time contour detection of multiple faces
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setContourMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .build();


        FaceDetector detector = FaceDetection.getClient(options);

        faceDetector = detector;


        //checkWritePermission();

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());

        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);


        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            getAssets(),
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
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
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
        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        txt_temp = (TextView) findViewById(R.id.txt_temp);
        lay_bottom = (LinearLayout) findViewById(R.id.lay_bottom);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (DESIRED_PREVIEW_SIZE.getWidth()*0.2));
        params.weight =1f;
        linearLayout.setLayoutParams(params);

        lay_mask = new LinearLayout(this);
        LinearLayout.LayoutParams params1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,0.6f);
        lay_mask.setLayoutParams(params1);
        lay_mask.setBackgroundColor(getResources().getColor(R.color.white_semi_transparent));
        linearLayout.addView(lay_mask);

        lay_msg = new LinearLayout(this);
        LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,0.4f);
        lay_msg.setLayoutParams(params2);
        lay_msg.setBackgroundColor(getResources().getColor(R.color.black_semi_transparent));
        lay_msg.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.addView(lay_msg);

        lay_bottom.addView(linearLayout);

        txt_mask = new TextView(getApplicationContext());
        txt_mask.setText(ContantValues.MASK_NOTFOUND_LABEL.getEventCodeString());
        txt_mask.setTextColor(getResources().getColor(R.color.black));
        txt_mask.setPadding(5,5,5,5);
        txt_mask.setLayoutParams(getTextParams());
        txt_mask.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL);
        lay_mask.addView(txt_mask);

        txt_msg = new TextView(getApplicationContext());
        txt_msg.setText(ContantValues.NOTPERMIT_LABEL.getEventCodeString());
        txt_msg.setGravity(Gravity.CENTER);
        txt_msg.setTextColor(getResources().getColor(R.color.white));
        txt_msg.setPadding(5,5,5,5);
        txt_msg.setLayoutParams(getTextParams());

        imagemsg =new ImageView(this);
        int img_size= (int) (DESIRED_PREVIEW_SIZE.getWidth()*0.2/2);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(img_size, img_size);
        layoutParams.gravity=Gravity.CENTER_VERTICAL;
        imagemsg.setLayoutParams(layoutParams);

        imagemsg.setPadding(5,5,5,5);
        lay_msg.addView(imagemsg);
        lay_msg.addView(txt_msg);

        lay_mask.measure(0,0);
        lay_msg.measure(0,0);
        float mskTextsize = getAutoResize(txt_mask,ContantValues.MASK_NOTFOUND_LABEL.getEventCodeString(),lay_mask.getMeasuredWidth(),lay_mask.getMeasuredHeight(),0.5f);
        float msgTextsize = getAutoResize(txt_msg,ContantValues.NOTPERMIT_LABEL.getEventCodeString(), (int) (lay_msg.getMeasuredWidth() * 0.8),lay_msg.getMeasuredHeight(),0.5f);
        txt_mask.setTextSize(mskTextsize);
        txt_msg.setTextSize(msgTextsize);
        txt_temp.setTextSize(mskTextsize);

        setValue("Welcome to Xtreme Media");

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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setValue(String ispermit)
    {
        final String detected_temp_value =
                !TextUtils.isEmpty(ContantValues.DETECTED_TEMPERATURE.getEventCodeString())
                        ? String.format("Temperature: %s °C", ContantValues.DETECTED_TEMPERATURE.getEventCodeString())
                        : "";
        try {
            if(ispermit.equalsIgnoreCase(ContantValues.PREVIEW_MASK_LABEL.getEventCodeString()))
            {
                    imagemsg.setImageDrawable(getResources().getDrawable(R.drawable.ic_checked));
                    lay_msg.setBackgroundColor(getResources().getColor(R.color.green));
                    txt_msg.setText(ContantValues.PERMIT_LABEL.getEventCodeString());
                    txt_mask.setText(ContantValues.MASK_FOUND_LABEL.getEventCodeString());
                    txt_temp.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.yellow_semi_transparent)));
                    txt_temp.setTextColor(getResources().getColor(R.color.black));
                    txt_temp.setText(detected_temp_value);
                    txt_temp.setVisibility(View.VISIBLE);
                    lay_bottom.setVisibility(View.VISIBLE);

            }else if(ispermit.equalsIgnoreCase(ContantValues.PREVIEW_NOMASK_LABEL.getEventCodeString()))
            {
                    imagemsg.setImageDrawable(getResources().getDrawable(R.drawable.ic_stop));
                    lay_msg.setBackgroundColor(getResources().getColor(R.color.red));
                    txt_msg.setText(ContantValues.NOTPERMIT_LABEL.getEventCodeString());
                    txt_mask.setText(ContantValues.MASK_NOTFOUND_LABEL.getEventCodeString());
                    txt_temp.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.red)));
                    txt_temp.setTextColor(getResources().getColor(R.color.white));
                    txt_temp.setText(detected_temp_value);
                    txt_temp.setVisibility(View.VISIBLE);
                    lay_bottom.setVisibility(View.VISIBLE);
            }else {
                    isNoface_inRect =false;
                    isNoface_outRect = false;
//                    lay_msg.setBackgroundColor(getResources().getColor(R.color.black_semi_transparent));
//                    txt_msg.setText(ContantValues.DEFAULT_MESSAGE.getEventCodeString());
//
//                    txt_mask.setText("");
//                    imagemsg.setVisibility(View.GONE);
//                    txt_temp.setVisibility(View.GONE);
                txt_temp.setVisibility(View.GONE);
                lay_bottom.setVisibility(View.GONE);

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
                        if (faces.size() == 0) {
                            if(isNoface_inRect)
                            {
                                Log.d("Face Detection: ", String.valueOf(faces.size()));
                                runOnUiThread(
                                        new Runnable() {
                                            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                                            @Override
                                            public void run() {
                                                setValue("Welcome to Xtreme Media");
                                                array_probability.clear();
                                            }});
                            }
                            updateResults(currTimestamp, new LinkedList<>());
                            return;
                        }
                        runInBackground(
                                new Runnable() {
                                    @Override
                                    public void run() {

                                        onFacesDetected(currTimestamp, faces);
                                    }
                                });
                    }

                });


    }

    @Override
    protected int getLayoutId() {
        return R.layout.lagacy_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
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


        runOnUiThread(
                new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void run() {
                        if(mappedRecognitions.size()>0)
                        {
                            array_probability.add(mappedRecognitions.get(0).getTitle());
                            if(array_probability.size()>5)
                            {
                                isNoface_inRect =true;
//                                //isNoface_outRect =true;
//                                int countyes= Collections.frequency(array_probability, "mask");
//                                int countno = Collections.frequency(array_probability, "no mask");
//                                //Log.d("count yes:",String.valueOf(countyes)+" , no:"+String.valueOf(countno));
//                                if(countyes>countno)
//                                {
//                                    setValue("mask");
//                                }else {
//                                    setValue("no mask");
//                                }
                                Log.d("count yes:",String.valueOf(Collections.max(array_probability)));
                                setValue(Collections.max(array_probability));
                                array_probability.clear();
                            }
                        }
                        showFrameInfo(previewWidth + "x" + previewHeight);
                        showCropInfo(croppedBitmap.getWidth() + "x" + croppedBitmap.getHeight());
                        showInference(lastProcessingTimeMs + "ms");
                    }
                });

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
                        final Classifier.Recognition result = new Classifier.Recognition(
                                "0", label, confidence, boundingBox);
                        result.setColor(color);
                        result.setLocation(boundingBox);
                        mappedRecognitions.add(result);
                    }
//                    else {
//                        runOnUiThread(
//                                new Runnable() {
//                                    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
//                                    @Override
//                                    public void run()
//                                    {
//                                        if(isNoface_outRect)
//                                        {
//                                            setValue("Welcome to Xtreme Media");
//                                            array_probability.clear();
//                                        }
//                                    }
//                                });
//
//
//                    }

            }

        }

        //    if (saved) {
//      lastSaved = System.currentTimeMillis();
//    }

        if(inRect==0)
        {
            runOnUiThread(
                    new Runnable() {
                        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                        @Override
                        public void run() {
                            setValue("Welcome to Xtreme Media");
                            array_probability.clear();
                        }});
        }
        updateResults(currTimestamp, mappedRecognitions);
    }
    private Matrix getFrameToCanvasMatrix() {

        return frameToCanvasMatrix;
    }
}
