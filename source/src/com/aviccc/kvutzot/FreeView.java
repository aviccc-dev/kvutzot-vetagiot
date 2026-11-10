package com.aviccc.kvutzot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.aviccc.kvutzot.model.FreeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * המפה הגמישה (טאב "סיעור מוחות", 0.8): פריטים בכל מקום, קווים בין כל שני פריטים.
 * נגיעה — בחירה; גרירה של פריט נבחר — הזזה; גרירה ברקע — הזזת המפה; שתי אצבעות — הגדלה;
 * נגיעה כפולה במקום ריק — פריט חדש שם. סביב פריט נבחר: "ערוך", "חבר", "⋯".
 */
public class FreeView extends View {

    public interface Host {
        void onFreeAdd(float x, float y);
        void onFreeEdit(FreeMap.Node n);
        void onFreeMenu(FreeMap.Node n);
        /** שינוי (קו, הזזה): לשמור. */
        void onFreeChanged();
        void onFreeStatus(String s);
    }

    private final float dens;
    private FreeMap map;
    private Host host;
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint btnText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            line = new Paint(Paint.ANTI_ALIAS_FLAG), sel = new Paint(Paint.ANTI_ALIAS_FLAG), btn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<FreeMap.Node, StaticLayout> texts = new HashMap<>();
    private float scale = 1f, tx, ty;
    private boolean placed;
    private FreeMap.Node selected, dragging, linkFrom;
    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;
    private final List<RectF> btnRects = new ArrayList<>();
    private final List<String> btnActs = new ArrayList<>();
    private final RectF r = new RectF();

