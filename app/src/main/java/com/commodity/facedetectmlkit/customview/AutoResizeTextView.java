package com.commodity.facedetectmlkit.customview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.ViewTreeObserver;
import android.widget.TextView;

@SuppressLint("AppCompatCustomView")
public class AutoResizeTextView extends TextView {

    TextPaint textPaint = null;
    // Minimum text size for this text view
    public static final float MIN_TEXT_SIZE = 15;
    // Our ellipse string
    private static final String mEllipsis = "...";
    // Registered resize listener
    private OnTextResizeListener mTextResizeListener;
    // Flag for text and/or size changes to force a resize
    private boolean mNeedsResize = false;
    // Text size that is set from code. This acts as a starting point for
    // resizing
    private static final int BISECTION_LOOP_WATCH_DOG = 30;
    private float mTextSize;
    // Temporary upper bounds on the starting text size
    private float mMaxTextSize = 0;
    // Lower bounds for text size
    private float mMinTextSize = MIN_TEXT_SIZE;
    // Text view line spacing multiplier
    private float mSpacingMult = 1.0f;
    // Text view additional line spacing
    private float mSpacingAdd = 0.0f;
    // Add ellipsis to text that overflows at the smallest text size
    private boolean mAddEllipsis = true;
    // X Position to draw text
    private int currentX = 0;
    // Y Position to draw text
    private int currentY = 0;
    // Length of text after resize
    public int textLength = 0;

    private int length = 0;
    // Text Color
    private int mTextColor = Color.BLACK;
    private SparseIntArray mTextCachedSizes;

    // Background Color
    private int mBackgroundColor = Color.WHITE;

    // Default constructor override
    public AutoResizeTextView(Context context) {
        this(context, null);
    }
    private boolean mEnableSizeCache = true;
    // Default constructor when inflating from XML file
    public AutoResizeTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public boolean isAutoResize() {
        return isAutoResize;
    }

    public void setAutoResizeTextView(boolean autoResize) {
        isAutoResize = autoResize;
    }

