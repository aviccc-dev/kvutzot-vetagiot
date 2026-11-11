package com.aviccc.kvutzot.desktop;

import com.aviccc.kvutzot.model.Edit;
import com.aviccc.kvutzot.model.Hebrew;
import com.aviccc.kvutzot.model.Model.Division;
import com.aviccc.kvutzot.model.Model.Info;
import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.Model.Tag;
import com.aviccc.kvutzot.model.TextLines;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * החלון על הפריט (כמו ItemWindow בטלפון): כל המידע עליו, וניתן לעריכה. כל שינוי נשמר מיד בפריט.
 * במחשב — לוח: מוצג בצד המפה (עוקב אחרי הבחירה), או בחלון נפרד; האפשרות בהגדרות.
 */
final class ItemPanel extends JPanel {

    interface Host {
        /** הפריט השתנה: לסמן, לשמור ולצייר מחדש. */
        void itemChanged(Item it);
        Window window();
    }

    private static final Color BLUE = new Color(0x185FA5), GREY = new Color(0x5F5E5A), LIGHT = new Color(0xF1EFE8),
            DARK = new Color(0x2C2C2A), HINT = new Color(0x888780), LINE = new Color(0xB5D4F4);

    private final Host host;
    private final Body body = new Body();
    private final JScrollPane scroll;
    private Project p;
    private Item it;
    private boolean readOnly;
    private String emptyText = "";
    /** פריטים שנפתחו מתוך הלוח (בחינה, או פריט שחילקו לפי בחינה) — כדי לחזור. */
    private final List<Item> back = new ArrayList<>();

    /** הגוף נמתח לרוחב הלוח, כדי שהטקסט יגלוש לשורות. */
    private static final class Body extends JPanel implements Scrollable {
        Body() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 30; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    ItemPanel(Host host) {
        super(new BorderLayout());
        this.host = host;
        setBackground(Color.WHITE);
        body.setBackground(Color.WHITE);
        body.setBorder(BorderFactory.createEmptyBorder(8, 14, 16, 14));
        scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getViewport().setBackground(Color.WHITE);
        add(scroll, BorderLayout.CENTER);
        setComponentOrientation(Ui.RTL);
    }

    Item item() { return it; }

    /** הצגת פריט. readOnly — בטאב נעול. */
    void show(Project p, Item it, boolean readOnly) {
        boolean same = this.it == it && this.p == p;
        this.p = p;
        this.it = it;
        this.readOnly = readOnly;
        if (!same) back.clear();
        rebuild(same);
    }

    /** בלי פריט: הסבר קצר. */
    void empty(String text) {
        it = null;
        back.clear();
        emptyText = text;
        rebuild(false);
    }

    private void open(Item other) {
        back.add(it);
        it = other;
        rebuild(false);
        scroll.getVerticalScrollBar().setValue(0);
    }

    private void changed() {
        host.itemChanged(it);
        rebuild(true);
    }

    // ---------------- בנייה ----------------