    public FreeView(Context c) {
        super(c);
        dens = getResources().getDisplayMetrics().density;
        tp.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15, getResources().getDisplayMetrics()));
        tp.setColor(0xFF2C2C2A);
        btnText.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13, getResources().getDisplayMetrics()));
        btnText.setColor(0xFF0C447C);
        btnText.setTextAlign(Paint.Align.CENTER);
        fill.setColor(0xFFFAEEDA);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(0xFFBA7517);
        stroke.setStrokeWidth(1.2f * dens);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(0xFF888780);
        line.setStrokeWidth(1.4f * dens);
        sel.setStyle(Paint.Style.STROKE);
        sel.setStrokeWidth(3f * dens);
        sel.setColor(0xFF185FA5);
        btn.setColor(0xFFFFFFFF);
        setBackgroundColor(0xFFFFFDF8);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float ns = Math.max(.3f, Math.min(3f, scale * d.getScaleFactor())), f = ns / scale;
                tx = d.getFocusX() - (d.getFocusX() - tx) * f;
                ty = d.getFocusY() - (d.getFocusY() - ty) * f;
                scale = ns;
                invalidate();
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) {
                FreeMap.Node n = hit(e.getX(), e.getY());
                dragging = !locked && n != null && n == selected && linkFrom == null ? n : null;
                return true;
            }
            @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                if (dragging != null) {
                    dragging.x -= dx / scale / dens;
                    dragging.y -= dy / scale / dens;
                } else { tx -= dx; ty -= dy; }
                invalidate();
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                for (int i = 0; i < btnRects.size(); i++)
                    if (btnRects.get(i).contains(e.getX(), e.getY())) { button(btnActs.get(i)); return true; }
                FreeMap.Node n = hit(e.getX(), e.getY());
                if (linkFrom != null) {
                    if (n != null && n != linkFrom) {
                        boolean added = map.link(linkFrom, n);
                        if (!added) map.unlink(linkFrom, n);
                        if (host != null) {
                            host.onFreeStatus(added ? "חובר: " + linkFrom.text + " — " + n.text : "הקו הוסר");
                            host.onFreeChanged();
                        }
                    }
                    linkFrom = null;
                    selected = n;
                    invalidate();
                    return true;
                }
                selected = n;
                invalidate();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                FreeMap.Node n = hit(e.getX(), e.getY());
                if (host == null || locked) return true;
                if (n != null) { selected = n; host.onFreeEdit(n); }
                else host.onFreeAdd(toX(e.getX()), toY(e.getY()));
                return true;
            }
        });
    }

    /** מאפייני הטאב (0.10): נעול — רק מסתכלים; links — מותר לחבר בקווים. */
    public boolean locked, links = true;

    public void setMap(FreeMap m, Host h) {
        if (m != map) { selected = null; linkFrom = null; placed = false; }
        map = m;
        host = h;
        texts.clear();
        invalidate();
    }

    public void select(FreeMap.Node n) { selected = n; invalidate(); }

    /** המרכז של המסך ביחידות המפה (לפריט חדש מהכפתור). */
    public float centerX() { return toX(getWidth() / 2f); }
    public float centerY() { return toY(getHeight() / 3f); }

    private float toX(float sx) { return (sx - tx) / scale / dens; }
    private float toY(float sy) { return (sy - ty) / scale / dens; }

    private StaticLayout layout(FreeMap.Node n) {
        StaticLayout sl = texts.get(n);
        String t = n.text.isEmpty() ? "(ריק)" : n.text;
        if (sl == null || !sl.getText().toString().equals(t)) {
            int w = (int) Math.ceil(Math.min(150 * dens, Layout.getDesiredWidth(t, tp)));
            sl = new StaticLayout(t, tp, Math.max(1, w), Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
            texts.put(n, sl);
        }
        return sl;
    }

    /** התיבה של פריט במסך המפה (לפני הגדלה). */
    private void box(FreeMap.Node n, RectF out) {
        StaticLayout sl = layout(n);
        float pad = 8 * dens, cx = n.x * dens, cy = n.y * dens;
        float w = sl.getWidth() + 2 * pad, h = sl.getHeight() + 2 * pad;
        out.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
    }

    private FreeMap.Node hit(float sx, float sy) {
        if (map == null) return null;
        float mx = (sx - tx) / scale, my = (sy - ty) / scale;
        for (int i = map.nodes.size() - 1; i >= 0; i--) {
            FreeMap.Node n = map.nodes.get(i);
            box(n, r);
            if (r.contains(mx, my)) return n;
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        if (!scaler.isInProgress()) gestures.onTouchEvent(e);
        if ((e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) && dragging != null) {
            dragging = null;
            if (host != null) host.onFreeChanged();
        }
        return true;
    }

    private void button(String a) {
        if (selected == null || host == null) return;
        switch (a) {
            case "edit": host.onFreeEdit(selected); break;
            case "link":
                linkFrom = selected;
                host.onFreeStatus("בחר פריט לחיבור עם \"" + selected.text + "\" (נגיעה בפריט שכבר מחובר — מסירה את הקו)");
                invalidate();
                break;
            case "more": host.onFreeMenu(selected); break;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (map == null) return;
        if (!placed && getWidth() > 0) {
            placed = true;
            if (map.nodes.isEmpty()) { tx = getWidth() / 2f; ty = getHeight() / 3f; }
            else {
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
                for (FreeMap.Node n : map.nodes) {
                    minX = Math.min(minX, n.x); maxX = Math.max(maxX, n.x);
                    minY = Math.min(minY, n.y); maxY = Math.max(maxY, n.y);
                }
                tx = getWidth() / 2f - (minX + maxX) / 2 * dens;
                ty = getHeight() / 2f - (minY + maxY) / 2 * dens;
            }
        }
        c.save();
        c.translate(tx, ty);
        c.scale(scale, scale);
        for (FreeMap.Link l : map.links) {
            FreeMap.Node a = map.node(l.a), b = map.node(l.b);
            if (a != null && b != null) c.drawLine(a.x * dens, a.y * dens, b.x * dens, b.y * dens, line);
        }
        float rad = 8 * dens;
        for (FreeMap.Node n : map.nodes) {
            box(n, r);
            c.drawRoundRect(r, rad, rad, fill);
            c.drawRoundRect(r, rad, rad, stroke);
            StaticLayout sl = layout(n);
            c.save();
            c.translate(r.left + 8 * dens, r.top + 8 * dens);
            sl.draw(c);
            c.restore();
            if (n == selected || n == linkFrom) {
                float o = 3 * dens;
                r.inset(-o, -o);
                c.drawRoundRect(r, rad + o, rad + o, sel);
            }
        }
        c.restore();
        btnRects.clear();
        btnActs.clear();
        if (map.nodes.isEmpty()) {
            tp.setColor(0xFF888780);
            String hint = "נגיעה כפולה בכל מקום — פריט חדש שם";
            float w = tp.measureText(hint);
            c.drawText(hint, (getWidth() - w) / 2, getHeight() / 3f, tp);
            tp.setColor(0xFF2C2C2A);
        }
        if (selected != null && linkFrom == null && !locked) drawButtons(c);
    }

    private void drawButtons(Canvas c) {
        String[] labels = links ? new String[]{"ערוך", "חבר", "⋯"} : new String[]{"ערוך", "⋯"};
        String[] acts = links ? new String[]{"edit", "link", "more"} : new String[]{"edit", "more"};
        box(selected, r);
        float sx = tx + r.left * scale, sy = ty + r.top * scale, sw = r.width() * scale, sh = r.height() * scale;
        float bw = 52 * dens, bh = 36 * dens, gap = 6 * dens, total = labels.length * bw + (labels.length - 1) * gap;
        float left = Math.max(gap, Math.min(sx + sw / 2 - total / 2, getWidth() - total - gap));
        float top = sy + sh + 8 * dens;
        if (top + bh > getHeight() - gap) top = sy - 8 * dens - bh;
        top = Math.max(gap, Math.min(top, getHeight() - bh - gap));
        Paint bs = new Paint(Paint.ANTI_ALIAS_FLAG);
        bs.setStyle(Paint.Style.STROKE);
        bs.setStrokeWidth(1.2f * dens);
        bs.setColor(0xFF185FA5);
        for (int i = 0; i < labels.length; i++) {
            float l = left + total - (i + 1) * bw - i * gap;
            RectF b = new RectF(l, top, l + bw, top + bh);
            btnRects.add(b);
            btnActs.add(acts[i]);
            c.drawRoundRect(b, bh / 2, bh / 2, btn);
            c.drawRoundRect(b, bh / 2, bh / 2, bs);
            c.drawText(labels[i], b.centerX(), b.centerY() - (btnText.ascent() + btnText.descent()) / 2, btnText);
        }
    }
}
