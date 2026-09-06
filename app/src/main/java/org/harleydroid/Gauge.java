// Copyright (c) 2010, Freddy Martens (http://atstechlab.wordpress.com),
// MindTheRobot (http://mindtherobot.com/blog/)  and contributors
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without modification,
// are permitted provided that the following conditions are met:
//
//	* Redistributions of source code must retain the above copyright notice,
//	  this list of conditions and the following disclaimer.
//	* Redistributions in binary form must reproduce the above copyright notice,
//	  this list of conditions and the following disclaimer in the documentation
//	  and/or other materials provided with the distribution.
//	* Neither the name of Ondrej Zara nor the names of its contributors may be used
//	  to endorse or promote products derived from this software without specific
//	  prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
// IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
// BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
// OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
// NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
// EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package org.harleydroid;

import org.harleydroid.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.content.res.TypedArray;

public final class Gauge extends View {

	private static final boolean D = false;
	private static final String TAG = Gauge.class.getSimpleName();

	// drawing tools
	private RectF rimRect;
	private Paint rimPaint;
	private Paint rimCirclePaint;

	private RectF faceRect;
	private Paint facePaint;
	private Paint rimShadowPaint;

	private Paint scalePaint;
	private Paint scaleTextPaint;
	private RectF scaleRect;

	private RectF valueRect;
	private RectF rangeRect;

	private RectF odoRect;

	private Paint rangeOkPaint;
	private Paint rangeWarningPaint;
	private Paint rangeErrorPaint;
	private Paint rangeAllPaint;

	private Paint valueOkPaint;
	private Paint valueWarningPaint;
	private Paint valueErrorPaint;
	private Paint valueAllPaint;

	private Paint unitPaint;

	private Paint lowerTitlePaint;
	private Paint upperTitlePaint;

	private Paint handPaint;
	private Path handPath;

	private Paint handScrewPaint;

	private Paint backgroundPaint;

	private Paint odoPaint;
	private Paint odoBackgroundPaint;
	private Paint odoFramePaint;
	private Paint accentRingPaint;
	private Paint faceHighlightPaint;
	private Paint unitSuffixPaint;
	private Paint logoPaint;
	/** Shared across Gauge instances — decoded once per process. */
	private static Bitmap sSharedLogo;
	private Bitmap logoBitmap;

	// end drawing tools

	private Bitmap background; // holds the cached static part

	// scale configuration
	// Values passed as property. Defaults are set here.
	private boolean showHand                 = false;
	private boolean showGauge                = false;
	private boolean showRange                = false;
	private boolean showOdo                  = false;

	private int totalNotches                 = 120; // Total number of notches on the scale.
	private int incrementPerLargeNotch       = 10;
	private int incrementPerSmallNotch       = 2;

	private int scaleColor                   = 0xffF5F0EA;
	private int scaleCenterValue             = 0; // the one in the top center (12 o'clock), this corresponds with -90 degrees
	private int scaleMinValue                = -90;
	private int scaleMaxValue                = 120;
	private float degreeMinValue             = 0;
	private float degreeMaxValue             = 0;

	private int rangeOkColor                 = 0x9f2ECC71;
	private int rangeOkMinValue              = scaleMinValue;
	private int rangeOkMaxValue              = 45;
	private float degreeOkMinValue           = 0;
	private float degreeOkMaxValue           = 0;

	private int rangeWarningColor            = 0x9fF5A623;
	private int rangeWarningMinValue         = rangeOkMaxValue;
	private int rangeWarningMaxValue         = 80;
	private float degreeWarningMinValue      = 0;
	private float degreeWarningMaxValue      = 0;

	private int rangeErrorColor              = 0x9fE74C3C;
	private int rangeErrorMinValue           = rangeWarningMaxValue;
	private int rangeErrorMaxValue           = 120;
	private float degreeErrorMinValue        = 0;
	private float degreeErrorMaxValue        = 0;

	private int odoColor                     = 0xffFF6B1E;
	private int odoBackgroundColor           = 0xff0A0A0A;

	private String lowerTitle                = "HarleyDroidEVO";
	private String upperTitle                = "";
	private String unitTitle                 = "\u2103";
	private String unitMain                  = "";
	private String unitSuffix                = "";

	// Fixed values.
	private static final float scalePosition = 0.10f;  // The distance from the rim to the scale
	private static final float valuePosition = 0.285f; // The distance from the rim to the ranges
	private static final float rangePosition = 0.122f; // The distance from the rim to the ranges
	private static final float rimSize       = 0.028f;

	private float degreesPerNotch            = 360.0f/totalNotches;
	private static final int centerDegrees   =  -90; // the one in the top center (12 o'clock), this corresponds with -90 degrees

