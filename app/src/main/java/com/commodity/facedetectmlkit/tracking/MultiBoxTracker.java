/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.commodity.facedetectmlkit.tracking;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;

import com.commodity.facedetectmlkit.R;
import com.commodity.facedetectmlkit.env.BorderedText;
import com.commodity.facedetectmlkit.env.ImageUtils;
import com.commodity.facedetectmlkit.env.Logger;
import com.commodity.facedetectmlkit.setting.ContantValues;
import com.commodity.facedetectmlkit.setting.FixedValues;
import com.commodity.facedetectmlkit.tflite.Classifier;

import com.commodity.facedetectmlkit.tflite.Classifier.Recognition;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static com.commodity.facedetectmlkit.setting.FixedValues.RECT_POS_BOTTOM;
import static com.commodity.facedetectmlkit.setting.FixedValues.RECT_POS_LEFT;
import static com.commodity.facedetectmlkit.setting.FixedValues.RECT_POS_RIGHT;
import static com.commodity.facedetectmlkit.setting.FixedValues.RECT_POS_TOP;

/**
 * A tracker that handles non-max suppression and matches existing objects to new detections.
 */
public class MultiBoxTracker {
    private static final float TEXT_SIZE_DIP = 18;
    private static final float MIN_SIZE = 16.0f;
    private static final int[] COLORS = {
            Color.BLUE,
            Color.RED,
            Color.GREEN,
            Color.YELLOW,
            Color.CYAN,
            Color.MAGENTA,
            Color.WHITE,
            Color.parseColor("#55FF55"),
            Color.parseColor("#FFA500"),
            Color.parseColor("#FF8888"),
            Color.parseColor("#AAAAFF"),
            Color.parseColor("#FFFFAA"),
            Color.parseColor("#55AAAA"),
            Color.parseColor("#AA33AA"),
            Color.parseColor("#0D0068")
    };
    final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();
    private final Logger logger = new Logger();
    private final Queue<Integer> availableColors = new LinkedList<Integer>();
    private final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();
    private final Paint boxPaint = new Paint();
    private final float textSizePx;
    private final BorderedText borderedText;
    public static Matrix frameToCanvasMatrix;
    private final Bitmap bitmap;
    private Canvas temp;
    private Paint spaint;
    private Paint p = new Paint();
    private Paint transparentPaint;
    private int frameWidth;
    private int frameHeight;
    private int sensorOrientation;

    public MultiBoxTracker(final Context context) {
        for (final int color : COLORS) {
            availableColors.add(color);
        }

        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Style.STROKE);
        boxPaint.setStrokeWidth(10.0f);
        boxPaint.setStrokeCap(Cap.ROUND);
        boxPaint.setStrokeJoin(Join.ROUND);
        boxPaint.setStrokeMiter(100);

//
//        int x = 290, y = 360, width = 500, height = 660;
//        FixedValues.RECT_POS_LEFT = x;
//        FixedValues.RECT_POS_RIGHT = y;
//        FixedValues.RECT_POS_TOP = x + width;
//        FixedValues.RECT_POS_BOTTOM = y + height;

        bitmap = Bitmap.createBitmap(context.getResources().getDisplayMetrics().widthPixels,context.getResources().getDisplayMetrics().heightPixels, Bitmap.Config.ARGB_8888);
        Canvas osCanvas = new Canvas(bitmap);

        temp = new Canvas(bitmap);
        spaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        spaint.setColor(context.getResources().getColor(R.color.dark_gray));
        spaint.setAlpha(99);
        osCanvas.drawRect(0, 0, temp.getWidth(), temp.getHeight(), spaint);