    private void rebuild(boolean keepScroll) {
        int y = scroll.getVerticalScrollBar().getValue();
        body.removeAll();
        if (it == null) {
            JTextArea t = area(emptyText, 14, HINT, false);
            t.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
            add(t);
            finish(keepScroll, y);
            return;
        }
        if (!back.isEmpty()) {
            final Item prev = back.get(back.size() - 1);
            JLabel b = link("→ חזרה אל \"" + name(prev) + "\"", 13, () -> { back.remove(back.size() - 1); it = prev; rebuild(false); });
            b.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
            add(b);
        }
        JTextArea name = area(name(it), 20, DARK, true);
        name.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
        if (!readOnly) {
            clickable(name, this::renameItem, null);
            name.setToolTipText("לחיצה — שינוי השם");
        }
        add(name);
        add(area(place(), 12.5f, GREY, false));
        if (readOnly) {
            JTextArea lk = area("הטאב נעול — רק קריאה (אפשר לשנות במאפייני הטאב).", 12.5f, HINT, false);
            add(lk);
        }

        aspects();

        Map<String, List<Info>> groups = new LinkedHashMap<>();
        for (Info in : it.infos) if (in.kind == Info.Kind.USER) groups.computeIfAbsent(in.name, k -> new ArrayList<>()).add(in);
        header("מידע", "+ מידע חדש", () -> editInfo(null, Info.Kind.USER, ""));
        if (groups.isEmpty()) add(hint("אין עדיין. למשל הגדרה, דוגמה, הערה."));
        for (final Map.Entry<String, List<Info>> g : groups.entrySet()) {
            String gname = g.getKey().isEmpty() ? "מידע" : g.getKey();
            sub(gname + (g.getValue().size() > 1 ? " (" + g.getValue().size() + ")" : ""), "+",
                    () -> editInfo(null, Info.Kind.USER, g.getKey()));
            for (final Info in : g.getValue()) row(in.text, () -> editInfo(in, in.kind, in.name), null);
        }

        header("תגיות", "+ תגית", this::addTag);
        if (it.tagIds.isEmpty()) add(hint("אין תגיות."));
        for (final String id : new ArrayList<>(it.tagIds)) {
            final Tag t = p.tag(id);
            if (t == null) continue;
            int others = Edit.tagUse(p, id) - 1;
            final JComponent[] r = new JComponent[1];
            r[0] = row("# " + t.name + (others > 0 ? "   ·   עוד " + others + " פריטים" : ""), () -> tagMenu(t, r[0]), () -> tagMenu(t, r[0]));
        }

        header("תכונות עצמיות (מחויבות)", "+ הוסף", () -> editInfo(null, Info.Kind.ESSENTIAL, ""));
        List<Info> ess = it.infos(Info.Kind.ESSENTIAL);
        if (ess.isEmpty()) add(hint("אין."));
        for (final Info in : ess) row(in.text, () -> editInfo(in, in.kind, ""), null);
        header("תכונות מקריות (שאינן מחויבות)", "+ הוסף", () -> editInfo(null, Info.Kind.INCIDENTAL, ""));
        List<Info> inc = it.infos(Info.Kind.INCIDENTAL);
        if (inc.isEmpty()) add(hint("אין."));
        for (final Info in : inc) row(in.text, () -> editInfo(in, in.kind, ""), null);
        finish(keepScroll, y);
    }

    private void finish(boolean keepScroll, int y) {
        body.applyComponentOrientation(Ui.RTL);
        body.revalidate();
        body.repaint();
        if (keepScroll) SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(y));
        else SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
    }

    private static String name(Item x) { return x == null || x.name.isEmpty() ? "(בלי שם)" : x.name; }

    private void add(JComponent c) {
        c.setAlignmentX(Component.RIGHT_ALIGNMENT);
        body.add(c);
    }

    /** האם הפריט הוא בחינה. */
    private String aspectIdOfItem() {
        for (Map.Entry<String, Item> e : p.aspects.entrySet()) if (e.getValue() == it) return e.getKey();
        return null;
    }

    private void aspects() {
        String aid = aspectIdOfItem();
        if (aid != null) {
            header("חילקו לפיה", "", null);
            List<Item> owners = new ArrayList<>();
            for (Item r : p.allRoots()) owners(r, aid, owners);
            if (owners.isEmpty()) add(hint("עוד לא חילקו לפיה."));
            for (final Item o : owners) {
                Item par = o.parent();
                row(o.name + (par == null ? "" : "   ·   תחת \"" + par.name + "\""), () -> open(o), null);
            }
            return;
        }
        header("בחינות", "+ חלוקה", () -> chooseAspect("חלוקה חדשה — לפי איזו בחינה?", "", s -> {
            Division d = Edit.addSide(it, "");
            Edit.setAspect(p, d, s);
            changed();
        }));
        final Division pd = it.parentDivision;
        if (pd != null) {
            sub("נחלק מאביו \"" + it.parent().name + "\" לפי", "", null);
            final JComponent[] r = new JComponent[1];
            r[0] = row(aspectLabel(pd), () -> aspectMenu(pd, r[0]), () -> aspectMenu(pd, r[0]));
        }
        boolean any = false;
        for (int k = 0; k < it.divisions.size(); k++) {
            final Division d = it.divisions.get(k);
            if (d.children.isEmpty() && d.aspectId.isEmpty() && d.basis.isEmpty()) continue;
            if (!any) sub("מחלק את בניו לפי", "", null);
            any = true;
            String side = it.divisions.size() > 1 ? "צד " + Hebrew.number(k + 1) + ": " : "";
            final JComponent[] r = new JComponent[1];
            r[0] = row(side + aspectLabel(d) + "   ·   " + d.children.size() + " בנים", () -> aspectMenu(d, r[0]), () -> aspectMenu(d, r[0]));
        }
        if (pd == null && !any) add(hint("אין חלוקות."));
    }