    boolean isAutoResize=true;
    // Default constructor override
    public AutoResizeTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.setSelected(true);
        mTextSize = getTextSize();
        mTextCachedSizes = new SparseIntArray();
    }

    /**
     * When text changes, set the force resize flag to true and reset the text
     * size.
     */
    @Override
    protected void onTextChanged(final CharSequence text, final int start,
                                 final int before, final int after) {
        mNeedsResize = true;
        // Since this view may be reused, it is good to reset the text size
        resetTextSize();
    }

    /**
     * If the text view size changed, set the force resize flag to true
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w != oldw || h != oldh) {
            mTextCachedSizes.clear();
            mNeedsResize = true;
        }
    }

    /**
     * Register listener to receive resize notifications
     *
     * @param listener
     */
    public void setOnResizeListener(OnTextResizeListener listener) {
        mTextResizeListener = listener;
    }

    /**
     * Override the set text size to update our internal reference values
     */
    @Override
    public void setTextSize(float size) {
        super.setTextSize(size);
        mTextCachedSizes.clear();
        mTextSize = getTextSize();
    }

    /**
     * Override the set text size to update our internal reference values
     */
    @Override
    public void setTextSize(int unit, float size) {
        super.setTextSize(unit, size);
        mTextCachedSizes.clear();

        mTextSize = getTextSize();
    }

    @Override
    public void setTextColor(int color) {
        mTextColor = color;
        super.setTextColor(color);
    }

    @Override
    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
        super.setBackgroundColor(color);
    }

    /**
     * Override the set line spacing to update our internal reference values
     */
    @Override
    public void setLineSpacing(float add, float mult) {
        super.setLineSpacing(add, mult);
        mSpacingMult = mult;
        mSpacingAdd = add;
    }

    /**
     * Return upper text size limit
     *
     * @return
     */
    public float getMaxTextSize() {
        return mMaxTextSize;
    }

    /**
     * Set the upper text size limit and invalidate the view
     *
     * @param maxTextSize
     */
    public void setMaxTextSize(float maxTextSize) {
        mMaxTextSize = maxTextSize;
        requestLayout();
        invalidate();
    }

    /**
     * Return lower text size limit
     *
     * @return
     */
    public float getMinTextSize() {
        return mMinTextSize;
    }

    /**
     * Set the lower text size limit and invalidate the view
     *
     * @param minTextSize
     */
    public void setMinTextSize(float minTextSize) {
        mMinTextSize = minTextSize;
        requestLayout();
        invalidate();
    }

    /**
     * Return flag to add ellipsis to text that overflows at the smallest text
     * size
     *
     * @return
     */
    public boolean getAddEllipsis() {
        return mAddEllipsis;
    }

    /**
     * Set flag to add ellipsis to text that overflows at the smallest text size
     *
     * @param addEllipsis
     */
    public void setAddEllipsis(boolean addEllipsis) {
        mAddEllipsis = addEllipsis;
    }

    /**
     * Reset the text to the original size
     */
    public void resetTextSize() {
        if (mTextSize > 0) {
            super.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
            mMaxTextSize = mTextSize;
        }
    }

    /**
     * Resize text after measuring
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
                            int bottom) {
        if (changed || mNeedsResize) {
            int widthLimit = (right - left) - getCompoundPaddingLeft()
                    - getCompoundPaddingRight();
            int heightLimit = (bottom - top) - getCompoundPaddingBottom()
                    - getCompoundPaddingTop();
            if(isAutoResize())
            {
                super.setTextSize(
                        TypedValue.COMPLEX_UNIT_PX,
                        efficientTextSizeSearch(widthLimit,heightLimit));
            }

        }
        textPaint = this.getPaint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(mTextColor);

        currentY = getHeight() - getHeight() / 4;
        super.onLayout(changed, left, top, right, bottom);
    }

    /**
     * Resize the text size with default width and height
     */
    public void resizeText() {

        int heightLimit = getHeight() - getPaddingBottom() - getPaddingTop();
        int widthLimit = getWidth() - getPaddingLeft() - getPaddingRight();
        resizeText(widthLimit, heightLimit);
    }

    /* AKS:If single Line is true return true */
    public boolean isSingleLine() {
        if (getMaxLines() == 1)
            return true;
        else
            return false;
    }

    /* AKS:get Max Lines */
    @Override
    public int getMaxLines() {
        return super.getMaxLines();
    }

    /* AKS:set Max Lines */
    @Override
    public void setMaxLines(int maxlines) {
        super.setMaxLines(maxlines);
    }

    /**
     * Resize the text size with specified width and height
     *
     * @param width
     * @param height
     */
    public float resizeText2(int width, int height) {

        CharSequence text = getText();
        // Do not resize if the view does not have dimensions or there is no
        // text
        if (text == null || text.length() == 0 || height <= 0 || width <= 0
                || mTextSize == 0) {
            return 0;
        }

        if (getTransformationMethod() != null) {
            text = getTransformationMethod().getTransformation(text, this);
        }

        // Get the text view's paint object
        textPaint = getPaint();
        textPaint.setColor(Color.RED);

        // Store the current text size
        float oldTextSize = textPaint.getTextSize();
        // If there is a max text size set, use the lesser of that and the
        // default text size
        float targetTextSize = mMaxTextSize > 0 ? Math.min(mTextSize,
                mMaxTextSize) : mTextSize;

        // Get the required text height
        int textHeight = getTextHeight(text, textPaint, width, targetTextSize);

        // If Single Line Attribute is true Show text in single line
        if (isSingleLine()) {
            int firstLineChars = 0;

            // Get charachters which fits in first line of the view
            if (textPaint.measureText(text.toString()) > width) {
                firstLineChars = textPaint.breakText(text.toString(), true,
                        width, null);
//				LocalLogger.LOGGER.info("Width" + width);
//				LocalLogger.LOGGER.info("firstLineChars " + firstLineChars);
            }
            CharSequence singleLine = getSingleLineHeight(text.toString(),
                    firstLineChars);
            textHeight = getTextHeight(singleLine, textPaint, width,
                    targetTextSize);

            // Calculate best fon size for selected line and apply to whole text
            // later.
            while (textHeight > height && targetTextSize > mMinTextSize) {
                targetTextSize = Math.max(targetTextSize - 2, mMinTextSize);
                textHeight = getTextHeight(singleLine, textPaint, width,
                        targetTextSize);
            }

            // Some devices try to auto adjust line spacing, so force default
            // line spacing
            // and invalidate the layout as a side effect
            // setTextSize(TypedValue.COMPLEX_UNIT_PX, targetTextSize);
            setLineSpacing(mSpacingAdd, mSpacingMult);

            // TextLength after resize
            textLength = (int) textPaint.measureText(getText().toString());
            firstLineChars = textPaint.breakText(text.toString(), true, width,
                    null);
//            LocalLogger.LOGGER.debug("First Line Chars AfterResize: "
//                  + firstLineChars);
//            LocalLogger.LOGGER.debug("Length " + text.length());

            // Notify the listener if registered
            if (mTextResizeListener != null) {
                mTextResizeListener.onTextResize(this, oldTextSize,
                        targetTextSize);
            }

            // Reset force resize flag
            mNeedsResize = false;
            return targetTextSize;
        }

        // Until we either fit within our text view or we had reached our min
        // text size, incrementally try smaller sizes
        while (textHeight > height && targetTextSize > mMinTextSize) {
            targetTextSize = Math.max(targetTextSize - 2, mMinTextSize);
            textHeight = getTextHeight(text, textPaint, width, targetTextSize);
        }
//        LocalLogger.LOGGER.debug("TargetTextSize 2: " + targetTextSize);
//        LocalLogger.LOGGER.debug("textHeight 2: " + textHeight);
        // If we had reached our minimum text size and still don't fit, append
        // an ellipsis
//            if (mAddEllipsis && targetTextSize == mMinTextSize
//                    && textHeight > height) {
//                // Draw using a static layout
//                // modified: use a copy of TextPaint for measuring
//                TextPaint paint = new TextPaint(textPaint);
//                // Draw using a static layout
//                StaticLayout layout = new StaticLayout(text, paint, width,
//                        Alignment.ALIGN_NORMAL, mSpacingMult, mSpacingAdd, false);
//                // Check that we have a least one line of rendered text
//                if (layout.getLineCount() > 0) {
//                    // Since the line at the specific vertical position would be cut
//                    // off,
//                    // we must trim up to the previous line
//                    int lastLine = layout.getLineForVertical(height) - 1;
//                    // If the text would not even fit on a single line, clear it
//                    if (lastLine < 0) {
//                        setText("");
//                    }
//                    // Otherwise, trim to the previous line and add an ellipsis
//                    else {
//                        int start = layout.getLineStart(lastLine);
//                        int end = layout.getLineEnd(lastLine);
//                        float lineWidth = layout.getLineWidth(lastLine);
//                        float ellipseWidth = textPaint.measureText(mEllipsis);
//
//                        // Trim characters off until we have enough room to draw the
//                        // ellipsis
//                        while (width < lineWidth + ellipseWidth) {
//                            lineWidth = textPaint.measureText(text.subSequence(
//                                    start, --end + 1).toString());
//                        }
//                        setText(text.subSequence(0, end) + mEllipsis);
//                    }
//                }
//            }
        if(mAddEllipsis && targetTextSize == mMinTextSize
                && textHeight > height)
        {
            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    int noOfLinesVisible = getHeight() / getLineHeight();

                    setMaxLines(noOfLinesVisible);
                    setEllipsize(TextUtils.TruncateAt.END);

                }
            });
        }
        // Some devices try to auto adjust line spacing, so force default line
        // spacing
        // and invalidate the layout as a side effect
