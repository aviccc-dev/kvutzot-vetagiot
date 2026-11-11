package com.aviccc.kvutzot.desktop;

import com.aviccc.kvutzot.model.MapLayout;
import com.aviccc.kvutzot.model.Model.Division;
import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.Palette;
import com.aviccc.kvutzot.model.Zoom;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * המפה הגרפית במחשב: אותו ציור כמו בטלפון (MapView), עם עכבר במקום מגע.
 * לחיצה בוחרת; גרירה של פריט מזיזה את הענף; גרירה ברקע מזיזה את המפה;
 * גלגלת — הגדלה או גלילה (לפי ההגדרות); לחיצה כפולה ברקע — התאמה לחלון.
 * היחידות כמו ב-dp של הטלפון, כך שהזזות ידניות נשמרות בקובץ באותו גודל בשתי הגרסאות.
 */
public class MapPanel extends JComponent {

    public interface Host {
        void onSelect(MapLayout.Node n);
        /** לחיצה כפולה על פריט או על תווית חלוקה. */
        void onOpen(MapLayout.Node n);
        /** לחיצה ימנית; n == null — ברקע. */
        void onMenu(MapLayout.Node n, int x, int y);
        /** אחרי גרירה של פריט (ההזזה כבר נרשמה בו). */
        void onDragged(Item it);
        void onZoom(float z);
    }

    private static final float PAD = 8, MAXW = 150, MARGIN = 24;
    private static final Color LINE = new Color(0xB4B2A9), SEL = new Color(0xEF9F27);
    private static final Stroke LINE_STROKE = new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke SEL_STROKE = new BasicStroke(3f);

    private final Font textFont, labelFont, markFont;
    private final BufferedImage scratch = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    private Host host;
    private MapLayout layout;

    /** תיבת טקסט: השורות אחרי גלישה, רוחב כל שורה, ורוחב/גובה כולל. */
    private static final class Box {
        String[] lines; float[] lineW; float w, h, lineH, ascent;
    }

    private final Map<MapLayout.Node, Box> boxes = new HashMap<>();
    private final Map<MapLayout.Node, Float> markW = new HashMap<>();
    private final Map<String, Box> cache = new HashMap<>();

    private float scale = 1f, tx, ty;
    private boolean needFit = true;
    private boolean readOnly;
    public boolean compact = true;
    public float spaceB = 1f, spaceD = 1f;
    /** true: הגלגלת מגדילה; false: הגלגלת גוללת, ו-Ctrl+גלגלת מגדילה. */
    public boolean wheelZooms = true;

    private Item selItem;
    private Division selDiv;
    private boolean branch, pendingReveal;

    private MapLayout.Node dragging;
    private float dragX, dragY;
    private boolean panning, moved;
    private Point last;