	// hand dynamics
	private boolean dialInitialized         = false;
	private float currentValue              = scaleCenterValue;
	private float targetValue               = scaleCenterValue;
	private float targetOdoValue            = 0.0f;
	private float dialVelocity              = 0.0f;
	private float dialAcceleration          = 0.0f;
	private long lastDialMoveTime           = -1L;
	private boolean dialAnimating           = false;
	private String cachedOdoText            = "0";
	private final Rect odoTextBounds        = new Rect();
	private final Runnable dialAnimator     = new Runnable() {
		@Override
		public void run() {
			if (!advanceDial()) {
				dialAnimating = false;
				return;
			}
			invalidateIfVisible();
			postOnAnimation(this);
		}
	};


	public Gauge(Context context) {
		super(context);
		init(context, null);
	}

	public Gauge(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs);
	}

	public Gauge(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context, attrs);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
	}

	@Override
	protected void onDetachedFromWindow() {
		removeCallbacks(dialAnimator);
		dialAnimating = false;
		super.onDetachedFromWindow();
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		Bundle bundle = (Bundle) state;
		Parcelable superState;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			superState = bundle.getParcelable("superState", Parcelable.class);
		} else {
			superState = bundle.getParcelable("superState");
		}
		super.onRestoreInstanceState(superState);

		dialInitialized  = bundle.getBoolean("dialInitialized");
		currentValue     = bundle.getFloat("currentValue");
		targetValue       = bundle.getFloat("targetValue");
		dialVelocity     = bundle.getFloat("dialVelocity");
		dialAcceleration = bundle.getFloat("dialAcceleration");
		lastDialMoveTime = bundle.getLong("lastDialMoveTime");
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();

		Bundle state = new Bundle();
		state.putParcelable("superState", superState);
		state.putBoolean("dialInitialized", dialInitialized);
		state.putFloat("currentValue", currentValue);
		state.putFloat("targetValue", targetValue);
		state.putFloat("dialVelocity", dialVelocity);
		state.putFloat("dialAcceleration", dialAcceleration);
		state.putLong("lastDialMoveTime", lastDialMoveTime);
		return state;
	}

	private void init(Context context, AttributeSet attrs) {
		// Hardware layer: SOFTWARE + continuous invalidate was saturating the UI thread
		// and making taps feel laggy after the gauge redesign.
		setLayerType(LAYER_TYPE_HARDWARE, null);

		// Get the properties from the resource file.
		if (context != null && attrs != null){
			TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Dial);
			showRange              = a.getBoolean(R.styleable.Dial_showRange,          showRange);
			showGauge              = a.getBoolean(R.styleable.Dial_showGauge,          showGauge);
			showHand               = a.getBoolean(R.styleable.Dial_showHand,           showHand);
			showOdo                = a.getBoolean(R.styleable.Dial_showOdo,            showOdo);

			totalNotches           = a.getInt(R.styleable.Dial_totalNotches,           totalNotches);
			incrementPerLargeNotch = a.getInt(R.styleable.Dial_incrementPerLargeNotch, incrementPerLargeNotch);
			incrementPerSmallNotch = a.getInt(R.styleable.Dial_incrementPerSmallNotch, incrementPerSmallNotch);
			scaleCenterValue       = a.getInt(R.styleable.Dial_scaleCenterValue,       scaleCenterValue);
			scaleColor             = a.getInt(R.styleable.Dial_scaleColor,             scaleColor);
			scaleMinValue          = a.getInt(R.styleable.Dial_scaleMinValue,          scaleMinValue);
			scaleMaxValue          = a.getInt(R.styleable.Dial_scaleMaxValue,          scaleMaxValue);
			rangeOkColor           = a.getInt(R.styleable.Dial_rangeOkColor,           rangeOkColor);
			rangeOkMinValue        = a.getInt(R.styleable.Dial_rangeOkMinValue,        rangeOkMinValue);
			rangeOkMaxValue        = a.getInt(R.styleable.Dial_rangeOkMaxValue,        rangeOkMaxValue);
			rangeWarningColor      = a.getInt(R.styleable.Dial_rangeWarningColor,      rangeWarningColor);
			rangeWarningMinValue   = a.getInt(R.styleable.Dial_rangeWarningMinValue,   rangeWarningMinValue);
			rangeWarningMaxValue   = a.getInt(R.styleable.Dial_rangeWarningMaxValue,   rangeWarningMaxValue);
			rangeErrorColor        = a.getInt(R.styleable.Dial_rangeErrorColor,        rangeErrorColor);
			rangeErrorMinValue     = a.getInt(R.styleable.Dial_rangeErrorMinValue,     rangeErrorMinValue);
			rangeErrorMaxValue     = a.getInt(R.styleable.Dial_rangeErrorMaxValue,     rangeErrorMaxValue);
			odoColor               = a.getInt(R.styleable.Dial_odoColor,               odoColor);
			odoBackgroundColor     = a.getInt(R.styleable.Dial_odoBackgroundColor,     odoBackgroundColor);
			String unitTitle       = a.getString(R.styleable.Dial_unitTitle);
			String lowerTitle      = a.getString(R.styleable.Dial_lowerTitle);
			String upperTitle      = a.getString(R.styleable.Dial_upperTitle);
			if (unitTitle != null) this.unitTitle = unitTitle;
			if (lowerTitle != null) this.lowerTitle = lowerTitle;
			if (upperTitle != null) this.upperTitle = upperTitle;
			parseUnitTitle();
			a.recycle();
		} else {
			parseUnitTitle();
		}
		degreesPerNotch       = 360.0f/totalNotches;
		degreeMinValue        = valueToAngle(scaleMinValue)        + centerDegrees;
		degreeMaxValue        = valueToAngle(scaleMaxValue)        + centerDegrees;
		degreeOkMinValue      = valueToAngle(rangeOkMinValue)      + centerDegrees;
		degreeOkMaxValue      = valueToAngle(rangeOkMaxValue)      + centerDegrees;
		degreeWarningMinValue = valueToAngle(rangeWarningMinValue) + centerDegrees;
		degreeWarningMaxValue = valueToAngle(rangeWarningMaxValue) + centerDegrees;
		degreeErrorMinValue   = valueToAngle(rangeErrorMinValue)   + centerDegrees;
		degreeErrorMaxValue   = valueToAngle(rangeErrorMaxValue)   + centerDegrees;

		initDrawingTools(context);
	}

	private void parseUnitTitle() {
		unitMain = unitTitle != null ? unitTitle : "";
		unitSuffix = "";
		if (unitTitle == null) {
			return;
		}
		int idx = unitTitle.lastIndexOf(" x");
		if (idx > 0) {
			unitMain = unitTitle.substring(0, idx).trim();
			unitSuffix = unitTitle.substring(idx + 1).trim();
		}
	}

	private void initDrawingTools(Context context) {
		int accent = AppTheme.primary(context);
		odoColor = accent;

		rimRect = new RectF(0.0f, 0.0f, 1.0f, 1.0f);

		faceRect = new RectF();
		faceRect.set(rimRect.left  + rimSize, rimRect.top    + rimSize,
				rimRect.right - rimSize, rimRect.bottom - rimSize);

		scaleRect = new RectF();
		scaleRect.set(faceRect.left + scalePosition, faceRect.top + scalePosition,
				faceRect.right - scalePosition, faceRect.bottom - scalePosition);

		rangeRect = new RectF();
		rangeRect.set(faceRect.left  + rangePosition, faceRect.top    + rangePosition,
				faceRect.right - rangePosition, faceRect.bottom - rangePosition);

		valueRect = new RectF();
		valueRect.set(faceRect.left  + valuePosition, faceRect.top    + valuePosition,
				faceRect.right - valuePosition, faceRect.bottom - valuePosition);

		// Digital readout lower in the dial (above brand)
		odoRect = new RectF(0.28f, 0.62f, 0.72f, 0.755f);

		if (sSharedLogo == null || sSharedLogo.isRecycled()) {
			sSharedLogo = BitmapFactory.decodeResource(
					getContext().getResources(), R.drawable.ic_logo_mark);
		}
		logoBitmap = sSharedLogo;

		// Dark brushed-metal face: deep charcoal with cool specular highlight
		facePaint = new Paint();
		facePaint.setAntiAlias(true);
		facePaint.setStyle(Paint.Style.FILL);
		facePaint.setShader(new RadialGradient(
				0.42f, 0.38f, 0.72f,
				new int[] { 0xff3A3A3C, 0xff1C1C1E, 0xff0B0B0C, 0xff050505 },
				new float[] { 0f, 0.35f, 0.75f, 1f },
				Shader.TileMode.CLAMP));

		faceHighlightPaint = new Paint();
		faceHighlightPaint.setAntiAlias(true);
		faceHighlightPaint.setStyle(Paint.Style.STROKE);
		faceHighlightPaint.setStrokeWidth(0.012f);
		faceHighlightPaint.setColor(0x14FFFFFF);

		rimShadowPaint = new Paint();
		rimShadowPaint.setShader(new RadialGradient(0.5f, 0.5f, faceRect.width() / 2.0f,
				new int[] { 0x00000000, 0x00000000, 0x44000000 },
				new float[] { 0.88f, 0.96f, 1f },
				Shader.TileMode.CLAMP));
		rimShadowPaint.setStyle(Paint.Style.FILL);

		rimPaint = new Paint();
		rimPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
		rimPaint.setShader(new LinearGradient(0.20f, 0.0f, 0.80f, 1.0f,
				Color.rgb(0x2A, 0x2A, 0x2C),
				Color.rgb(0x12, 0x12, 0x14),
				Shader.TileMode.CLAMP));

		rimCirclePaint = new Paint();
		rimCirclePaint.setAntiAlias(true);
		rimCirclePaint.setStyle(Paint.Style.STROKE);
		rimCirclePaint.setColor(Color.argb(0x33, 0x20, 0x20, 0x22));
		rimCirclePaint.setStrokeWidth(0.004f);

		accentRingPaint = new Paint();
		accentRingPaint.setAntiAlias(true);
		accentRingPaint.setStyle(Paint.Style.STROKE);
		accentRingPaint.setColor(accent);
		accentRingPaint.setStrokeWidth(0.022f);

		scalePaint = new Paint();
		scalePaint.setStyle(Paint.Style.STROKE);
		scalePaint.setColor(scaleColor);
		scalePaint.setStrokeWidth(0.010f);
		scalePaint.setAntiAlias(true);

		scaleTextPaint = new Paint();
		scaleTextPaint.setStyle(Paint.Style.FILL);
		scaleTextPaint.setColor(scaleColor);
		scaleTextPaint.setAntiAlias(true);
		scaleTextPaint.setTextSize(0.052f);
		scaleTextPaint.setStrokeWidth(0f);
		scaleTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
		scaleTextPaint.setTextAlign(Paint.Align.CENTER);
		scaleTextPaint.setFakeBoldText(false);

		rangeOkPaint = new Paint();
		rangeOkPaint.setStyle(Paint.Style.STROKE);
		rangeOkPaint.setColor(rangeOkColor);
		rangeOkPaint.setStrokeWidth(0.014f);
		rangeOkPaint.setAntiAlias(true);

		rangeWarningPaint = new Paint();
		rangeWarningPaint.setStyle(Paint.Style.STROKE);
		rangeWarningPaint.setColor(rangeWarningColor);
		rangeWarningPaint.setStrokeWidth(0.014f);
		rangeWarningPaint.setAntiAlias(true);

		rangeErrorPaint = new Paint();
		rangeErrorPaint.setStyle(Paint.Style.STROKE);
		rangeErrorPaint.setColor(rangeErrorColor);
		rangeErrorPaint.setStrokeWidth(0.014f);
		rangeErrorPaint.setAntiAlias(true);

		rangeAllPaint = new Paint();
		rangeAllPaint.setStyle(Paint.Style.STROKE);
		rangeAllPaint.setColor(0x55FFFFFF);
		rangeAllPaint.setStrokeWidth(0.014f);
		rangeAllPaint.setAntiAlias(true);

		valueOkPaint = new Paint();
		valueOkPaint.setStyle(Paint.Style.STROKE);
		valueOkPaint.setColor(rangeOkColor);
		valueOkPaint.setStrokeWidth(0.20f);
		valueOkPaint.setAntiAlias(true);

		valueWarningPaint = new Paint();
		valueWarningPaint.setStyle(Paint.Style.STROKE);
		valueWarningPaint.setColor(rangeWarningColor);
		valueWarningPaint.setStrokeWidth(0.20f);
		valueWarningPaint.setAntiAlias(true);

		valueErrorPaint = new Paint();
		valueErrorPaint.setStyle(Paint.Style.STROKE);
		valueErrorPaint.setColor(rangeErrorColor);
		valueErrorPaint.setStrokeWidth(0.20f);
		valueErrorPaint.setAntiAlias(true);

		valueAllPaint = new Paint();
		valueAllPaint.setStyle(Paint.Style.STROKE);
		valueAllPaint.setColor(0x55FFFFFF);
		valueAllPaint.setStrokeWidth(0.20f);
		valueAllPaint.setAntiAlias(true);

		unitPaint = new Paint();
		unitPaint.setStyle(Paint.Style.FILL);
		unitPaint.setColor(0xffF5F0EA);
		unitPaint.setAntiAlias(true);
		unitPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
		unitPaint.setTextAlign(Paint.Align.CENTER);
		unitPaint.setTextSize(0.048f);
		unitPaint.setLetterSpacing(0.06f);

		unitSuffixPaint = new Paint();
		unitSuffixPaint.setStyle(Paint.Style.FILL);
		unitSuffixPaint.setColor(accent);
		unitSuffixPaint.setAntiAlias(true);
		unitSuffixPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
		unitSuffixPaint.setTextAlign(Paint.Align.CENTER);
		unitSuffixPaint.setTextSize(0.028f);
		unitSuffixPaint.setLetterSpacing(0.04f);

		upperTitlePaint = new Paint();
		upperTitlePaint.setStyle(Paint.Style.FILL);
		upperTitlePaint.setColor(0xffCCCCCC);
		upperTitlePaint.setAntiAlias(true);
		upperTitlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
		upperTitlePaint.setTextAlign(Paint.Align.CENTER);
		upperTitlePaint.setTextSize(0.040f);

		lowerTitlePaint = new Paint();
		lowerTitlePaint.setStyle(Paint.Style.FILL);
		lowerTitlePaint.setColor(accent);
		lowerTitlePaint.setAntiAlias(true);
		lowerTitlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
		lowerTitlePaint.setTextAlign(Paint.Align.CENTER);
		lowerTitlePaint.setTextSize(0.032f);
		lowerTitlePaint.setLetterSpacing(0.08f);

		logoPaint = new Paint();
		logoPaint.setAntiAlias(true);
		logoPaint.setFilterBitmap(true);
		logoPaint.setAlpha(210);

		handPaint = new Paint();
		handPaint.setAntiAlias(true);
		handPaint.setColor(0xffE8E0D8);
		// No setShadowLayer — requires SOFTWARE layers and tanks frame time.
		handPaint.setStyle(Paint.Style.FILL);

		handScrewPaint = new Paint();
		handScrewPaint.setAntiAlias(true);
		handScrewPaint.setColor(accent);
		handScrewPaint.setStyle(Paint.Style.FILL);

		backgroundPaint = new Paint();
		backgroundPaint.setFilterBitmap(true);

		handPath = new Path();
		handPath.moveTo(0.5f, 0.5f + 0.2f);
		handPath.lineTo(0.5f - 0.010f, 0.5f + 0.2f - 0.007f);
		handPath.lineTo(0.5f - 0.002f, 0.5f - 0.40f);
		handPath.lineTo(0.5f + 0.002f, 0.5f - 0.40f);
		handPath.lineTo(0.5f + 0.010f, 0.5f + 0.2f - 0.007f);
		handPath.lineTo(0.5f, 0.5f + 0.2f);
		handPath.addCircle(0.5f, 0.5f, 0.025f, Path.Direction.CW);

		odoPaint = new Paint();
		Typeface lcd = Typeface.MONOSPACE;
		try {
			lcd = Typeface.createFromAsset(context.getAssets(), "fonts/digital-7-mono.ttf");
		} catch (RuntimeException e) {
			Log.w(TAG, "LCD font missing, using monospace", e);
		}
		odoPaint.setStyle(Paint.Style.FILL);
		odoPaint.setColor(odoColor);
		odoPaint.setAntiAlias(true);
		odoPaint.setTextSize(0.090f);
		odoPaint.setTypeface(lcd);
		odoPaint.setTextAlign(Paint.Align.CENTER);

		odoBackgroundPaint = new Paint();
		odoBackgroundPaint.setStyle(Paint.Style.FILL);
		odoBackgroundPaint.setColor(odoBackgroundColor);
		odoBackgroundPaint.setAntiAlias(true);

		odoFramePaint = new Paint();
		odoFramePaint.setStyle(Paint.Style.STROKE);
		odoFramePaint.setColor(accent);
		odoFramePaint.setStrokeWidth(0.010f);
		odoFramePaint.setAntiAlias(true);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (D) Log.d(TAG, "Width spec: " + MeasureSpec.toString(widthMeasureSpec));
		if (D) Log.d(TAG, "Height spec: " + MeasureSpec.toString(heightMeasureSpec));

		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);

		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		int chosenWidth = chooseDimension(widthMode, widthSize);
		int chosenHeight = chooseDimension(heightMode, heightSize);

		// Always square: the dial is drawn in a 1×1 normalized space.
		// Parent layouts must use full-size weight cells (0dp + weight) so
		// leftover space is not packed to the start when we report a smaller size.
		int chosenDimension = Math.min(chosenWidth, chosenHeight);

		setMeasuredDimension(chosenDimension, chosenDimension);
	}

	private int chooseDimension(int mode, int size) {
		if (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY) {
			return size;
		} else { // (mode == MeasureSpec.UNSPECIFIED)
			return getPreferredSize();
		}
	}

	// in case there is no size specified
	private int getPreferredSize() {
		return 250;
	}

	private void drawRim(Canvas canvas) {
		canvas.drawOval(rimRect, rimPaint);
		// Clean orange accent ring only — no pale halo
		RectF accent = new RectF(
				rimRect.left + 0.012f, rimRect.top + 0.012f,
				rimRect.right - 0.012f, rimRect.bottom - 0.012f);
		canvas.drawOval(accent, accentRingPaint);
	}

	private void drawFace(Canvas canvas) {
		canvas.drawOval(faceRect, facePaint);
		// Soft inner shadow only (no whitish rim that covers scale digits)
		canvas.drawOval(faceRect, rimShadowPaint);
	}

	private void drawCenterLogo(Canvas canvas) {
		if (logoBitmap == null) {
			return;
		}
		float size = 0.14f;
		RectF dst = new RectF(0.5f - size / 2f, 0.5f - size / 2f,
				0.5f + size / 2f, 0.5f + size / 2f);
		canvas.drawBitmap(logoBitmap, null, dst, logoPaint);
	}


	private void drawBackground(Canvas canvas) {
		if (background == null) {
			Log.w(TAG, "Background not created");
		} else {
			canvas.drawBitmap(background, 0, 0, backgroundPaint);
		}
	}

	private void drawScale(Canvas canvas) {
		// Ticks + ring only. Labels are drawn in pixel space (see drawScaleLabelsPx).
		canvas.drawOval(scaleRect, scalePaint);

		canvas.save();
		for (int i = 0; i < totalNotches; ++i) {
			float y1 = scaleRect.top;
			float y2 = y1 - 0.015f;
			float y3 = y1 - 0.025f;

			int value = notchToValue(i);

			if (i % (incrementPerLargeNotch / incrementPerSmallNotch) == 0) {
				if (value >= scaleMinValue && value <= scaleMaxValue) {
					// Thicker major ticks for readability
					float saved = scalePaint.getStrokeWidth();
					scalePaint.setStrokeWidth(0.014f);
					canvas.drawLine(0.5f, y1, 0.5f, y3 - 0.008f, scalePaint);
					scalePaint.setStrokeWidth(saved);
				}
			} else if (value >= scaleMinValue && value <= scaleMaxValue) {
				canvas.drawLine(0.5f, y1, 0.5f, y2, scalePaint);
			}

			canvas.rotate(degreesPerNotch, 0.5f, 0.5f);
		}
		canvas.restore();
	}

	private void drawScaleRanges(Canvas canvas) {
		canvas.save();
		canvas.drawArc(rangeRect, degreeMinValue,        degreeMaxValue -        degreeMinValue,        false, rangeAllPaint);
		canvas.drawArc(rangeRect, degreeOkMinValue,      degreeOkMaxValue      - degreeOkMinValue,      false, rangeOkPaint);
		canvas.drawArc(rangeRect, degreeWarningMinValue, degreeWarningMaxValue - degreeWarningMinValue, false, rangeWarningPaint);
		canvas.drawArc(rangeRect, degreeErrorMinValue,   degreeErrorMaxValue   - degreeErrorMinValue,   false, rangeErrorPaint);
		canvas.restore();
	}

	/**
	 * Scale numerals in pixel space, kept upright and optically centered on each major tick.
	 */
	private void drawScaleLabelsPx(Canvas canvas, float s) {
		float savedSize = scaleTextPaint.getTextSize();
		scaleTextPaint.setTextSize(savedSize * s);

		Paint.FontMetrics fm = scaleTextPaint.getFontMetrics();
		float textMid = (fm.ascent + fm.descent) / 2f;
		// Slightly outside the major tick tips so digits clear the white notches
		float rNorm = 0.5f - (scaleRect.top - 0.070f);
		float cx = 0.5f * s;
		float cy = 0.5f * s;
		float r = rNorm * s;

		for (int i = 0; i < totalNotches; ++i) {
			int value = notchToValue(i);
			if (i % (incrementPerLargeNotch / incrementPerSmallNotch) != 0) {
				continue;
			}
			if (value < scaleMinValue || value > scaleMaxValue) {
				continue;
			}
			// Notch 0 = 12 o'clock; canvas rotation is clockwise
			double rad = Math.toRadians(-90.0 + i * degreesPerNotch);
			float x = cx + (float) (r * Math.cos(rad));
			float y = cy + (float) (r * Math.sin(rad)) - textMid;
			canvas.drawText(Integer.toString(value), x, y, scaleTextPaint);
		}
		scaleTextPaint.setTextSize(savedSize);
	}

	private void drawTitlesPx(Canvas canvas, float s) {
		if (upperTitle != null && upperTitle.length() > 0) {
			drawPxTextCentered(canvas, upperTitle, 0.5f, 0.28f, upperTitlePaint, s);
		}
		if (unitMain != null && unitMain.length() > 0) {
			drawPxTextCentered(canvas, unitMain, 0.5f, 0.325f, unitPaint, s);
		}
		if (unitSuffix != null && unitSuffix.length() > 0) {
			drawPxTextCentered(canvas, unitSuffix, 0.5f, 0.358f, unitSuffixPaint, s);
		}
		if (lowerTitle != null && lowerTitle.length() > 0) {
			float y = showOdo ? 0.825f : 0.72f;
			drawPxTextCentered(canvas, lowerTitle, 0.5f, y, lowerTitlePaint, s);
		}
	}

	private void drawPxText(Canvas canvas, String text, float nx, float ny, Paint paint, float s) {
		float saved = paint.getTextSize();
		paint.setTextSize(saved * s);
		canvas.drawText(text, nx * s, ny * s, paint);
		paint.setTextSize(saved);
	}

	/** Draw text centered on (nx, ny) in normalized dial coordinates. */
	private void drawPxTextCentered(Canvas canvas, String text, float nx, float ny, Paint paint, float s) {
		float saved = paint.getTextSize();
		Paint.Align savedAlign = paint.getTextAlign();
		paint.setTextSize(saved * s);
		paint.setTextAlign(Paint.Align.CENTER);
		Paint.FontMetrics fm = paint.getFontMetrics();
		float baseline = ny * s - (fm.ascent + fm.descent) / 2f;
		canvas.drawText(text, nx * s, baseline, paint);
		paint.setTextSize(saved);
		paint.setTextAlign(savedAlign);
	}

	/** Frame + digits in the same pixel space so they stay aligned. */
	private void drawOdoPx(Canvas canvas, float s) {
		// Frame
		RectF box = new RectF(
				odoRect.left * s, odoRect.top * s,
				odoRect.right * s, odoRect.bottom * s);
		float radius = 0.02f * s;
		float strokeSaved = odoFramePaint.getStrokeWidth();
		odoFramePaint.setStrokeWidth(Math.max(2f, strokeSaved * s));
		canvas.drawRoundRect(box, radius, radius, odoBackgroundPaint);
		canvas.drawRoundRect(box, radius, radius, odoFramePaint);
		odoFramePaint.setStrokeWidth(strokeSaved);

		// Digits: center on actual glyph ink (Digital-7 has uneven metrics)
		String text = cachedOdoText;
		float saved = odoPaint.getTextSize();
		Paint.Align savedAlign = odoPaint.getTextAlign();
		odoPaint.setTextSize(saved * s);
		odoPaint.setTextAlign(Paint.Align.LEFT);
		odoPaint.getTextBounds(text, 0, text.length(), odoTextBounds);
		float cx = box.centerX();
		float cy = box.centerY();
		float x = cx - odoTextBounds.exactCenterX();
		float baseline = cy - odoTextBounds.exactCenterY();
		canvas.drawText(text, x, baseline, odoPaint);
		odoPaint.setTextSize(saved);
		odoPaint.setTextAlign(savedAlign);
	}

	private void drawHand(Canvas canvas) {
		if (dialInitialized) {
			float angle = valueToAngle(currentValue);
			canvas.save();
			canvas.rotate(angle, 0.5f, 0.5f);
			canvas.drawPath(handPath, handPaint);
			canvas.restore();

			canvas.drawCircle(0.5f, 0.5f, 0.01f, handScrewPaint);
		}
	}

	private void drawGauge(Canvas canvas) {
		if (dialInitialized) {
			// When currentValue is not rotated, the tip of the hand points
			// to n -90 degrees.
			float angle = valueToAngle(currentValue) - 90;

			if(targetValue <= rangeOkMaxValue){
				canvas.drawArc(valueRect, degreeMinValue, angle - degreeMinValue, false, valueOkPaint);
			}
			if((targetValue > rangeOkMaxValue) && (targetValue <= rangeWarningMaxValue)){
				canvas.drawArc(valueRect, degreeMinValue, degreeOkMaxValue - degreeMinValue, false, valueOkPaint);
				canvas.drawArc(valueRect, degreeWarningMinValue, angle - degreeWarningMinValue, false, valueWarningPaint);
			}
			if((targetValue > rangeWarningMaxValue) && (targetValue <= rangeErrorMaxValue)){
				canvas.drawArc(valueRect, degreeMinValue, degreeOkMaxValue - degreeMinValue, false, valueOkPaint);
				canvas.drawArc(valueRect, degreeWarningMinValue, degreeWarningMaxValue - degreeWarningMinValue, false, valueWarningPaint);
				canvas.drawArc(valueRect, degreeErrorMinValue, angle - degreeErrorMinValue, false, valueErrorPaint);
			}
		}
	}

	private void drawBezel(Canvas canvas) {
		// Draw the bevel in which the value is draw.
		canvas.save();
		canvas.drawArc(valueRect, degreeMinValue, degreeMaxValue - degreeMinValue, false, valueAllPaint);
		canvas.restore();
	}

	/* Translate a notch to a value for the scale.
	 * The notches are evenly spread across the scale, half of the notches on the left hand side
	 * and the other half on the right hand side.
	 * The raw value calculation uses a constant so that each notch represents a value n + 2.
	 */
	private int notchToValue(int notch) {
		int rawValue = ((notch < totalNotches / 2) ? notch : (notch - totalNotches)) * incrementPerSmallNotch;
		int shiftedValue = rawValue + scaleCenterValue;
		return shiftedValue;
	}

	private float valueToAngle(float value) {
		// scaleCenterValue represents an angle of -90 degrees.
		return (value - scaleCenterValue) / incrementPerSmallNotch * degreesPerNotch;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		drawBackground(canvas);

		float scale = (float) getWidth();
		if (showGauge) {
			canvas.save();
			canvas.scale(scale, scale);
			drawGauge(canvas);
			canvas.restore();
		}

		// Digital readout under the needle
		if (showOdo) {
			drawOdoPx(canvas, scale);
		}

		// Needle on top of digital frame
		if (showHand) {
			canvas.save();
			canvas.scale(scale, scale);
			drawHand(canvas);
			canvas.restore();
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		if (D) Log.d(TAG, "Size changed to " + w + "x" + h);
		regenerateBackground();
	}

	private void regenerateBackground() {
		if (getWidth() <= 0 || getHeight() <= 0) {
			return;
		}
		if (background != null) {
			background.recycle();
		}

		background = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
		Canvas backgroundCanvas = new Canvas(background);
		float scale = (float) getWidth();

		backgroundCanvas.save();
		backgroundCanvas.scale(scale, scale);
		drawRim(backgroundCanvas);
		drawFace(backgroundCanvas);
		drawCenterLogo(backgroundCanvas);
		drawScale(backgroundCanvas);
		if (showRange) {
			drawScaleRanges(backgroundCanvas);
		}
		if (showGauge) {
			drawBezel(backgroundCanvas);
		}
		backgroundCanvas.restore();

		// Labels after restore — full pixel advances, no stacking
		drawScaleLabelsPx(backgroundCanvas, scale);
		drawTitlesPx(backgroundCanvas, scale);
	}

	/** @return true if the needle still needs another frame. */
	private boolean advanceDial() {
		if (Math.abs(currentValue - targetValue) <= 0.01f) {
			currentValue = targetValue;
			dialVelocity = 0.0f;
			dialAcceleration = 0.0f;
			lastDialMoveTime = -1L;
			return false;
		}

		if (lastDialMoveTime == -1L) {
			lastDialMoveTime = System.currentTimeMillis();
			return true;
		}

		long currentTime = System.currentTimeMillis();
		float delta = (currentTime - lastDialMoveTime) / 1000.0f;
		if (delta <= 0f) {
			return true;
		}
		// Cap dt so a stalled frame does not fling the needle.
		if (delta > 0.05f) {
			delta = 0.05f;
		}

		float direction = Math.signum(targetValue - currentValue);
		if (Math.abs(dialVelocity) < 90.0f) {
			dialAcceleration = 5.0f * (targetValue - currentValue);
		} else {
			dialAcceleration = 0.0f;
		}
		currentValue += dialVelocity * delta;
		dialVelocity += dialAcceleration * delta;
		if ((targetValue - currentValue) * direction < 0.01f * direction) {
			currentValue = targetValue;
			dialVelocity = 0.0f;
			dialAcceleration = 0.0f;
			lastDialMoveTime = -1L;
			return false;
		}
		lastDialMoveTime = currentTime;
		return true;
	}

	private void startDialAnimation() {
		if (dialAnimating || getVisibility() != VISIBLE) {
			return;
		}
		dialAnimating = true;
		postOnAnimation(dialAnimator);
	}

	private void stopDialAnimation() {
		if (dialAnimating) {
			removeCallbacks(dialAnimator);
			dialAnimating = false;
		}
		lastDialMoveTime = -1L;
	}

	private void invalidateIfVisible() {
		if (getVisibility() == VISIBLE) {
			invalidate();
		}
	}

	private void updateCachedOdoText(float value) {
		cachedOdoText = String.format(java.util.Locale.US, "%.0f", value);
	}

	public void setValue(float value) {
		if (value < scaleMinValue) value = scaleMinValue;
		else if (value > scaleMaxValue) value = scaleMaxValue;

		if (dialInitialized && Math.abs(targetValue - value) < 0.001f) {
			return;
		}
		targetValue = value;
		dialInitialized = true;
		invalidateIfVisible();
		startDialAnimation();
	}

	/** Jump the needle immediately (cluster self-test / reset). */
	public void setValueImmediate(float value) {
		if (value < scaleMinValue) value = scaleMinValue;
		else if (value > scaleMaxValue) value = scaleMaxValue;
		stopDialAnimation();
		currentValue = value;
		targetValue = value;
		dialVelocity = 0.0f;
		dialAcceleration = 0.0f;
		dialInitialized = true;
		invalidateIfVisible();
	}

	public float getValue() {
		return targetValue;
	}

	public void setOdoValue(float value) {
		if (Math.abs(targetOdoValue - value) < 0.01f) {
			return;
		}
		targetOdoValue = value;
		updateCachedOdoText(value);
		invalidateIfVisible();
	}

	/** Needle + odo in one invalidate (avoids double redraw per telemetry tick). */
	public void setNeedleAndOdo(float needle, float odo) {
		if (needle < scaleMinValue) needle = scaleMinValue;
		else if (needle > scaleMaxValue) needle = scaleMaxValue;

		boolean needleChanged = !dialInitialized || Math.abs(targetValue - needle) >= 0.001f;
		boolean odoChanged = Math.abs(targetOdoValue - odo) >= 0.01f;
		if (!needleChanged && !odoChanged) {
			return;
		}

		if (needleChanged) {
			targetValue = needle;
			dialInitialized = true;
			if (getVisibility() == VISIBLE) {
				startDialAnimation();
			} else {
				// Keep hidden unit gauge in sync without scheduling frames
				stopDialAnimation();
				currentValue = needle;
				dialVelocity = 0.0f;
				dialAcceleration = 0.0f;
			}
		}
		if (odoChanged) {
			targetOdoValue = odo;
			updateCachedOdoText(odo);
		}
		invalidateIfVisible();
	}

	/** Immediate needle + odo (self-test sweep). */
	public void setNeedleAndOdoImmediate(float needle, float odo) {
		if (needle < scaleMinValue) needle = scaleMinValue;
		else if (needle > scaleMaxValue) needle = scaleMaxValue;
		stopDialAnimation();
		currentValue = needle;
		targetValue = needle;
		dialVelocity = 0.0f;
		dialAcceleration = 0.0f;
		dialInitialized = true;
		if (Math.abs(targetOdoValue - odo) >= 0.01f) {
			targetOdoValue = odo;
			updateCachedOdoText(odo);
		}
		invalidateIfVisible();
	}
}