//        LocalLogger.LOGGER.info("targetTextSize 3: "+targetTextSize);
        //  setTextSize(TypedValue.COMPLEX_UNIT_PX, targetTextSize);
        setLineSpacing(mSpacingAdd, mSpacingMult);
        // Notify the listener if registered
        if (mTextResizeListener != null) {
            mTextResizeListener.onTextResize(this, oldTextSize, targetTextSize);
        }
        mNeedsResize = false;
        return targetTextSize;


    }
    public float resizeText(int width, int height) {
        CharSequence text = getText();
        // Do not resize if the view does not have dimensions or there is no text
        if(text == null || text.length() == 0 || height <= 0 || width <= 0 || mTextSize == 0) {
            return 0;
        }

        // Get the text view's paint object
        TextPaint textPaint = getPaint();

        // Store the current text size
        //  float oldTextSize = textPaint.getTextSize();

        if (getTransformationMethod() != null) {
            text = getTransformationMethod().getTransformation(text, this);
        }

        // Get the text view's paint object
        //textPaint = getPaint();
        textPaint.setColor(Color.RED);

        // Store the current text size
        float oldTextSize = textPaint.getTextSize();
        // If there is a max text size set, use the lesser of that and the
        // default text size
        float targetTextSize = mMaxTextSize > 0 ? Math.min(mTextSize,
                mMaxTextSize) : mTextSize;

        // Get the required text height
        int textHeight = getTextHeight(text, textPaint, width, targetTextSize);
        if (isSingleLine()) {
            int firstLineChars = 0;

            // Get charachters which fits in first line of the view
            if (textPaint.measureText(text.toString()) > width) {
                firstLineChars = textPaint.breakText(text.toString(), true,
                        width, null);
            }
            CharSequence singleLine = getSingleLineHeight(text.toString(),
                    firstLineChars);
            textHeight = getTextHeight(singleLine, textPaint, width,
                    targetTextSize);
            // Calculate best fon size for selected line and apply to whole text
            // later.
            while (textHeight > height && targetTextSize > mMinTextSize) {
                targetTextSize = Math.max(targetTextSize - 2, mMinTextSize);
                textHeight = getTextHeight(singleLine, textPaint, width,
                        targetTextSize);
            }
            // Some devices try to auto adjust line spacing, so force default
            // line spacing
            // and invalidate the layout as a side effect
            setTextSize(TypedValue.COMPLEX_UNIT_PX, targetTextSize);
            setLineSpacing(mSpacingAdd, mSpacingMult);

            // TextLength after resize
            textLength = (int) textPaint.measureText(getText().toString());
            //            firstLineChars = textPaint.breakText(text.toString(), true, width,
            //                    null);
            // Notify the listener if registered
            if (mTextResizeListener != null) {
                mTextResizeListener.onTextResize(this, oldTextSize,
                        targetTextSize);
            }
            // Reset force resize flag
            mNeedsResize = false;
            return targetTextSize;
        }
        // Bisection method: fast & precise
        float lower = mMinTextSize;
        float upper = mMaxTextSize;
        int loop_counter=1;
        targetTextSize = (lower+upper)/2;
        textHeight = getTextHeight(text, textPaint, width, targetTextSize);
        while(loop_counter < BISECTION_LOOP_WATCH_DOG && upper - lower > 1) {
            targetTextSize = (lower+upper)/2;
            textHeight = getTextHeight(text, textPaint, width, targetTextSize);
            if(textHeight > height)
                upper = targetTextSize;
            else
                lower = targetTextSize;
            loop_counter++;
        }

        targetTextSize = lower;
        textHeight = getTextHeight(text, textPaint, width, targetTextSize);

        // If we had reached our minimum text size and still don't fit, append an ellipsis
        if(mAddEllipsis && targetTextSize == mMinTextSize && textHeight > height) {
            // Draw using a static layout
            // modified: use a copy of TextPaint for measuring
            TextPaint paintCopy = new TextPaint(textPaint);
            paintCopy.setTextSize(targetTextSize);
            StaticLayout layout = new StaticLayout(text, paintCopy, width, Alignment.ALIGN_NORMAL, mSpacingMult, mSpacingAdd, false);
            // Check that we have a least one line of rendered text
            if(layout.getLineCount() > 0) {
                // Since the line at the specific vertical position would be cut off,
                // we must trim up to the previous line
                int lastLine = layout.getLineForVertical(height) - 1;
                // If the text would not even fit on a single line, clear it
                if(lastLine < 0) {
                    setText("");
                }
                // Otherwise, trim to the previous line and add an ellipsis
                else {
                    int start = layout.getLineStart(lastLine);
                    int end = layout.getLineEnd(lastLine);
                    float lineWidth = layout.getLineWidth(lastLine);
                    float ellipseWidth = paintCopy.measureText(mEllipsis);

                    // Trim characters off until we have enough room to draw the ellipsis
                    while(width < lineWidth + ellipseWidth) {
                        lineWidth = paintCopy.measureText(text.subSequence(start, --end + 1).toString());
                    }
                    setText(text.subSequence(0, end) + mEllipsis);
                }
            }
        }

        // Some devices try to auto adjust line spacing, so force default line spacing
        // and invalidate the layout as a side effect
        setTextSize(TypedValue.COMPLEX_UNIT_PX, targetTextSize);
        setLineSpacing(mSpacingAdd, mSpacingMult);

        // Notify the listener if registered
        if(mTextResizeListener != null) {
            mTextResizeListener.onTextResize(this, oldTextSize, targetTextSize);
        }

        // Reset force resize flag
        mNeedsResize = false;
        return targetTextSize;
    }
    private float efficientTextSizeSearch(int width, int height) {
        if (!mEnableSizeCache) {
            resizeText(width, height);
        }
        int key = getText().toString().length();
        float size = mTextCachedSizes.get(key);
        if (size != 0) {
            return size;
        }
        size = resizeText(width, height);
        mTextCachedSizes.put(key, (int) size);
        return size;
    }
    private CharSequence getSingleLineHeight(String text, int NoOfChars) {
        length = text.length();
        CharSequence charSequence = "";
        if (text != null && length > 0) {
            charSequence = text.substring(0, NoOfChars);
        }
        return charSequence;
    }

    // Set the text size of the text paint object and use a static layout to
    // render text off screen before measuring
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
                Alignment.ALIGN_NORMAL, mSpacingMult, mSpacingAdd, true);
        return layout.getHeight();
    }

    // Interface for resize notifications
    public interface OnTextResizeListener {
        public void onTextResize(TextView textView, float oldSize, float newSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isSingleLine()) {
            canvas.drawText(getText().toString(), currentX, currentY, textPaint);
            this.invalidate();
        } else {
            super.onDraw(canvas);
        }
    }
}
