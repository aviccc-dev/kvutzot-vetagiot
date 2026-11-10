package com.aviccc.kvutzot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.aviccc.kvutzot.model.MapLayout;
import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.Palette;

import java.util.HashMap;
import java.util.Map;

/** המפה הגרפית (גרסה 0.2: קריאה בלבד): תיבות, קווים, צבעים; הזזה באצבע, הגדלה בשתי אצבעות. */
public class MapView extends View {
    private final float dens;
    private final TextPaint textPaint, labelPaint, markPaint;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DashPathEffect dash;
    private final float pad, maxW, margin;
    private MapLayout layout;
    private final Map<MapLayout.Node, StaticLayout> texts = new HashMap<>();
    private final Map<MapLayout.Node, Float> markW = new HashMap<>();
    private float scale = 1f, tx, ty;
    private boolean needFit = true;
    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;
    private final RectF r = new RectF();
    private final Path path = new Path();

    public MapView(Context c) {
        super(c);
        dens = getResources().getDisplayMetrics().density;
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(sp(15));
        labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setTextSize(sp(12));
        labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        markPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        markPaint.setTextSize(sp(10));
        stroke.setStyle(Paint.Style.STROKE);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.4f * dens);
        line.setColor(0xFFB4B2A9);
        dash = new DashPathEffect(new float[]{5 * dens, 4 * dens}, 0);
        pad = 8 * dens;
        maxW = 150 * dens;
        margin = 24 * dens;
        setBackgroundColor(0xFFFFFFFF);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float ns = Math.max(.25f, Math.min(4f, scale * d.getScaleFactor()));
                float f = ns / scale;
                tx = d.getFocusX() - (d.getFocusX() - tx) * f;
                ty = d.getFocusY() - (d.getFocusY() - ty) * f;
                scale = ns;
                invalidate();
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                tx -= dx; ty -= dy; invalidate(); return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) { fit(); return true; }
        });
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    public void setProject(Project p, boolean vertical, boolean refit) {
        texts.clear();
        markW.clear();
        layout = MapLayout.build(p, vertical, new MapLayout.Measurer() {
            public float[] measure(MapLayout.Node n) {
                TextPaint tp = n.isDivision() ? labelPaint : textPaint;
                String t = label(n);
                float want = Math.min(maxW, (float) Math.ceil(Layout.getDesiredWidth(t, tp)));
                StaticLayout sl = new StaticLayout(t, tp, Math.max(1, (int) Math.ceil(want)),
                        Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
                texts.put(n, sl);
                float mw = n.mark.isEmpty() ? 0 : markPaint.measureText(n.mark) + 5 * dens;
                markW.put(n, mw);
                float vp = n.isDivision() ? pad * .5f : pad;
                return new float[]{sl.getWidth() + 2 * pad + mw, sl.getHeight() + 2 * vp};
            }
        }, 26 * dens, 10 * dens);
        if (refit) needFit = true;
        invalidate();
    }

    private static String label(MapLayout.Node n) {
        String t = n.text.isEmpty() ? "(בלי שם)" : n.text;
        return n.hidden > 0 ? t + "  ▸ " + n.hidden : t;
    }

    /** מקטין או מגדיל כך שכל המפה נראית; לא מגדיל מעבר לגודל טבעי. */
    public void fit() {
        if (layout == null || getWidth() == 0) return;
        float w = layout.width + 2 * margin, h = layout.height + 2 * margin;
        scale = Math.max(.25f, Math.min(1f, Math.min(getWidth() / w, getHeight() / h)));
        tx = (getWidth() - w * scale) / 2;
        ty = layout.vertical ? 0 : (getHeight() - h * scale) / 2;
        if (ty < 0) ty = 0;
        needFit = false;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        needFit = true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        if (!scaler.isInProgress()) gestures.onTouchEvent(e);
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (layout == null) return;
        if (needFit) fit();
        c.save();
        c.translate(tx, ty);
        c.scale(scale, scale);
        c.translate(margin, margin);
        for (MapLayout.Node n : layout.nodes)
            for (MapLayout.Node k : n.children) {
                float[] e = layout.edge(n, k);
                path.reset();
                path.moveTo(e[0], e[1]);
                for (int i = 2; i + 1 < e.length; i += 2) path.lineTo(e[i], e[i + 1]);
                c.drawPath(path, line);
            }
        float rad = 7 * dens;
        for (MapLayout.Node n : layout.nodes) {
            int depth = n.isDivision() ? n.depth - 1 : n.depth;
            r.set(n.x, n.y, n.x + n.w, n.y + n.h);
            int textColor;
            if (n.isDivision()) {
                fill.setColor(0xFFFFFFFF);
                stroke.setColor(Palette.border(n.hue, depth));
                stroke.setStrokeWidth(1.2f * dens);
                stroke.setPathEffect(dash);
                textColor = Palette.border(n.hue, depth);
            } else {
                fill.setColor(Palette.fill(n.hue, depth));
                stroke.setColor(Palette.border(n.hue, depth));
                stroke.setStrokeWidth(n.loose ? 1.2f * dens : Palette.borderWidth(depth, dens));
                stroke.setPathEffect(n.loose ? dash : null);
                textColor = Palette.text(n.hue, depth);
            }
            c.drawRoundRect(r, rad, rad, fill);
            c.drawRoundRect(r, rad, rad, stroke);
            StaticLayout sl = texts.get(n);
            Float mwObj = markW.get(n);
            float mw = mwObj == null ? 0 : mwObj;
            if (sl != null) {
                TextPaint tp = n.isDivision() ? labelPaint : textPaint;
                tp.setColor(textColor);
                float avail = n.w - 2 * pad - mw;
                float vp = n.isDivision() ? pad * .5f : pad;
                c.save();
                c.translate(n.x + pad + (avail - sl.getWidth()) / 2, n.y + vp);
                sl.draw(c);
                c.restore();
            }
            if (!n.mark.isEmpty()) {
                markPaint.setColor(n.isDivision() ? textColor : (textColor & 0x00FFFFFF) | 0xB0000000);
                markPaint.setTextAlign(Paint.Align.RIGHT);
                c.drawText(n.mark, n.x + n.w - 4 * dens, n.y + 3 * dens - markPaint.ascent(), markPaint);
            }
        }
        c.restore();
    }
}
