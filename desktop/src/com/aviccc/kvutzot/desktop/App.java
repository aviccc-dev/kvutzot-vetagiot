package com.aviccc.kvutzot.desktop;

import com.aviccc.kvutzot.model.Brain;
import com.aviccc.kvutzot.model.Edit;
import com.aviccc.kvutzot.model.Hebrew;
import com.aviccc.kvutzot.model.MapLayout;
import com.aviccc.kvutzot.model.Model.Division;
import com.aviccc.kvutzot.model.Model.Info;
import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.Model.Sheet;
import com.aviccc.kvutzot.model.Model.Tag;
import com.aviccc.kvutzot.model.OpmlIO;
import com.aviccc.kvutzot.model.Sheets;
import com.aviccc.kvutzot.model.TextLines;
import com.aviccc.kvutzot.model.Views;
import com.aviccc.kvutzot.model.Zoom;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * קבוצות ותגיות — הגרסה למחשב (חלונות). אותו מוח (model) כמו בטלפון, מסך שנכתב מחדש ב-Swing.
 * "בעיקרון כמו בטלפון, רק צריך התאמות: שם אין מגע, ויש מקלדת ועכבר; התפריטים מקובלים אחרת; יש יותר מקום."
 */
public final class App implements MapPanel.Host, FreePanel.Host, ItemPanel.Host {

    /** קובץ פתוח (טאב). כל המצב של הטאב נשמר כאן. */
    static final class Doc {
        Path path;
        String fileName = "";
        String lastText;
        Project project;
        String sheetId = "";
        boolean dirty;
        int viewMode, tagLevel = 1, levelFrom = 1, levelTo = 1;
        String viewInfo = "";
        Views.Sort viewSort = Views.Sort.MAP;
        boolean spread = true;
        Views.Result viewResult;
        boolean loading;

        String label() {
            if (project != null && !project.title.isEmpty()) return project.title;
            String f = fileName == null || fileName.isEmpty() ? "קובץ" : fileName;
            return f.endsWith(".xml") ? f.substring(0, f.length() - 4) : f;
        }
    }

    private final Preferences prefs = Preferences.userRoot().node("aviccc/kvutzot");
    private final List<Doc> docs = new ArrayList<>();
    private Doc cur;
    private Item clip;

    private JFrame frame;
    private JPanel tabRow, sheetRow;
    private Ui.RtlLabel status;
    private JButton zoomLabel;
    private JPanel zoomBar;
    private CardLayout cards;
    private JPanel center;
    private MapPanel map;
    private JTextPane text;
    private JLabel welcome;
    private BrainPanel brainPanel;
    private JScrollPane brainScroll;
    private FreePanel freePanel;
    /** חלון הפריט: לוח בצד המפה (עוקב אחרי הבחירה) או חלון נפרד — לפי ההגדרות. */
    private ItemPanel side;
    private javax.swing.JSplitPane split;
    private JPanel centerWrap;
    private boolean sideMode, sideShown;
    private Project shownP;
    private boolean showMap, vertical, autosave, asking;
    private float zoomStep;
    private final Timer saver = new Timer(2000, e -> autoSaveAll());

