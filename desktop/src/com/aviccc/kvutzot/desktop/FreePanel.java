package com.aviccc.kvutzot.desktop;

import com.aviccc.kvutzot.model.FreeMap;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * "סיעור מוחות" במחשב (כמו FreeView בטלפון): פריטים בכל מקום, וקווים בין כל שני פריטים.
 * לחיצה — בחירה; גרירה של פריט — הזזה; גרירה ברקע — הזזת המפה; גלגלת — הגדלה (או גלילה, לפי ההגדרות);
 * לחיצה כפולה במקום ריק — פריט חדש שם; על פריט — עריכה; לחיצה ימנית — תפריט.
 */
final class FreePanel extends JComponent {

    interface Host {
        void onFreeAdd(float x, float y);
        void onFreeEdit(FreeMap.Node n);
        void onFreeMenu(FreeMap.Node n, int sx, int sy);
        /** לחיצה ימנית ברקע: x, y ביחידות המפה. */
        void onFreeBackground(float x, float y, int sx, int sy);
        void onFreeChanged();
        void onFreeStatus(String s);
        void onFreeDelete(FreeMap.Node n);
    }

    private static final float PAD = 8, MAXW = 150;
    private static final Color FILL = new Color(0xFAEEDA), STROKE = new Color(0xBA7517), LINE = new Color(0x888780),
            SEL = new Color(0x185FA5), TEXT = new Color(0x2C2C2A);

    private final Font font;
    private final BufferedImage scratch = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    private FreeMap map;
    private Host host;
    private float scale = 1f, tx, ty;
    private boolean placed;
    private FreeMap.Node selected, dragging, linkFrom;
    private Point last;
    private boolean moved, panning;
    boolean locked, links = true, wheelZooms = true;

