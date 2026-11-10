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

/**
 * המפה הגרפית: תיבות, קווים, צבעים; הזזה באצבע, הגדלה בשתי אצבעות.
 * 0.3: נגיעה בוחרת פריט, וסביבו כפתורים; נגיעה כפולה — עריכת שם; לחיצה ארוכה — ענף ותפריט.
 */
public class MapView extends View {

    /** מה שהמסך מבקש מהפעילות הראשית. */
    public interface Host {
        void onAction(String action, MapLayout.Node node);
        void onEditNode(MapLayout.Node node);
        void onBranchMenu(MapLayout.Node node);
        /** בתצוגה לפי פריט מידע: האם לנגיעה בצומת יש פריט מקורי לפתוח. */
        boolean canOpen(MapLayout.Node node);
        /** אחרי גרירה של פריט (ההזזה כבר נרשמה בו). */
        void onDragged(com.aviccc.kvutzot.model.Model.Item item);
    }

    /** גרירה (0.10.3): פריט נבחר נגרר עם הענף שלו. */
    private MapLayout.Node dragging;
    private float dragX, dragY;

    private boolean readOnly;
    public void setReadOnly(boolean ro) { readOnly = ro; }

    public static final String A_CHILD = "child", A_SIBLING = "sibling", A_EDIT = "edit",
            A_COLLAPSE = "collapse", A_MORE = "more", A_ASPECT = "aspect";

    private Host host;
    private com.aviccc.kvutzot.model.Model.Item selItem;
    private com.aviccc.kvutzot.model.Model.Division selDiv;
    private boolean branch;
    private final Paint btnFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint btnText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final java.util.List<RectF> btnRects = new java.util.ArrayList<>();
    private final java.util.List<String> btnActions = new java.util.ArrayList<>();
    private boolean pendingReveal;

    public void setHost(Host h) { host = h; }

    /** קורא בכל שינוי הגדלה (לתא המספרים). */
    public interface ZoomListener { void onZoom(float scale); }
    private ZoomListener zoomListener;
    public void setZoomListener(ZoomListener l) { zoomListener = l; }
    public float getZoom() { return scale; }

    private void zoomChanged() { if (zoomListener != null) zoomListener.onZoom(scale); }