    static String version() {
        try (InputStream in = App.class.getResourceAsStream("/version.txt")) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) { }
        return "?";
    }

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new App().start(args));
    }

    private void start(String[] args) {
        Ui.base = Ui.pickFont();
        UIManager.put("ToolTip.font", Ui.base.deriveFont(13f));
        showMap = prefs.getBoolean("showMap", true);
        vertical = prefs.getBoolean("vertical", true);
        autosave = prefs.getBoolean("autosave", true);
        zoomStep = Zoom.clampStep(prefs.getFloat("zoomStep", 0.5f));
        saver.setRepeats(false);

        frame = new JFrame("קבוצות ותגיות");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        try {
            InputStream in = App.class.getResourceAsStream("/icon.png");
            if (in != null) frame.setIconImage(javax.imageio.ImageIO.read(in));
        } catch (Exception ignored) { }

        JPanel root = new JPanel(new BorderLayout());
        JPanel top = new JPanel(new BorderLayout());
        tabRow = Ui.strip(Ui.STRIP);
        sheetRow = Ui.strip(Color.WHITE);
        top.add(tabRow, BorderLayout.NORTH);
        top.add(sheetRow, BorderLayout.CENTER);
        JPanel sep = new JPanel();
        sep.setBackground(Ui.GREY_LINE);
        sep.setPreferredSize(new Dimension(1, 1));
        top.add(sep, BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);

        map = new MapPanel(Ui.base);
        map.setHost(this);
        map.wheelZooms = !"scroll".equals(prefs.get("wheel", "zoom"));
        text = new JTextPane();
        text.setEditable(false);
        text.setComponentOrientation(Ui.RTL);
        text.setMargin(new Insets(10, 14, 40, 14));
        brainPanel = new BrainPanel(null);
        brainScroll = new JScrollPane(brainPanel);
        brainScroll.setBorder(null);
        brainScroll.getVerticalScrollBar().setUnitIncrement(18);
        brainScroll.setComponentOrientation(Ui.RTL);
        brainScroll.getViewport().setBackground(Color.WHITE);
        freePanel = new FreePanel(Ui.base);
        freePanel.wheelZooms = map.wheelZooms;
        welcome = new JLabel("<html><div dir=rtl style='text-align:center'>פתח קובץ (Ctrl+O), צור קובץ חדש (Ctrl+N),<br>או פתח דוגמה מתפריט \"קובץ\".</div></html>", JLabel.CENTER);
        welcome.setFont(Ui.base.deriveFont(16f));
        welcome.setForeground(new Color(0x5F5E5A));
        cards = new CardLayout();
        center = new JPanel(cards);
        center.add(map, "map");
        JScrollPane ts = new JScrollPane(text);
        ts.setBorder(null);
        ts.getVerticalScrollBar().setUnitIncrement(18);
        center.add(ts, "text");
        center.add(brainScroll, "brain");
        center.add(freePanel, "free");
        center.add(welcome, "welcome");
        center.setBackground(Color.WHITE);
        side = new ItemPanel(this);
        side.setMinimumSize(new Dimension(220, 100));
        sideMode = !"window".equals(prefs.get("itemPane", "side"));
        sideShown = prefs.getBoolean("sideShown", true);
        split = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT);
        split.setBorder(null);
        split.setResizeWeight(1);
        split.setContinuousLayout(true);
        split.setDividerSize(6);
        centerWrap = new JPanel(new BorderLayout());
        centerWrap.add(center, BorderLayout.CENTER);
        root.add(centerWrap, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.GREY_LINE),
                BorderFactory.createEmptyBorder(3, 10, 3, 10)));
        status = new Ui.RtlLabel(Ui.base.deriveFont(12.5f), new Color(0x5F5E5A));
        bottom.add(status, BorderLayout.CENTER);
        zoomBar = new JPanel(new FlowLayout(FlowLayout.LEADING, 2, 0));
        zoomBar.setOpaque(false);
        JButton minus = flat(Ui.glyph("−", "-"), "הקטן (Ctrl+מינוס)");
        minus.addActionListener(e -> map.setZoom(Zoom.next(map.getZoom(), zoomStep, -1)));
        zoomLabel = flat("1", "הגדלה — בחירה מרשימה");
        zoomLabel.setPreferredSize(new Dimension(56, zoomLabel.getPreferredSize().height));
        zoomLabel.addActionListener(e -> zoomMenu());
        JButton plus = flat("+", "הגדל (Ctrl+שווה)");
        plus.addActionListener(e -> map.setZoom(Zoom.next(map.getZoom(), zoomStep, 1)));
        JButton fitB = flat("התאם", "התאם לחלון (Ctrl+0)");
        fitB.addActionListener(e -> map.fit());
        zoomBar.add(fitB);
        zoomBar.add(plus);
        zoomBar.add(zoomLabel);
        zoomBar.add(minus);
        bottom.add(zoomBar, BorderLayout.LINE_END);
        root.add(bottom, BorderLayout.SOUTH);

        brainPanel = new BrainPanel(frame);
        brainScroll.setViewportView(brainPanel);
        frame.setContentPane(root);
        frame.setJMenuBar(buildMenus());
        Ui.rtl(frame.getContentPane());
        Ui.rtl(frame.getJMenuBar());
        // המפה מציירת מימין לשמאל בעצמה; כיוון הרכיב לא משנה בה
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { quit(); }
            @Override public void windowActivated(WindowEvent e) { checkExternalChange(); }
        });
        restoreBounds();
        frame.setVisible(true);
        restoreTabs(args);
    }

    private static JButton flat(String s, String tip) {
        JButton b = new JButton(s);
        b.setFont(Ui.base.deriveFont(Font.BOLD, 13f));
        b.setForeground(Ui.BLUE_DARK);
        b.setFocusable(false);
        b.setMargin(new Insets(1, 8, 1, 8));
        b.setToolTipText(tip);
        return b;
    }

    // ---------------- תפריטים ----------------

    private JMenuItem item(JMenu m, String label, KeyStroke key, Runnable r) {
        JMenuItem i = new JMenuItem(label);
        if (key != null) i.setAccelerator(key);
        i.addActionListener(e -> r.run());
        m.add(i);
        return i;
    }

    private static KeyStroke ctrl(int k) { return KeyStroke.getKeyStroke(k, InputEvent.CTRL_DOWN_MASK); }

    private JMenuBar buildMenus() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("קובץ");
        item(file, "קובץ חדש…", ctrl(KeyEvent.VK_N), this::newFile);
        item(file, "פתח…", ctrl(KeyEvent.VK_O), this::openFile);
        file.addSeparator();
        JMenuItem save = item(file, "שמור", ctrl(KeyEvent.VK_S), () -> { if (cur != null) save(cur); });
        JMenuItem saveAs = item(file, "שמור עותק בשם…", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), this::saveAs);
        JMenuItem reload = item(file, "טען מחדש מהדיסק", null, this::reload);
        JMenuItem export = item(file, "ייצוא ל-OPML (לתוכנות אחרות)…", null, this::exportOpml);
        JMenuItem title = item(file, "שנה כותרת…", null, () -> {
            if (cur == null || cur.project == null) return;
            Ui.ask(frame, "כותרת הקובץ", cur.project.title, false, s -> {
                cur.project.title = s;
                cur.project.start.title = s;
                changed(null, null);
            });
        });
        file.addSeparator();
        JMenu ex = new JMenu("דוגמאות");
        item(ex, "משולש", null, () -> example("triangle.xml", "משולש"));
        item(ex, "צעצועים", null, () -> example("toys.xml", "צעצועים"));
        file.add(ex);
        file.addSeparator();
        JMenuItem close = item(file, "סגור את הטאב", ctrl(KeyEvent.VK_W), () -> closeTab(cur));
        item(file, "יציאה", KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK), this::quit);
        file.addMenuListener(new MenuListener() {
            public void menuSelected(MenuEvent e) {
                boolean has = cur != null && cur.project != null;
                save.setEnabled(has && cur.path != null);
                saveAs.setEnabled(has);
                reload.setEnabled(cur != null && cur.path != null);
                export.setEnabled(has);
                title.setEnabled(has);
                close.setEnabled(cur != null);
            }
            public void menuDeselected(MenuEvent e) { }
            public void menuCanceled(MenuEvent e) { }
        });
        bar.add(file);

        JMenu edit = new JMenu("עריכה");
        edit.addMenuListener(new MenuListener() {
            public void menuSelected(MenuEvent e) {
                edit.removeAll();
                MapLayout.Node n = map.selectedNode();
                Sheet es = cur == null ? null : sheet(cur);
                if (es != null && es.kind == Sheet.Kind.FREE) {
                    if (!es.locked) edit.add(mi("פריט חדש…", () -> onFreeAdd(freePanel.centerX(), freePanel.centerY())));
                    com.aviccc.kvutzot.model.FreeMap.Node fn = freePanel.selected();
                    if (fn != null) { edit.addSeparator(); fillFreeMenu(edit, fn); }
                } else if (es != null && es.kind == Sheet.Kind.START) {
                    JMenuItem none = new JMenuItem(es.locked ? "הטאב נעול — רק קריאה" : "עורכים ישירות בחלקים; \"…\" ליד כל חלק — פעולות");
                    none.setEnabled(false);
                    edit.add(none);
                } else if (cur == null || cur.project == null || !onMap()) {
                    JMenuItem none = new JMenuItem("אין מפה פתוחה");
                    none.setEnabled(false);
                    edit.add(none);
                } else if (n == null) {
                    JMenuItem none = new JMenuItem("בחר פריט במפה (לחיצה עליו)");
                    none.setEnabled(false);
                    edit.add(none);
                    backgroundItems(edit);
                } else fillNodeMenu(edit, n);
                Ui.rtl(edit.getPopupMenu());
            }
            public void menuDeselected(MenuEvent e) { }
            public void menuCanceled(MenuEvent e) { }
        });
        bar.add(edit);

        JMenu view = new JMenu("תצוגה");
        view.addMenuListener(new MenuListener() {
            public void menuSelected(MenuEvent e) { fillViewMenu(view); Ui.rtl(view.getPopupMenu()); }
            public void menuDeselected(MenuEvent e) { }
            public void menuCanceled(MenuEvent e) { }
        });
        bar.add(view);

        JMenu tabs = new JMenu("טאבים");
        tabs.addMenuListener(new MenuListener() {
            public void menuSelected(MenuEvent e) { fillSheetMenu(tabs); Ui.rtl(tabs.getPopupMenu()); }
            public void menuDeselected(MenuEvent e) { }
            public void menuCanceled(MenuEvent e) { }
        });
        bar.add(tabs);

        JMenu tools = new JMenu("כלים");
        item(tools, "הגדרות…", null, this::settings);
        bar.add(tools);

        JMenu help = new JMenu("עזרה");
        item(help, "עכבר ומקלדת", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), this::helpKeys);
        item(help, "אודות", null, () -> Ui.message(frame, "קבוצות ותגיות",
                "גרסה למחשב " + version() + "\nמבלגן לסדר.\n\nאותו קובץ עובד גם בטלפון וגם במחשב."));
        bar.add(help);

        // קיצורים שאינם בתפריט (פועלים בכל החלון)
        JComponent rp = frame.getRootPane();
        bind(rp, ctrl(KeyEvent.VK_0), map::fit);
        bind(rp, KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), () -> { if (sideMode && onMap()) toggleSide(); });
        bind(rp, ctrl(KeyEvent.VK_EQUALS), () -> map.setZoom(Zoom.next(map.getZoom(), zoomStep, 1)));
        bind(rp, ctrl(KeyEvent.VK_ADD), () -> map.setZoom(Zoom.next(map.getZoom(), zoomStep, 1)));
        bind(rp, ctrl(KeyEvent.VK_MINUS), () -> map.setZoom(Zoom.next(map.getZoom(), zoomStep, -1)));
        bind(rp, ctrl(KeyEvent.VK_SUBTRACT), () -> map.setZoom(Zoom.next(map.getZoom(), zoomStep, -1)));
        bind(rp, ctrl(KeyEvent.VK_TAB), () -> cycleDoc(1));
        bind(rp, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), () -> cycleDoc(-1));
        bind(rp, ctrl(KeyEvent.VK_PAGE_DOWN), () -> cycleSheet(1));
        bind(rp, ctrl(KeyEvent.VK_PAGE_UP), () -> cycleSheet(-1));
        // מקשים של המפה: פועלים רק כשהמפה בפוקוס, כדי לא להפריע להקלדה בשדות
        map.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), () -> { MapLayout.Node n = map.selectedNode(); if (n != null) onOpen(n); });
        map.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), () -> { MapLayout.Node n = map.selectedNode(); if (n != null && editable()) deleteNode(n); });
        map.bindKey(ctrl(KeyEvent.VK_C), () -> { MapLayout.Node n = map.selectedNode(); if (n != null && !n.isDivision() && onMap()) copy(n.item); });
        map.bindKey(ctrl(KeyEvent.VK_X), () -> { MapLayout.Node n = map.selectedNode(); if (n != null && !n.isDivision() && editable()) cut(n.item); });
        map.bindKey(ctrl(KeyEvent.VK_V), () -> { MapLayout.Node n = map.selectedNode(); if (n != null && editable() && clip != null) paste(n); });
        map.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), this::menuAtSelection);
        map.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK), this::menuAtSelection);
        map.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), () -> map.select(null, false));
        map.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK), () -> { MapLayout.Node n = map.selectedNode(); if (n != null) details(n); });
        return bar;
    }

    private static void bind(JComponent c, KeyStroke k, Runnable r) {
        String key = "kv-" + k;
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(k, key);
        c.getActionMap().put(key, new javax.swing.AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { r.run(); }
        });
    }

    private void menuAtSelection() {
        MapLayout.Node n = map.selectedNode();
        if (n == null || !onMap()) return;
        java.awt.Point p = map.screenPoint(n);
        onMenu(n, p.x, p.y);
    }

    private void helpKeys() {
        Ui.message(frame, "עכבר ומקלדת",
                "במפה:\n"
                + "• לחיצה — בחירת פריט.\n"
                + "• לחיצה כפולה על פריט — שינוי השם; על חלוקה — בחירת בחינה.\n"
                + "• לחיצה ימנית — כל הפעולות על הפריט (כמו … בטלפון). גם תפריט \"עריכה\" למעלה.\n"
                + "• לוח הפריט בצד מראה את הפריט הנבחר: מידע, תגיות, תכונות ובחינות. F4 — הסתרה והחזרה.\n"
                + "  בהגדרות אפשר לבחור חלון נפרד במקום הלוח (נפתח ב-Alt+Enter).\n"
                + "• גרירה של פריט — הזזת הענף שלו (נשמרת בקובץ). גרירה ברקע — הזזת המפה.\n"
                + "• גלגלת — הגדלה סביב העכבר (בהגדרות אפשר לבחור שהגלגלת גוללת). Shift+גלגלת — גלילה לרוחב.\n"
                + "• לחיצה כפולה ברקע, או Ctrl+0 — התאמה לחלון.\n\n"
                + "מקלדת במפה (כמו במפות חשיבה):\n"
                + "• Tab (או Insert) — הוספת בנים · Enter — הוספת אחים. בחלון ההוספה Enter מוסיף והחלון נשאר פתוח לבא.\n"
                + "• החיצים — מעבר בין פריטים: לכיוון האב, לכיוון הבנים, ולצדדים. Home — ראש המפה.\n"
                + "• רווח — כיווץ או פתיחה של הענף · Alt+Enter — פרטי הפריט.\n\n"
                + "בסיעור מוחות: לחיצה כפולה במקום ריק — פריט חדש שם; לחיצה ימנית — ערוך, חבר, העבר למפה, מחק.\n"
                + "בהתחלת נושא: עורכים ישירות; בגיליון — לחיצה ימנית על משבצת להוספה ומחיקה; בפנקס — לחיצה ימנית על שורה.\n\n"
                + "מקשים נוספים:\n"
                + "• F2 — שינוי שם · Delete — מחיקה · Ctrl+C / Ctrl+X / Ctrl+V — העתק, גזור, הדבק כבן.\n"
                + "• מקש התפריט או Shift+F10 — תפריט הפריט הנבחר · Esc — ביטול הבחירה.\n"
                + "• Ctrl+Tab — הקובץ הבא · Ctrl+PageDown / PageUp — הטאב הבא / הקודם בקובץ.\n"
                + "• Ctrl+S שמירה · Ctrl+O פתיחה · Ctrl+N קובץ חדש · Ctrl+W סגירת הטאב.");
    }

    private void fillViewMenu(JMenu view) {
        view.removeAll();
        boolean has = cur != null && cur.project != null && onMap();
        if (!has) {
            JMenuItem none = new JMenuItem("אין מפה פתוחה");
            none.setEnabled(false);
            view.add(none);
            return;
        }
        ButtonGroup g1 = new ButtonGroup();
        javax.swing.JRadioButtonMenuItem gm = new javax.swing.JRadioButtonMenuItem("גרפי", showMap);
        gm.addActionListener(e -> setShowMapAndApply(true));
        javax.swing.JRadioButtonMenuItem tm = new javax.swing.JRadioButtonMenuItem("טקסט", !showMap);
        tm.addActionListener(e -> setShowMapAndApply(false));
        g1.add(gm); g1.add(tm);
        view.add(gm); view.add(tm);
        view.addSeparator();
        ButtonGroup g2 = new ButtonGroup();
        javax.swing.JRadioButtonMenuItem vv = new javax.swing.JRadioButtonMenuItem("כיוון: מלמעלה למטה", vertical);
        vv.addActionListener(e -> { setVertical(true); render(null, true); });
        javax.swing.JRadioButtonMenuItem hh = new javax.swing.JRadioButtonMenuItem("כיוון: מימין לשמאל", !vertical);
        hh.addActionListener(e -> { setVertical(false); render(null, true); });
        g2.add(vv); g2.add(hh);
        view.add(vv); view.add(hh);
        view.addSeparator();
        if (sideMode) {
            javax.swing.JCheckBoxMenuItem sp = new javax.swing.JCheckBoxMenuItem("לוח הפריט בצד", sideShown);
            sp.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0));
            sp.addActionListener(e -> toggleSide());
            view.add(sp);
        } else item(view, "פרטי הפריט הנבחר…", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK),
                () -> { MapLayout.Node n = map.selectedNode(); if (n != null) details(n); });
        view.addSeparator();
        item(view, "התאם לחלון", ctrl(KeyEvent.VK_0), map::fit);
        item(view, "הגדל", ctrl(KeyEvent.VK_EQUALS), () -> map.setZoom(Zoom.next(map.getZoom(), zoomStep, 1)));
        item(view, "הקטן", ctrl(KeyEvent.VK_MINUS), () -> map.setZoom(Zoom.next(map.getZoom(), zoomStep, -1)));
        view.addSeparator();
        item(view, "הצג עד רמה… (מכווץ בעץ עצמו)", null, this::upToLevel);
        item(view, "מרמה עד רמה… (תצוגה, בלי מה שמעליהן)", null, () -> fromToLevel(Math.max(1, Edit.maxDepth(cur.project))));
        view.addSeparator();
        JMenu by = new JMenu("תצוגה לפי");
        for (Map.Entry<String, Runnable> en : viewChoices().entrySet()) item(by, en.getKey(), null, en.getValue());
        view.add(by);
        if (cur.viewMode != 0 && cur.viewMode != 5)
            item(view, cur.viewSort == Views.Sort.NAME ? "מיון: לפי המפה" : "מיון: לפי א-ב", null, this::toggleSort);
    }

    private Map<String, Runnable> viewChoices() {
        Map<String, Runnable> m = new LinkedHashMap<>();
        Project p = cur.project;
        m.put("רגילה (העץ)", () -> setView(0, 1, ""));
        m.put("לפי תגיות", () -> setView(1, 1, ""));
        int maxLevel = Views.maxTagLevel(p);
        for (int k = 2; k <= maxLevel; k++) { final int lv = k; m.put("לפי תגיות על תגיות (רמה " + k + ")", () -> setView(1, lv, "")); }
        for (final String n : Views.usedInfoNames(p)) m.put("לפי " + (n.isEmpty() ? "מידע בלי שם" : n), () -> setView(2, 1, n));
        m.put("לפי תכונות (עצמיות ומקריות)", () -> setView(3, 1, ""));
        if (!p.aspects.isEmpty()) m.put("לפי בחינות", () -> setView(4, 1, ""));
        return m;
    }

    private void fillSheetMenu(JMenu m) {
        m.removeAll();
        if (cur == null || cur.project == null) {
            JMenuItem none = new JMenuItem("אין קובץ פתוח");
            none.setEnabled(false);
            m.add(none);
            return;
        }
        item(m, "טאב חדש…", ctrl(KeyEvent.VK_T), this::addSheet);
        Sheet s = sheet(cur);
        m.addSeparator();
        for (JMenuItem i : sheetActions(s)) m.add(i);
        int hidden = 0;
        for (Sheet x : cur.project.sheets) if (x.hidden) hidden++;
        if (hidden > 0) {
            m.addSeparator();
            JMenu dr = new JMenu("מגירה (" + hidden + ")");
            for (Sheet x : cur.project.sheets) if (x.hidden) {
                final Sheet h = x;
                item(dr, x.name + " · " + x.kind.label, null, () -> { h.hidden = false; cur.sheetId = h.id; changedAny(true); });
            }
            m.add(dr);
        }
    }

    // ---------------- קבצים ----------------

    private JFileChooser chooser() {
        JFileChooser fc = new JFileChooser(prefs.get("lastDir", System.getProperty("user.home")));
        fc.setComponentOrientation(Ui.RTL);
        fc.setFileFilter(new FileNameExtensionFilter("קובצי מפה (xml, opml)", "xml", "opml"));
        return fc;
    }

    private void rememberDir(File f) {
        if (f != null && f.getParentFile() != null) prefs.put("lastDir", f.getParentFile().getAbsolutePath());
    }

    private void openFile() {
        JFileChooser fc = chooser();
        fc.setDialogTitle("פתח קובץ");
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        rememberDir(f);
        openPath(f.toPath());
    }

    private void openPath(Path p) {
        p = p.toAbsolutePath().normalize();
        for (Doc d : docs) if (p.equals(d.path)) { d.spread = true; switchTo(d); return; }
        Doc d = new Doc();
        d.path = p;
        d.fileName = p.getFileName().toString();
        docs.add(d);
        switchTo(d);
    }

    private static Path withExt(File f, String ext) {
        String n = f.getName();
        return n.toLowerCase().endsWith(ext) ? f.toPath() : new File(f.getParentFile(), n + ext).toPath();
    }

    private void newFile() {
        JFileChooser fc = chooser();
        fc.setDialogTitle("קובץ חדש — איפה לשמור?");
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), "מפה חדשה.xml"));
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        Path p = withExt(fc.getSelectedFile(), ".xml");
        rememberDir(p.toFile());
        if (Files.exists(p)) { Ui.message(frame, "הקובץ כבר קיים", "בחר שם אחר, או פתח אותו מ\"פתח…\"."); return; }
        Project pr = new Project();
        String base = p.getFileName().toString();
        pr.title = base.endsWith(".xml") ? base.substring(0, base.length() - 4) : base;
        pr.start.title = pr.title;
        pr.roots.add(new Item("נושא"));
        pr.first(Sheet.Kind.MAP).absolute = true;
        try {
            writeFile(p, OpmlIO.write(pr));
        } catch (Exception e) { fail("השמירה נכשלה", e); return; }
        openPath(p);
    }

    private void saveAs() {
        if (cur == null || cur.project == null) return;
        JFileChooser fc = chooser();
        fc.setDialogTitle("שמור עותק בשם");
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), cur.label() + ".xml"));
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        Path p = withExt(fc.getSelectedFile(), ".xml").toAbsolutePath().normalize();
        rememberDir(p.toFile());
        Runnable go = () -> {
            String t = OpmlIO.write(cur.project);
            try { writeFile(p, t); } catch (Exception e) { fail("השמירה נכשלה", e); return; }
            cur.path = p;
            cur.fileName = p.getFileName().toString();
            cur.lastText = t;
            cur.dirty = false;
            saveTabs();
            render("נשמר בשם " + cur.fileName, false);
        };
        if (Files.exists(p)) Ui.confirm(frame, "הקובץ \"" + p.getFileName() + "\" כבר קיים. להחליף אותו?", "החלף", go);
        else go.run();
    }

    private void exportOpml() {
        if (cur == null || cur.project == null) return;
        JFileChooser fc = chooser();
        fc.setDialogTitle("ייצוא ל-OPML");
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), (cur.project.title.isEmpty() ? "מפה" : cur.project.title) + ".opml"));
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        Path p = withExt(fc.getSelectedFile(), ".opml");
        try {
            writeFile(p, OpmlIO.writeOpml(cur.project));
            setStatus("יוצא ל-OPML: " + p.getFileName());
        } catch (Exception e) { fail("הייצוא נכשל", e); }
    }

    private void reload() {
        if (cur == null || cur.path == null) return;
        Doc d = cur;
        if (d.dirty) Ui.confirm(frame, "יש שינויים שלא נשמרו. לטעון מהדיסק ולאבד אותם?", "טען", () -> load(d));
        else load(d);
    }

    private void example(String res, String name) {
        try (InputStream in = App.class.getResourceAsStream("/examples/" + res)) {
            Project ex = OpmlIO.read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            ex.syncAspects();
            Doc d = new Doc();
            d.project = ex;
            d.fileName = "דוגמה: " + name + " (לא שמורה)";
            docs.add(d);
            switchTo(d);
            render("כדי לשמור: \"שמור עותק בשם\"", true);
        } catch (Exception e) { fail("לא ניתן לפתוח את הדוגמה", e); }
    }

    private static String readFile(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** כתיבה דרך קובץ זמני באותה תיקייה, ואז החלפה — כדי שקריסה באמצע לא תשאיר קובץ חתוך. */
    private static void writeFile(Path p, String t) throws Exception {
        byte[] b = t.getBytes(StandardCharsets.UTF_8);
        Path dir = p.toAbsolutePath().getParent();
        Path tmp = null;
        try {
            tmp = Files.createTempFile(dir, ".kv-", ".tmp");
            Files.write(tmp, b);
            // הקובץ הזמני נוצר עם הרשאות מצומצמות; מעתיקים את ההרשאות של הקובץ הקיים (בחלונות — לא רלוונטי)
            try {
                if (Files.exists(p)) Files.setPosixFilePermissions(tmp, Files.getPosixFilePermissions(p));
                else Files.setPosixFilePermissions(tmp, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
            } catch (UnsupportedOperationException ignored) { }
            try { Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (Exception e) { Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception e) {
            if (tmp != null) try { Files.deleteIfExists(tmp); } catch (Exception ignored) { }
            Files.write(p, b);
        }
    }

    /** טעינה ברקע; המסך ממשיך לעבוד. */
    private void load(Doc d) {
        if (d.path == null) return;
        d.loading = true;
        if (d == cur) { setStatus("טוען…"); showCard("welcome"); }
        final Path p = d.path;
        new Thread(() -> {
            try {
                long t0 = System.nanoTime();
                String t = readFile(p);
                Project pr = OpmlIO.read(t);
                pr.syncAspects();
                long t1 = System.nanoTime();
                SwingUtilities.invokeLater(() -> {
                    d.loading = false;
                    d.lastText = t;
                    d.project = pr;
                    d.dirty = false;
                    d.viewMode = 0;
                    d.viewResult = null;
                    if (d != cur) { rebuildStrips(); return; }
                    map.select(null, false);
                    render("נטען ב-" + String.format(java.util.Locale.US, "%.1f", (t1 - t0) / 1e9) + " ש׳", true);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    d.loading = false;
                    if (d == cur) { showCard("welcome"); setStatus("לא ניתן לטעון"); }
                    fail("לא ניתן לטעון את הקובץ " + p.getFileName(), e);
                });
            }
        }, "load").start();
    }

    private void save(Doc d) {
        if (d == null || d.project == null) return;
        if (d.path == null) { if (d == cur) saveAs(); return; }
        String t = OpmlIO.write(d.project);
        try {
            writeFile(d.path, t);
            d.lastText = t;
            d.dirty = false;
            if (d == cur) setStatus(summary(d) + " · נשמר");
            rebuildTabRow();
        } catch (Exception e) { fail("השמירה נכשלה", e); }
    }

    private void autoSaveAll() {
        if (!autosave) return;
        for (Doc d : new ArrayList<>(docs)) if (d.dirty && d.path != null && d.project != null) save(d);
    }

    /** בחזרה לחלון: אם הקובץ השתנה מבחוץ (למשל בטלפון, דרך Dropbox), מציעים לטעון מחדש. */
    private void checkExternalChange() {
        if (cur == null || cur.path == null || cur.lastText == null || asking || cur.loading) return;
        final Doc d = cur;
        final String known = d.lastText;
        new Thread(() -> {
            try {
                String now = readFile(d.path);
                if (now.equals(known)) return;
                SwingUtilities.invokeLater(() -> {
                    if (asking || d != cur) return;
                    asking = true;
                    String q = "הקובץ \"" + d.fileName + "\" השתנה מבחוץ. לטעון את הגרסה החדשה?"
                            + (d.dirty ? " (השינויים שלא נשמרו כאן יאבדו)" : " (מה שמוצג עכשיו יוחלף)");
                    JDialog dl = Ui.dialog(frame, "הקובץ השתנה מבחוץ");
                    JPanel body = new JPanel(new BorderLayout());
                    JLabel l = new JLabel("<html><div dir=rtl style='width:340px'>" + Ui.esc(q) + "</div></html>");
                    l.setFont(Ui.base.deriveFont(14f));
                    body.add(l, BorderLayout.CENTER);
                    JButton yes = Ui.button("טען", () -> { dl.dispose(); load(d); });
                    JButton no = Ui.button("לא עכשיו", () -> { dl.dispose(); d.lastText = now; });
                    body.add(Ui.buttons(yes, no), BorderLayout.SOUTH);
                    dl.getRootPane().setDefaultButton(yes);
                    Ui.show(dl, body, frame);
                    asking = false;
                });
            } catch (Exception ignored) { }
        }, "check").start();
    }

    private void fail(String what, Exception e) {
        setStatus(what);
        Ui.message(frame, what, e.getMessage() == null ? e.toString() : e.getMessage());
    }

    // ---------------- טאבי קבצים ----------------

    private void switchTo(Doc d) {
        if (d != cur && cur != null && cur.dirty && autosave && cur.path != null) save(cur);
        cur = d;
        d.spread = true;
        map.select(null, false);
        saveTabs();
        if (d.project == null && d.path != null && !d.loading) { rebuildStrips(); load(d); return; }
        if (d.project == null) { rebuildStrips(); showCard("welcome"); return; }
        render(null, true);
        checkExternalChange();
    }

    private void cycleDoc(int dir) {
        List<Doc> vis = new ArrayList<>();
        for (Doc d : docs) if (d.spread || d == cur) vis.add(d);
        if (vis.size() < 2) return;
        int i = vis.indexOf(cur);
        switchTo(vis.get((i + dir + vis.size()) % vis.size()));
    }

    private void closeTab(Doc d) {
        if (d == null) return;
        if (d.dirty && (d.path == null || !autosave)) {
            Ui.confirm(frame, d.path == null ? "הדוגמה לא נשמרה. לסגור ולאבד את השינויים?" : "יש שינויים שלא נשמרו. לסגור בלי לשמור?",
                    "סגור בלי לשמור", () -> { d.dirty = false; closeTab(d); });
            return;
        }
        if (d.dirty) save(d);
        int i = docs.indexOf(d);
        docs.remove(d);
        if (d == cur) {
            cur = null;
            if (docs.isEmpty()) {
                saveTabs();
                map.clear();
                showWelcome();
                return;
            }
            switchTo(docs.get(Math.max(0, Math.min(i, docs.size() - 1))));
            return;
        }
        saveTabs();
        rebuildStrips();
    }

    private void quit() {
        saver.stop();
        List<Doc> unsaved = new ArrayList<>();
        for (Doc d : docs) {
            if (!d.dirty) continue;
            if (autosave && d.path != null) save(d);
            if (d.dirty) unsaved.add(d);
        }
        Runnable bye = () -> {
            saveTabs();
            saveBounds();
            frame.dispose();
            System.exit(0);
        };
        if (unsaved.isEmpty()) { bye.run(); return; }
        StringBuilder b = new StringBuilder();
        for (Doc d : unsaved) b.append(b.length() > 0 ? ", " : "").append(d.label());
        Ui.confirm(frame, "יש שינויים שלא נשמרו: " + b + ". לצאת בלי לשמור?", "צא בלי לשמור", bye);
    }

    private void saveTabs() {
        StringBuilder b = new StringBuilder();
        int curIndex = -1, k = 0;
        for (Doc d : docs) {
            if (d.path == null) continue;
            if (d == cur) curIndex = k;
            b.append(d.spread ? "1" : "0").append('|').append(d.path).append('\n');
            k++;
        }
        prefs.put("tabs", b.toString());
        prefs.putInt("tab", curIndex);
    }

    private void restoreTabs(String[] args) {
        String s = prefs.get("tabs", "");
        for (String line : s.split("\n")) {
            int bar = line.indexOf('|');
            if (bar < 0) continue;
            Path p = Paths.get(line.substring(bar + 1));
            if (!Files.exists(p)) continue;
            Doc d = new Doc();
            d.spread = line.startsWith("1");
            d.path = p;
            d.fileName = p.getFileName().toString();
            docs.add(d);
        }
        if (args != null) for (String a : args) {
            Path p = Paths.get(a);
            if (Files.exists(p)) { openPath(p); }
        }
        if (cur != null) return;
        if (docs.isEmpty()) { showWelcome(); return; }
        int i = Math.max(0, Math.min(prefs.getInt("tab", 0), docs.size() - 1));
        switchTo(docs.get(i));
    }

    private void restoreBounds() {
        int w = prefs.getInt("w", 1200), h = prefs.getInt("h", 800);
        int x = prefs.getInt("x", -1), y = prefs.getInt("y", -1);
        frame.setSize(w, h);
        Rectangle screen = frame.getGraphicsConfiguration().getBounds();
        if (x >= 0 && y >= 0 && x < screen.width - 100 && y < screen.height - 100) frame.setLocation(x, y);
        else frame.setLocationRelativeTo(null);
        if (prefs.getBoolean("max", false)) frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    private void saveBounds() {
        boolean max = (frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        prefs.putBoolean("max", max);
        if (!max) {
            prefs.putInt("x", frame.getX()); prefs.putInt("y", frame.getY());
            prefs.putInt("w", frame.getWidth()); prefs.putInt("h", frame.getHeight());
        }
    }

    // ---------------- שורות למעלה ----------------

    private void rebuildStrips() {
        rebuildTabRow();
        rebuildSheetRow();
    }

    private void rebuildTabRow() {
        tabRow.removeAll();
        int hidden = 0;
        for (final Doc d : docs) {
            if (!d.spread && d != cur) { hidden++; continue; }
            Ui.Chip c = new Ui.Chip(d.label() + (d.dirty ? " •" : ""), d == cur, !d.spread)
                    .onClick(() -> switchTo(d)).onClose(() -> closeTab(d));
            c.setToolTipText(d.path == null ? d.fileName : d.path.toString());
            c.onMenu(() -> {
                JPopupMenu m = Ui.popup();
                JMenuItem bg = new JMenuItem("העבר לרקע");
                bg.addActionListener(e -> {
                    d.spread = false;
                    if (d == cur) for (Doc o : docs) if (o != d && o.spread) { switchTo(o); break; }
                    saveTabs();
                    rebuildStrips();
                });
                m.add(bg);
                JMenuItem cl = new JMenuItem("סגור");
                cl.addActionListener(e -> closeTab(d));
                m.add(cl);
                if (d.path != null) {
                    JMenuItem op = new JMenuItem("פתח את התיקייה");
                    op.addActionListener(e -> {
                        try { java.awt.Desktop.getDesktop().open(d.path.getParent().toFile()); } catch (Exception ignored) { }
                    });
                    m.add(op);
                }
                Ui.showUnder(m, c);
            });
            tabRow.add(c);
        }
        tabRow.add(new Ui.Chip("+ פתח", false, false).onClick(this::openFile));
        if (hidden > 0) {
            final int h = hidden;
            Ui.Chip bgc = new Ui.Chip(Ui.glyph("☰", "≡") + " " + h + " ברקע", false, true);
            bgc.onClick(() -> {
                JPopupMenu m = Ui.popup();
                for (final Doc d : docs) if (!d.spread && d != cur) {
                    JMenuItem i = new JMenuItem(d.label());
                    i.addActionListener(e -> switchTo(d));
                    m.add(i);
                }
                Ui.showUnder(m, bgc);
            });
            tabRow.add(bgc);
        }
        tabRow.revalidate();
        tabRow.repaint();
        String t = cur == null ? "קבוצות ותגיות" : cur.label() + (cur.dirty ? " •" : "") + " — קבוצות ותגיות";
        frame.setTitle(t);
    }

    private void rebuildSheetRow() {
        sheetRow.removeAll();
        sheetRow.setVisible(cur != null && cur.project != null);
        if (cur == null || cur.project == null) { sheetRow.revalidate(); return; }
        final Project p = cur.project;
        final Sheet curSh = sheet(cur);
        int hiddenSheets = 0;
        for (final Sheet s : p.sheets) {
            if (s.hidden) { hiddenSheets++; continue; }
            Ui.Chip c = new Ui.Chip((s.locked ? Ui.glyph("🔒", "נעול:") + " " : "") + s.name, s == curSh, false);
            c.setToolTipText(s.kind.label + (s.note.isEmpty() ? "" : " · " + s.note));
            c.onClick(() -> { if (s != sheet(cur)) enterSheet(s); });
            c.onMenu(() -> {
                JPopupMenu m = Ui.popup();
                for (JMenuItem i : sheetActions(s)) m.add(i);
                Ui.showUnder(m, c);
            });
            sheetRow.add(c);
        }
        sheetRow.add(new Ui.Chip("+ טאב", false, false).onClick(this::addSheet));
        if (hiddenSheets > 0) {
            Ui.Chip dr = new Ui.Chip("מגירה (" + hiddenSheets + ")", false, true);
            dr.onClick(() -> {
                JPopupMenu m = Ui.popup();
                for (final Sheet s : p.sheets) if (s.hidden) {
                    JMenuItem i = new JMenuItem(s.name + " · " + s.kind.label);
                    i.addActionListener(e -> { s.hidden = false; cur.sheetId = s.id; changedAny(true); });
                    m.add(i);
                }
                Ui.showUnder(m, dr);
            });
            sheetRow.add(dr);
        }
        if (curSh.kind == Sheet.Kind.FREE && !curSh.locked) {
            sheetRow.add(Ui.gap());
            sheetRow.add(new Ui.Chip("+ פריט", false, false).onClick(() -> onFreeAdd(freePanel.centerX(), freePanel.centerY())));
        }
        if (curSh.kind == Sheet.Kind.MAP) {
            sheetRow.add(Ui.gap());
            sheetRow.add(new Ui.Chip("גרפי", showMap, false).onClick(() -> setShowMapAndApply(true)));
            sheetRow.add(new Ui.Chip("טקסט", !showMap, false).onClick(() -> setShowMapAndApply(false)));
            sheetRow.add(Ui.gap());
            sheetRow.add(new Ui.Chip("עץ", cur.viewMode == 0, false).onClick(() -> setView(0, 1, "")));
            sheetRow.add(new Ui.Chip("# תגיות", cur.viewMode == 1 && cur.tagLevel == 1, false).onClick(() -> setView(1, 1, "")));
            int maxLevel = Views.maxTagLevel(p);
            for (int k = 2; k <= maxLevel; k++) {
                final int lv = k;
                sheetRow.add(new Ui.Chip("# תגיות " + k, cur.viewMode == 1 && cur.tagLevel == k, false).onClick(() -> setView(1, lv, "")));
            }
            for (final String n : Views.usedInfoNames(p))
                sheetRow.add(new Ui.Chip(n.isEmpty() ? "מידע" : n, cur.viewMode == 2 && cur.viewInfo.equals(n), false).onClick(() -> setView(2, 1, n)));
            sheetRow.add(new Ui.Chip("תכונות", cur.viewMode == 3, false).onClick(() -> setView(3, 1, "")));
            if (!p.aspects.isEmpty())
                sheetRow.add(new Ui.Chip("בחינות", cur.viewMode == 4, false).onClick(() -> setView(4, 1, "")));
            String lv = cur.viewMode == 5 ? (cur.levelFrom == cur.levelTo ? "רמה " + cur.levelFrom : "רמות " + cur.levelFrom + "–" + cur.levelTo) : "רמות…";
            Ui.Chip lc = new Ui.Chip(lv, cur.viewMode == 5, false);
            lc.onClick(() -> {
                JPopupMenu m = Ui.popup();
                JMenuItem a = new JMenuItem("הצג עד רמה… (מכווץ בעץ עצמו)");
                a.addActionListener(e -> upToLevel());
                JMenuItem b = new JMenuItem("מרמה עד רמה… (תצוגה, בלי מה שמעליהן)");
                b.addActionListener(e -> fromToLevel(Math.max(1, Edit.maxDepth(p))));
                m.add(a);
                m.add(b);
                Ui.showUnder(m, lc);
            });
            sheetRow.add(lc);
            if (cur.viewMode != 0 && cur.viewMode != 5) {
                sheetRow.add(Ui.gap());
                sheetRow.add(new Ui.Chip(cur.viewSort == Views.Sort.NAME ? "מיון: א-ב" : "מיון: מפה", false, false).onClick(this::toggleSort));
            }
        }
        sheetRow.revalidate();
        sheetRow.repaint();
    }

    // ---------------- תצוגה ----------------

    private void showWelcome() {
        rebuildStrips();
        setStatus("אין קובץ פתוח");
        showCard("welcome");
        zoomBar.setVisible(false);
    }

    private String card = "welcome";

    private void showCard(String name) {
        card = name;
        cards.show(center, name);
        zoomBar.setVisible("map".equals(name));
        layoutSide();
        if ("map".equals(name)) SwingUtilities.invokeLater(() -> { if (!side.isAncestorOf(frame.getFocusOwner())) map.requestFocusInWindow(); });
        if ("free".equals(name)) SwingUtilities.invokeLater(freePanel::requestFocusInWindow);
    }

    // ---------------- לוח הפריט בצד ----------------

    /** הלוח מוצג בצד רק במפה הגרפית, כשבהגדרות נבחר "לוח בצד", ולא הוסתר. */
    private void layoutSide() {
        boolean want = sideMode && sideShown && "map".equals(card);
        boolean has = split.getParent() == centerWrap;
        if (want && !has) {
            centerWrap.removeAll();
            split.setRightComponent(center);
            split.setLeftComponent(side);
            centerWrap.add(split, BorderLayout.CENTER);
            int w = prefs.getInt("sideW", 360);
            centerWrap.revalidate();
            SwingUtilities.invokeLater(() -> split.setDividerLocation(Math.max(220, w)));
        } else if (!want && has) {
            prefs.putInt("sideW", split.getDividerLocation());
            centerWrap.removeAll();
            split.remove(center);
            centerWrap.add(center, BorderLayout.CENTER);
            centerWrap.revalidate();
            centerWrap.repaint();
        }
        if (want) updateSide();
    }

    /** הלוח מראה את הפריט הנבחר במפה (בתצוגה — את הפריט המקורי; בחלוקה — את הבחינה). */
    private void updateSide() {
        if (!sideMode || !sideShown || cur == null || cur.project == null || !"map".equals(card)) return;
        MapLayout.Node n = map.selectedNode();
        if (n == null) {
            side.empty("בחר פריט במפה, וכאן יוצג כל המידע עליו: בחינות, מידע, תגיות ותכונות.\n\n"
                    + "אפשר לבחור בעכבר או בחיצים. לסגירת הלוח: תצוגה ← לוח הפריט בצד.");
            return;
        }
        if (n.isDivision()) {
            Item a = cur.project.aspect(n.division.aspectId);
            if (a == null) {
                side.empty("חלוקה" + (n.division.basis.isEmpty() ? " בלי בחינה" : ": " + n.division.basis)
                        + ".\n\nבחירת בחינה לחלוקה: לחיצה כפולה עליה, או F2.");
                return;
            }
            side.show(cur.project, a, locked());
            return;
        }
        Item o = original(n);
        if (o == null) { side.empty("אין פריט מקורי לתיבה הזאת."); return; }
        side.show(cur.project, o, locked());
    }

    /** "פרטי הפריט" (Alt+Enter): בלוח שבצד, או בחלון נפרד. */
    private void details(MapLayout.Node n) {
        if (cur == null || cur.project == null) return;
        Item o = n.isDivision() ? cur.project.aspect(n.division.aspectId) : original(n);
        if (o == null) { if (n.isDivision() && editable()) editNode(n); return; }
        if (sideMode) {
            if (!sideShown) { sideShown = true; prefs.putBoolean("sideShown", true); }
            layoutSide();
            return;
        }
        ItemPanel.showWindow(frame, this, cur.project, o, locked());
    }

    private void toggleSide() {
        sideShown = !sideShown;
        prefs.putBoolean("sideShown", sideShown);
        layoutSide();
    }

    @Override public void itemChanged(Item it) {
        markChanged();
        render(null, false);
    }

    @Override public java.awt.Window window() { return frame; }

    private boolean onMap() {
        Sheet s = cur == null ? null : sheet(cur);
        return s != null && s.kind == Sheet.Kind.MAP;
    }

    private boolean editable() {
        return onMap() && cur.viewResult == null && !locked();
    }

    private Project shown(Doc d) {
        d.viewResult = null;
        switch (d.viewMode) {
            case 1: d.viewResult = Views.byTags(d.project, d.tagLevel, d.viewSort); break;
            case 2: d.viewResult = Views.byInfoName(d.project, d.viewInfo, d.viewSort); break;
            case 3: d.viewResult = Views.byProperties(d.project, d.viewSort); break;
            case 4: d.viewResult = Views.byAspects(d.project, d.viewSort); break;
            case 5: d.viewResult = Views.byLevels(d.project, d.levelFrom, d.levelTo); break;
            default: break;
        }
        return d.viewResult == null ? d.project : d.viewResult.project;
    }

    private void setView(int mode, int level, String info) {
        cur.viewMode = mode;
        cur.tagLevel = level;
        cur.viewInfo = info;
        map.select(null, false);
        render(null, true);
    }

    private void toggleSort() {
        cur.viewSort = cur.viewSort == Views.Sort.MAP ? Views.Sort.NAME : Views.Sort.MAP;
        render(null, true);
    }

    private String summary(Doc d) {
        Sheet s = sheet(d);
        if (s.kind == Sheet.Kind.FREE) return d.project.free.nodes.size() + " פריטים · " + d.project.free.links.size() + " קווים";
        if (s.kind == Sheet.Kind.START) return d.project.start.sections.size() + (d.project.start.sections.size() == 1 ? " חלק" : " חלקים");
        return TextLines.countItems(d.project) + " פריטים";
    }

    private void render(String note, boolean refit) {
        if (cur == null || cur.project == null) { showWelcome(); return; }
        syncSheet();
        Sheet sh = sheet(cur);
        Project pr = cur.project;
        String sheetNote = sh.note.isEmpty() ? "" : " · " + sh.note;
        String dirtyNote = cur.dirty ? (cur.path == null ? " · לא שמור" : autosave ? " · שומר…" : " · לא נשמר") : "";
        if (sh.kind != Sheet.Kind.MAP) {
            setStatus(summary(cur) + (sh.locked ? " · נעול" : "") + dirtyNote + (note == null ? "" : " · " + note) + sheetNote);
            rebuildStrips();
            if (sh.kind == Sheet.Kind.START) {
                final int y = brainScroll.getVerticalScrollBar().getValue();
                brainPanel.set(pr.start, sh, new BrainPanel.Listener() {
                    public void changed() { changedAny(false); }
                    public void structure() { changedAny(true); }
                });
                showCard("brain");
                SwingUtilities.invokeLater(() -> brainScroll.getVerticalScrollBar().setValue(y));
            } else {
                freePanel.locked = sh.locked;
                freePanel.links = sh.links;
                freePanel.wheelZooms = map.wheelZooms;
                freePanel.setMap(pr.free, this);
                showCard("free");
            }
            return;
        }
        pr.syncAspects();
        Project p = shown(cur);
        int items = TextLines.countItems(pr);
        setStatus(items + " פריטים"
                + (pr.absolute() ? " · סדר מוחלט" + (Sheets.misplaced(pr).isEmpty() ? "" : " · " + Sheets.misplaced(pr).size() + " לא במקומם") : "")
                + (sh.locked ? " · נעול" : "")
                + (cur.viewResult != null ? " · תצוגה (לקריאה)" : "")
                + dirtyNote + (note == null ? "" : " · " + note) + sheetNote);
        rebuildStrips();
        map.setReadOnly(cur.viewResult != null || sh.locked);
        shownP = p;
        if (showMap) {
            map.setProject(p, vertical, refit);
            showCard("map");
            updateSide();
            zoomLabel.setText(Zoom.format(map.getZoom()));
        } else {
            buildText(p);
            showCard("text");
        }
    }

    private void setShowMapAndApply(boolean b) {
        setShowMap(b);
        if (shownP == null) return;
        if (showMap) { map.setProject(shownP, vertical, true); showCard("map"); }
        else { buildText(shownP); showCard("text"); }
        rebuildSheetRow();
    }

    private void buildText(Project p) {
        StyledDocument doc = text.getStyledDocument();
        try { doc.remove(0, doc.getLength()); } catch (Exception ignored) { }
        text.setFont(Ui.base.deriveFont(17f));
        Color grey = new Color(136, 135, 128), blue = new Color(24, 95, 165), ink = new Color(44, 44, 42);
        String fam = Ui.base.getFamily();
        try {
            for (TextLines.Line l : TextLines.build(p)) {
                int start = doc.getLength();
                if (l.kind == TextLines.Kind.HEADER) {
                    doc.insertString(doc.getLength(), "\n" + l.text, style(fam, 17, true, false, grey));
                } else if (l.kind == TextLines.Kind.DIVISION) {
                    doc.insertString(doc.getLength(), (l.mark.isEmpty() ? "" : l.mark + " · ") + l.text, style(fam, 14.5f, false, true, blue));
                } else {
                    doc.insertString(doc.getLength(), l.mark, style(fam, 11, false, false, grey));
                    doc.insertString(doc.getLength(), "  " + (l.text.isEmpty() ? "(בלי שם)" : l.text), style(fam, 17, l.depth == 0, false, ink));
                    if (l.hidden > 0) doc.insertString(doc.getLength(), "  " + Ui.glyph("▸", "›") + " " + l.hidden, style(fam, 17, false, false, blue));
                    if (!l.info.isEmpty()) doc.insertString(doc.getLength(), "  (" + l.info + ")", style(fam, 12.5f, false, false, grey));
                }
                doc.insertString(doc.getLength(), "\n", style(fam, 17, false, false, ink));
                SimpleAttributeSet para = new SimpleAttributeSet();
                StyleConstants.setAlignment(para, StyleConstants.ALIGN_RIGHT);
                StyleConstants.setRightIndent(para, l.depth * 24f);
                StyleConstants.setSpaceBelow(para, 3f);
                doc.setParagraphAttributes(start, doc.getLength() - start, para, false);
            }
        } catch (Exception ignored) { }
        text.setCaretPosition(0);
    }

    private static SimpleAttributeSet style(String fam, float size, boolean bold, boolean italic, Color c) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setFontFamily(a, fam);
        StyleConstants.setFontSize(a, Math.round(size));
        StyleConstants.setBold(a, bold);
        StyleConstants.setItalic(a, italic);
        StyleConstants.setForeground(a, c);
        return a;
    }

    private void setStatus(String s) {
        String f = cur == null || cur.fileName == null ? "" : cur.fileName;
        // שם הקובץ בסוף: כך שם באנגלית לא מבלבל את הסדר של המספרים בשורה
        status.setText(f.isEmpty() ? s : (s == null || s.isEmpty() ? f : s + " · " + f));
    }

    private void zoomMenu() {
        JPopupMenu m = Ui.popup();
        for (final float z : Zoom.values(zoomStep)) {
            JMenuItem i = new JMenuItem(Zoom.format(z));
            i.addActionListener(e -> map.setZoom(z));
            m.add(i);
        }
        m.addSeparator();
        JMenuItem f = new JMenuItem("התאם לחלון");
        f.addActionListener(e -> map.fit());
        m.add(f);
        m.show(zoomLabel, zoomLabel.getWidth() - m.getPreferredSize().width, -m.getPreferredSize().height);
    }

    private void upToLevel() {
        if (cur == null || cur.project == null) return;
        if (locked()) { setStatus("הטאב נעול — אפשר לשנות במאפייני הטאב"); return; }
        final int max = Edit.maxDepth(cur.project);
        if (max < 1) { setStatus("אין עדיין רמות מתחת לשורש"); return; }
        List<String> names = new ArrayList<>();
        for (int k = 0; k < max; k++) names.add(k == 0 ? "רק השורש" : "עד רמה " + k);
        names.add("הכול פתוח");
        Ui.choose(frame, "הצג עד רמה", names, -1, w -> {
            int n = Edit.showToLevel(cur.project, w == max ? Integer.MAX_VALUE : w);
            cur.viewMode = 0;
            map.select(null, false);
            if (n > 0) { markChanged(); render(null, true); } else render(null, true);
        });
    }

    private void fromToLevel(final int max) {
        List<String> a = new ArrayList<>();
        for (int k = 1; k <= max; k++) a.add("מרמה " + k);
        Ui.choose(frame, "מאיזו רמה (השורש — 0)", a, -1, w -> {
            final int from = w + 1;
            List<String> b = new ArrayList<>();
            for (int k = from; k <= max; k++) b.add(k == from ? "רק רמה " + k : "עד רמה " + k);
            Ui.choose(frame, "מרמה " + from + " — עד איזו רמה", b, -1, w2 -> {
                cur.levelFrom = from;
                cur.levelTo = from + w2;
                setView(5, 1, "");
            });
        });
    }

    // ---------------- טאבים בתוך הקובץ ----------------

    private Sheet sheet(Doc d) {
        if (d == null || d.project == null) return null;
        Sheet s = d.project.sheet(d.sheetId);
        if (s == null || s.hidden) {
            s = null;
            for (Sheet x : d.project.sheets) if (!x.hidden && x.kind == Sheet.Kind.MAP) { s = x; break; }
            if (s == null) for (Sheet x : d.project.sheets) if (!x.hidden) { s = x; break; }
            if (s == null) { s = d.project.sheets.get(0); s.hidden = false; }
            d.sheetId = s.id;
        }
        return s;
    }

    private void syncSheet() {
        Sheet s = sheet(cur);
        if (s == null) return;
        cur.project.use(s);
        showMap = s.view.equals("map") || (!s.view.equals("text") && prefs.getBoolean("showMap", true));
        vertical = s.dir.equals("v") || (!s.dir.equals("h") && prefs.getBoolean("vertical", true));
        if (s.kind == Sheet.Kind.MAP) {
            map.compact = !s.layout.equals("tidy");
            map.spaceB = s.spaceB;
            map.spaceD = s.spaceD;
        }
    }

    private boolean locked() {
        Sheet s = sheet(cur);
        return s != null && s.locked;
    }

    private void enterSheet(Sheet s) {
        if (s.hidden) s.hidden = false;
        cur.sheetId = s.id;
        cur.viewMode = 0;
        map.select(null, false);
        render(null, true);
    }

    private void cycleSheet(int dir) {
        if (cur == null || cur.project == null) return;
        List<Sheet> vis = new ArrayList<>();
        for (Sheet s : cur.project.sheets) if (!s.hidden) vis.add(s);
        if (vis.size() < 2) return;
        int i = vis.indexOf(sheet(cur));
        enterSheet(vis.get((i + dir + vis.size()) % vis.size()));
    }

    private void setShowMap(boolean b) {
        Sheet s = sheet(cur);
        if (s != null && s.kind == Sheet.Kind.MAP && !s.view.isEmpty()) { s.view = b ? "map" : "text"; markChanged(); }
        else prefs.putBoolean("showMap", b);
        showMap = b;
    }

    private void setVertical(boolean b) {
        Sheet s = sheet(cur);
        if (s != null && s.kind == Sheet.Kind.MAP && !s.dir.isEmpty()) { s.dir = b ? "v" : "h"; markChanged(); }
        else prefs.putBoolean("vertical", b);
        vertical = b;
    }

    private List<JMenuItem> sheetActions(final Sheet s) {
        List<JMenuItem> l = new ArrayList<>();
        final Project p = cur.project;
        l.add(mi("מאפיינים…", () -> sheetProps(s)));
        l.add(mi("שנה שם…", () -> Ui.ask(frame, "שם הטאב", s.name, false, x -> { Sheets.rename(s, x); changedAny(true); })));
        l.add(mi("העתק (טאב נוסף עם אותו תוכן)", () -> {
            Sheet c = Sheets.copy(p, s);
            cur.sheetId = c.id;
            changedAny(true);
            setStatus("נוצר עותק: " + c.name);
        }));
        final int i = p.sheets.indexOf(s);
        if (i > 0) l.add(mi("הזז ימינה", () -> { Sheets.move(p, s, -1); changedAny(true); }));
        if (i < p.sheets.size() - 1) l.add(mi("הזז שמאלה", () -> { Sheets.move(p, s, 1); changedAny(true); }));
        if (Sheets.visible(p) > 1 && !s.hidden) l.add(mi("העבר למגירה (נשמר בקובץ, לא מוצג)", () -> { s.hidden = true; changedAny(true); }));
        int same = 0;
        for (Sheet x : p.sheets) if (x.kind == s.kind) same++;
        if (same > 1) l.add(mi("מחק את הטאב…", () -> Ui.confirm(frame, "למחוק את הטאב \"" + s.name + "\" עם כל התוכן שבו?", "מחק", () -> {
            if (Sheets.delete(p, s)) changedAny(true);
        })));
        return l;
    }

    private static JMenuItem mi(String label, Runnable r) {
        JMenuItem i = new JMenuItem(label);
        i.addActionListener(e -> r.run());
        return i;
    }

    private static final float[] SPACES = {0.5f, 1f, 1.5f, 2f, 3f};
    private static final String[] SPACE_NAMES = {"צפוף", "רגיל", "רחב", "רחב מאוד", "רחב ביותר"};

    private static JComboBox<String> spaceBox(float cur) {
        JComboBox<String> b = new JComboBox<>();
        b.setEditable(true);
        String sel = null;
        for (int k = 0; k < SPACES.length; k++) {
            String n = SPACE_NAMES[k] + " (" + Zoom.format(SPACES[k]) + ")";
            b.addItem(n);
            if (Math.abs(SPACES[k] - cur) < 0.01f) sel = n;
        }
        b.setSelectedItem(sel != null ? sel : Zoom.format(cur));
        return b;
    }

    private static float spaceOf(JComboBox<String> b, float old) {
        Object o = b.getEditor().getItem();
        String s = o == null ? "" : o.toString();
        for (int k = 0; k < SPACES.length; k++) if (s.startsWith(SPACE_NAMES[k] + " (")) return SPACES[k];
        try {
            float f = Float.parseFloat(s.trim().replace(',', '.'));
            if (f > 0) return Math.max(0.2f, Math.min(5f, f));
        } catch (NumberFormatException ignored) { }
        return old;
    }

    /** מאפייני הטאב: כל המאפיינים בחלון אחד (במחשב יש מקום). */
    private void sheetProps(final Sheet s) {
        JDialog d = Ui.dialog(frame, "מאפייני הטאב: " + s.name);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        int[] row = {0};
        java.util.function.BiConsumer<String, JComponent> add = (label, comp) -> {
            c.gridy = row[0]++;
            c.gridx = 0; c.weightx = 0;
            form.add(new JLabel(label), c);
            c.gridx = 1; c.weightx = 1;
            form.add(comp, c);
        };
        JTextField name = new JTextField(s.name, 26);
        JTextField note = new JTextField(s.note, 26);
        JCheckBox lock = new JCheckBox("נעול — רק קריאה", s.locked);
        add.accept("שם:", name);
        add.accept("הערה — למה הוא משמש:", note);
        add.accept("עריכה:", lock);
        final List<Runnable> apply = new ArrayList<>();
        apply.add(() -> { Sheets.rename(s, name.getText()); s.note = note.getText().trim(); s.locked = lock.isSelected(); });
        boolean[] refit = {false};
        switch (s.kind) {
            case START: {
                JPanel kinds = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
                List<JCheckBox> boxes = new ArrayList<>();
                for (Brain.Kind k : Brain.Kind.values()) {
                    JCheckBox b = new JCheckBox(k.label, s.kinds.contains(k));
                    boxes.add(b);
                    kinds.add(b);
                }
                add.accept("סוגי חלקים מותרים:", kinds);
                JComboBox<String> erase = new JComboBox<>();
                for (Sheet.Erase e : Sheet.Erase.values()) erase.addItem(e.label);
                erase.setSelectedIndex(s.erase.ordinal());
                add.accept("מחיקה בפנקס:", erase);
                apply.add(() -> {
                    java.util.Set<Brain.Kind> n = java.util.EnumSet.noneOf(Brain.Kind.class);
                    for (int k = 0; k < boxes.size(); k++) if (boxes.get(k).isSelected()) n.add(Brain.Kind.values()[k]);
                    if (!n.isEmpty()) { s.kinds.clear(); s.kinds.addAll(n); }
                    s.erase = Sheet.Erase.values()[erase.getSelectedIndex()];
                });
                break;
            }
            case FREE: {
                JCheckBox links = new JCheckBox("מותר לחבר בקווים", s.links);
                add.accept("קווים:", links);
                apply.add(() -> s.links = links.isSelected());
                break;
            }
            case MAP: {
                JCheckBox abs = new JCheckBox("סדר מוחלט — לכל פריט מקום בעץ (בלי תלושים)", s.absolute);
                add.accept("סדר:", abs);
                JComboBox<String> dir = new JComboBox<>(new String[]{"לפי התוכנה", "מלמעלה למטה", "מימין לשמאל"});
                dir.setSelectedIndex(s.dir.equals("v") ? 1 : s.dir.equals("h") ? 2 : 0);
                add.accept("כיוון:", dir);
                JComboBox<String> lay = new JComboBox<>(new String[]{"צפופה — כמו מפת חשיבה, קווים בעיקול", "מרווחת — כל ענף מעל התחום שלו"});
                lay.setSelectedIndex(s.layout.equals("tidy") ? 1 : 0);
                add.accept("פריסה:", lay);
                JComboBox<String> sb = spaceBox(s.spaceB), sd = spaceBox(s.spaceD);
                add.accept("מרווח בין אחים:", sb);
                add.accept("מרווח בין רמות:", sd);
                JComboBox<String> view = new JComboBox<>(new String[]{"לפי התוכנה", "גרפית", "טקסט"});
                view.setSelectedIndex(s.view.equals("map") ? 1 : s.view.equals("text") ? 2 : 0);
                add.accept("תצוגה:", view);
                int moved = 0;
                for (Item r : s.roots) moved += Edit.countOffsets(r);
                if (moved > 0) {
                    JCheckBox reset = new JCheckBox("להחזיר " + moved + " פריטים שהוזזו ביד למקום האוטומטי", false);
                    add.accept("הזזות ביד:", reset);
                    apply.add(() -> { if (reset.isSelected()) { for (Item r : s.roots) Edit.clearOffsets(r); refit[0] = true; } });
                }
                apply.add(() -> {
                    s.absolute = abs.isSelected();
                    String nd = dir.getSelectedIndex() == 1 ? "v" : dir.getSelectedIndex() == 2 ? "h" : "";
                    String nl = lay.getSelectedIndex() == 1 ? "tidy" : "";
                    float b = spaceOf(sb, s.spaceB), dd = spaceOf(sd, s.spaceD);
                    if (!nd.equals(s.dir) || !nl.equals(s.layout) || b != s.spaceB || dd != s.spaceD) refit[0] = true;
                    s.dir = nd; s.layout = nl; s.spaceB = b; s.spaceD = dd;
                    s.view = view.getSelectedIndex() == 1 ? "map" : view.getSelectedIndex() == 2 ? "text" : "";
                });
                break;
            }
        }
        JPanel body = new JPanel(new BorderLayout());
        JLabel kind = new JLabel("סוג: " + s.kind.label);
        kind.setForeground(new Color(0x888780));
        kind.setBorder(BorderFactory.createEmptyBorder(0, 4, 8, 4));
        body.add(kind, BorderLayout.NORTH);
        body.add(form, BorderLayout.CENTER);
        JButton ok = Ui.button("אישור", () -> {
            for (Runnable r : apply) r.run();
            d.dispose();
            changedAny(true);
            if (refit[0]) render(null, true);
        });
        body.add(Ui.buttons(ok, Ui.button("ביטול", d::dispose)), BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(ok);
        Ui.show(d, body, frame);
    }

    private void addSheet() {
        if (cur == null || cur.project == null) return;
        JDialog d = Ui.dialog(frame, "טאב חדש");
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        final Sheet.Kind[] all = Sheet.Kind.values();
        ButtonGroup g = new ButtonGroup();
        List<JRadioButton> rs = new ArrayList<>();
        JPanel kinds = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        JTextField name = new JTextField(Sheet.Kind.MAP.label, 24);
        for (Sheet.Kind k : all) {
            JRadioButton r = new JRadioButton(k.label, k == Sheet.Kind.MAP);
            r.addActionListener(e -> {
                boolean isDefault = false;
                for (Sheet.Kind x : all) if (name.getText().trim().equals(x.label)) isDefault = true;
                if (isDefault || name.getText().trim().isEmpty()) name.setText(k.label);
            });
            g.add(r);
            rs.add(r);
            kinds.add(r);
        }
        c.gridy = 0; c.gridx = 0; form.add(new JLabel("סוג:"), c);
        c.gridx = 1; c.weightx = 1; form.add(kinds, c);
        c.gridy = 1; c.gridx = 0; c.weightx = 0; form.add(new JLabel("שם:"), c);
        c.gridx = 1; c.weightx = 1; form.add(name, c);
        JButton ok = Ui.button("צור", () -> {
            Sheet.Kind k = Sheet.Kind.MAP;
            for (int i = 0; i < rs.size(); i++) if (rs.get(i).isSelected()) k = all[i];
            Sheet s = Sheets.add(cur.project, k, name.getText().trim().isEmpty() ? k.label : name.getText().trim());
            d.dispose();
            cur.sheetId = s.id;
            cur.viewMode = 0;
            changedAny(true);
            render(null, true);
        });
        name.addActionListener(e -> ok.doClick());
        JPanel body = new JPanel(new BorderLayout());
        body.add(form, BorderLayout.CENTER);
        body.add(Ui.buttons(ok, Ui.button("ביטול", d::dispose)), BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(ok);
        Ui.show(d, body, frame);
    }

    // ---------------- הגדרות ----------------

    private void settings() {
        JDialog d = Ui.dialog(frame, "הגדרות");
        JPanel box = new JPanel();
        box.setLayout(new javax.swing.BoxLayout(box, javax.swing.BoxLayout.Y_AXIS));
        JCheckBox cb = new JCheckBox("שמירה אוטומטית (שתי שניות אחרי כל שינוי, ובסגירה)", autosave);
        box.add(cb);
        box.add(javax.swing.Box.createVerticalStrut(12));
        box.add(new JLabel("צפיפות סקאלת ההגדלה:"));
        ButtonGroup g = new ButtonGroup();
        JRadioButton half = new JRadioButton("כל חצי (0.5, 1, 1.5, 2…)");
        JRadioButton quarter = new JRadioButton("כל רבע (0.25, 0.5, 0.75, 1…)");
        JRadioButton otherR = new JRadioButton("מספר אחר:");
        JTextField custom = new JTextField(6);
        g.add(half); g.add(quarter); g.add(otherR);
        if (Math.abs(zoomStep - 0.5f) < 1e-4) half.setSelected(true);
        else if (Math.abs(zoomStep - 0.25f) < 1e-4) quarter.setSelected(true);
        else { otherR.setSelected(true); custom.setText(Zoom.format(zoomStep)); }
        box.add(half);
        box.add(quarter);
        JPanel o = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        o.add(otherR);
        o.add(custom);
        o.setAlignmentX(0);
        box.add(o);
        box.add(javax.swing.Box.createVerticalStrut(12));
        box.add(new JLabel("גלגלת העכבר במפה:"));
        ButtonGroup g2 = new ButtonGroup();
        JRadioButton wz = new JRadioButton("מגדילה ומקטינה (Shift+גלגלת — גלילה לרוחב)", map.wheelZooms);
        JRadioButton ws = new JRadioButton("גוללת (Ctrl+גלגלת — הגדלה)", !map.wheelZooms);
        g2.add(wz); g2.add(ws);
        box.add(wz);
        box.add(ws);
        box.add(javax.swing.Box.createVerticalStrut(12));
        box.add(new JLabel("חלון הפריט (מידע, תגיות, תכונות, בחינות):"));
        ButtonGroup g3 = new ButtonGroup();
        JRadioButton ps = new JRadioButton("לוח קבוע בצד המפה — מראה את הפריט הנבחר (F4 מסתיר ומחזיר)", sideMode);
        JRadioButton pw = new JRadioButton("חלון נפרד — נפתח ב-Alt+Enter או בלחיצה ימנית", !sideMode);
        g3.add(ps); g3.add(pw);
        box.add(ps);
        box.add(pw);
        for (java.awt.Component c : box.getComponents()) if (c instanceof JComponent) ((JComponent) c).setAlignmentX(1f);
        JPanel body = new JPanel(new BorderLayout());
        body.add(box, BorderLayout.CENTER);
        JButton ok = Ui.button("אישור", () -> {
            autosave = cb.isSelected();
            float s = zoomStep;
            if (half.isSelected()) s = 0.5f;
            else if (quarter.isSelected()) s = 0.25f;
            else try { s = Float.parseFloat(custom.getText().trim().replace(',', '.')); } catch (NumberFormatException ignored) { }
            zoomStep = Zoom.clampStep(s);
            map.wheelZooms = wz.isSelected();
            prefs.putBoolean("autosave", autosave);
            prefs.putFloat("zoomStep", zoomStep);
            prefs.put("wheel", map.wheelZooms ? "zoom" : "scroll");
            freePanel.wheelZooms = map.wheelZooms;
            sideMode = ps.isSelected();
            prefs.put("itemPane", sideMode ? "side" : "window");
            layoutSide();
            d.dispose();
            if (cur != null && cur.project != null) render(null, false);
        });
        body.add(Ui.buttons(ok, Ui.button("ביטול", d::dispose)), BorderLayout.SOUTH);
        Ui.show(d, body, frame);
    }

    // ---------------- סיעור מוחות ----------------

    private float freeOff;

    @Override public void onFreeAdd(float x, float y) {
        freeOff = 0;
        Ui.ask(frame, "פריט חדש", "", true, s -> {
            com.aviccc.kvutzot.model.FreeMap.Node n = cur.project.free.add(s, x, y + freeOff);
            freeOff += 56;
            freePanel.select(n);
            changedAny(false);
            freePanel.repaint();
        });
    }

    @Override public void onFreeEdit(com.aviccc.kvutzot.model.FreeMap.Node n) {
        Ui.ask(frame, "הפריט", n.text, false, s -> {
            n.text = s;
            changedAny(false);
            freePanel.repaint();
        });
    }

    @Override public void onFreeDelete(com.aviccc.kvutzot.model.FreeMap.Node n) {
        com.aviccc.kvutzot.model.FreeMap fm = cur.project.free;
        Ui.confirm(frame, "למחוק את \"" + n.text + "\"" + (fm.neighbors(n).isEmpty() ? "?" : " ואת הקווים שלו?"), "מחק", () -> {
            fm.remove(n);
            freePanel.select(null);
            changedAny(false);
            freePanel.repaint();
        });
    }

    @Override public void onFreeMenu(com.aviccc.kvutzot.model.FreeMap.Node n, int sx, int sy) {
        JPopupMenu m = Ui.popup();
        fillFreeMenu(m, n);
        if (m.getComponentCount() == 0) return;
        m.show(freePanel, Math.max(0, sx - m.getPreferredSize().width), sy);
    }

    private void fillFreeMenu(java.awt.Container m, com.aviccc.kvutzot.model.FreeMap.Node n) {
        if (locked()) {
            JMenuItem x = new JMenuItem("הטאב נעול — רק קריאה");
            x.setEnabled(false);
            m.add(x);
            return;
        }
        com.aviccc.kvutzot.model.FreeMap fm = cur.project.free;
        m.add(withKey(mi("ערוך…", () -> onFreeEdit(n)), KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)));
        if (freePanel.links) m.add(mi("חבר לפריט אחר…", () -> freePanel.startLink(n)));
        sep(m);
        m.add(mi("העבר למפה", () -> toMap(n, true)));
        m.add(mi("העתק למפה (ונשאר כאן)", () -> toMap(n, false)));
        List<com.aviccc.kvutzot.model.FreeMap.Node> nb = fm.neighbors(n);
        if (!nb.isEmpty()) {
            sep(m);
            for (com.aviccc.kvutzot.model.FreeMap.Node o : nb)
                m.add(mi("נתק מ: " + o.text, () -> { fm.unlink(n, o); changedAny(false); freePanel.repaint(); }));
        }
        sep(m);
        m.add(withKey(mi("מחק…", () -> onFreeDelete(n)), KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)));
    }

    @Override public void onFreeBackground(float x, float y, int sx, int sy) {
        if (locked()) return;
        JPopupMenu m = Ui.popup();
        m.add(mi("פריט חדש כאן…", () -> onFreeAdd(x, y)));
        m.show(freePanel, Math.max(0, sx - m.getPreferredSize().width), sy);
    }

    @Override public void onFreeChanged() { changedAny(false); freePanel.repaint(); }

    @Override public void onFreeStatus(String st) { setStatus(st); }

    /** מסיעור המוחות למפה: בוחרים מפה (אם יש כמה); במפה בסדר מוחלט — גם מקום בעץ, אחרת — תלוש. */
    private void toMap(com.aviccc.kvutzot.model.FreeMap.Node n, boolean move) {
        List<Sheet> maps = new ArrayList<>();
        for (Sheet s : cur.project.sheets) if (s.kind == Sheet.Kind.MAP && !s.locked) maps.add(s);
        if (maps.isEmpty()) { setStatus("כל המפות נעולות"); return; }
        if (maps.size() == 1) { toMap(n, move, maps.get(0)); return; }
        List<String> names = new ArrayList<>();
        for (Sheet s : maps) names.add(s.name + (s.absolute ? " · סדר מוחלט" : ""));
        Ui.choose(frame, "לאיזו מפה?", names, -1, w -> toMap(n, move, maps.get(w)));
    }

    private void toMap(com.aviccc.kvutzot.model.FreeMap.Node n, boolean move, Sheet ms) {
        Runnable done = () -> {
            if (move) { cur.project.free.remove(n); freePanel.select(null); freePanel.repaint(); }
            changedAny(false);
        };
        if (!ms.absolute) {
            Item it = new Item(n.text);
            it.loose = true;
            ms.roots.add(it);
            done.run();
            setStatus((move ? "עבר" : "הועתק") + " ל\"" + ms.name + "\" כתלוש: " + n.text);
            return;
        }
        chooseParent(ms, null, (parent, d) -> {
            Item it = Edit.addChild(parent, d, n.text);
            parent.collapsed = false;
            done.run();
            setStatus((move ? "עבר" : "הועתק") + " ל\"" + ms.name + "\" תחת \"" + parent.name + "\": " + it.name);
        });
    }

    // ---------------- המפה: בחירה, תפריט, עריכה ----------------

    @Override public void onZoom(float z) { zoomLabel.setText(Zoom.format(z)); }

    @Override public void onSelect(MapLayout.Node n) { updateSide(); }

    /** מקשים כמו במפות חשיבה: Tab — בן, Enter — אח, רווח — כיווץ או פתיחה. */
    @Override public void onKey(String action) {
        MapLayout.Node n = map.selectedNode();
        if (n == null || cur == null || !onMap()) return;
        if (!editable()) {
            if ("sibling".equals(action)) details(n);
            else setStatus(locked() ? "הטאב נעול — רק קריאה" : "תצוגה — רק קריאה. לעריכה: \"עץ\"");
            return;
        }
        final Item it = n.item;
        switch (action) {
            case "child":
                addChildren(it, n.division, n.isDivision() ? "בן חדש ב\"" + n.text + "\"" : "בן חדש ל\"" + it.name + "\"");
                break;
            case "sibling":
                if (n.isDivision()) { setStatus("לחלוקה אין אחים — Tab מוסיף לה בן"); break; }
                if (it.parentDivision == null && !it.loose) { setStatus("לראש המפה אין אחים — Tab מוסיף בן"); break; }
                addSiblings(it);
                break;
            case "collapse":
                if (!n.isDivision() && it.childCount() > 0) { it.collapsed = !it.collapsed; changed(it, null); }
                break;
            default: break;
        }
    }

    @Override public void onDragged(Item it) {
        if (!editable()) return;
        changed(null, null);
        setStatus("הוזז: " + it.name + " · להחזיר — לחיצה ימנית, ואז \"החזר למקום האוטומטי\"");
    }

    /** לחיצה כפולה: שינוי שם (או בחינה לחלוקה); בתצוגה או בטאב נעול — המידע על הפריט. */
    @Override public void onOpen(MapLayout.Node n) {
        if (!editable()) { details(n); return; }
        editNode(n);
    }

    private Item original(MapLayout.Node n) {
        if (n.isDivision()) return null;
        return cur.viewResult == null ? n.item : cur.viewResult.original(n.item);
    }

    private void editNode(MapLayout.Node n) {
        if (n.isDivision()) {
            askAspect("החלוקה — לפי איזו בחינה?", n.division.basis, s -> {
                Edit.setAspect(cur.project, n.division, s);
                changed(n.item, n.division);
            });
        } else {
            Ui.ask(frame, "שם הפריט", n.item.name, false, s -> {
                if (!s.isEmpty()) { n.item.name = s; changed(n.item, null); }
            });
        }
    }

    @Override public void onMenu(MapLayout.Node n, int x, int y) {
        if (cur == null || cur.project == null) return;
        JPopupMenu m = Ui.popup();
        if (n == null) backgroundItems(m);
        else fillNodeMenu(m, n);
        if (m.getComponentCount() == 0) return;
        Ui.rtl(m);
        // מימין לשמאל: התפריט נפתח שמאלה מהעכבר
        m.show(map, Math.max(0, x - m.getPreferredSize().width), y);
    }

    private void backgroundItems(java.awt.Container m) {
        if (editable() && !cur.project.absolute())
            m.add(mi("פריט תלוש חדש…", () -> Ui.ask(frame, "פריט תלוש חדש", "", true, s -> {
                Item n = Edit.addLoose(cur.project, s);
                changed(n, null);
            })));
        m.add(mi("התאם לחלון", map::fit));
        m.add(mi(vertical ? "כיוון: מימין לשמאל" : "כיוון: מלמעלה למטה", () -> { setVertical(!vertical); render(null, true); }));
        m.add(mi("הצג עד רמה…", this::upToLevel));
    }

    private void fillNodeMenu(java.awt.Container m, final MapLayout.Node n) {
        final Item it = n.item;
        if (!editable()) {
            Item o = original(n);
            if (o != null) m.add(withKey(mi("פרטי הפריט…", () -> details(n)), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK)));
            if (o == null) {
                JMenuItem x = new JMenuItem(locked() ? "הטאב נעול — רק קריאה" : "תצוגה — רק קריאה");
                x.setEnabled(false);
                m.add(x);
            }
            return;
        }
        if (n.isDivision()) {
            final Division d = n.division;
            m.add(withKey(mi("הוסף בנים לחלוקה…", () -> addChildren(it, d, "בן חדש ב\"" + n.text + "\"")), KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)));
            m.add(withKey(mi("בחר בחינה לחלוקה…", () -> editNode(n)), KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)));
            Item a = cur.project.aspect(d.aspectId);
            if (a != null) m.add(withKey(mi("פרטי הבחינה \"" + a.name + "\"…", () -> details(n)), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK)));
            if (clip != null) m.add(mi("הדבק כבן כאן: " + clip.name, () -> { Item c = Edit.paste(clip, it, d); changed(c, null); }));
            sep(m);
            m.add(mi("מחק את החלוקה" + (d.children.isEmpty() ? "" : " ואת " + countDiv(d) + " הפריטים שבה") + "…", () -> deleteNode(n)));
            return;
        }
        m.add(withKey(mi("הוסף בנים…", () -> addChildren(it, null, "בן חדש ל\"" + it.name + "\"")), KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)));
        if (it.parentDivision != null || it.loose) m.add(withKey(mi("הוסף אחים…", () -> addSiblings(it)), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)));
        m.add(withKey(mi("שנה שם…", () -> editNode(n)), KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)));
        m.add(mi("הוסף חלוקה בצד…", () -> askAspect("החלוקה החדשה — לפי איזו בחינה? (למשל \"לפי צבע\")", "", s -> {
            Division d = Edit.addSide(it, s);
            Edit.setAspect(cur.project, d, s);
            it.collapsed = false;
            changed(it, d.children.isEmpty() && !d.basis.isEmpty() ? d : null);
        })));
        if (it.childCount() > 0) m.add(withKey(mi(it.collapsed ? "פתח את הענף" : "כווץ את הענף", () -> { it.collapsed = !it.collapsed; changed(it, null); }), KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)));
        sep(m);
        if (it.parentDivision != null) {
            m.add(mi("הזז לפני", () -> { if (Edit.move(it, -1)) changed(it, null); }));
            m.add(mi("הזז אחרי", () -> { if (Edit.move(it, 1)) changed(it, null); }));
        }
        if (it.loose || it.parentDivision != null)
            m.add(mi(it.loose ? "הצב בעץ…" : "העבר למקום אחר בעץ…", () -> chooseParent(cur.project.active(Sheet.Kind.MAP), it, (parent, d) -> {
                if (Edit.placeUnder(cur.project, it, parent, d)) { parent.collapsed = false; changed(it, null); }
                else setStatus("אי אפשר להציב פריט תחת עצמו או תחת בנו");
            })));
        final int movedHere = Edit.countOffsets(it);
        if (movedHere > 0)
            m.add(mi("החזר למקום האוטומטי" + (movedHere > 1 || (it.dx == 0 && it.dy == 0) ? " (" + movedHere + " בענף)" : ""),
                    () -> { Edit.clearOffsets(it); changed(it, null); }));
        sep(m);
        final boolean canDelete = it.parentDivision != null || it.loose;
        if (canDelete) m.add(withKey(mi("גזור", () -> cut(it)), ctrl(KeyEvent.VK_X)));
        m.add(withKey(mi("העתק", () -> copy(it)), ctrl(KeyEvent.VK_C)));
        if (clip != null) m.add(withKey(mi("הדבק כבן: " + clip.name, () -> paste(n)), ctrl(KeyEvent.VK_V)));
        sep(m);
        m.add(withKey(mi("פרטי הפריט (מידע, תגיות, תכונות, בחינות)…", () -> details(n)), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK)));
        if (canDelete) {
            sep(m);
            m.add(withKey(mi("מחק" + (it.childCount() > 0 ? " (עם " + (Edit.countBranch(it) - 1) + " הפריטים שמתחתיו)" : "") + "…", () -> deleteNode(n)),
                    KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)));
        }
    }

    private static JMenuItem withKey(JMenuItem i, KeyStroke k) { i.setAccelerator(k); return i; }

    private static void sep(java.awt.Container m) {
        if (m instanceof JPopupMenu) ((JPopupMenu) m).addSeparator();
        else if (m instanceof JMenu) ((JMenu) m).addSeparator();
    }

    private void addChildren(Item it, Division d, String title) {
        Ui.ask(frame, title, "", true, s -> {
            Item c = Edit.addChild(it, d, s);
            if (it.collapsed) it.collapsed = false;
            changed(c, null);
        });
    }

    private void addSiblings(Item it) {
        final Item[] last = {it};
        Ui.ask(frame, "אח חדש ל\"" + it.name + "\"", "", true, s -> {
            Item c = Edit.addSibling(cur.project, last[0], s);
            if (c != null) { last[0] = c; changed(c, null); }
        });
    }

    private void copy(Item it) {
        clip = Edit.copy(it);
        setStatus("הועתק: " + it.name);
    }

    private void cut(Item it) {
        if (it.parentDivision == null && !it.loose) return;
        clip = Edit.copy(it);
        Item parent = it.parent();
        Edit.delete(cur.project, it);
        changed(parent, null);
        setStatus("נגזר: " + clip.name + " · בחר פריט ו\"הדבק\" (Ctrl+V)");
    }

    private void paste(MapLayout.Node n) {
        if (clip == null) return;
        Item c = Edit.paste(clip, n.item, n.division);
        n.item.collapsed = false;
        changed(c, null);
    }

    private void deleteNode(MapLayout.Node n) {
        final Item it = n.item;
        if (n.isDivision()) {
            final Division d = n.division;
            Ui.confirm(frame, "למחוק את החלוקה \"" + n.text + "\"" + (d.children.isEmpty() ? "?" : " ואת כל " + countDiv(d) + " הפריטים שבה?"), "מחק",
                    () -> { Edit.deleteDivision(d); changed(it, null); });
            return;
        }
        if (it.parentDivision == null && !it.loose) { setStatus("את ראש המפה אי אפשר למחוק"); return; }
        Ui.confirm(frame, "למחוק את \"" + it.name + "\"" + (it.childCount() > 0 ? " ואת כל " + (Edit.countBranch(it) - 1) + " הפריטים שמתחתיו?" : "?"), "מחק", () -> {
            Item parent = it.parent();
            Edit.delete(cur.project, it);
            changed(parent, null);
        });
    }

    private static int countDiv(Division d) {
        int c = 0;
        for (Item i : d.children) c += Edit.countBranch(i);
        return c;
    }

    private interface Placed { void at(Item parent, Division d); }

    /** בחירת מקום בעץ: אב (רשימה לפי העץ), ואם יש לו כמה חלוקות — החלוקה. */
    private void chooseParent(Sheet ms, final Item moving, final Placed then) {
        final List<Item> items = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (Item r : ms.roots) if (!r.loose) listTree(r, 0, moving, items, names);
        if (items.isEmpty()) { setStatus("אין בעץ מקום להציב"); return; }
        Ui.choose(frame, "תחת איזה פריט?", names, -1, w -> {
            final Item par = items.get(w);
            if (par.divisions.size() <= 1) { then.at(par, null); return; }
            List<String> ds = new ArrayList<>();
            for (int k = 0; k < par.divisions.size(); k++) {
                String b = par.divisions.get(k).basis;
                ds.add(b.isEmpty() ? "צד " + Hebrew.number(k + 1) : b);
            }
            Ui.choose(frame, "באיזו חלוקה של \"" + par.name + "\"?", ds, -1, w2 -> then.at(par, par.divisions.get(w2)));
        });
    }

    private static void listTree(Item it, int depth, Item skip, List<Item> items, List<String> names) {
        if (it == skip) return;
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < depth; k++) b.append("      ");
        if (depth > 0) b.append("· ");
        items.add(it);
        names.add(b + (it.name.isEmpty() ? "(בלי שם)" : it.name));
        for (Division d : it.divisions) for (Item c : d.children) listTree(c, depth + 1, skip, items, names);
    }

    /** בחירת בחינה: מתוך הקיימות או חדשה. ריק — חלוקה בלי בחינה. */
    private void askAspect(String title, String initial, java.util.function.Consumer<String> then) {
        List<String> names = new ArrayList<>();
        for (Item a : cur.project.aspects.values()) names.add(a.name);
        Ui.askCombo(frame, title, initial, names, names.isEmpty() ? "בחינה חדשה תישמר ברשימת הבחינות של הקובץ."
                : "בחינה קיימת — מהרשימה; שם חדש — בחינה חדשה. ריק — חלוקה בלי בחינה.", then);
    }

    /** אחרי כל שינוי במפה: ציור מחדש בלי לזוז, בחירה, ושמירה אוטומטית אחרי שתי שניות של שקט. */
    private void changed(Item select, Division selDiv) {
        markChanged();
        if (select != null) map.selectItem(select, selDiv);
        render(null, false);
    }

    /** שינוי בטאבים או בכותרת: לסמן, לשמור, ולבנות מחדש כשהמבנה השתנה. */
    private void changedAny(boolean structure) {
        markChanged();
        if (structure) render(null, false);
        else {
            rebuildTabRow();
            Sheet sh = sheet(cur);
            if (sh != null && sh.kind != Sheet.Kind.MAP)
                setStatus(summary(cur) + (sh.locked ? " · נעול" : "") + (cur.path == null ? " · לא שמור" : autosave ? " · שומר…" : " · לא נשמר"));
        }
    }

    private void markChanged() {
        if (cur == null) return;
        cur.dirty = true;
        rebuildTabRow();
        if (autosave && cur.path != null) saver.restart();
    }
}