        transparentPaint = new Paint();
        transparentPaint.setColor(context.getResources().getColor(android.R.color.transparent));
        transparentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));


        textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
    }

    public synchronized void setFrameConfiguration(
            final int width, final int height, final int sensorOrientation) {
        frameWidth = width;
        frameHeight = height;
        this.sensorOrientation = sensorOrientation;
    }

    public synchronized void drawDebug(final Canvas canvas) {
        final Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(60.0f);

        final Paint boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setAlpha(200);
        boxPaint.setStyle(Style.STROKE);

        for (final Pair<Float, RectF> detection : screenRects) {
            final RectF rect = detection.second;
            canvas.drawRect(rect, boxPaint);
            canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
            borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first);
        }
    }

    public synchronized void trackResults(final List<Classifier.Recognition> results, final long timestamp) {
        logger.i("Processing %d results from %d", results.size(), timestamp);
        processResults(results);
    }

    private Matrix getFrameToCanvasMatrix() {
        return frameToCanvasMatrix;
    }

    public synchronized void draw(final Canvas canvas) {
        try {
            final boolean rotated = sensorOrientation % 180 == 90;
            final float multiplier =
                    Math.min(
                            canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                            canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
            frameToCanvasMatrix =
                    ImageUtils.getTransformationMatrix(
                            frameWidth,
                            frameHeight,
                            (int) (multiplier * (rotated ? frameHeight : frameWidth)),
                            (int) (multiplier * (rotated ? frameWidth : frameHeight)),
                            sensorOrientation,
                            false);

            final Paint paint = new Paint();
            paint.setColor(Color.YELLOW);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(8.0f);


            RectF rect = new RectF(FixedValues.RECT_POS_LEFT,
                    FixedValues.RECT_POS_TOP, FixedValues.RECT_POS_RIGHT,
                    FixedValues.RECT_POS_BOTTOM);
            // canvas.drawRoundRect(rect , 0, 0, paint);
            canvas.drawOval(rect, paint);
            temp.drawOval(rect, transparentPaint);
            canvas.drawBitmap(bitmap, 0, 0, p);

            for (final TrackedRecognition recognition : trackedObjects) {
                final RectF trackedPos = new RectF(recognition.location);

                getFrameToCanvasMatrix().mapRect(trackedPos);
//          Log.d("Temp boundingBox left: ", String.valueOf(trackedPos.left)+", right: "+String.valueOf(trackedPos.right)+", Top: "+
//                  String.valueOf(trackedPos.top)+", bottom: "+String.valueOf(trackedPos.bottom));

                if (recognition.title.equalsIgnoreCase("no mask")) {
                    final Paint redPaint = new Paint();
                    redPaint.setColor(Color.RED);
                    redPaint.setStyle(Style.STROKE);
                    redPaint.setStrokeWidth(8.0f);
                    canvas.drawOval(rect,redPaint);
                } else if (recognition.title.equalsIgnoreCase("mask")) {
                    final Paint greenPaint = new Paint();
                    greenPaint.setColor(Color.GREEN);
                    greenPaint.setStyle(Style.STROKE);
                    greenPaint.setStrokeWidth(8.0f);
                    canvas.drawOval(rect,greenPaint);
                }

//                boxPaint.setColor(recognition.color);
//
//                float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
//                canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);
//
//                @SuppressLint("DefaultLocale") final String strConfidence =
//                        recognition.detectionConfidence < 0
//                                ? ""
//                                : String.format("%.2f", (100 * recognition.detectionConfidence)) + "%";
//
//                final String labelString =
//                        !TextUtils.isEmpty(recognition.title)
//                                ? String.format("%s %s", recognition.title, strConfidence)
//                                : strConfidence;
//
//                borderedText.drawText(
//                        canvas, trackedPos.left + cornerSize, trackedPos.top, labelString, boxPaint);

            }
        } catch (Exception e) {
            // LocalLogger.LOGGER.error("FaceDetection- Exception on onDraw in MultiBoxTracer: ", e);
        }
    }


    private void processResults(final List<Recognition> results) {
        final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();

        screenRects.clear();
        final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

        for (final Recognition result : results) {
            if (result.getLocation() == null) {
                continue;
            }
            final RectF detectionFrameRect = new RectF(result.getLocation());

            final RectF detectionScreenRect = new RectF();
            rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

            logger.v(
                    "Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);

            screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));

            if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
                logger.w("Degenerate rectangle! " + detectionFrameRect);
                continue;
            }

            rectsToTrack.add(new Pair<Float, Recognition>(result.getConfidence(), result));
        }

        trackedObjects.clear();
        if (rectsToTrack.isEmpty()) {
            logger.v("Nothing to track, aborting.");
            return;
        }

        for (final Pair<Float, Recognition> potential : rectsToTrack) {
            final TrackedRecognition trackedRecognition = new TrackedRecognition();
            trackedRecognition.detectionConfidence = potential.first;
            trackedRecognition.location = new RectF(potential.second.getLocation());
            trackedRecognition.title = potential.second.getTitle();
            if (potential.second.getColor() != null) {
                trackedRecognition.color = potential.second.getColor();
            } else {
                trackedRecognition.color = COLORS[trackedObjects.size()];

            }
            trackedObjects.add(trackedRecognition);

            if (trackedObjects.size() >= COLORS.length) {
                break;
            }
        }
    }

    private static class TrackedRecognition {
        RectF location;
        float detectionConfidence;
        int color;
        String title;
    }
}