    private static void owners(Item x, String aid, List<Item> out) {
        for (Division d : x.divisions) {
            if (aid.equals(d.aspectId) && !out.contains(x)) out.add(x);
            for (Item c : d.children) owners(c, aid, out);
        }
    }

    private String aspectLabel(Division d) {
        if (!d.aspectId.isEmpty()) {
            Item a = p.aspect(d.aspectId);
            if (a != null) return a.name;
        }
        return d.basis.isEmpty() ? "(בלי בחינה)" : d.basis;
    }

    private void aspectMenu(final Division d, JComponent at) {
        final Item a = d.aspectId.isEmpty() ? null : p.aspect(d.aspectId);
        JPopupMenu m = Ui.popup();
        if (!readOnly) m.add(mi(a == null ? "קבע בחינה…" : "שנה בחינה לחלוקה הזאת…", () ->
                chooseAspect("לפי איזו בחינה?", a == null ? d.basis : a.name, s -> { Edit.setAspect(p, d, s); changed(); })));
        if (a != null) m.add(mi("פתח את הבחינה \"" + a.name + "\" (מידע עליה, ואיפה חילקו לפיה)", () -> open(a)));
        if (!readOnly && (a != null || !d.basis.isEmpty())) m.add(mi("בלי בחינה", () -> { Edit.setAspect(p, d, ""); changed(); }));
        if (m.getComponentCount() > 0) Ui.showUnder(m, at);
    }

    private void chooseAspect(String title, String initial, Consumer<String> then) {
        List<String> names = new ArrayList<>();
        for (Item x : p.aspects.values()) if (!names.contains(x.name)) names.add(x.name);
        Ui.askCombo(host.window(), title + (names.isEmpty() ? "" : " (קיימת או חדשה)"), initial, names,
                "בחינה קיימת — מהרשימה; שם חדש — בחינה חדשה.", s -> { if (!s.isEmpty()) then.accept(s); });
    }

    private String place() {
        for (Map.Entry<String, Item> e : p.aspects.entrySet())
            if (e.getValue() == it) {
                int use = p.aspectUse(e.getKey());
                return "בחינה · " + (use == 0 ? "עוד לא חילקו לפיה" : "חילקו לפיה " + use + (use == 1 ? " פעם" : " פעמים"))
                        + " · לחיצה על השם — שינוי בכל החלוקות";
            }
        StringBuilder b = new StringBuilder();
        Item par = it.parent();
        if (par != null) {
            b.append("תחת \"").append(par.name).append("\"");
            Division d = it.parentDivision;
            if (!d.basis.isEmpty()) b.append(" · ").append(d.basis);
        } else b.append(it.loose ? "פריט תלוש" : "ראש המפה");
        int depth = 0;
        for (Item x = par; x != null; x = x.parent()) depth++;
        b.append(" · עומק ").append(depth);
        b.append(" · ").append(it.childCount()).append(" בנים");
        int all = TextLines.descendants(it);
        if (all > it.childCount()) b.append(" (").append(all).append(" בסך הכול)");
        return b.toString();
    }

    // ---------------- רכיבים ----------------

    private static JTextArea area(String s, float size, Color c, boolean bold) {
        JTextArea t = new JTextArea(s);
        t.setEditable(false);
        t.setFocusable(false);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setOpaque(false);
        t.setForeground(c);
        t.setFont(Ui.base.deriveFont(bold ? Font.BOLD : Font.PLAIN, size));
        t.setComponentOrientation(Ui.RTL);
        return t;
    }

    private static JTextArea hint(String s) {
        JTextArea t = area(s, 13, HINT, false);
        t.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        return t;
    }

