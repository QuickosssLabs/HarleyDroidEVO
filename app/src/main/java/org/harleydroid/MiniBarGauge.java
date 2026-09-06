package org.harleydroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * Thin vertical bar gauge (fuel / temp) between the dials.
 */
public final class MiniBarGauge extends View {

	private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF trackRect = new RectF();
	private final RectF fillRect = new RectF();

	private String label = "";
	private String unit = "";
	private String valueText = "—";
	private float progress = 0f; // 0..1
	private int accentColor = 0xffFF6B1E;
	private int fillColor = 0xff2ECC71;
	private int warnColor = 0xffF5A623;
	private int errorColor = 0xffE74C3C;
	private float warnAt = 0.75f;
	private float errorAt = 0.90f;
	private boolean invertColors = false;

	private int cachedFillColor = 0;
	private float cachedFillTop = Float.NaN;
	private float cachedFillBottom = Float.NaN;
	private boolean suppressInvalidate = false;

	public MiniBarGauge(Context context) {
		super(context);
		init();
	}

	public MiniBarGauge(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public MiniBarGauge(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	private void init() {
		accentColor = AppTheme.primary(getContext());
		trackPaint.setStyle(Paint.Style.FILL);
		trackPaint.setColor(0xff2A2A2A);

		fillPaint.setStyle(Paint.Style.FILL);

		framePaint.setStyle(Paint.Style.STROKE);
		framePaint.setStrokeWidth(dp(1.5f));
		framePaint.setColor(accentColor);

		labelPaint.setColor(0xAAF2F2F2);
		labelPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
		labelPaint.setTextAlign(Paint.Align.CENTER);
		labelPaint.setLetterSpacing(0.04f);

		valuePaint.setColor(0xffF2F2F2);
		valuePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
		valuePaint.setTextAlign(Paint.Align.CENTER);
		valuePaint.setFakeBoldText(true);
	}

	/** Batch several setters without one invalidate each. */
	public void beginBatchUpdate() {
		suppressInvalidate = true;
	}

	public void endBatchUpdate() {
		suppressInvalidate = false;
		invalidate();
	}

	private void requestDraw() {
		if (!suppressInvalidate) {
			invalidate();
		}
	}

	public void setLabel(String label) {
		String next = label != null ? label.toUpperCase() : "";
		if (next.equals(this.label)) {
			return;
		}
		this.label = next;
		requestDraw();
	}

	public void setUnit(String unit) {
		String next = unit != null ? unit : "";
		if (next.equals(this.unit)) {
			return;
		}
		this.unit = next;
		requestDraw();
	}

	public void setAccentColor(int color) {
		if (accentColor == color) {
			return;
		}
		accentColor = color;
		framePaint.setColor(color);
		requestDraw();
	}

	public void setThresholds(float warnAt, float errorAt, boolean invertColors) {
		if (this.warnAt == warnAt && this.errorAt == errorAt && this.invertColors == invertColors) {
			return;
		}
		this.warnAt = warnAt;
		this.errorAt = errorAt;
		this.invertColors = invertColors;
		requestDraw();
	}

	public void setValue(float value, float max, String displayText) {
		float nextProgress;
		if (max <= 0f) {
			nextProgress = 0f;
		} else {
			nextProgress = Math.max(0f, Math.min(1f, value / max));
		}
		String nextText = displayText != null ? displayText : "—";
		if (Math.abs(progress - nextProgress) < 0.001f && nextText.equals(valueText)) {
			return;
		}
		progress = nextProgress;
		valueText = nextText;
		requestDraw();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int desiredW = (int) dp(40);
		int w = resolveSize(desiredW, widthMeasureSpec);
		int h = MeasureSpec.getSize(heightMeasureSpec);
		if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
			h = (int) dp(120);
		}
		setMeasuredDimension(w, h);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		float w = getWidth();
		float h = getHeight();
		float cx = w / 2f;

		valuePaint.setTextSize(sp(11));
		labelPaint.setTextSize(sp(9));

		boolean hasValue = valueText != null && valueText.length() > 0 && !valueText.equals("—");
		float topY;
		if (hasValue) {
			float valueBaseline = dp(12);
			String top = valueText;
			if (unit.length() > 0 && valueText.length() <= 4) {
				top = valueText + unit;
			}
			canvas.drawText(top, cx, valueBaseline, valuePaint);
			topY = valueBaseline + dp(6);
		} else {
			topY = dp(8);
		}

		float labelBaseline = h - dp(4);
		String shortLabel = shortLabel(label);
		canvas.drawText(shortLabel, cx, labelBaseline, labelPaint);

		float barWidth = Math.min(dp(14), w - dp(12));
		float bottomY = labelBaseline - dp(10);
		if (bottomY - topY < dp(24)) {
			bottomY = topY + dp(24);
		}

		trackRect.set(cx - barWidth / 2f, topY, cx + barWidth / 2f, bottomY);
		trackPaint.setColor(0xff1A1A1A);
		canvas.drawRoundRect(trackRect, dp(7), dp(7), trackPaint);
		framePaint.setColor(accentColor);
		canvas.drawRoundRect(trackRect, dp(7), dp(7), framePaint);

		float fillH = trackRect.height() * progress;
		if (fillH > dp(2)) {
			fillRect.set(trackRect.left, trackRect.bottom - fillH, trackRect.right, trackRect.bottom);
			int color = colorForProgress(progress);
			ensureFillShader(color, fillRect.top, fillRect.bottom);
			canvas.drawRoundRect(fillRect, dp(7), dp(7), fillPaint);
		}
	}

	private void ensureFillShader(int color, float top, float bottom) {
		if (fillPaint.getShader() != null
				&& cachedFillColor == color
				&& cachedFillTop == top
				&& cachedFillBottom == bottom) {
			return;
		}
		cachedFillColor = color;
		cachedFillTop = top;
		cachedFillBottom = bottom;
		fillPaint.setShader(new LinearGradient(
				0, bottom, 0, top,
				color, lighten(color),
				Shader.TileMode.CLAMP));
	}

	private static String shortLabel(String label) {
		if (label == null || label.length() == 0) {
			return "";
		}
		// Prefer first word truncated for FR "Carburant" / "Température"
		if (label.length() <= 5) {
			return label;
		}
		return label.substring(0, 4);
	}

	private int colorForProgress(float p) {
		if (invertColors) {
			if (p <= (1f - errorAt)) return errorColor;
			if (p <= (1f - warnAt)) return warnColor;
			return fillColor;
		}
		if (p >= errorAt) return errorColor;
		if (p >= warnAt) return warnColor;
		return fillColor;
	}

	private static int lighten(int color) {
		int a = (color >> 24) & 0xff;
		int r = Math.min(255, ((color >> 16) & 0xff) + 40);
		int g = Math.min(255, ((color >> 8) & 0xff) + 40);
		int b = Math.min(255, (color & 0xff) + 40);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private float dp(float v) {
		return v * getResources().getDisplayMetrics().density;
	}

	private float sp(float v) {
		return TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
	}
}
