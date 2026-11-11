package com.aviccc.kvutzot.desktop;

import com.aviccc.kvutzot.model.Brain;
import com.aviccc.kvutzot.model.Hebrew;
import com.aviccc.kvutzot.model.Model.Sheet;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.util.function.Consumer;

/** "התחלת נושא" במחשב (כמו BrainView בטלפון): החלקים בזה אחר זה — כתבן, גיליון, פנקס. */
final class BrainPanel extends JPanel implements Scrollable {

    interface Listener {
        /** שינוי תוכן (הקלדה): לסמן ולשמור, בלי לבנות מחדש. */
        void changed();
        /** שינוי מבנה (שורה, עמודה, חלק): לשמור ולבנות מחדש. */
        void structure();
    }

    private static final Color BLUE = new Color(0x185FA5), GREY = new Color(0x888780), DARK = new Color(0x2C2C2A),
            FRAME = new Color(0xD3D1C7), STRUCK = new Color(0xF1EFE8);

    private final Window owner;
    private Brain brain;
    private Listener listener;
    private boolean locked;
    private Set<Brain.Kind> kinds;
    private Sheet.Erase erase = Sheet.Erase.BOTH;
    private Brain.Section pendingFocus;

    BrainPanel(Window owner) {
        this.owner = owner;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(10, 18, 60, 18));
    }

    public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
    public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 40; }
    public boolean getScrollableTracksViewportWidth() { return true; }
    public boolean getScrollableTracksViewportHeight() { return false; }

    void set(Brain b, Sheet props, Listener l) {
        brain = b;
        listener = l;
        locked = props.locked;
        kinds = props.kinds;
        erase = props.erase;
        build();
    }

    private void build() {
        removeAll();
        if (brain == null) return;
        for (int i = 0; i < brain.sections.size(); i++) {
            Brain.Section s = brain.sections.get(i);
            header(i, s);
            switch (s.kind) {
                case TEXT: textEditor(s); break;
                case GRID: gridEditor(s); break;
                case NOTEBOOK: notebook(s); break;
                default: break;
            }
        }
        if (locked) {
            JLabel lk = label("נעול — רק קריאה (מאפייני הטאב)", 13, GREY, false);
            lk.setBorder(BorderFactory.createEmptyBorder(18, 0, 8, 0));
            add(lk);
        } else {
            JLabel add = link("+ הוסף חלק", 15);
            add.setBorder(BorderFactory.createEmptyBorder(18, 0, 8, 0));
            add.addMouseListener(click(() -> addSectionMenu(add)));
            add(add);
        }
        applyComponentOrientation(Ui.RTL);
        revalidate();
        repaint();
    }

    @Override public Component add(Component c) {
        if (c instanceof JComponent) ((JComponent) c).setAlignmentX(Component.RIGHT_ALIGNMENT);
        return super.add(c);
    }

    private static JLabel label(String s, float size, Color c, boolean bold) {
        JLabel l = new JLabel(s);
        l.setFont(Ui.base.deriveFont(bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(c);
        return l;
    }

    private static JLabel link(String s, float size) {
        JLabel l = label(s, size, BLUE, false);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return l;
    }

    private static MouseAdapter click(Runnable r) {
        return new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) r.run(); }
        };
    }

    private static JMenuItem mi(String label, Runnable r) {
        JMenuItem i = new JMenuItem(label);
        i.addActionListener(e -> r.run());
        return i;
    }

    private void askMulti(String title, String initial, Consumer<String> then) { Ui.askMulti(owner, title, initial, then); }

    // ---------------- כותרת חלק ----------------

    private void header(final int idx, final Brain.Section s) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(idx == 0 ? 4 : 20, 0, 5, 0));
        String t = s.title.isEmpty() ? s.kind.label : s.title + "  ·  " + s.kind.label;
        row.add(label(t, 15, BLUE, true), BorderLayout.LINE_START);
        if (!locked) {
            JLabel more = link("…", 20);
            more.setToolTipText("פעולות על החלק");
            more.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 4));
            more.addMouseListener(click(() -> {
                JPopupMenu m = Ui.popup();
                m.add(mi("שם לחלק…", () -> Ui.ask(owner, "שם לחלק", s.title, false, x -> { s.title = x.trim(); listener.structure(); })));
                if (idx > 0) m.add(mi("הזז למעלה", () -> swap(idx, idx - 1)));
                if (idx < brain.sections.size() - 1) m.add(mi("הזז למטה", () -> swap(idx, idx + 1)));
                if (brain.sections.size() > 1) m.add(mi("מחק את החלק…", () -> Ui.confirm(owner,
                        "למחוק את החלק \"" + (s.title.isEmpty() ? s.kind.label : s.title) + "\" וכל מה שבו?", "מחק",
                        () -> { brain.sections.remove(s); listener.structure(); })));
                Ui.showUnder(m, more);
            }));
            row.add(more, BorderLayout.LINE_END);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        add(row);
    }

    private void swap(int a, int b) {
        Brain.Section x = brain.sections.get(a);
        brain.sections.set(a, brain.sections.get(b));
        brain.sections.set(b, x);
        listener.structure();
    }

    private void addSectionMenu(JComponent at) {
        JPopupMenu m = Ui.popup();
        for (final Brain.Kind k : Brain.Kind.values()) {
            if (!kinds.contains(k)) continue;
            m.add(mi(k.label, () -> { pendingFocus = brain.addSection(k); listener.structure(); }));
        }
        Ui.showUnder(m, at);
    }

    // ---------------- כתבן ----------------

    private void textEditor(final Brain.Section s) {
        JTextArea ed = new JTextArea(s.text);
        ed.setFont(Ui.base.deriveFont(16f));
        ed.setLineWrap(true);
        ed.setWrapStyleWord(true);
        ed.setRows(6);
        ed.setEditable(!locked);
        ed.setComponentOrientation(Ui.RTL);
        ed.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(FRAME), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        if (pendingFocus == s) {
            pendingFocus = null;
            SwingUtilities.invokeLater(ed::requestFocusInWindow);
        }
        ed.getDocument().addDocumentListener(new DocumentListener() {
            void now() { s.text = ed.getText(); listener.changed(); }
            public void insertUpdate(DocumentEvent e) { now(); }
            public void removeUpdate(DocumentEvent e) { now(); }
            public void changedUpdate(DocumentEvent e) { now(); }
        });
        add(ed);
    }

    // ---------------- גיליון ----------------

    private void gridEditor(final Brain.Section s) {
        AbstractTableModel model = new AbstractTableModel() {
            public int getRowCount() { return s.grid.size(); }
            public int getColumnCount() { return s.cols() + 1; }
            @Override public String getColumnName(int c) { return c == 0 ? "" : Hebrew.number(c); }
            public Object getValueAt(int r, int c) { return c == 0 ? String.valueOf(r + 1) : s.grid.get(r).get(c - 1); }
            @Override public boolean isCellEditable(int r, int c) { return !locked && c > 0; }
            @Override public void setValueAt(Object v, int r, int c) {
                if (c == 0 || r >= s.grid.size() || c - 1 >= s.grid.get(r).size()) return;
                String t = v == null ? "" : v.toString();
                if (t.equals(s.grid.get(r).get(c - 1))) return;
                s.grid.get(r).set(c - 1, t);
                listener.changed();
            }
        };
        JTable table = new JTable(model);
        table.setFont(Ui.base.deriveFont(14.5f));
        table.setRowHeight(Math.round(table.getFontMetrics(table.getFont()).getHeight() * 1.6f));
        table.getTableHeader().setFont(Ui.base.deriveFont(Font.BOLD, 13f));
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setCellSelectionEnabled(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setGridColor(FRAME);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setComponentOrientation(Ui.RTL);
        DefaultTableCellRenderer num = new DefaultTableCellRenderer();
        num.setHorizontalAlignment(JLabel.CENTER);
        num.setForeground(GREY);
        num.setBackground(new Color(0xF7F6F2));
        table.getColumnModel().getColumn(0).setCellRenderer(num);
        table.getColumnModel().getColumn(0).setPreferredWidth(36);
        DefaultTableCellRenderer cell = new DefaultTableCellRenderer();
        cell.setHorizontalAlignment(JLabel.RIGHT);
        for (int c = 1; c < table.getColumnCount(); c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(140);
            table.getColumnModel().getColumn(c).setCellRenderer(cell);
        }
        if (!locked) {
            MouseAdapter menu = new MouseAdapter() {
                void pop(MouseEvent e, boolean header) {
                    int c = table.columnAtPoint(e.getPoint());
                    int r = header ? -1 : table.rowAtPoint(e.getPoint());
                    JPopupMenu m = Ui.popup();
                    if (r >= 0) {
                        m.add(mi("הוסף שורה מעל", () -> { s.addRow(r); listener.structure(); }));
                        m.add(mi("הוסף שורה מתחת", () -> { s.addRow(r + 1); listener.structure(); }));
                        if (s.grid.size() > 1) m.add(mi("מחק את השורה " + (r + 1), () -> { s.removeRow(r); listener.structure(); }));
                    }
                    if (c > 0) {
                        if (r >= 0) m.addSeparator();
                        m.add(mi("הוסף עמודה לפני", () -> { s.addCol(c - 1); listener.structure(); }));
                        m.add(mi("הוסף עמודה אחרי", () -> { s.addCol(c); listener.structure(); }));
                        if (s.cols() > 1) m.add(mi("מחק את העמודה " + Hebrew.number(c), () -> { s.removeCol(c - 1); listener.structure(); }));
                    }
                    if (m.getComponentCount() == 0) return;
                    if (table.isEditing()) table.getCellEditor().stopCellEditing();
                    m.show(e.getComponent(), Math.max(0, e.getX() - m.getPreferredSize().width), e.getY());
                }
                @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) pop(e, e.getComponent() != table); }
                @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) pop(e, e.getComponent() != table); }
            };
            table.addMouseListener(menu);
            table.getTableHeader().addMouseListener(menu);
        }
        if (pendingFocus == s) {
            pendingFocus = null;
            SwingUtilities.invokeLater(() -> { table.requestFocusInWindow(); table.changeSelection(0, 1, false, false); });
        }
        JScrollPane sp = new JScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setComponentOrientation(Ui.RTL);
        sp.setBorder(BorderFactory.createLineBorder(FRAME));
        int h = table.getTableHeader().getPreferredSize().height + table.getRowHeight() * table.getRowCount() + 3;
        int w = table.getPreferredSize().width + 3;
        sp.setPreferredSize(new Dimension(w, h + (w > 900 ? 18 : 0)));
        sp.setMaximumSize(new Dimension(Math.max(w, 200), h + 18));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(sp, BorderLayout.LINE_START);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, h + 18));
        add(wrap);
        if (!locked) {
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
            btns.setOpaque(false);
            JLabel addRow = link("+ שורה", 14);
            addRow.setBorder(BorderFactory.createEmptyBorder(6, 10, 4, 10));
            addRow.addMouseListener(click(() -> { s.addRow(s.grid.size()); listener.structure(); }));
            JLabel addCol = link("+ עמודה", 14);
            addCol.setBorder(BorderFactory.createEmptyBorder(6, 10, 4, 18));
            addCol.addMouseListener(click(() -> { s.addCol(s.cols()); listener.structure(); }));
            btns.add(addRow);
            btns.add(addCol);
            btns.add(label("לחיצה ימנית על משבצת או על אות עמודה — הוספה ומחיקה", 12, GREY, false));
            btns.setMaximumSize(new Dimension(Integer.MAX_VALUE, btns.getPreferredSize().height));
            add(btns);
        }
    }

    // ---------------- פנקס ----------------

    private void notebook(final Brain.Section s) {
        for (int i = 0; i < s.lines.size(); i++) {
            final int idx = i;
            final Brain.Line ln = s.lines.get(i);
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setBackground(ln.struck ? STRUCK : Color.WHITE);
            row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(FRAME), BorderFactory.createEmptyBorder(7, 8, 7, 8)));
            JLabel num = label(String.valueOf(i + 1), 12, GREY, false);
            num.setPreferredSize(new Dimension(24, num.getPreferredSize().height));
            row.add(num, BorderLayout.LINE_START);
            // JTextPane — כי הוא מצייר פס על הטקסט (StrikeThrough), ו-JTextArea לא
            javax.swing.JTextPane tx = new javax.swing.JTextPane();
            tx.setEditable(false);
            tx.setFocusable(false);
            tx.setOpaque(false);
            tx.setComponentOrientation(Ui.RTL);
            javax.swing.text.SimpleAttributeSet a = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setFontFamily(a, Ui.base.getFamily());
            javax.swing.text.StyleConstants.setFontSize(a, 16);
            javax.swing.text.StyleConstants.setForeground(a, ln.struck ? GREY : DARK);
            javax.swing.text.StyleConstants.setStrikeThrough(a, ln.struck);
            javax.swing.text.StyleConstants.setAlignment(a, javax.swing.text.StyleConstants.ALIGN_RIGHT);
            try {
                javax.swing.text.StyledDocument doc = tx.getStyledDocument();
                doc.insertString(0, ln.text.isEmpty() ? "(ריקה)" : ln.text, a);
                doc.setParagraphAttributes(0, doc.getLength(), a, false);
            } catch (Exception ignored) { }
            row.add(tx, BorderLayout.CENTER);
            if (!locked) {
                MouseAdapter m = new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) lineMenu(s, idx, row); }
                    @Override public void mouseReleased(MouseEvent e) {
                        if (e.isPopupTrigger()) { lineMenu(s, idx, row); return; }
                        if (SwingUtilities.isLeftMouseButton(e))
                            askMulti("שורה " + (idx + 1), ln.text, x -> { ln.text = x; listener.structure(); });
                    }
                };
                row.addMouseListener(m);
                tx.addMouseListener(m);
                row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                tx.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            JPanel gap = new JPanel(new BorderLayout());
            gap.setOpaque(false);
            gap.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
            gap.add(row);
            add(gap);
        }
        if (locked) return;
        final JTextField add = new JTextField();
        add.setFont(Ui.base.deriveFont(15f));
        add.setComponentOrientation(Ui.RTL);
        add.setToolTipText("שורה חדשה — Enter מוסיף");
        add.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(FRAME), BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        add.addActionListener(e -> {
            String t = add.getText().trim();
            if (t.isEmpty()) return;
            s.lines.add(new Brain.Line(t, false));
            pendingFocus = s;
            listener.structure();
        });
        add.setMaximumSize(new Dimension(Integer.MAX_VALUE, add.getPreferredSize().height));
        JPanel gap = new JPanel(new BorderLayout());
        gap.setOpaque(false);
        gap.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        gap.add(add);
        gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, gap.getPreferredSize().height));
        add(gap);
        JLabel hint = label("שורה חדשה — כתיבה בשדה ו-Enter · לחיצה על שורה — עריכה · לחיצה ימנית — פס, הזזה, מחיקה", 12, GREY, false);
        hint.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        add(hint);
        if (pendingFocus == s) {
            pendingFocus = null;
            SwingUtilities.invokeLater(() -> {
                add.requestFocusInWindow();
                add.scrollRectToVisible(new Rectangle(0, 0, add.getWidth(), add.getHeight() + 30));
            });
        }
    }

    private void lineMenu(final Brain.Section s, final int i, JComponent at) {
        final Brain.Line ln = s.lines.get(i);
        final int n = s.lines.size();
        JPopupMenu m = Ui.popup();
        if (erase != Sheet.Erase.DELETE || ln.struck)
            m.add(mi(ln.struck ? "הסר את הפס" : "העבר פס (השורה נשארת)", () -> { ln.struck = !ln.struck; listener.structure(); }));
        if (i > 0) {
            m.add(mi("הזז למעלה", () -> { s.moveLine(i, i - 1); listener.structure(); }));
            m.add(mi("הזז לראש", () -> { s.moveLine(i, 0); listener.structure(); }));
        }
        if (i < n - 1) {
            m.add(mi("הזז למטה", () -> { s.moveLine(i, i + 1); listener.structure(); }));
            m.add(mi("הזז לסוף", () -> { s.moveLine(i, n - 1); listener.structure(); }));
        }
        if (n > 2) m.add(mi("הזז למקום…", () -> Ui.ask(owner, "למקום מספר (1–" + n + ")", String.valueOf(i + 1), false, x -> {
            try { s.moveLine(i, Math.max(1, Math.min(n, Integer.parseInt(x.trim()))) - 1); listener.structure(); }
            catch (NumberFormatException ignored) { }
        })));
        m.add(mi("הוסף שורה מעל…", () -> askMulti("שורה חדשה מעל " + (i + 1), "", x -> {
            if (!x.trim().isEmpty()) { s.lines.add(i, new Brain.Line(x.trim(), false)); listener.structure(); }
        })));
        if (erase != Sheet.Erase.STRIKE) m.add(mi("מחק לגמרי…", () -> Ui.confirm(owner, "למחוק את השורה לגמרי?\n" + ln.text, "מחק",
                () -> { s.lines.remove(ln); listener.structure(); })));
        Ui.showUnder(m, at);
    }
}