    FreePanel(Font base) {
        font = base.deriveFont(15f);
        setOpaque(true);
        setBackground(new Color(0xFFFDF8));
        setFocusable(true);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                last = e.getPoint();
                moved = false;
                dragging = null;
                panning = false;
                FreeMap.Node n = hit(e.getX(), e.getY());
                if (e.isPopupTrigger()) { popup(n, e); return; }
                if (!SwingUtilities.isLeftMouseButton(e)) { panning = SwingUtilities.isMiddleMouseButton(e); return; }
                if (linkFrom != null) {
                    if (n != null && n != linkFrom) {
                        boolean added = map.link(linkFrom, n);
                        if (!added) map.unlink(linkFrom, n);
                        if (host != null) {
                            host.onFreeStatus(added ? "חובר: " + linkFrom.text + " — " + n.text : "הקו הוסר");
                            host.onFreeChanged();
                        }
                    } else if (host != null) host.onFreeStatus("החיבור בוטל");
                    linkFrom = null;
                    selected = n;
                    repaint();
                    return;
                }
                selected = n;
                if (n != null && !locked) dragging = n; else panning = true;
                repaint();
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (last == null) return;
                if (!moved && Math.abs(e.getX() - last.x) + Math.abs(e.getY() - last.y) < 4 && dragging != null) return;
                float dx = e.getX() - last.x, dy = e.getY() - last.y;
                moved = true;
                last = e.getPoint();
                if (dragging != null) { dragging.x += dx / scale; dragging.y += dy / scale; }
                else if (panning) { tx += dx; ty += dy; }
                repaint();
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) { popup(hit(e.getX(), e.getY()), e); return; }
                if (dragging != null && moved && host != null) host.onFreeChanged();
                dragging = null;
                panning = false;
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 2 || moved || host == null || locked) return;
                FreeMap.Node n = hit(e.getX(), e.getY());
                if (n != null) { selected = n; host.onFreeEdit(n); }
                else host.onFreeAdd(toX(e.getX()), toY(e.getY()));
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                double r = e.getPreciseWheelRotation();
                boolean zoom = wheelZooms ? !e.isShiftDown() && !e.isControlDown() : e.isControlDown();
                if (zoom) {
                    float ns = (float) Math.max(.2, Math.min(4, scale * Math.pow(1.12, -r))), f = ns / scale;
                    tx = e.getX() - (e.getX() - tx) * f;
                    ty = e.getY() - (e.getY() - ty) * f;
                    scale = ns;
                } else if (e.isShiftDown()) tx -= r * 40; else ty -= r * 40;
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
        key(KeyStroke.getKeyStroke("ESCAPE"), () -> {
            if (linkFrom != null) { linkFrom = null; if (host != null) host.onFreeStatus("החיבור בוטל"); }
            else selected = null;
            repaint();
        });
        key(KeyStroke.getKeyStroke("F2"), () -> { if (selected != null && host != null && !locked) host.onFreeEdit(selected); });
        key(KeyStroke.getKeyStroke("DELETE"), () -> { if (selected != null && host != null && !locked) host.onFreeDelete(selected); });
        key(KeyStroke.getKeyStroke("CONTEXT_MENU"), this::menuAtSelection);
        key(KeyStroke.getKeyStroke("shift F10"), this::menuAtSelection);
    }

    private void key(KeyStroke k, Runnable r) {
        String id = "kv-" + k;
        getInputMap(WHEN_FOCUSED).put(k, id);
        getActionMap().put(id, new AbstractAction() { public void actionPerformed(ActionEvent e) { r.run(); } });
    }

    private void menuAtSelection() {
        if (selected == null || host == null) return;
        RoundRectangle2D.Float r = box(selected);
        host.onFreeMenu(selected, Math.round(tx + (r.x + r.width / 2) * scale), Math.round(ty + (r.y + r.height) * scale));
    }

    private void popup(FreeMap.Node n, MouseEvent e) {
        if (host == null) return;
        if (n != null) { selected = n; repaint(); host.onFreeMenu(n, e.getX(), e.getY()); }
        else host.onFreeBackground(toX(e.getX()), toY(e.getY()), e.getX(), e.getY());
    }

    void setMap(FreeMap m, Host h) {
        if (m != map) { selected = null; linkFrom = null; placed = false; }
        map = m;
        host = h;
        repaint();
    }

    FreeMap.Node selected() { return selected; }
    void select(FreeMap.Node n) { selected = n; repaint(); }

    /** מצב חיבור: הלחיצה הבאה על פריט מחברת אותו (או מסירה קו קיים). */
    void startLink(FreeMap.Node n) {
        linkFrom = n;
        if (host != null) host.onFreeStatus("בחר פריט לחיבור עם \"" + n.text + "\" (לחיצה על פריט שכבר מחובר — מסירה את הקו; Esc — ביטול)");
        repaint();
    }

    /** מרכז החלון ביחידות המפה (לפריט חדש מהתפריט). */
    float centerX() { return toX(getWidth() / 2f); }
    float centerY() { return toY(getHeight() / 3f); }

    private float toX(float sx) { return (sx - tx) / scale; }
    private float toY(float sy) { return (sy - ty) / scale; }

    private List<String> lines(FreeMap.Node n, FontMetrics fm) {
        String t = n.text.isEmpty() ? "(ריק)" : n.text;
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : t.split(" ")) {
            String cand = line.length() == 0 ? w : line + " " + w;
            if (fm.stringWidth(cand) > MAXW && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(w); }
            else line = new StringBuilder(cand);
        }
        out.add(line.toString());
        return out;
    }

    private RoundRectangle2D.Float box(FreeMap.Node n) {
        FontMetrics fm = scratch.createGraphics().getFontMetrics(font);
        List<String> ls = lines(n, fm);
        float w = 0;
        for (String l : ls) w = Math.max(w, fm.stringWidth(l));
        w += 2 * PAD;
        float h = ls.size() * fm.getHeight() + 2 * PAD;
        return new RoundRectangle2D.Float(n.x - w / 2, n.y - h / 2, w, h, 16, 16);
    }

    private FreeMap.Node hit(float sx, float sy) {
        if (map == null) return null;
        float mx = toX(sx), my = toY(sy);
        for (int i = map.nodes.size() - 1; i >= 0; i--) {
            FreeMap.Node n = map.nodes.get(i);
            if (box(n).contains(mx, my)) return n;
        }
        return null;
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        if (map == null) { g.dispose(); return; }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (!placed && getWidth() > 0) {
            placed = true;
            if (map.nodes.isEmpty()) { tx = getWidth() / 2f; ty = getHeight() / 3f; }
            else {
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
                for (FreeMap.Node n : map.nodes) {
                    minX = Math.min(minX, n.x); maxX = Math.max(maxX, n.x);
                    minY = Math.min(minY, n.y); maxY = Math.max(maxY, n.y);
                }
                tx = getWidth() / 2f - (minX + maxX) / 2;
                ty = getHeight() / 2f - (minY + maxY) / 2;
            }
        }
        if (map.nodes.isEmpty()) {
            g.setFont(font);
            g.setColor(new Color(0x888780));
            String hint = locked ? "אין פריטים." : "לחיצה כפולה בכל מקום — פריט חדש שם";
            float w = Ui.rtlWidth(hint, font, g.getFontRenderContext());
            Ui.drawRtl(g, hint, font, (getWidth() - w) / 2, getHeight() / 3f);
        }
        java.awt.geom.AffineTransform base = g.getTransform();
        g.translate(tx, ty);
        g.scale(scale, scale);
        g.setColor(LINE);
        g.setStroke(new BasicStroke(1.4f));
        for (FreeMap.Link l : map.links) {
            FreeMap.Node a = map.node(l.a), b = map.node(l.b);
            if (a != null && b != null) g.draw(new Line2D.Float(a.x, a.y, b.x, b.y));
        }
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics(font);
        for (FreeMap.Node n : map.nodes) {
            RoundRectangle2D.Float r = box(n);
            g.setColor(FILL);
            g.fill(r);
            g.setColor(STROKE);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(r);
            g.setColor(TEXT);
            float y = r.y + PAD + fm.getAscent();
            for (String l : lines(n, fm)) {
                g.drawString(l, r.x + (r.width - fm.stringWidth(l)) / 2, y);
                y += fm.getHeight();
            }
            if (n == selected || n == linkFrom) {
                float o = 3;
                g.setColor(SEL);
                g.setStroke(new BasicStroke(n == linkFrom ? 3f : 2.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10,
                        n == linkFrom ? new float[]{6, 4} : null, 0));
                g.draw(new RoundRectangle2D.Float(r.x - o, r.y - o, r.width + 2 * o, r.height + 2 * o, 16 + 2 * o, 16 + 2 * o));
            }
        }
        g.setTransform(base);
        g.dispose();
    }
}