    private JLabel link(String s, float size, Runnable r) {
        JLabel l = new JLabel(s);
        l.setFont(Ui.base.deriveFont(size));
        l.setForeground(BLUE);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) r.run(); }
        });
        return l;
    }

    private static void clickable(JComponent c, Runnable click, Runnable menu) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger() && menu != null) menu.run(); }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) { if (menu != null) menu.run(); return; }
                if (SwingUtilities.isLeftMouseButton(e)) click.run();
            }
        });
    }

    private void header(String title, String action, Runnable onAdd) {
        JPanel l = new JPanel(new BorderLayout());
        l.setOpaque(false);
        l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(16, 0, 4, 0),
                BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE), BorderFactory.createEmptyBorder(0, 0, 3, 0))));
        JLabel t = new JLabel(title);
        t.setFont(Ui.base.deriveFont(Font.BOLD, 15f));
        t.setForeground(BLUE);
        l.add(t, BorderLayout.LINE_START);
        if (onAdd != null && !action.isEmpty() && !readOnly) l.add(link(action, 14, onAdd), BorderLayout.LINE_END);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, l.getPreferredSize().height));
        add(l);
    }

    private void sub(String title, String action, Runnable onAdd) {
        JPanel l = new JPanel(new BorderLayout());
        l.setOpaque(false);
        l.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JLabel t = new JLabel(title);
        t.setFont(Ui.base.deriveFont(Font.BOLD, 13.5f));
        t.setForeground(GREY);
        l.add(t, BorderLayout.LINE_START);
        if (onAdd != null && !action.isEmpty() && !readOnly) l.add(link(action, 16, onAdd), BorderLayout.LINE_END);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, l.getPreferredSize().height));
        add(l);
    }

    /** שורה ברקע בהיר; לחיצה — פעולה (בטאב נעול — כלום). */
    private JComponent row(String s, Runnable click, Runnable menu) {
        JTextArea t = area(s.isEmpty() ? "(ריק)" : s, 14.5f, DARK, false);
        t.setOpaque(true);
        t.setBackground(LIGHT);
        t.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        if (!readOnly) clickable(t, click, menu);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        wrap.add(t, BorderLayout.CENTER);
        add(wrap);
        return t;
    }

    private static JMenuItem mi(String label, Runnable r) {
        JMenuItem i = new JMenuItem(label);
        i.addActionListener(e -> r.run());
        return i;
    }

    // ---------------- פעולות ----------------

    private void renameItem() {
        Ui.ask(host.window(), "שם הפריט", it.name, false, s -> {
            if (s.isEmpty()) return;
            String aid = aspectIdOfItem();
            if (aid != null) Edit.renameAspect(p, aid, s); else it.name = s;
            changed();
        });
    }

    /** הוספה (in == null) או עריכה של מידע. במידע של המשתמש — גם שם התא, עם הצעות. */
    private void editInfo(final Info in, final Info.Kind kind, String name) {
        Window owner = host.window();
        String title = in != null ? "עריכה" : kind == Info.Kind.ESSENTIAL ? "תכונה עצמית חדשה"
                : kind == Info.Kind.INCIDENTAL ? "תכונה מקרית חדשה" : "מידע חדש";
        JDialog d = Ui.dialog(owner, title);
        JPanel box = new JPanel(new BorderLayout(0, 8));
        final JComboBox<String> nameField;
        if (kind == Info.Kind.USER) {
            nameField = new JComboBox<>(Edit.userInfoNames(p).toArray(new String[0]));
            nameField.setEditable(true);
            nameField.setSelectedItem(name);
            nameField.setFont(Ui.base.deriveFont(14.5f));
            JPanel top = new JPanel(new BorderLayout(8, 0));
            top.add(new JLabel("שם (למשל הגדרה, דוגמה):"), BorderLayout.LINE_START);
            top.add(nameField, BorderLayout.CENTER);
            box.add(top, BorderLayout.NORTH);
        } else nameField = null;
        JTextArea text = new JTextArea(in == null ? "" : in.text, 6, 36);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(Ui.base.deriveFont(15f));
        text.setComponentOrientation(Ui.RTL);
        box.add(new JScrollPane(text), BorderLayout.CENTER);
        JLabel h = new JLabel(kind == Info.Kind.USER ? "התוכן · שמירה ב-Ctrl+Enter" : "התכונה · שמירה ב-Ctrl+Enter");
        h.setForeground(HINT);
        h.setFont(Ui.base.deriveFont(12f));
        Runnable save = () -> {
            String t = text.getText().trim();
            String n = nameField == null ? "" : String.valueOf(nameField.getEditor().getItem()).trim();
            d.dispose();
            if (t.isEmpty()) return;
            if (in == null) Edit.addInfo(it, kind, n, t);
            else {
                in.text = t;
                if (kind == Info.Kind.USER && !n.equals(in.name)) {
                    it.infos.remove(in);
                    Info moved = Edit.addInfo(it, kind, n, t);
                    it.infos.set(it.infos.indexOf(moved), in);
                    in.name = n;
                }
            }
            changed();
        };
        text.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "save");
        text.getActionMap().put("save", new javax.swing.AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { save.run(); } });
        List<JButton> bs = new ArrayList<>();
        bs.add(Ui.button("שמור", save));
        if (in != null) {
            if (kind == Info.Kind.USER) bs.add(Ui.button("מחק", () -> { d.dispose(); it.infos.remove(in); changed(); }));
            else {
                final Info.Kind other = kind == Info.Kind.ESSENTIAL ? Info.Kind.INCIDENTAL : Info.Kind.ESSENTIAL;
                bs.add(Ui.button(kind == Info.Kind.ESSENTIAL ? "העבר למקריות" : "העבר לעצמיות", () -> {
                    String t = text.getText().trim();
                    if (!t.isEmpty()) in.text = t;
                    d.dispose();
                    Edit.setKind(it, in, other);
                    changed();
                }));
                bs.add(Ui.button("מחק את התכונה", () -> { d.dispose(); it.infos.remove(in); changed(); }));
            }
        }
        bs.add(Ui.button("ביטול", d::dispose));
        JPanel south = new JPanel(new BorderLayout());
        south.add(h, BorderLayout.NORTH);
        south.add(Ui.buttons(bs.toArray(new JButton[0])), BorderLayout.SOUTH);
        box.add(south, BorderLayout.SOUTH);
        SwingUtilities.invokeLater(() -> { if (nameField != null && name.isEmpty() && in == null) nameField.requestFocusInWindow(); else text.requestFocusInWindow(); });
        Ui.show(d, box, owner);
    }

    private void addTag() {
        List<String> names = new ArrayList<>();
        for (Tag t : p.tags) if (!it.tagIds.contains(t.id)) names.add(t.name);
        Ui.askCombo(host.window(), "תגית" + (names.isEmpty() ? " חדשה" : " (קיימת או חדשה)"), "", names,
                names.isEmpty() ? "תגית חדשה תישמר ברשימת התגיות של הקובץ." : "תגית קיימת — מהרשימה; שם חדש — תגית חדשה.", s -> {
                    if (!s.isEmpty()) { Edit.addTag(p, it, s); changed(); }
                });
    }

    private void tagMenu(final Tag t, JComponent at) {
        int use = Edit.tagUse(p, t.id);
        JPopupMenu m = Ui.popup();
        m.add(mi("הסר מהפריט הזה", () -> { Edit.removeTag(it, t.id); changed(); }));
        m.add(mi("שנה שם (בכל " + use + " הפריטים)…", () -> Ui.ask(host.window(), "שם חדש לתגית — אם כבר יש תגית בשם הזה, שתיהן יתאחדו",
                t.name, false, s -> { if (!s.isEmpty()) { Edit.renameTag(p, t, s); changed(); } })));
        Ui.showUnder(m, at);
    }

    /** הלוח בחלון נפרד (כשבהגדרות נבחר "חלון נפרד"). */
    static void showWindow(Window owner, Host host, Project p, Item it, boolean readOnly) {
        JDialog d = Ui.dialog(owner, name(it));
        ItemPanel panel = new ItemPanel(host);
        panel.show(p, it, readOnly);
        JPanel body = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(460, 560));
        body.add(panel, BorderLayout.CENTER);
        JButton close = Ui.button("סגור", d::dispose);
        body.add(Ui.buttons(close), BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(close);
        d.setResizable(true);
        Ui.show(d, body, owner);
    }
}
