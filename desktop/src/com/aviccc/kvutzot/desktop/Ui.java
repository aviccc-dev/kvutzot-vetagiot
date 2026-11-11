package com.aviccc.kvutzot.desktop;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** עזרים למסך: גופן, כפתורי "שבב" כמו בטלפון, שורות שגולשות, וחלונות שאלה מימין לשמאל. */
final class Ui {
    private Ui() {}

    static final ComponentOrientation RTL = ComponentOrientation.RIGHT_TO_LEFT;
    static final Color BLUE_DARK = new Color(0x0C447C), BLUE = new Color(0x378ADD), BLUE_BG = new Color(0xE6F1FB);
    static final Color GREY_LINE = new Color(0xD3D1C7), GREY_DASH = new Color(0xB4B2A9), INK = new Color(0x444441);
    static final Color STRIP = new Color(241, 239, 232);

    static Font base;

    /** גופן עם עברית: Segoe UI בחלונות, ואחריו Arial; אחרת — הגופן של Java. */
    static Font pickFont() {
        java.util.Set<String> have = new java.util.HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : new String[]{"Segoe UI", "Arial", "Tahoma", "Liberation Sans", "DejaVu Sans"})
            if (have.contains(f)) return new Font(f, Font.PLAIN, 14);
        return new Font(Font.DIALOG, Font.PLAIN, 14);
    }

    static <T extends Container> T rtl(T c) {
        c.applyComponentOrientation(RTL);
        return c;
    }

    // ---------------- שבב (כפתור מעוגל) ----------------

    /** כפתור מעוגל כמו בטלפון: on — מודגש בכחול; dashed — מסגרת מקווקוות (ברקע / מגירה). */
    static final class Chip extends JComponent {
        final String text;
        final boolean on, dashed;
        Runnable click, menu, close;
        private boolean hover, hoverClose;

        Chip(String text, boolean on, boolean dashed) {
            this.text = text;
            this.on = on;
            this.dashed = dashed;
            setFont(base.deriveFont(on ? Font.BOLD : Font.PLAIN, 13f));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; hoverClose = false; repaint(); }
                @Override public void mouseMoved(MouseEvent e) {
                    boolean h = close != null && e.getX() < 22;
                    if (h != hoverClose) { hoverClose = h; repaint(); }
                }
                @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger() && menu != null) menu.run(); }
                @Override public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger() && menu != null) { menu.run(); return; }
                    if (javax.swing.SwingUtilities.isMiddleMouseButton(e) && close != null) { close.run(); return; }
                    if (!javax.swing.SwingUtilities.isLeftMouseButton(e) || !contains(e.getPoint())) return;
                    if (close != null && e.getX() < 22) { close.run(); return; }
                    if (click != null) click.run();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        Chip onClick(Runnable r) { click = r; return this; }
        Chip onMenu(Runnable r) { menu = r; return this; }
        Chip onClose(Runnable r) { close = r; return this; }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(Math.round(rtlWidth(text, getFont(), fm.getFontRenderContext())) + 24 + (close != null ? 18 : 0), fm.getHeight() + 10);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth() - 1, h = getHeight() - 1;
            g.setColor(on ? BLUE_BG : hover ? new Color(0xF4F3EE) : Color.WHITE);
            g.fillRoundRect(0, 0, w, h, h, h);
            g.setColor(on ? BLUE : dashed ? GREY_DASH : GREY_LINE);
            if (dashed) g.setStroke(new java.awt.BasicStroke(1f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_ROUND, 10, new float[]{4, 3}, 0));
            g.drawRoundRect(0, 0, w, h, h, h);
            FontMetrics fm = g.getFontMetrics(getFont());
            g.setFont(getFont());
            g.setColor(on ? BLUE_DARK : INK);
            float textW = rtlWidth(text, getFont(), g.getFontRenderContext());
            float x = close != null ? 12 + 18 + (w - 24 - 18 - textW) / 2 : (w - textW) / 2;
            drawRtl(g, text, getFont(), x, (h - fm.getHeight()) / 2f + fm.getAscent() + 1);
            if (close != null) {
                g.setColor(hoverClose ? new Color(0xA32D2D) : new Color(0x888780));
                g.setStroke(new java.awt.BasicStroke(1.4f));
                int cx = 14, cy = h / 2;
                g.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
                g.drawLine(cx - 4, cy + 4, cx + 4, cy - 4);
            }
            g.dispose();
        }
    }

    // ---------------- טקסט מימין לשמאל ----------------

    /** פריסת טקסט שכיוונה הבסיסי מימין לשמאל — גם כשהיא מתחילה במילה באנגלית או במספר. */
    static java.awt.font.TextLayout rtlLayout(String s, Font f, java.awt.font.FontRenderContext frc) {
        java.text.AttributedString a = new java.text.AttributedString(s.isEmpty() ? " " : s);
        a.addAttribute(java.awt.font.TextAttribute.FONT, f);
        a.addAttribute(java.awt.font.TextAttribute.RUN_DIRECTION, java.awt.font.TextAttribute.RUN_DIRECTION_RTL);
        return new java.awt.font.TextLayout(a.getIterator(), frc);
    }

    static float rtlWidth(String s, Font f, java.awt.font.FontRenderContext frc) {
        return rtlLayout(s, f, frc).getAdvance();
    }

    static void drawRtl(Graphics2D g, String s, Font f, float x, float baseline) {
        rtlLayout(s, f, g.getFontRenderContext()).draw(g, x, baseline);
    }

    /** שורת טקסט אחת, מיושרת לימין, בכיוון מימין לשמאל (לשורת המצב). */
    static final class RtlLabel extends JComponent {
        private String text = "";
        RtlLabel(Font f, Color c) { setFont(f); setForeground(c); }
        void setText(String t) { text = t == null ? "" : t; setToolTipText(text.isEmpty() ? null : text); repaint(); }
        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(100, fm.getHeight() + 4);
        }
        @Override protected void paintComponent(Graphics g0) {
            if (text.isEmpty()) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(getForeground());
            FontMetrics fm = g.getFontMetrics(getFont());
            java.awt.font.TextLayout tl = rtlLayout(text, getFont(), g.getFontRenderContext());
            g.drawString("", 0, 0);
            tl.draw(g, getWidth() - tl.getAdvance(), (getHeight() - fm.getHeight()) / 2f + fm.getAscent());
            g.dispose();
        }
    }

    /** קו מפריד קטן בין קבוצות בשורה. */
    static JComponent gap() {
        JComponent v = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(GREY_LINE);
                g.fillRect(getWidth() / 2, 4, 1, getHeight() - 8);
            }
        };
        v.setPreferredSize(new Dimension(13, 26));
        return v;
    }

    // ---------------- שורה שגולשת לשורה נוספת כשאין מקום ----------------

    static final class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override public Dimension preferredLayoutSize(Container t) { return size(t, true); }
        @Override public Dimension minimumLayoutSize(Container t) {
            Dimension d = size(t, false);
            d.width -= getHgap() + 1;
            return d;
        }

        private Dimension size(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                Container c = target;
                while (c.getSize().width == 0 && c.getParent() != null) c = c.getParent();
                targetWidth = c.getSize().width;
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;
                int hgap = getHgap(), vgap = getVgap();
                Insets in = target.getInsets();
                int maxWidth = targetWidth - (in.left + in.right + hgap * 2);
                Dimension dim = new Dimension(0, 0);
                int rowW = 0, rowH = 0;
                for (int i = 0; i < target.getComponentCount(); i++) {
                    java.awt.Component m = target.getComponent(i);
                    if (!m.isVisible()) continue;
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowW + d.width > maxWidth) {
                        dim.width = Math.max(dim.width, rowW);
                        if (dim.height > 0) dim.height += vgap;
                        dim.height += rowH;
                        rowW = 0;
                        rowH = 0;
                    }
                    if (rowW != 0) rowW += hgap;
                    rowW += d.width;
                    rowH = Math.max(rowH, d.height);
                }
                dim.width = Math.max(dim.width, rowW);
                if (dim.height > 0) dim.height += vgap;
                dim.height += rowH;
                dim.width += in.left + in.right + hgap * 2;
                dim.height += in.top + in.bottom + vgap * 2;
                return dim;
            }
        }
    }

    static JPanel strip(Color bg) {
        JPanel p = new JPanel(new WrapLayout(FlowLayout.LEADING, 5, 3));
        p.setBackground(bg);
        p.setComponentOrientation(RTL);
        return p;
    }

    // ---------------- חלונות שאלה ----------------

    static JDialog dialog(Window owner, String title) {
        JDialog d = new JDialog(owner, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JRootPane rp = d.getRootPane();
        rp.registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        return d;
    }

    static JButton button(String text, Runnable r) {
        JButton b = new JButton(text);
        b.addActionListener(e -> r.run());
        return b;
    }

    static JPanel buttons(JButton... bs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        for (JButton b : bs) p.add(b);
        p.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        return p;
    }

    static void show(JDialog d, JComponent body, Window owner) {
        body.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        d.setContentPane(body);
        rtl(d.getContentPane());
        d.pack();
        d.setMinimumSize(new Dimension(Math.max(360, d.getWidth()), d.getHeight()));
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    /**
     * שאלת טקסט. withNext: כפתור "הבא" שמוסיף ומשאיר את החלון פתוח (הוספה ברצף), ו-Enter = הבא.
     * בלי withNext: Enter = סיום.
     */
    static void ask(Window owner, String title, String initial, boolean withNext, Consumer<String> then) {
        JDialog d = dialog(owner, title);
        JTextField f = new JTextField(initial, 32);
        f.setFont(base.deriveFont(15f));
        f.selectAll();
        JPanel body = new JPanel(new BorderLayout());
        body.add(f, BorderLayout.CENTER);
        JLabel hint = new JLabel(withNext ? "מוסיף ב-Enter, והחלון נשאר פתוח לבא · סגירה ב-Esc" : "אישור ב-Enter · ביטול ב-Esc");
        hint.setForeground(new Color(0x888780));
        hint.setFont(base.deriveFont(12f));
        hint.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        JButton done = button("סיום", () -> {
            String s = f.getText().trim();
            if (!s.isEmpty() || !withNext) then.accept(s);
            d.dispose();
        });
        Runnable next = () -> {
            String s = f.getText().trim();
            if (s.isEmpty()) return;
            then.accept(s);
            f.setText("");
            f.requestFocusInWindow();
        };
        JButton cancel = button("ביטול", d::dispose);
        f.addActionListener(e -> { if (withNext) next.run(); else done.doClick(); });
        JPanel south = new JPanel(new BorderLayout());
        south.add(hint, BorderLayout.NORTH);
        south.add(withNext ? buttons(button("הבא", next), done, cancel) : buttons(done, cancel), BorderLayout.SOUTH);
        body.add(south, BorderLayout.SOUTH);
        show(d, body, owner);
    }

    /** בחירה מתוך רשימה (עם עכבר או מקלדת). */
    static void choose(Window owner, String title, List<String> names, int sel, IntConsumer then) {
        JDialog d = dialog(owner, title);
        JList<String> list = new JList<>(names.toArray(new String[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(base.deriveFont(14f));
        list.setComponentOrientation(RTL);
        ((DefaultListCellRenderer) list.getCellRenderer()).setHorizontalAlignment(SwingConstants.RIGHT);
        if (sel >= 0) list.setSelectedIndex(sel); else if (!names.isEmpty()) list.setSelectedIndex(0);
        list.setVisibleRowCount(Math.min(18, Math.max(4, names.size())));
        Runnable ok = () -> {
            int w = list.getSelectedIndex();
            d.dispose();
            if (w >= 0) then.accept(w);
        };
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) ok.run(); }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ok");
        list.getActionMap().put("ok", new AbstractAction() { public void actionPerformed(ActionEvent e) { ok.run(); }});
        JPanel body = new JPanel(new BorderLayout());
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(Math.max(340, list.getPreferredSize().width + 40),
                Math.min(460, list.getPreferredScrollableViewportSize().height + 8)));
        body.add(sp, BorderLayout.CENTER);
        body.add(buttons(button("אישור", ok), button("ביטול", d::dispose)), BorderLayout.SOUTH);
        if (sel >= 0) list.ensureIndexIsVisible(sel);
        show(d, body, owner);
    }

    /** בחירה מתוך רשימה או שם חדש (לבחינות). */
    static void askCombo(Window owner, String title, String initial, List<String> names, String hintText, Consumer<String> then) {
        JDialog d = dialog(owner, title);
        JComboBox<String> box = new JComboBox<>(names.toArray(new String[0]));
        box.setEditable(true);
        box.setSelectedItem(initial);
        box.setFont(base.deriveFont(15f));
        box.setPrototypeDisplayValue("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        JButton ok = button("אישור", () -> {
            Object v = box.getEditor().getItem();
            d.dispose();
            then.accept(v == null ? "" : v.toString().trim());
        });
        ((JTextField) box.getEditor().getEditorComponent()).addActionListener(e -> ok.doClick());
        JPanel body = new JPanel(new BorderLayout());
        body.add(box, BorderLayout.NORTH);
        JLabel hint = new JLabel("<html><div dir=rtl style='width:320px'>" + hintText + "</div></html>");
        hint.setForeground(new Color(0x888780));
        hint.setFont(base.deriveFont(12f));
        hint.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        body.add(hint, BorderLayout.CENTER);
        body.add(buttons(ok, button("ביטול", d::dispose)), BorderLayout.SOUTH);
        show(d, body, owner);
    }

    /** שאלת כן/לא. */
    static void confirm(Window owner, String q, String yes, Runnable then) {
        JDialog d = dialog(owner, "אישור");
        JPanel body = new JPanel(new BorderLayout());
        JLabel l = new JLabel("<html><div dir=rtl style='width:340px'>" + esc(q) + "</div></html>");
        l.setFont(base.deriveFont(14f));
        body.add(l, BorderLayout.CENTER);
        JButton y = button(yes, () -> { d.dispose(); then.run(); });
        body.add(buttons(y, button("ביטול", d::dispose)), BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(y);
        show(d, body, owner);
    }

    /** הודעה עם כפתור סגירה. */
    static void message(Window owner, String title, String text) {
        JDialog d = dialog(owner, title);
        JPanel body = new JPanel(new BorderLayout());
        javax.swing.JTextArea a = new javax.swing.JTextArea(text);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setFont(base.deriveFont(14f));
        a.setColumns(36);
        a.setRows(Math.min(20, Math.max(2, text.split("\n").length + text.length() / 50)));
        body.add(new JScrollPane(a) {{ setBorder(null); setOpaque(false); getViewport().setOpaque(false); }}, BorderLayout.CENTER);
        JButton c = button("סגור", d::dispose);
        body.add(buttons(c), BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(c);
        show(d, body, owner);
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    static JPopupMenu popup() {
        JPopupMenu m = new JPopupMenu();
        m.setComponentOrientation(RTL);
        return m;
    }

    /** תפריט מתחת לרכיב, מיושר לימין שלו. */
    static void showUnder(JPopupMenu m, JComponent c) {
        m.applyComponentOrientation(RTL);
        m.show(c, c.getWidth() - m.getPreferredSize().width, c.getHeight());
    }

    static Box.Filler space(int w) { return (Box.Filler) Box.createHorizontalStrut(w); }
}