    public MapPanel(Font base) {
        textFont = base.deriveFont(Font.PLAIN, 15f);
        labelFont = base.deriveFont(Font.ITALIC, 12f);
        markFont = base.deriveFont(Font.PLAIN, 10f);
        setOpaque(true);
        setBackground(Color.WHITE);
        setFocusable(true);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                last = e.getPoint();
                moved = false;
                dragging = null;
                dragX = dragY = 0;
                panning = false;
                if (layout == null) return;
                MapLayout.Node n = hit(e.getX(), e.getY());
                if (e.isPopupTrigger()) { popup(n, e); return; }
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    if (SwingUtilities.isMiddleMouseButton(e)) panning = true;
                    return;
                }
                if (n != null) {
                    select(n, false);
                    if (!readOnly && !n.isDivision() && (n.parent != null || n.loose)) dragging = n;
                } else panning = true;
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (last == null) return;
                float dx = e.getX() - last.x, dy = e.getY() - last.y;
                if (!moved && Math.abs(e.getX() - last.x) + Math.abs(e.getY() - last.y) < 4 && dragging != null) return;
                moved = true;
                last = e.getPoint();
                if (dragging != null) {
                    float mx = dx / scale, my = dy / scale;
                    dragX += mx;
                    dragY += my;
                    moveBranch(dragging, mx, my);
                    repaint();
                } else if (panning) {
                    tx += dx;
                    ty += dy;
                    repaint();
                }
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) { popup(hit(e.getX(), e.getY()), e); return; }
                MapLayout.Node d = dragging;
                dragging = null;
                panning = false;
                if (d != null && moved && Math.abs(dragX) + Math.abs(dragY) > 2 / scale && host != null) {
                    d.item.dx += dragX;
                    d.item.dy += dragY;
                    host.onDragged(d.item);
                }
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 2 || moved) return;
                MapLayout.Node n = hit(e.getX(), e.getY());
                if (n == null) { fit(); return; }
                if (host != null) host.onOpen(n);
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                double r = e.getPreciseWheelRotation();
                // הגדלה: גלגלת לבד (או Ctrl+גלגלת, כשהגלגלת גוללת). גלילה: Shift — לרוחב; אחרת — לגובה
                boolean zoom = wheelZooms ? !e.isShiftDown() && !e.isControlDown() : e.isControlDown();
                if (zoom) {
                    zoomAt((float) (scale * Math.pow(1.12, -r)), e.getX(), e.getY());
                } else {
                    float step = (float) (r * 40);
                    if (e.isShiftDown()) tx -= step; else ty -= step;
                    repaint();
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
    }

    private void popup(MapLayout.Node n, MouseEvent e) {
        if (n != null) select(n, false);
        if (host != null) host.onMenu(n, e.getX(), e.getY());
    }

    public void setHost(Host h) { host = h; }
    public void setReadOnly(boolean ro) { readOnly = ro; }
    public float getZoom() { return scale; }
    public MapLayout mapLayout() { return layout; }

    // ---------------- הגדלה ----------------

    public void setZoom(float z) { zoomAt(z, getWidth() / 2f, getHeight() / 2f); }

    private void zoomAt(float z, float cx, float cy) {
        z = Math.max(Zoom.MIN, Math.min(Zoom.MAX, z));
        float f = z / scale;
        tx = cx - (cx - tx) * f;
        ty = cy - (cy - ty) * f;
        scale = z;
        needFit = false;
        if (host != null) host.onZoom(scale);
        repaint();
    }

    /** מקטין או מגדיל כך שכל המפה נראית; לא מגדיל מעבר לגודל טבעי. */
    public void fit() {
        if (layout == null || getWidth() == 0) { needFit = true; return; }
        float w = layout.width + 2 * MARGIN, h = layout.height + 2 * MARGIN;
        scale = Math.max(Zoom.MIN, Math.min(1f, Math.min(getWidth() / w, getHeight() / h)));
        tx = (getWidth() - w * scale) / 2;
        ty = layout.vertical ? 0 : (getHeight() - h * scale) / 2;
        if (ty < 0) ty = 0;
        needFit = false;
        if (host != null) host.onZoom(scale);
        repaint();
    }

    /** גלילה במקשים (או בכפתורים): הזזה בפיקסלים של המסך. */
    public void scrollBy(float dx, float dy) {
        tx += dx;
        ty += dy;
        repaint();
    }

    // ---------------- בחירה ----------------

    public MapLayout.Node hit(float sx, float sy) {
        if (layout == null) return null;
        float mx = (sx - tx) / scale - MARGIN, my = (sy - ty) / scale - MARGIN;
        float slack = 3 / scale;
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
        if (host != null) host.onSelect(n);
        repaint();
    }

    /** בחירת פריט לפי המודל (אחרי הוספה), והזזת המפה כך שייראה. */
    public void selectItem(Item it, Division d) {
        selItem = it;
        selDiv = d;
        branch = false;
        pendingReveal = true;
        repaint();
    }

    public MapLayout.Node selectedNode() {
        if (layout == null || selItem == null) return null;
        for (MapLayout.Node n : layout.nodes) if (n.item == selItem && n.division == selDiv) return n;
        return null;
    }

    /** הצומת במקום הנבחר הקודם, אחרי בנייה מחדש — לפי המודל. */
    public Item selectedItem() { return selItem; }
    public Division selectedDivision() { return selDiv; }

    private static boolean inBranch(MapLayout.Node n, MapLayout.Node top) {
        for (MapLayout.Node x = n; x != null; x = x.parent) if (x == top) return true;
        return false;
    }

    // ---------------- בנייה ----------------

    public void clear() {
        layout = null;
        boxes.clear();
        repaint();
    }

    public void setProject(Project p, boolean vertical, boolean refit) {
        boxes.clear();
        markW.clear();
        if (cache.size() > 6000) cache.clear();
        MapLayout old = layout;
        final FontMetrics mfm = scratch.createGraphics().getFontMetrics(markFont);
        layout = MapLayout.build(p, vertical, new MapLayout.Measurer() {
            public float[] measure(MapLayout.Node n) {
                Box b = box(n);
                boxes.put(n, b);
                float mw = n.mark.isEmpty() ? 0 : mfm.stringWidth(n.mark) + 5;
                markW.put(n, mw);
                float vp = n.isDivision() ? PAD * .5f : PAD;
                return new float[]{b.w + 2 * PAD + mw, b.h + 2 * vp};
            }
        }, 26 * spaceD, 10 * spaceB, compact, 1f);
        if (old != null && !refit) {
            tx += (old.shiftX - layout.shiftX) * scale;
            ty += (old.shiftY - layout.shiftY) * scale;
        }
        if (refit) needFit = true;
        if (refit && getWidth() > 0) fit();
        repaint();
    }

    private static String label(MapLayout.Node n) {
        String t = n.text.isEmpty() ? "(בלי שם)" : n.text;
        return n.hidden > 0 ? t + "  ▸ " + n.hidden : t;
    }

    private Box box(MapLayout.Node n) {
        String t = label(n);
        String key = (n.isDivision() ? "D" : "I") + t;
        Box b = cache.get(key);
        if (b != null) return b;
        Font f = n.isDivision() ? labelFont : textFont;
        FontMetrics fm = scratch.createGraphics().getFontMetrics(f);
        List<String> lines = wrap(t, fm);
        b = new Box();
        b.lines = lines.toArray(new String[0]);
        b.lineW = new float[b.lines.length];
        for (int i = 0; i < b.lines.length; i++) {
            b.lineW[i] = fm.stringWidth(b.lines[i]);
            b.w = Math.max(b.w, b.lineW[i]);
        }
        b.lineH = fm.getHeight();
        b.ascent = fm.getAscent();
        b.h = b.lines.length * b.lineH;
        cache.put(key, b);
        return b;
    }

    /** גלישה לפי מילים ברוחב MAXW; מילה ארוכה מדי נשברת באמצע. */
    private static List<String> wrap(String t, FontMetrics fm) {
        List<String> out = new ArrayList<>();
        for (String para : t.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String w : para.split(" ")) {
                String cand = line.length() == 0 ? w : line + " " + w;
                if (fm.stringWidth(cand) <= MAXW || line.length() == 0 && fm.stringWidth(w) <= MAXW) {
                    line = new StringBuilder(cand);
                    continue;
                }
                if (line.length() > 0) { out.add(line.toString()); line = new StringBuilder(); }
                String rest = w;
                while (fm.stringWidth(rest) > MAXW && rest.length() > 1) {
                    int k = rest.length() - 1;
                    while (k > 1 && fm.stringWidth(rest.substring(0, k)) > MAXW) k--;
                    out.add(rest.substring(0, k));
                    rest = rest.substring(k);
                }
                line.append(rest);
            }
            out.add(line.toString());
        }
        return out;
    }

    private void moveBranch(MapLayout.Node n, float mx, float my) {
        n.x += mx;
        n.y += my;
        for (MapLayout.Node k : n.children) moveBranch(k, mx, my);
    }

    // ---------------- ציור ----------------

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        if (layout == null) { g.dispose(); return; }
        if (needFit) fit();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        AffineTransform base = g.getTransform();
        g.translate(tx, ty);
        g.scale(scale, scale);
        g.translate(MARGIN, MARGIN);
        float slack = 40;
        float vx0 = -tx / scale - MARGIN - slack, vy0 = -ty / scale - MARGIN - slack;
        float vx1 = (getWidth() - tx) / scale - MARGIN + slack, vy1 = (getHeight() - ty) / scale - MARGIN + slack;
        boolean showText = textFont.getSize2D() * scale >= 5.5f;
        boolean fine = scale >= 0.35f;
        g.setColor(LINE);
        g.setStroke(LINE_STROKE);
        Path2D.Float path = new Path2D.Float();
        for (MapLayout.Node n : layout.nodes)
            for (MapLayout.Node k : n.children) {
                float x0 = Math.min(n.x, k.x), x1 = Math.max(n.x + n.w, k.x + k.w);
                float y0 = Math.min(n.y, k.y), y1 = Math.max(n.y + n.h, k.y + k.h);
                if (x1 < vx0 || x0 > vx1 || y1 < vy0 || y0 > vy1) continue;
                float[] e = layout.edge(n, k);
                path.reset();
                path.moveTo(e[0], e[1]);
                for (int i = 2; i + 1 < e.length; i += 2) path.lineTo(e[i], e[i + 1]);
                g.draw(path);
            }
        float rad = 14;
        Stroke dash = new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, new float[]{5, 4}, 0);
        RoundRectangle2D.Float r = new RoundRectangle2D.Float();
        for (MapLayout.Node n : layout.nodes) {
            if (n.x + n.w < vx0 || n.x > vx1 || n.y + n.h < vy0 || n.y > vy1) continue;
            int depth = n.isDivision() ? n.depth - 1 : n.depth;
            int fillC, strokeC, textC;
            float sw;
            if (n.isDivision()) { fillC = 0xFFFFFFFF; strokeC = Palette.border(n.hue, depth); textC = strokeC; sw = 1.2f; }
            else {
                fillC = Palette.fill(n.hue, depth); strokeC = Palette.border(n.hue, depth); textC = Palette.text(n.hue, depth);
                sw = n.loose ? 1.2f : Palette.borderWidth(depth, 1f);
            }
            r.setRoundRect(n.x, n.y, n.w, n.h, rad, rad);
            g.setColor(new Color(fillC, true));
            g.fill(r);
            g.setColor(new Color(strokeC, true));
            g.setStroke(fine && (n.isDivision() || n.loose) ? dash : new BasicStroke(sw));
            g.draw(r);
            if (!showText) continue;
            Box b = boxes.get(n);
            Float mwObj = markW.get(n);
            float mw = mwObj == null ? 0 : mwObj;
            Color tc = new Color(textC, true);
            if (b != null) {
                g.setFont(n.isDivision() ? labelFont : textFont);
                g.setColor(tc);
                float avail = n.w - 2 * PAD - mw;
                float vp = n.isDivision() ? PAD * .5f : PAD;
                float y = n.y + vp + b.ascent;
                for (int i = 0; i < b.lines.length; i++) {
                    g.drawString(b.lines[i], n.x + PAD + (avail - b.lineW[i]) / 2, y);
                    y += b.lineH;
                }
            }
            if (!fine) continue;
            if (!n.isDivision() && (!n.item.infos.isEmpty() || !n.item.tagIds.isEmpty())) {
                g.setColor(new Color((textC & 0x00FFFFFF) | 0xC0000000, true));
                g.fill(new Ellipse2D.Float(n.x + 7 - 2.6f, n.y + n.h - 7 - 2.6f, 5.2f, 5.2f));
            }
            if (!n.mark.isEmpty()) {
                g.setFont(markFont);
                g.setColor(n.isDivision() ? tc : new Color((textC & 0x00FFFFFF) | 0xB0000000, true));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(n.mark, n.x + n.w - 4 - fm.stringWidth(n.mark), n.y + 3 + fm.getAscent());
            }
        }
        MapLayout.Node sel = selectedNode();
        if (sel != null) {
            float o = 3;
            g.setStroke(SEL_STROKE);
            for (MapLayout.Node n : layout.nodes) {
                if (n != sel && !(branch && inBranch(n, sel))) continue;
                r.setRoundRect(n.x - o, n.y - o, n.w + 2 * o, n.h + 2 * o, rad + 2 * o, rad + 2 * o);
                g.setColor(n == sel ? SEL : new Color(0x78EF9F27, true));
                g.draw(r);
            }
        }
        g.setTransform(base);
        g.dispose();
        if (sel != null && pendingReveal) { pendingReveal = false; reveal(sel); }
    }

    /** מזיז את המפה כך שהפריט ייראה, אם הוא מחוץ לחלון. */
    public void reveal(MapLayout.Node n) {
        float sx = tx + (n.x + MARGIN) * scale, sy = ty + (n.y + MARGIN) * scale;
        float sw = n.w * scale, sh = n.h * scale, m = 40;
        if (sx < m || sx + sw > getWidth() - m || sy < m || sy + sh > getHeight() - m) {
            tx += getWidth() / 2f - (sx + sw / 2);
            ty += getHeight() / 3f - (sy + sh / 2);
            repaint();
        }
    }

    /** מיקום הצומת על המסך (לפתיחת תפריט ליד פריט שנבחר במקלדת). */
    public Point screenPoint(MapLayout.Node n) {
        return new Point(Math.round(tx + (n.x + MARGIN + n.w / 2) * scale), Math.round(ty + (n.y + MARGIN + n.h) * scale));
    }
}