    /** הגדלה לערך מסוים, סביב מרכז המסך. */
    public void setZoom(float z) {
        z = Math.max(com.aviccc.kvutzot.model.Zoom.MIN, Math.min(com.aviccc.kvutzot.model.Zoom.MAX, z));
        float cx = getWidth() / 2f, cy = getHeight() / 2f, f = z / scale;
        tx = cx - (cx - tx) * f;
        ty = cy - (cy - ty) * f;
        scale = z;
        needFit = false;
        zoomChanged();
        invalidate();
    }
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
    /** צבעים מחושבים מראש לכל צומת: מילוי, מסגרת, טקסט, עובי. */
    private final Map<MapLayout.Node, float[]> look = new HashMap<>();
    /** פריסות טקסט שמורות לפי התווית — כך שינוי אחד לא בונה מחדש את כל הטקסטים. */
    private final Map<String, StaticLayout> slCache = new java.util.concurrent.ConcurrentHashMap<>();
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
        line.setStrokeJoin(Paint.Join.ROUND);
        dash = new DashPathEffect(new float[]{5 * dens, 4 * dens}, 0);
        pad = 8 * dens;
        maxW = 150 * dens;
        margin = 24 * dens;
        setBackgroundColor(0xFFFFFFFF);
        btnFill.setColor(0xFFFFFFFF);
        btnStroke.setStyle(Paint.Style.STROKE);
        btnStroke.setStrokeWidth(1.2f * dens);
        btnStroke.setColor(0xFF185FA5);
        btnText.setTextSize(sp(13));
        btnText.setColor(0xFF0C447C);
        btnText.setTextAlign(Paint.Align.CENTER);
        selPaint.setStyle(Paint.Style.STROKE);
        selPaint.setStrokeWidth(3f * dens);
        selPaint.setColor(0xFFEF9F27);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float ns = Math.max(com.aviccc.kvutzot.model.Zoom.MIN, Math.min(com.aviccc.kvutzot.model.Zoom.MAX, scale * d.getScaleFactor()));
                float f = ns / scale;
                tx = d.getFocusX() - (d.getFocusX() - tx) * f;
                ty = d.getFocusY() - (d.getFocusY() - ty) * f;
                scale = ns;
                zoomChanged();
                invalidate();
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) {
                dragging = null;
                dragX = dragY = 0;
                if (readOnly || layout == null) return true;
                MapLayout.Node n = hit(e.getX(), e.getY());
                MapLayout.Node sel = selectedNode();
                if (n != null && n == sel && !n.isDivision() && (n.parent != null || n.loose)) dragging = n;
                return true;
            }
            @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                if (dragging != null) {
                    float mx = -dx / scale, my = -dy / scale;
                    dragX += mx;
                    dragY += my;
                    moveBranch(dragging, mx, my);
                    invalidate();
                    return true;
                }
                tx -= dx; ty -= dy; invalidate(); return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                for (int i = 0; i < btnRects.size(); i++)
                    if (btnRects.get(i).contains(e.getX(), e.getY())) {
                        MapLayout.Node n = selectedNode();
                        if (host != null && n != null) host.onAction(btnActions.get(i), n);
                        return true;
                    }
                MapLayout.Node n = hit(e.getX(), e.getY());
                select(n, false);
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                MapLayout.Node n = hit(e.getX(), e.getY());
                if (n == null) { fit(); return true; }
                select(n, false);
                if (host != null) host.onEditNode(n);
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                MapLayout.Node n = hit(e.getX(), e.getY());
                if (n == null) return;
                select(n, true);
                if (host != null) host.onBranchMenu(n);
            }
        });
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    // ---------------- בחירה ----------------

    private MapLayout.Node hit(float sx, float sy) {
        if (layout == null) return null;
        float mx = (sx - tx) / scale - margin, my = (sy - ty) / scale - margin;
        float slack = 4 * dens / scale;
        for (int i = layout.nodes.size() - 1; i >= 0; i--) {
            MapLayout.Node n = layout.nodes.get(i);
            if (mx >= n.x - slack && mx <= n.x + n.w + slack && my >= n.y - slack && my <= n.y + n.h + slack) return n;
        }
        return null;
    }

    public void select(MapLayout.Node n, boolean wholeBranch) {
        selItem = n == null ? null : n.item;
        selDiv = n == null ? null : n.division;
        branch = n != null && wholeBranch;
        invalidate();
    }

    /** בחירת פריט לפי המודל (אחרי הוספה), והזזת המפה כך שייראה. */
    public void selectItem(com.aviccc.kvutzot.model.Model.Item it, com.aviccc.kvutzot.model.Model.Division d) {
        selItem = it;
        selDiv = d;
        branch = false;
        pendingReveal = true;
        invalidate();
    }

    public MapLayout.Node selectedNode() {
        if (layout == null || selItem == null) return null;
        for (MapLayout.Node n : layout.nodes) if (n.item == selItem && n.division == selDiv) return n;
        return null;
    }

    private boolean inBranch(MapLayout.Node n, MapLayout.Node top) {
        for (MapLayout.Node x = n; x != null; x = x.parent) if (x == top) return true;
        return false;
    }

    /** פריסה צפופה (כמו מפת חשיבה) או מרווחת. */
    public boolean compact = true;
    /** מרווחים: בין אחים ובין רמות (כפולה של הרגיל). */
    public float spaceB = 1f, spaceD = 1f;

    public void setProject(Project p, boolean vertical, boolean refit) {
        texts.clear();
        markW.clear();
        look.clear();
        if (slCache.size() > 4000) slCache.clear();
        MapLayout old = layout;
        layout = MapLayout.build(p, vertical, new MapLayout.Measurer() {
            public float[] measure(MapLayout.Node n) {
                StaticLayout sl = textLayout(n, textPaint, labelPaint);
                texts.put(n, sl);
                float mw = n.mark.isEmpty() ? 0 : markPaint.measureText(n.mark) + 5 * dens;
                markW.put(n, mw);
                float vp = n.isDivision() ? pad * .5f : pad;
                return new float[]{sl.getWidth() + 2 * pad + mw, sl.getHeight() + 2 * vp};
            }
        }, 26 * dens * spaceD, 10 * dens * spaceB, compact, dens);
        if (old != null && !refit) {
            // אחרי גרירה המפה עשויה להתרחב; התמונה נשארת במקומה על המסך
            tx += (old.shiftX - layout.shiftX) * scale;
            ty += (old.shiftY - layout.shiftY) * scale;
        }
        for (MapLayout.Node n : layout.nodes) {
            int depth = n.isDivision() ? n.depth - 1 : n.depth;
            int f, s, tc;
            float sw;
            if (n.isDivision()) { f = 0xFFFFFFFF; s = Palette.border(n.hue, depth); tc = s; sw = 1.2f * dens; }
            else {
                f = Palette.fill(n.hue, depth); s = Palette.border(n.hue, depth); tc = Palette.text(n.hue, depth);
                sw = n.loose ? 1.2f * dens : Palette.borderWidth(depth, dens);
            }
            look.put(n, new float[]{Float.intBitsToFloat(f), Float.intBitsToFloat(s), Float.intBitsToFloat(tc), sw});
        }
        if (refit) needFit = true;
        invalidate();
    }

    /** פריסת הטקסט של תיבה — מהמטמון, או חדשה. */
    private StaticLayout textLayout(MapLayout.Node n, TextPaint text, TextPaint label) {
        TextPaint tp = n.isDivision() ? label : text;
        String t = label(n);
        String key = (n.isDivision() ? "D" : "I") + t;
        StaticLayout sl = slCache.get(key);
        if (sl == null) {
            float want = Math.min(maxW, (float) Math.ceil(Layout.getDesiredWidth(t, tp)));
            sl = new StaticLayout(t, tp, Math.max(1, (int) Math.ceil(want)),
                    Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
            slCache.put(key, sl);
        }
        return sl;
    }

    /**
     * הכנה ברקע (בזמן הטעינה, לא בתהליך המסך): בונה מראש את פריסות הטקסט של כל התיבות,
     * כך שהציור הראשון לא מחכה להן. עם עותקים של הצבעים — כדי לא לגעת במה שהמסך משתמש בו.
     */
    public void prepare(Project p, boolean vertical) {
        final TextPaint tx = new TextPaint(textPaint), lb = new TextPaint(labelPaint);
        MapLayout.build(p, vertical, new MapLayout.Measurer() {
            public float[] measure(MapLayout.Node n) {
                StaticLayout sl = textLayout(n, tx, lb);
                return new float[]{sl.getWidth() + 2 * pad, sl.getHeight() + 2 * pad};
            }
        }, 26 * dens, 10 * dens);
    }

    private static String label(MapLayout.Node n) {
        String t = n.text.isEmpty() ? "(בלי שם)" : n.text;
        return n.hidden > 0 ? t + "  ▸ " + n.hidden : t;
    }

    /** מקטין או מגדיל כך שכל המפה נראית; לא מגדיל מעבר לגודל טבעי. */
    public void fit() {
        if (layout == null || getWidth() == 0) return;
        float w = layout.width + 2 * margin, h = layout.height + 2 * margin;
        scale = Math.max(com.aviccc.kvutzot.model.Zoom.MIN, Math.min(1f, Math.min(getWidth() / w, getHeight() / h)));
        tx = (getWidth() - w * scale) / 2;
        ty = layout.vertical ? 0 : (getHeight() - h * scale) / 2;
        if (ty < 0) ty = 0;
        needFit = false;
        zoomChanged();
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
        if (scaler.isInProgress() && dragging != null) { moveBranch(dragging, -dragX, -dragY); dragging = null; invalidate(); }
        if (!scaler.isInProgress()) gestures.onTouchEvent(e);
        int act = e.getActionMasked();
        if ((act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) && dragging != null) {
            MapLayout.Node d = dragging;
            dragging = null;
            if (Math.abs(dragX) + Math.abs(dragY) > 2 / scale && host != null) {
                d.item.dx += dragX / dens;
                d.item.dy += dragY / dens;
                host.onDragged(d.item);
            }
        }
        return true;
    }

    private void moveBranch(MapLayout.Node n, float mx, float my) {
        n.x += mx;
        n.y += my;
        for (MapLayout.Node k : n.children) moveBranch(k, mx, my);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (layout == null) return;
        if (needFit) fit();
        c.save();
        c.translate(tx, ty);
        c.scale(scale, scale);
        c.translate(margin, margin);
        // רק מה שבמסך (ביחידות המפה), עם שוליים
        float slack = 40 * dens;
        float vx0 = -tx / scale - margin - slack, vy0 = -ty / scale - margin - slack;
        float vx1 = (getWidth() - tx) / scale - margin + slack, vy1 = (getHeight() - ty) / scale - margin + slack;
        // בהקטנה גדולה הטקסט לא קריא — מציירים רק תיבות
        boolean showText = textPaint.getTextSize() * scale >= 5.5f;
        boolean fine = scale >= 0.35f;
        for (MapLayout.Node n : layout.nodes)
            for (MapLayout.Node k : n.children) {
                float x0 = Math.min(n.x, k.x), x1 = Math.max(n.x + n.w, k.x + k.w);
                float y0 = Math.min(n.y, k.y), y1 = Math.max(n.y + n.h, k.y + k.h);
                if (x1 < vx0 || x0 > vx1 || y1 < vy0 || y0 > vy1) continue;
                float[] e = layout.edge(n, k);
                path.reset();
                path.moveTo(e[0], e[1]);
                for (int i = 2; i + 1 < e.length; i += 2) path.lineTo(e[i], e[i + 1]);
                c.drawPath(path, line);
            }
        float rad = 7 * dens;
        for (MapLayout.Node n : layout.nodes) {
            if (n.x + n.w < vx0 || n.x > vx1 || n.y + n.h < vy0 || n.y > vy1) continue;
            float[] lk = look.get(n);
            if (lk == null) continue;
            int textColor = Float.floatToRawIntBits(lk[2]);
            r.set(n.x, n.y, n.x + n.w, n.y + n.h);
            fill.setColor(Float.floatToRawIntBits(lk[0]));
            c.drawRoundRect(r, rad, rad, fill);
            stroke.setColor(Float.floatToRawIntBits(lk[1]));
            stroke.setStrokeWidth(lk[3]);
            stroke.setPathEffect(fine && (n.isDivision() || n.loose) ? dash : null);
            c.drawRoundRect(r, rad, rad, stroke);
            if (!showText) continue;
            StaticLayout sl = texts.get(n);
            Float mwObj = markW.get(n);
            float mw = mwObj == null ? 0 : mwObj;
            if (sl != null) {
                sl.getPaint().setColor(textColor);
                float avail = n.w - 2 * pad - mw;
                float vp = n.isDivision() ? pad * .5f : pad;
                c.save();
                c.translate(n.x + pad + (avail - sl.getWidth()) / 2, n.y + vp);
                sl.draw(c);
                c.restore();
            }
            if (!fine) continue;
            if (!n.isDivision() && (!n.item.infos.isEmpty() || !n.item.tagIds.isEmpty())) {
                // נקודה קטנה: יש לפריט מידע נלווה (נחבא מתחתיו; נפתח ב"ערוך")
                fill.setColor((textColor & 0x00FFFFFF) | 0xC0000000);
                c.drawCircle(n.x + 7 * dens, n.y + n.h - 7 * dens, 2.6f * dens, fill);
            }
            if (!n.mark.isEmpty()) {
                markPaint.setColor(n.isDivision() ? textColor : (textColor & 0x00FFFFFF) | 0xB0000000);
                markPaint.setTextAlign(Paint.Align.RIGHT);
                c.drawText(n.mark, n.x + n.w - 4 * dens, n.y + 3 * dens - markPaint.ascent(), markPaint);
            }
        }
        MapLayout.Node sel = selectedNode();
        if (sel != null) {
            float o = 3 * dens;
            for (MapLayout.Node n : layout.nodes) {
                if (n != sel && !(branch && inBranch(n, sel))) continue;
                r.set(n.x - o, n.y - o, n.x + n.w + o, n.y + n.h + o);
                selPaint.setAlpha(n == sel ? 255 : 120);
                c.drawRoundRect(r, rad + o, rad + o, selPaint);
            }
        }
        c.restore();
        btnRects.clear();
        btnActions.clear();
        if (sel != null) {
            if (pendingReveal) { reveal(sel); pendingReveal = false; }
            drawButtons(c, sel);
        }
    }

    /** מזיז את המפה כך שהפריט ייראה, אם הוא מחוץ למסך. */
    private void reveal(MapLayout.Node n) {
        float sx = tx + (n.x + margin) * scale, sy = ty + (n.y + margin) * scale;
        float sw = n.w * scale, sh = n.h * scale, m = 60 * dens;
        if (sx < m || sx + sw > getWidth() - m || sy < m || sy + sh > getHeight() - m * 2) {
            tx += getWidth() / 2f - (sx + sw / 2);
            ty += getHeight() / 3f - (sy + sh / 2);
            invalidate();
        }
    }

    private void drawButtons(Canvas c, MapLayout.Node n) {
        java.util.List<String> labels = new java.util.ArrayList<>();
        if (readOnly) {
            if (host == null || !host.canOpen(n)) return;
            labels.add("פתח"); btnActions.add(A_EDIT);
        } else if (n.isDivision()) {
            labels.add("+ בן"); btnActions.add(A_CHILD);
            labels.add("בחינה"); btnActions.add(A_ASPECT);
            labels.add("⋯"); btnActions.add(A_MORE);
        } else {
            labels.add("+ בן"); btnActions.add(A_CHILD);
            if (n.item.parentDivision != null || n.item.loose) { labels.add("+ אח"); btnActions.add(A_SIBLING); }
            labels.add("ערוך"); btnActions.add(A_EDIT);
            if (n.item.childCount() > 0) { labels.add(n.item.collapsed ? "פתח" : "כווץ"); btnActions.add(A_COLLAPSE); }
            labels.add("⋯"); btnActions.add(A_MORE);
        }
        float bw = 52 * dens, bh = 36 * dens, gap = 6 * dens;
        float total = labels.size() * bw + (labels.size() - 1) * gap;
        float sx = tx + (n.x + margin) * scale, sy = ty + (n.y + margin) * scale;
        float sw = n.w * scale, sh = n.h * scale;
        float left = sx + sw / 2 - total / 2;
        left = Math.max(gap, Math.min(left, getWidth() - total - gap));
        float top = sy + sh + 8 * dens;
        if (top + bh > getHeight() - gap) top = sy - 8 * dens - bh;
        top = Math.max(gap, Math.min(top, getHeight() - bh - gap));
        float rr = bh / 2;
        // מימין לשמאל: הכפתור הראשון בצד ימין
        for (int i = 0; i < labels.size(); i++) {
            float l = left + total - (i + 1) * bw - i * gap;
            RectF b = new RectF(l, top, l + bw, top + bh);
            btnRects.add(b);
            c.drawRoundRect(b, rr, rr, btnFill);
            c.drawRoundRect(b, rr, rr, btnStroke);
            c.drawText(labels.get(i), b.centerX(), b.centerY() - (btnText.ascent() + btnText.descent()) / 2, btnText);
        }
    }
}
