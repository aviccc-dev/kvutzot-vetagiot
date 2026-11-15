package com.aviccc.kvutzot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.aviccc.kvutzot.model.Brain;
import com.aviccc.kvutzot.model.FreeMap;
import com.aviccc.kvutzot.model.Edit;
import com.aviccc.kvutzot.model.MapLayout;
import com.aviccc.kvutzot.model.Model.Division;
import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.Model.Sheet;
import com.aviccc.kvutzot.model.Sheets;
import com.aviccc.kvutzot.model.OpmlIO;
import com.aviccc.kvutzot.model.TextLines;
import com.aviccc.kvutzot.model.Views;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/** גרסה 0.1: פתיחת קובץ, תצוגת טקסט, שמירה, וזיהוי שינוי מבחוץ. */
public class Main extends Activity implements MapView.Host, FreeView.Host {
    private static final int REQ_OPEN = 1, REQ_CREATE = 2, REQ_SAVE_AS = 3, REQ_EXPORT = 4, REQ_BRAIN = 5, REQ_PNG = 6, REQ_PDF = 7;
    private static final int M_OPEN = 1, M_NEW = 2, M_SAVE = 3, M_SAVE_AS = 4, M_RELOAD = 5,
            M_EX_TRI = 6, M_EX_TOYS = 7, M_ABOUT = 8, M_EXPORT = 9,
            M_VIEW = 10, M_DIR = 11, M_FIT = 12, M_LOOSE = 13, M_SETTINGS = 14, M_SHOW = 15, M_SORT = 16, M_TABS = 17, M_CLOSE = 18, M_BRAIN = 19, M_TITLE = 20, M_LEVEL = 21, M_UNDO = 22, M_REDO = 23, M_SEARCH = 24, M_TAGS = 25, M_BACKUPS = 26, M_PNG = 27, M_PDF = 28;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private SharedPreferences prefs;
    private TextView body;
    private LinearLayout tabRow, viewRow;
    /** הטאבים: כל טאב — קובץ פתוח (או דוגמה). המצב של הטאב הנוכחי נמצא בשדות של הפעילות. */
    private final java.util.List<Doc> docs = new java.util.ArrayList<>();
    private Doc cur;
    /** פעולת קריאה/כתיבה ברקע — לא עוברים טאב באמצע. */
    private int busy;

    static final class Doc {
        Uri uri;
        String fileName = "";
        String lastText;
        /** מה שנקרא בפועל מהקובץ אחרי השמירה האחרונה (שירותים כמו Dropbox מחזירים לפעמים גרסה אחרת). */
        String lastRead;
        Project project;
        /** הטאב בתוך הקובץ: 0 התחלת נושא, 1 סיעור מוחות, 2 מפה. */
        int part = 2;
        boolean dirty;
        int viewMode, tagLevel = 1, levelFrom = 1, levelTo = 1;
        String sheetId = "";
        /** ביטול פעולה (0.11): מצבי הקובץ הקודמים, המצב הנוכחי, ומה שבוטל. */
        final java.util.ArrayList<String> undo = new java.util.ArrayList<>(), redo = new java.util.ArrayList<>();
        String snap;
        /** המקום וההגדלה של כל מפה ותצוגה, כדי לחזור אליהם. */
        final java.util.HashMap<String, float[]> views = new java.util.HashMap<>();
        String viewInfo = "";
        Views.Sort viewSort = Views.Sort.MAP;
        /** פרוס בשורת הטאבים (או ברקע — רק בניהול). */
        boolean spread = true;
        String label() {
            if (project != null && !project.title.isEmpty()) return project.title;
            String f = fileName == null || fileName.isEmpty() ? "קובץ" : fileName;
            return f.endsWith(".xml") ? f.substring(0, f.length() - 4) : f;
        }
    }
    /** הקובץ הפתוח; null כשמוצגת דוגמה שלא נשמרה. */
    private Uri uri;
    private String fileName = "";
    /** הטקסט כפי שנטען או נשמר לאחרונה, להשוואה עם שינוי מבחוץ. */
    private String lastText;
    private String lastRead;
    /** מונה שינויים: כך יודעים אחרי שמירה אם השתנה משהו בינתיים, בלי לבנות את כל הקובץ שוב. */
    private long rev;
    /** כל הקריאות והכתיבות לקבצים — בתור אחד, אחת אחרי השנייה (0.10.6: בלי שתי שמירות במקביל). */
    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor();
    private Project project;
    /** סוג הטאב הנוכחי בתוך הקובץ: 0 התחלת נושא, 1 סיעור מוחות (מפה גמישה), 2 מפה. */
    private int part = 2;
    /** הטאב הנוכחי (0.10: כמה מכל סוג). */
    private String sheetId = "";
    private BrainView brainView;
    private ScrollView brainScroll;
    private FreeView freeView;
    private TextView freeAdd;
    private boolean asking;
    private ScrollView textScroll;
    private MapView map;
    private boolean showMap, vertical;
    /** מה שמוצג כרגע במפה; הטקסט והמפה נבנים רק כשהם על המסך (מהירות בקובץ גדול). */
    private Project shownP;
    private boolean textStale = true, mapStale = true, refitPending;
    /** יש שינויים שלא נשמרו. */
    private boolean dirty;
    private boolean autosave;
    private float zoomStep;
    private LinearLayout zoomBar;
    private TextView zoomLabel;
    /** תצוגה לפי פריט מידע: 0 רגילה, 1 תגיות, 2 מידע בשם, 3 תכונות. */
    private int viewMode;
    private int tagLevel = 1;
    private String viewInfo = "";
    private Views.Sort viewSort = Views.Sort.MAP;
    /** תצוגה 5: מרמה עד רמה. */
    private int levelFrom = 1, levelTo = 1;
    private Views.Result viewResult;
    private Item clip;
    private final android.os.Handler handler = new android.os.Handler();
    private final Runnable saver = new Runnable() { public void run() { if (dirty && uri != null) save(); } };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("kv", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pad = dp(10);
        tabRow = strip(Color.rgb(241, 239, 232));
        root.addView((View) tabRow.getParent());
        viewRow = strip(0xFFFFFFFF);
        root.addView((View) viewRow.getParent());
        View sep = new View(this);
        sep.setBackgroundColor(0xFFD3D1C7);
        root.addView(sep, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
        ScrollView sv = new ScrollView(this);
        body = new TextView(this);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        body.setTextColor(Color.rgb(44, 44, 42));
        body.setPadding(pad, pad, pad, dp(40));
        body.setLineSpacing(dp(3), 1f);
        body.setTextDirection(View.TEXT_DIRECTION_RTL);
        body.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        body.setHighlightColor(0x33185FA5);
        sv.addView(body);
        textScroll = sv;
        map = new MapView(this);
        map.setHost(this);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(sv, new android.widget.FrameLayout.LayoutParams(-1, -1));
        frame.addView(map, new android.widget.FrameLayout.LayoutParams(-1, -1));
        brainScroll = new ScrollView(this);
        brainScroll.setFillViewport(true);
        brainView = new BrainView(this);
        brainScroll.addView(brainView);
        frame.addView(brainScroll, new android.widget.FrameLayout.LayoutParams(-1, -1));
        freeView = new FreeView(this);
        frame.addView(freeView, new android.widget.FrameLayout.LayoutParams(-1, -1));
        freeAdd = new TextView(this);
        freeAdd.setText("+ פריט");
        freeAdd.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        freeAdd.setTextColor(0xFF0C447C);
        freeAdd.setPadding(dp(16), dp(9), dp(16), dp(9));
        android.graphics.drawable.GradientDrawable fg = new android.graphics.drawable.GradientDrawable();
        fg.setColor(0xF2FFFFFF);
        fg.setStroke(dp(1), 0xFF185FA5);
        fg.setCornerRadius(dp(20));
        freeAdd.setBackground(fg);
        freeAdd.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            onFreeAdd(freeView.centerX(), freeView.centerY()); }});
        android.widget.FrameLayout.LayoutParams fl = new android.widget.FrameLayout.LayoutParams(-2, -2,
                android.view.Gravity.BOTTOM | android.view.Gravity.LEFT);
        fl.setMargins(dp(12), 0, 0, dp(14));
        frame.addView(freeAdd, fl);
        zoomBar = buildZoomBar();
        android.widget.FrameLayout.LayoutParams zl = new android.widget.FrameLayout.LayoutParams(-2, -2,
                android.view.Gravity.BOTTOM | android.view.Gravity.LEFT);
        zl.setMargins(dp(12), 0, 0, dp(14));
        frame.addView(zoomBar, zl);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        showMap = prefs.getBoolean("showMap", true);
        vertical = prefs.getBoolean("vertical", true);
        autosave = prefs.getBoolean("autosave", true);
        zoomStep = com.aviccc.kvutzot.model.Zoom.clampStep(prefs.getFloat("zoomStep", 0.5f));
        applyView();

        restoreTabs();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stash();
        saveTabs();
        handler.removeCallbacks(saver);
        if (dirty && autosave && uri != null) save();
    }

    /** יציאה ב"חזרה": אם יש שינויים שלא יישמרו (דוגמה, או שמירה אוטומטית כבויה) — שואלים קודם. */
    @Override
    public void onBackPressed() {
        stash();
        int n = 0;
        String first = null;
        for (Doc d : docs) if (d.dirty && (d.uri == null || !autosave)) { n++; if (first == null) first = d.label(); }
        if (n == 0) { super.onBackPressed(); return; }
        new AlertDialog.Builder(this).setTitle("יש שינויים שלא נשמרו")
                .setMessage((n == 1 ? "ב\"" + first + "\"" : "ב-" + n + " טאבים") + ". לצאת ולאבד אותם?")
                .setPositiveButton("צא בלי לשמור", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { Main.super.onBackPressed(); }})
                .setNegativeButton("ביטול", null).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkExternalChange();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void applyView() {
        boolean has = project != null;
        if (brainScroll != null) brainScroll.setVisibility(has && part == 0 ? View.VISIBLE : View.GONE);
        if (freeView != null) freeView.setVisibility(has && part == 1 ? View.VISIBLE : View.GONE);
        if (freeAdd != null) freeAdd.setVisibility(has && part == 1 && !locked() ? View.VISIBLE : View.GONE);
        if (has && part != 2) {
            map.setVisibility(View.GONE);
            textScroll.setVisibility(View.GONE);
            if (zoomBar != null) zoomBar.setVisibility(View.GONE);
            invalidateOptionsMenu();
            return;
        }
        boolean m = showMap && project != null;
        if (project != null && shownP != null) {
            if (m && mapStale) {
                mapStale = false;
                float[] st = wantRestore && cur != null ? cur.views.get(viewKey()) : null;
                wantRestore = false;
                if (st != null) { map.setProject(shownP, vertical, false); map.setView(st); }
                else map.setProject(shownP, vertical, refitPending);
                refitPending = false;
            }
            if (!m && textStale) { textStale = false; buildText(shownP); }
        }
        map.setVisibility(m ? View.VISIBLE : View.GONE);
        if (zoomBar != null) zoomBar.setVisibility(m ? View.VISIBLE : View.GONE);
        textScroll.setVisibility(m ? View.GONE : View.VISIBLE);
        invalidateOptionsMenu();
    }

    private void showWelcome() {
        setTitle("קבוצות ותגיות");
        fileName = "";
        setStatus("אין קובץ פתוח");
        rebuildStrips();
        body.setText("פתח קובץ, צור קובץ חדש, או פתח דוגמה מהתפריט (⋮).\n\n"
                + "כדי לבדוק שמירה ב-Dropbox: פתח את דוגמת המשולש ובחר \"שמור עותק בשם\".");
    }

    // ---------------- תפריט ----------------

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, M_UNDO, 0, "↶").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        m.add(0, M_SEARCH, 0, "חיפוש").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        m.add(0, M_REDO, 0, "בצע שוב (↷)");
        m.add(0, M_TAGS, 0, "תגיות ובחינות…");
        m.add(0, M_OPEN, 0, "פתח קובץ (בטאב חדש)");
        m.add(0, M_TABS, 0, "ניהול טאבים");
        m.add(0, M_CLOSE, 0, "סגור את הטאב");
        m.add(0, M_DIR, 1, "כיוון המפה");
        m.add(0, M_FIT, 1, "התאם למסך");
        m.add(0, M_LEVEL, 1, "רמות…");
        m.add(0, M_NEW, 1, "מפה חדשה");
        m.add(0, M_TITLE, 1, "שנה כותרת");
        m.add(0, M_SAVE, 2, "שמור");
        m.add(0, M_SAVE_AS, 3, "שמור עותק בשם");
        m.add(0, M_RELOAD, 4, "טען מחדש");
        m.add(0, M_EXPORT, 4, "ייצוא ל-OPML (לתוכנות אחרות)");
        m.add(0, M_PDF, 4, "ייצוא המפה ל-PDF");
        m.add(0, M_PNG, 4, "ייצוא המפה לתמונה");
        m.add(0, M_BACKUPS, 4, "גיבויים…");
        m.add(0, M_EX_TRI, 5, "דוגמה: משולש");
        m.add(0, M_EX_TOYS, 6, "דוגמה: צעצועים");
        m.add(0, M_LOOSE, 5, "פריט תלוש חדש");
        m.add(0, M_SETTINGS, 7, "הגדרות");
        m.add(0, M_ABOUT, 7, "אודות");
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu m) {
        m.findItem(M_SAVE).setEnabled(uri != null && project != null);
        m.findItem(M_SAVE_AS).setEnabled(project != null);
        m.findItem(M_TITLE).setVisible(project != null);
        m.findItem(M_RELOAD).setEnabled(uri != null);
        m.findItem(M_EXPORT).setEnabled(project != null);
        m.findItem(M_TABS).setVisible(!docs.isEmpty());
        m.findItem(M_UNDO).setVisible(project != null);
        m.findItem(M_REDO).setVisible(project != null && cur != null && !cur.redo.isEmpty());
        m.findItem(M_SEARCH).setVisible(project != null);
        m.findItem(M_TAGS).setVisible(project != null);
        m.findItem(M_BACKUPS).setVisible(uri != null && project != null);
        m.findItem(M_PDF).setVisible(project != null && showMap && part == 2);
        m.findItem(M_PNG).setVisible(project != null && showMap && part == 2);
        m.findItem(M_CLOSE).setVisible(cur != null);
        m.findItem(M_DIR).setTitle(vertical ? "כיוון: מימין לשמאל" : "כיוון: מלמעלה למטה").setVisible(project != null && showMap && part == 2);
        m.findItem(M_FIT).setVisible(project != null && showMap && part == 2);
        m.findItem(M_LEVEL).setVisible(project != null && part == 2);
        m.findItem(M_LOOSE).setVisible(project != null && viewMode == 0 && part == 2 && !project.absolute() && !locked());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {
        switch (mi.getItemId()) {
            case M_UNDO: undo(false); return true;
            case M_REDO: undo(true); return true;
            case M_SEARCH: search(); return true;
            case M_TAGS: manageTags(); return true;
            case M_BACKUPS: backups(); return true;
            case M_PDF: create(REQ_PDF, (project.title.isEmpty() ? "מפה" : project.title) + ".pdf", "application/pdf"); return true;
            case M_PNG: create(REQ_PNG, (project.title.isEmpty() ? "מפה" : project.title) + ".png", "image/png"); return true;
            case M_TABS: manageTabs(); return true;
            case M_CLOSE: closeTab(cur); return true;
            case M_OPEN: {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_OPEN);
                return true;
            }
            case M_NEW: create(REQ_CREATE, "מפה חדשה.xml"); return true;
            case M_SAVE_AS: create(REQ_SAVE_AS, (cur == null ? "מפה" : cur.label()) + ".xml"); return true;
            case M_TITLE:
                if (project != null) askName("כותרת הקובץ", project.title, false, new Named() { public void done(String s) {
                    project.title = s; project.start.title = s; changedAny(true); }});
                return true;
            case M_SAVE: save(); return true;
            case M_DIR:
                setVertical(!vertical);
                render(null, true);
                invalidateOptionsMenu();
                return true;
            case M_FIT: map.fit(); return true;
            case M_LEVEL: showToLevel(); return true;
            case M_LOOSE:
                askName("פריט תלוש חדש", "", true, new Named() { public void done(String s) {
                    Item n = Edit.addLoose(project, s);
                    changed(n, null);
                }});
                return true;
            case M_SETTINGS: settings(); return true;
            case M_EXPORT: create(REQ_EXPORT, (project.title.isEmpty() ? "מפה" : project.title) + ".opml"); return true;
            case M_RELOAD: if (!guardDirty(mi)) load(uri, false); return true;
            case M_EX_TRI: example("triangle.xml", "משולש"); return true;
            case M_EX_TOYS: example("toys.xml", "צעצועים"); return true;
            case M_ABOUT:
                new AlertDialog.Builder(this).setTitle("קבוצות ותגיות")
                        .setMessage("גרסה " + versionName() + "\nמבלגן לסדר.")
                        .setPositiveButton("סגור", null).show();
                return true;
        }
        return super.onOptionsItemSelected(mi);
    }

    private void create(int req, String title) { create(req, title, "text/xml"); }

    private void create(int req, String title, String mime) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, title);
        startActivityForResult(i, req);
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(u,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Exception ignored) { }
        }
        if (req == REQ_OPEN) {
            for (Doc d : docs) if (u.equals(d.uri)) { d.spread = true; switchTo(d); return; }
            newTab();
            load(u, true);
        } else if (req == REQ_CREATE) {
            Project p = new Project();
            p.title = "מפה חדשה";
            p.roots.add(new Item("נושא"));
            p.first(Sheet.Kind.MAP).absolute = true;
            newTab();
            part = 0;
            sheetId = "";
            writeThenLoad(u, OpmlIO.write(p));
        } else if (req == REQ_SAVE_AS) {
            writeThenLoad(u, currentText());
        } else if (req == REQ_PNG || req == REQ_PDF) {
            exportMap(u, req == REQ_PDF);
        } else if (req == REQ_EXPORT) {
            final Uri out = u;
            final String text = OpmlIO.writeOpml(project);
            io.execute(new Runnable() { public void run() {
                try {
                    writeText(out, text);
                    runOnUiThread(new Runnable() { public void run() { setStatus("יוצא ל-OPML: " + displayName(out)); }});
                } catch (Exception e) { fail("הייצוא נכשל", e); }
            }});
        }
    }

    // ---------------- קריאה וכתיבה ----------------

    private static String sec(long ns) {
        return String.format(java.util.Locale.US, "%.1f", ns / 1e9);
    }

    private String readText(Uri u) throws Exception {
        InputStream in = getContentResolver().openInputStream(u);
        if (in == null) throw new Exception("השירות (למשל Dropbox) לא מצא את הקובץ בכתובת הזו. ייתכן שהוא הוחלף בשמירה. פתח אותו שוב מ\"פתח קובץ\".");
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), UTF8);
        } finally { in.close(); }
    }

    private void writeText(Uri u, String text) throws Exception {
        OutputStream out;
        try { out = getContentResolver().openOutputStream(u, "wt"); }
        catch (IllegalArgumentException | java.io.FileNotFoundException e) { out = getContentResolver().openOutputStream(u, "w"); }
        if (out == null) throw new Exception("לא ניתן לכתוב לקובץ");
        try { out.write(text.getBytes(UTF8)); out.flush(); }
        finally { out.close(); }
    }

    private String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        String s = u.getLastPathSegment();
        return s == null ? "קובץ" : s;
    }

    /** טעינה ברקע. remember: לזכור את הקובץ לפתיחה הבאה. */
    private void load(final Uri u, final boolean remember) {
        setStatus("טוען…");
        final Doc target = cur;
        busy++;
        io.execute(new Runnable() { public void run() {
            try {
                final long t0 = System.nanoTime();
                final String text = readText(u);
                final long t1 = System.nanoTime();
                final Project p = OpmlIO.read(text);
                p.syncAspects();
                // המצב ההתחלתי לביטול פעולה — בצורה שבה התוכנה עצמה כותבת (כדי שהשוואה תהיה מדויקת)
                final String norm = OpmlIO.write(p);
                final long t2 = System.nanoTime();
                try { map.prepare(p, vertical); } catch (Throwable ignored) { }
                final long t3 = System.nanoTime();
                final String name = displayName(u);
                runOnUiThread(new Runnable() { public void run() {
                    busy--;
                    if (target != cur) {
                        target.uri = u; target.fileName = name; target.lastText = text; target.lastRead = text;
                        target.project = p; target.dirty = false; target.viewMode = 0;
                        resetUndo(target, norm);
                        saveTabs();
                        return;
                    }
                    uri = u;
                    fileName = name;
                    lastText = text;
                    lastRead = text;
                    project = p;
                    dirty = false;
                    viewMode = 0;
                    resetUndo(cur, norm);
                    stash();
                    saveTabs();
                    long t4 = System.nanoTime();
                    render("נטען", true);
                    long t5 = System.nanoTime();
                    // זמני הטעינה בשניות — כדי לדעת מה מאט (קריאה מהשירות / פענוח / הכנת התיבות / הצגה)
                    setStatus(TextLines.countItems(project) + " פריטים" + " · נטען ב-" + sec(t5 - t0)
                            + " ש׳ (קריאה " + sec(t1 - t0) + " · פענוח " + sec(t2 - t1)
                            + " · תיבות " + sec(t3 - t2) + " · הצגה " + sec(t5 - t4) + ")");
                }});
            } catch (final SecurityException e) {
                done();
                dropIfEmpty(target);
                fail("אין הרשאה לקובץ. פתח אותו שוב מהתפריט.", e);
            } catch (final Exception e) {
                done();
                dropIfEmpty(target);
                fail("לא ניתן לטעון את הקובץ", e);
            }
        }});
    }

    /** טאב שנפתח בשביל קובץ שלא נטען (נבחר קובץ לא מתאים, או אין הרשאה): לא משאירים טאב ריק. */
    private void dropIfEmpty(final Doc target) {
        runOnUiThread(new Runnable() { public void run() {
            if (target == null || !docs.contains(target)) return;
            Uri tu = target == cur ? uri : target.uri;
            Project tp = target == cur ? project : target.project;
            if (tu == null && tp == null) closeTab(target);
        }});
    }

    /** כתיבה לקובץ חדש, ומעבר אליו מתוך הזיכרון (בלי לקרוא שוב: Dropbox מחליף כתובות). */
    private void writeThenLoad(final Uri u, final String text) {
        setStatus("שומר…");
        final Doc target = cur;
        busy++;
        io.execute(new Runnable() { public void run() {
            try {
                writeText(u, text);
                final Project p = OpmlIO.read(text);
                final String name = displayName(u);
                final boolean ok = readsBack(u, text);
                runOnUiThread(new Runnable() { public void run() {
                    busy--;
                    if (target != cur) {
                        target.uri = u; target.fileName = name; target.lastText = text; target.lastRead = text;
                        target.project = p; target.dirty = false; target.viewMode = 0;
                        if (target.snap == null) resetUndo(target, text);
                        saveTabs();
                        return;
                    }
                    uri = u;
                    fileName = name;
                    lastText = text;
                    lastRead = text;
                    project = p;
                    dirty = false;
                    viewMode = 0;
                    if (cur != null && cur.snap == null) resetUndo(cur, text);
                    stash();
                    saveTabs();
                    render(ok ? "נשמר" : STALE, true);
                }});
            } catch (Exception e) {
                done();
                dropIfEmpty(target);
                fail("השמירה נכשלה", e);
            }
        }});
    }

    private static final String STALE = "נשמר, אבל השירות החליף את הקובץ. לפני השמירה הבאה: פתח אותו שוב מ\"פתח קובץ\"";

    /** בדיקה אחרי כתיבה: הקובץ נקרא בחזרה מאותה כתובת וזהה. */
    private boolean readsBack(Uri u, String text) {
        try { return readText(u).equals(text); } catch (Exception e) { return false; }
    }

    private void save() { save(false); }

    /**
     * שמירה. לפני הכתיבה בודקים שהקובץ לא השתנה במקום אחר (למשל במחשב) מאז שנטען או נשמר כאן;
     * אם השתנה — לא דורסים, ושואלים. force: לשמור בכל זאת.
     */
    private void save(final boolean force) {
        if (uri == null || project == null) return;
        final Uri u = uri;
        final Doc target = cur;
        final Project proj = project;
        // מצב הקובץ נבנה פעם אחת — גם לביטול פעולה וגם לשמירה
        snapshot();
        final String text = cur != null && cur.snap != null ? cur.snap : currentText();
        final String known = lastText, seen = lastRead;
        final long savedRev = rev;
        setStatus("שומר…");
        busy++;
        io.execute(new Runnable() { public void run() {
            try {
                if (!force && known != null) {
                    String now = null;
                    try { now = readText(u); } catch (Exception ignored) { }
                    if (now != null && !now.equals(known) && !now.equals(seen) && !now.equals(text)) {
                        final String outside = now;
                        runOnUiThread(new Runnable() { public void run() {
                            if (busy > 0) busy--;
                            conflict(target, u, outside);
                        }});
                        return;
                    }
                }
                if (known != null) backup(u, known);
                writeText(u, text);
                String back = null;
                try { back = readText(u); } catch (Exception ignored) { }
                final boolean ok = text.equals(back);
                final String seenNow = back == null ? text : back;
                runOnUiThread(new Runnable() { public void run() {
                    busy--;
                    if (target != cur) {
                        if (target != null) {
                            target.lastText = text;
                            target.lastRead = seenNow;
                            if (OpmlIO.write(proj).equals(text)) target.dirty = false;
                        }
                        return;
                    }
                    lastText = text;
                    lastRead = seenNow;
                    if (rev == savedRev) dirty = false;
                    stash();
                    // אחרי שמירה לא בונים את המסך מחדש (0.10.7): רק השורה העליונה והנקודה בטאב.
                    // בנייה מחדש של כל המפה שתי שניות אחרי כל שינוי גרמה לתקיעות קצרות.
                    setStatus(partSummary() + (dirty ? " · שומר…" : " · " + (ok ? "נשמר" : STALE)));
                    rebuildTabsOnly();
                }});
            } catch (Exception e) {
                done();
                fail("השמירה נכשלה", e);
            }
        }});
    }

    /** הקובץ השתנה במקום אחר, ויש כאן שינויים: לא דורסים בלי לשאול. */
    private void conflict(final Doc target, final Uri u, final String outside) {
        if (target != cur) return; // הטאב ברקע נשאר "לא נשמר"; השאלה תעלה כשיחזרו אליו וישמרו
        setStatus("לא נשמר — הקובץ השתנה במקום אחר");
        if (asking) return;
        asking = true;
        new AlertDialog.Builder(this)
                .setTitle("הקובץ השתנה במקום אחר")
                .setMessage("מאז שהקובץ נטען כאן, הוא שונה במקום אחר (למשל במחשב). השמירה נעצרה כדי לא לדרוס את השינוי ההוא.")
                .setPositiveButton("שמור את מה שכאן", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { asking = false; if (u.equals(uri)) save(true); }})
                .setNeutralButton("טען את הגרסה האחרת", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        asking = false;
                        if (!u.equals(uri)) return;
                        handler.removeCallbacks(saver);
                        dirty = false;
                        load(u, false);
                    }})
                .setNegativeButton("לא עכשיו", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { asking = false; }})
                .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                    public void onCancel(android.content.DialogInterface d) { asking = false; }})
                .show();
    }

    private String currentText() {
        return OpmlIO.write(project);
    }

    /** שינוי בסיעור המוחות: לסמן, לשמור (אוטומטית), ולבנות מחדש רק כשהמבנה השתנה. */
    private void brainChanged(boolean structure) { changedAny(structure); }

    /** שינוי בטאבים 1-2 (או בכותרת): לסמן, לשמור (אוטומטית), ולבנות מחדש רק כשהמבנה השתנה. */
    private void changedAny(boolean structure) {
        dirty = true;
        rev++;
        touchUndo();
        stash();
        if (structure) render(null, false);
        else setStatus(partSummary() + (uri == null ? " · לא שמור" : autosave ? " · שומר…" : " · לא נשמר"));
        rebuildTabsOnly();
        if (autosave && uri != null) {
            handler.removeCallbacks(saver);
            handler.postDelayed(saver, 2000);
        }
    }

    private String partSummary() {
        if (part == 1) return project.free.nodes.size() + " פריטים · " + project.free.links.size() + " קווים";
        if (part == 0) {
            Brain b = project.start;
            int lines = 0;
            for (Brain.Section s : b.sections) lines += s.kind == Brain.Kind.NOTEBOOK ? s.lines.size() : 0;
            return b.sections.size() + (b.sections.size() == 1 ? " חלק" : " חלקים") + (lines > 0 ? " · " + lines + " שורות בפנקס" : "");
        }
        return TextLines.countItems(project) + " פריטים";
    }

    private boolean tabsDirtyShown;

    /** עדכון הנקודה • בטאב בלי לבנות את כל השורות (כדי לא להפריע להקלדה). */
    private void rebuildTabsOnly() {
        if (tabsDirtyShown == dirty) return;
        tabsDirtyShown = dirty;
        rebuildStrips();
    }

    private void done() { runOnUiThread(new Runnable() { public void run() { if (busy > 0) busy--; }}); }

    private void fail(final String what, final Exception e) {
        runOnUiThread(new Runnable() { public void run() {
            setStatus(what);
            new AlertDialog.Builder(Main.this).setTitle(what)
                    .setMessage(e.getMessage() == null ? e.toString() : e.getMessage())
                    .setPositiveButton("סגור", null).show();
        }});
    }

    /** בחזרה לאפליקציה: אם הקובץ השתנה מבחוץ, מציעים לטעון מחדש. */
    private void checkExternalChange() {
        if (uri == null || lastText == null || asking) return;
        final Uri u = uri;
        io.execute(new Runnable() { public void run() {
            try {
                final String now = readText(u);
                runOnUiThread(new Runnable() { public void run() {
                    // ההשוואה כאן, אחרי שכל שמירה שהייתה בתור כבר עדכנה מה שידוע (בלי הודעת שווא)
                    if (asking || !u.equals(uri) || busy > 0) return;
                    if (now.equals(lastText) || now.equals(lastRead)) return;
                    asking = true;
                    new AlertDialog.Builder(Main.this)
                            .setTitle("הקובץ השתנה מבחוץ")
                            .setMessage(dirty ? "לטעון את הגרסה החדשה? השינויים שנעשו כאן ועוד לא נשמרו יאבדו."
                                    : "לטעון את הגרסה החדשה? (מה שמוצג עכשיו יוחלף)")
                            .setPositiveButton("טען", new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface d, int w) {
                                    asking = false;
                                    handler.removeCallbacks(saver);
                                    dirty = false;
                                    load(u, false);
                                }})
                            .setNegativeButton("לא עכשיו", new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface d, int w) { asking = false; lastRead = now; }})
                            .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                                public void onCancel(android.content.DialogInterface d) { asking = false; }})
                            .show();
                }});
            } catch (Exception ignored) { }
        }});
    }

    private void example(String asset, String name) {
        try {
            InputStream in = getAssets().open("examples/" + asset);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            Project ex = OpmlIO.read(new String(bo.toByteArray(), UTF8));
            newTab();
            project = ex;
            part = 2;
            sheetId = "";
            uri = null;
            lastText = null;
            dirty = false;
            viewMode = 0;
            fileName = "דוגמה: " + name + " (לא שמורה)";
            resetUndo(cur, OpmlIO.write(ex));
            stash();
            render("כדי לשמור: \"שמור עותק בשם\"", true);
        } catch (Exception e) {
            fail("לא ניתן לפתוח את הדוגמה", e);
        }
    }

    // ---------------- תצוגת טקסט ----------------

    /** הפרויקט שמוצג: העץ עצמו, או עץ מדומה לפי פריט מידע. */
    private Project shown() {
        viewResult = null;
        switch (viewMode) {
            case 1: viewResult = Views.byTags(project, tagLevel, viewSort); break;
            case 2: viewResult = Views.byInfoName(project, viewInfo, viewSort); break;
            case 3: viewResult = Views.byProperties(project, viewSort); break;
            case 4: viewResult = Views.byAspects(project, viewSort); break;
            case 5: viewResult = Views.byLevels(project, levelFrom, levelTo); break;
        }
        return viewResult == null ? project : viewResult.project;
    }

    /** שם התצוגה הנוכחית, לשבב. */
    private String viewName() {
        switch (viewMode) {
            case 1: return "# תגיות" + (tagLevel > 1 ? " " + tagLevel : "");
            case 2: return viewInfo.isEmpty() ? "מידע" : viewInfo;
            case 3: return "תכונות";
            case 4: return "בחינות";
            case 5: return levelFrom == levelTo ? "רמה " + levelFrom : "רמות " + levelFrom + "–" + levelTo;
            default: return "עץ";
        }
    }

    /** הטאבים שבקובץ, ברשימה נגללת: מעבר לטאב, טאב חדש, המגירה, ופעולות על הטאב הנוכחי. */
    private void sheetsPopup(View anchor) {
        final java.util.List<Runnable> acts = new java.util.ArrayList<>();
        final Drop pm = new Drop();
        final Sheet now = sheet();
        int hidden = 0;
        for (final Sheet s : project.sheets) {
            if (s.hidden) { hidden++; continue; }
            addView(pm, acts, (s.locked ? "🔒 " : "") + s.name + (s.name.equals(s.kind.label) ? "" : "  ·  " + s.kind.label), s == now,
                    new Runnable() { public void run() { if (s != sheet()) enterSheet(s); }});
        }
        addView(pm, acts, "+ טאב חדש…", false, new Runnable() { public void run() { addSheet(); }});
        if (hidden > 0) addView(pm, acts, "מגירה (" + hidden + ")…", false, new Runnable() { public void run() { showHidden(); }});
        addView(pm, acts, "מאפיינים ופעולות: " + now.name + "…", false, new Runnable() { public void run() { sheetMenu(now); }});
        dropdown(anchor, pm.labels, acts);
    }

    /** רשימת השורות של תפריט נגלל. */
    private static final class Drop { final java.util.List<String> labels = new java.util.ArrayList<>(); }

    /**
     * תפריט קטן מתחת לשבב. כשיש יותר משש שורות הוא נגלל, ורואים שהוא נגלל:
     * פס גלילה קבוע בצד, והשורה האחרונה חתוכה באמצע.
     */
    private void dropdown(View anchor, final java.util.List<String> labels, final java.util.List<Runnable> acts) {
        final android.widget.ListPopupWindow lp = new android.widget.ListPopupWindow(this);
        final int rowH = dp(44);
        lp.setAnchorView(anchor);
        lp.setModal(true);
        lp.setAdapter(new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int pos, View cv, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(pos, cv, parent);
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                v.setTextDirection(View.TEXT_DIRECTION_RTL);
                v.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.RIGHT);
                v.setSingleLine(true);
                v.setEllipsize(android.text.TextUtils.TruncateAt.END);
                v.setMinHeight(rowH);
                v.setMinimumHeight(rowH);
                v.setHeight(rowH);
                v.setPadding(dp(14), 0, dp(14), 0);
                String s = labels.get(pos);
                v.setTextColor(s.startsWith("✓") ? 0xFF0C447C : 0xFF2C2C2A);
                v.setTypeface(s.startsWith("✓") ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                return v;
            }
        });
        android.graphics.Paint pt = new android.graphics.Paint();
        pt.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16, getResources().getDisplayMetrics()));
        pt.setTypeface(Typeface.DEFAULT_BOLD);
        float w = 0;
        for (String s : labels) w = Math.max(w, pt.measureText(s));
        int screen = getResources().getDisplayMetrics().widthPixels;
        lp.setWidth(Math.min(screen - dp(24), Math.max(dp(170), (int) w + dp(44))));
        final boolean scrolls = labels.size() > 6;
        if (scrolls) lp.setHeight(rowH * 6 + rowH / 2);
        lp.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> a, View v, int pos, long id) { lp.dismiss(); acts.get(pos).run(); }});
        lp.show();
        android.widget.ListView lv = lp.getListView();
        if (lv != null) {
            lv.setVerticalScrollBarEnabled(scrolls);
            lv.setScrollbarFadingEnabled(false);
            lv.setScrollBarSize(dp(5));
            for (int k = 0; k < labels.size(); k++) if (labels.get(k).startsWith("✓")) { lv.setSelection(Math.max(0, k - 2)); break; }
        }
    }

    /** תפריט קטן מתחת לשבב: כל התצוגות ברשימה נגללת; הנוכחית מסומנת. */
    private void viewsPopup(View anchor) {
        final java.util.List<Runnable> acts = new java.util.ArrayList<>();
        final Drop pm = new Drop();
        addView(pm, acts, "עץ", viewMode == 0, new Runnable() { public void run() { setView(0, 1, ""); }});
        addView(pm, acts, "# תגיות", viewMode == 1 && tagLevel == 1, new Runnable() { public void run() { setView(1, 1, ""); }});
        int maxLevel = Views.maxTagLevel(project);
        for (int k = 2; k <= maxLevel; k++) {
            final int lv = k;
            addView(pm, acts, "# תגיות " + k, viewMode == 1 && tagLevel == k, new Runnable() { public void run() { setView(1, lv, ""); }});
        }
        for (final String n : Views.usedInfoNames(project))
            addView(pm, acts, n.isEmpty() ? "מידע" : n, viewMode == 2 && viewInfo.equals(n), new Runnable() { public void run() { setView(2, 1, n); }});
        addView(pm, acts, "תכונות", viewMode == 3, new Runnable() { public void run() { setView(3, 1, ""); }});
        if (!project.aspects.isEmpty())
            addView(pm, acts, "בחינות", viewMode == 4, new Runnable() { public void run() { setView(4, 1, ""); }});
        if (Edit.maxDepth(project) >= 1)
            addView(pm, acts, viewMode == 5 ? viewName() + " (שנה…)" : "מרמה עד רמה…", viewMode == 5,
                    new Runnable() { public void run() { fromToLevel(Math.max(1, Edit.maxDepth(project))); }});
        dropdown(anchor, pm.labels, acts);
    }

    private static void addView(Drop pm, java.util.List<Runnable> acts, String label, boolean on, Runnable r) {
        pm.labels.add((on ? "✓ " : "    ") + label);
        acts.add(r);
    }

    private void setView(int mode, int level, String info) {
        rememberView();
        wantRestore = true;
        viewMode = mode;
        tagLevel = level;
        viewInfo = info;
        map.select(null, false);
        render(null, true);
    }

    private void render(String note, boolean refit) {
        if (project == null) return;
        syncSheet();
        Sheet sh = sheet();
        String sheetNote = sh == null || sh.note.isEmpty() ? "" : " · " + sh.note;
        if (part != 2) {
            setTitle(project.title.isEmpty() ? "קבוצות ותגיות" : project.title);
            setStatus(partSummary()
                    + (sh.locked ? " · נעול" : "")
                    + (dirty ? (uri == null ? " · לא שמור" : autosave ? " · שומר…" : " · לא נשמר") : "")
                    + (note == null ? "" : " · " + note) + sheetNote);
            stash();
            if (part == 0) {
                int y = brainScroll.getScrollY();
                brainView.setProps(sh.locked, sh.kinds, sh.erase);
                brainView.setBrain(project.start, new BrainView.Listener() {
                    public void changed() { changedAny(false); }
                    public void structure() { changedAny(true); }
                });
                brainScroll.scrollTo(0, y);
            } else {
                freeView.locked = sh.locked;
                freeView.links = sh.links;
                freeView.setMap(project.free, this);
            }
            tabsDirtyShown = dirty;
            rebuildStrips();
            applyView();
            return;
        }
        project.syncAspects();
        Project p = shown();
        setTitle(project.title.isEmpty() ? "קבוצות ותגיות" : project.title);
        int items = TextLines.countItems(project);
        setStatus(items + " פריטים"
                + (project.absolute() ? " · סדר מוחלט" + (Sheets.misplaced(project).isEmpty() ? "" : " · " + Sheets.misplaced(project).size() + " לא במקומם") : "")
                + (sh.locked ? " · נעול" : "")
                + (dirty ? (uri == null ? " · לא שמור" : autosave ? " · שומר…" : " · לא נשמר") : "")
                + (note == null ? "" : " · " + note) + sheetNote);
        stash();
        rebuildStrips();
        map.setReadOnly(viewResult != null || sh.locked);
        shownP = p;
        textStale = true;
        mapStale = true;
        refitPending |= refit;
        applyView();
    }

    /** בניית תצוגת הטקסט — רק כשהיא על המסך. */
    private void buildText(Project p) {
        int indent = dp(22);
        int grey = Color.rgb(136, 135, 128);
        int blue = Color.rgb(24, 95, 165);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (TextLines.Line l : TextLines.build(p)) {
            int start = sb.length();
            if (l.kind == TextLines.Kind.HEADER) {
                sb.append("\n").append(l.text);
                span(sb, new StyleSpan(Typeface.BOLD), start, sb.length());
                span(sb, new ForegroundColorSpan(grey), start, sb.length());
            } else if (l.kind == TextLines.Kind.DIVISION) {
                if (!l.mark.isEmpty()) sb.append(l.mark).append(" · ");
                sb.append(l.text);
                span(sb, new StyleSpan(Typeface.ITALIC), start, sb.length());
                span(sb, new ForegroundColorSpan(blue), start, sb.length());
                span(sb, new RelativeSizeSpan(0.85f), start, sb.length());
            } else {
                int m = sb.length();
                sb.append(l.mark);
                span(sb, new RelativeSizeSpan(0.65f), m, sb.length());
                span(sb, new ForegroundColorSpan(grey), m, sb.length());
                sb.append("  ");
                int n = sb.length();
                sb.append(l.text.isEmpty() ? "(בלי שם)" : l.text);
                if (l.item != null) {
                    // נגיעה בשם פותחת את חלון הפריט (0.11)
                    final Item shownItem = l.item;
                    span(sb, new android.text.style.ClickableSpan() {
                        @Override public void onClick(View w) { openItem(shownItem); }
                        @Override public void updateDrawState(android.text.TextPaint ds) { }
                    }, n, sb.length());
                }
                if (l.depth == 0) span(sb, new StyleSpan(Typeface.BOLD), n, sb.length());
                if (l.hidden > 0) {
                    int h = sb.length();
                    sb.append("  ▸ ").append(String.valueOf(l.hidden));
                    span(sb, new ForegroundColorSpan(blue), h, sb.length());
                }
                if (!l.info.isEmpty()) {
                    int f = sb.length();
                    sb.append("  (").append(l.info).append(")");
                    span(sb, new RelativeSizeSpan(0.72f), f, sb.length());
                    span(sb, new ForegroundColorSpan(grey), f, sb.length());
                }
            }
            sb.append("\n");
            sb.setSpan(new LeadingMarginSpan.Standard(l.depth * indent), start, sb.length(),
                    Spanned.SPAN_PARAGRAPH);
        }
        body.setText(sb);
    }

    /** "הצג עד רמה": מכווץ כל מה שמתחת לרמה שנבחרה (אפשר לפתוח שוב ענף ענף, או "הכול"). */
    private void showToLevel() {
        final int max = Edit.maxDepth(project);
        if (max < 1) { setStatus("אין עדיין רמות מתחת לשורש"); return; }
        new AlertDialog.Builder(this).setTitle("רמות")
                .setItems(new String[]{"הצג עד רמה… (מכווץ בעץ עצמו)", "מרמה עד רמה… (תצוגה, בלי מה שמעליהן)"},
                        new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (w == 0) upToLevel(); else fromToLevel(max);
                    }}).show();
    }

    /** גלגל מספרים (במקום רשימה של כל הרמות). */
    private android.widget.NumberPicker wheel(int min, int max, int value, String[] shown) {
        android.widget.NumberPicker np = new android.widget.NumberPicker(this);
        np.setMinValue(min);
        np.setMaxValue(max);
        if (shown != null) np.setDisplayedValues(shown);
        np.setValue(Math.max(min, Math.min(max, value)));
        np.setWrapSelectorWheel(false);
        np.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        return np;
    }

    private TextView wheelLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setGravity(android.view.Gravity.CENTER);
        return t;
    }

    /** מרמה עד רמה: שני גלגלי מספרים — ההתחלה והסוף. */
    private void fromToLevel(final int max) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setGravity(android.view.Gravity.CENTER);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        int f0 = viewMode == 5 ? Math.min(levelFrom, max) : 1, t0 = viewMode == 5 ? Math.min(Math.max(levelTo, f0), max) : max;
        final android.widget.NumberPicker from = wheel(1, max, f0, null), to = wheel(f0, max, t0, null);
        from.setOnValueChangedListener(new android.widget.NumberPicker.OnValueChangeListener() {
            public void onValueChange(android.widget.NumberPicker p, int o, int n) {
                int v = Math.max(to.getValue(), n);
                to.setMinValue(n);
                to.setValue(v);
            }});
        LinearLayout a = new LinearLayout(this), b = new LinearLayout(this);
        a.setOrientation(LinearLayout.VERTICAL);
        b.setOrientation(LinearLayout.VERTICAL);
        a.addView(wheelLabel("מרמה"));
        a.addView(from);
        b.addView(wheelLabel("עד רמה"));
        b.addView(to);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(18), 0, dp(18), 0);
        box.addView(a, lp);
        box.addView(b, lp);
        new AlertDialog.Builder(this).setTitle("מרמה עד רמה (השורש — 0)").setView(box)
                .setPositiveButton("הצג", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        levelFrom = from.getValue();
                        levelTo = Math.max(levelFrom, to.getValue());
                        setView(5, 1, "");
                    }})
                .setNegativeButton("ביטול", null).show();
    }

    private void upToLevel() {
        if (locked()) { setStatus("הטאב נעול — אפשר לשנות במאפייני הטאב"); return; }
        final int max = Edit.maxDepth(project);
        final String[] names = new String[max + 1];
        for (int k = 0; k < max; k++) names[k] = k == 0 ? "רק השורש" : String.valueOf(k);
        names[max] = "הכול";
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        final android.widget.NumberPicker np = wheel(0, max, max, names);
        box.addView(wheelLabel("עד רמה"));
        box.addView(np, new LinearLayout.LayoutParams(-2, -2));
        new AlertDialog.Builder(this).setTitle("הצג עד רמה").setView(box)
                .setPositiveButton("הצג", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int x) {
                        int w = np.getValue();
                        int n = Edit.showToLevel(project, w == max ? Integer.MAX_VALUE : w);
                        refitPending = true;
                        viewMode = 0;
                        map.select(null, false);
                        if (n > 0) changedAny(true); else render(null, true);
                    }})
                .setNegativeButton("ביטול", null).show();
    }

    private static void span(SpannableStringBuilder sb, Object what, int s, int e) {
        if (e > s) sb.setSpan(what, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // ---------------- שורה עליונה: שם הקובץ ומצב ----------------

    /** שם הקובץ למעלה ובקטן (תת־כותרת), ואחריו המצב. */
    private void setStatus(String s) {
        String f = fileName == null ? "" : fileName;
        String line = f.isEmpty() ? s : (s == null || s.isEmpty() ? f : f + " · " + s);
        if (getActionBar() != null) getActionBar().setSubtitle(line);
    }

    // ---------------- טאבים ----------------

    /** שומר את מצב הטאב הנוכחי מהשדות. */
    private void stash() {
        if (cur == null) return;
        cur.uri = uri; cur.fileName = fileName; cur.lastText = lastText; cur.lastRead = lastRead; cur.project = project; cur.part = part;
        cur.dirty = dirty; cur.viewMode = viewMode; cur.tagLevel = tagLevel; cur.viewInfo = viewInfo;
        cur.viewSort = viewSort; cur.levelFrom = levelFrom; cur.levelTo = levelTo; cur.sheetId = sheetId;
    }

    private void unstash(Doc d) {
        uri = d.uri; fileName = d.fileName; lastText = d.lastText; lastRead = d.lastRead; project = d.project; part = d.part;
        dirty = d.dirty; viewMode = d.viewMode; tagLevel = d.tagLevel; viewInfo = d.viewInfo;
        viewSort = d.viewSort; levelFrom = d.levelFrom; levelTo = d.levelTo; sheetId = d.sheetId;
    }

    /** טאב חדש וריק, והופך לנוכחי (מה שהיה — נשמר בטאב שלו). */
    private void newTab() {
        leaveCurrent();
        Doc d = new Doc();
        docs.add(d);
        cur = d;
        unstash(d);
        map.select(null, false);
        // המסך מתרוקן עד שהקובץ החדש נטען (תיקון 0.10.6: המפה הקודמת נשארה, ונגיעה בה הקריסה)
        shownP = null;
        viewResult = null;
        body.setText("");
        applyView();
        rebuildStrips();
    }

    /** לפני מעבר: שמירת המצב, ושמירה לקובץ אם יש שינויים ושמירה אוטומטית. */
    private void leaveCurrent() {
        if (cur == null) return;
        snapshot();
        rememberView();
        stash();
        handler.removeCallbacks(saver);
        if (dirty && autosave && uri != null) save();
    }

    private void switchTo(Doc d) {
        if (d == cur) { rebuildStrips(); return; }
        leaveCurrent();
        cur = d;
        unstash(d);
        map.select(null, false);
        wantRestore = true;
        saveTabs();
        if (project == null && uri != null) { load(uri, false); return; }
        if (project == null) { showWelcome(); return; }
        render(null, true);
        checkExternalChange();
    }

    private void closeTab(final Doc d) {
        if (d == null) return;
        boolean unsaved = d == cur ? dirty : d.dirty;
        Uri du = d == cur ? uri : d.uri;
        if (unsaved && (du == null || !autosave)) {
            new AlertDialog.Builder(this).setTitle("יש שינויים שלא נשמרו")
                    .setMessage(du == null ? "הדוגמה לא נשמרת. לסגור ולאבד את השינויים?" : "לסגור בלי לשמור?")
                    .setPositiveButton("סגור בלי לשמור", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface di, int w) {
                            if (d == cur) dirty = false; else d.dirty = false;
                            closeTab(d);
                        }})
                    .setNegativeButton("ביטול", null).show();
            return;
        }
        if (d == cur) leaveCurrent();
        int i = docs.indexOf(d);
        docs.remove(d);
        if (d == cur) {
            cur = null;
            if (docs.isEmpty()) {
                project = null; uri = null; lastText = null; dirty = false;
                saveTabs();
                applyView();
                body.setText("");
                showWelcome();
                return;
            }
            Doc next = docs.get(Math.max(0, Math.min(i, docs.size() - 1)));
            next.spread = true;
            cur = next;
            unstash(next);
            if (project == null && uri != null) { saveTabs(); load(uri, false); return; }
            render(null, true);
        }
        saveTabs();
        rebuildStrips();
    }

    /** ניהול מרוכז: מי פרוס בשורה ומי ברקע; מעבר; סגירה. */
    private void manageTabs() {
        stash();
        final String[] names = new String[docs.size()];
        final boolean[] on = new boolean[docs.size()];
        for (int i = 0; i < docs.size(); i++) {
            Doc d = docs.get(i);
            names[i] = tabLabel(d) + (d == cur ? "  (פתוח עכשיו)" : "") + (d.dirty ? "  · לא נשמר" : "");
            on[i] = d.spread || d == cur;
        }
        new AlertDialog.Builder(this).setTitle("ניהול טאבים — מסומן = פרוס בשורה")
                .setMultiChoiceItems(names, on, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(android.content.DialogInterface di, int w, boolean c) { on[w] = c; }})
                .setPositiveButton("אישור", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface di, int w) {
                        for (int i = 0; i < docs.size() && i < on.length; i++) docs.get(i).spread = on[i];
                        saveTabs();
                        rebuildStrips();
                    }})
                .setNeutralButton("פתח / סגור…", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface di, int w) { tabActions(); }})
                .setNegativeButton("ביטול", null).show();
    }

    private void tabActions() {
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<Runnable> acts = new java.util.ArrayList<>();
        for (final Doc d : new java.util.ArrayList<>(docs)) {
            labels.add("עבור אל: " + tabLabel(d));
            acts.add(new Runnable() { public void run() { d.spread = true; switchTo(d); }});
        }
        for (final Doc d : new java.util.ArrayList<>(docs)) {
            labels.add("סגור: " + tabLabel(d));
            acts.add(new Runnable() { public void run() { closeTab(d); }});
        }
        menu("טאבים", labels, acts);
    }

    /** זיכרון הטאבים בין הפעלות: רק קבצים (לא דוגמאות). */
    private void saveTabs() {
        StringBuilder b = new StringBuilder();
        int curIndex = -1, k = 0;
        for (Doc d : docs) {
            Uri u = d == cur ? uri : d.uri;
            if (u == null) continue;
            if (d == cur) curIndex = k;
            b.append(d.spread ? "1" : "0").append('|').append(u.toString()).append('\n');
            k++;
        }
        prefs.edit().putString("tabs", b.toString()).putInt("tab", curIndex)
                .putString("uri", cur != null && uri != null ? uri.toString() : prefs.getString("uri", null)).apply();
    }

    private void restoreTabs() {
        String s = prefs.getString("tabs", null);
        if (s == null) {
            String one = prefs.getString("uri", null);
            if (one != null) s = "1|" + one + "\n";
        }
        if (s != null) for (String line : s.split("\n")) {
            int bar = line.indexOf('|');
            if (bar < 0) continue;
            Doc d = new Doc();
            d.spread = line.startsWith("1");
            d.uri = Uri.parse(line.substring(bar + 1));
            docs.add(d);
        }
        // שמות הקבצים — ברקע (שאלה לשירות, למשל Dropbox, עלולה להתעכב)
        final java.util.List<Doc> named = new java.util.ArrayList<>(docs);
        new Thread(new Runnable() { public void run() {
            for (final Doc d : named) {
                if (d.uri == null) continue;
                final String nm = displayName(d.uri);
                runOnUiThread(new Runnable() { public void run() {
                    if (d.fileName == null || d.fileName.isEmpty()) d.fileName = nm;
                    if (d == cur && (fileName == null || fileName.isEmpty())) fileName = nm;
                    rebuildStrips();
                }});
            }
        }}).start();
        if (docs.isEmpty()) { showWelcome(); return; }
        int i = Math.max(0, Math.min(prefs.getInt("tab", 0), docs.size() - 1));
        cur = docs.get(i);
        cur.spread = true;
        unstash(cur);
        rebuildStrips();
        load(uri, false);
    }

    // ---------------- שורות הכפתורים: טאבים ותצוגות ----------------

    private LinearLayout strip(int bg) {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setBackgroundColor(bg);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(6), dp(5), dp(6), dp(5));
        hs.addView(row);
        hs.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private TextView chip(LinearLayout row, String label, boolean on, boolean dashed, View.OnClickListener click) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(on ? 0xFF0C447C : 0xFF444441);
        if (on) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(12), dp(5), dp(12), dp(5));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(on ? 0xFFE6F1FB : 0xFFFFFFFF);
        if (dashed) g.setStroke(dp(1), on ? 0xFF378ADD : 0xFFB4B2A9, dp(4), dp(3));
        else g.setStroke(dp(1), on ? 0xFF378ADD : 0xFFD3D1C7);
        g.setCornerRadius(dp(16));
        t.setBackground(g);
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(dp(5));
        row.addView(t, lp);
        return t;
    }

    private void gap(LinearLayout row) {
        View v = new View(this);
        v.setBackgroundColor(0xFFD3D1C7);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(22));
        lp.setMargins(dp(6), dp(4), dp(6), 0);
        row.addView(v, lp);
    }

    private void rebuildStrips() {
        // טאבים
        tabRow.removeAllViews();
        for (final Doc d : docs) {
            if (!d.spread && d != cur) continue;
            boolean unsaved = d == cur ? dirty : d.dirty;
            TextView t = chip(tabRow, tabLabel(d) + (unsaved ? " •" : ""), d == cur, !d.spread, new View.OnClickListener() {
                public void onClick(View v) { switchTo(d); }});
            t.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) {
                final java.util.List<String> labels = new java.util.ArrayList<>();
                final java.util.List<Runnable> acts = new java.util.ArrayList<>();
                labels.add("העבר לרקע"); acts.add(new Runnable() { public void run() {
                    d.spread = false;
                    if (d == cur) { for (Doc o : docs) if (o != d && o.spread) { switchTo(o); break; } }
                    saveTabs(); rebuildStrips(); }});
                labels.add("סגור"); acts.add(new Runnable() { public void run() { closeTab(d); }});
                menu(d.label(), labels, acts);
                return true;
            }});
        }
        int hidden = 0;
        for (Doc d : docs) if (!d.spread && d != cur) hidden++;
        chip(tabRow, "+ פתח", false, false, new View.OnClickListener() { public void onClick(View v) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_OPEN);
        }});
        if (docs.size() > 1 || hidden > 0)
            chip(tabRow, "☰" + (hidden > 0 ? " " + hidden + " ברקע" : ""), false, false, new View.OnClickListener() {
                public void onClick(View v) { manageTabs(); }});
        // תצוגות
        viewRow.removeAllViews();
        ((View) viewRow.getParent()).setVisibility(project == null ? View.GONE : View.VISIBLE);
        if (project == null) return;
        final Sheet curSh = sheet();
        // (0.10.6) שבב אחד לטאב הנוכחי; נגיעה — רשימה נגללת של כל הטאבים והפעולות עליהם
        final TextView sc = chip(viewRow, (curSh.locked ? "🔒 " : "") + curSh.name + " ▾", true, false, null);
        sc.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { sheetsPopup(sc); }});
        sc.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { sheetMenu(sheet()); return true; }});
        if (part != 2) return;
        chip(viewRow, showMap ? "גרפי ⇄" : "טקסט ⇄", false, false, new View.OnClickListener() { public void onClick(View v) {
            setShowMap(!showMap); applyView(); rebuildStrips(); }});
        // תצוגות (0.10.5): שבב אחד עם התצוגה הנוכחית; נגיעה — תפריט קטן נגלל עם כל התצוגות
        final TextView vc = chip(viewRow, "תצוגה: " + viewName() + " ▾", viewMode != 0, false, null);
        vc.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { viewsPopup(vc); }});
        if (viewMode != 0 && viewMode != 5) {
            gap(viewRow);
            chip(viewRow, viewSort == Views.Sort.NAME ? "מיון: א-ב" : "מיון: מפה", false, false, new View.OnClickListener() {
                public void onClick(View v) {
                    viewSort = viewSort == Views.Sort.MAP ? Views.Sort.NAME : Views.Sort.MAP;
                    render(null, true);
                }});
        }
    }

    // ---------------- טאב 2: המפה הגמישה ----------------

    @Override
    public void onFreeAdd(final float x, final float y) {
        if (project == null || locked()) return;
        askName("פריט חדש", "", true, new Named() {
            float off = 0;
            public void done(String s) {
                FreeMap.Node n = project.free.add(s, x, y + off);
                off += 56;
                freeView.select(n);
                changedAny(false);
                freeView.invalidate();
            }});
    }

    @Override
    public void onFreeEdit(final FreeMap.Node n) {
        askName("הפריט", n.text, false, new Named() { public void done(String s) {
            n.text = s;
            changedAny(false);
            freeView.invalidate();
        }});
    }

    @Override
    public void onFreeMenu(final FreeMap.Node n) {
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<Runnable> acts = new java.util.ArrayList<>();
        labels.add("העבר למפה"); acts.add(new Runnable() { public void run() { toMap(n, true); }});
        labels.add("העתק למפה (ונשאר כאן)"); acts.add(new Runnable() { public void run() { toMap(n, false); }});
        for (final FreeMap.Node o : project.free.neighbors(n)) {
            labels.add("נתק מ: " + o.text);
            acts.add(new Runnable() { public void run() { project.free.unlink(n, o); changedAny(false); freeView.invalidate(); }});
        }
        labels.add("מחק"); acts.add(new Runnable() { public void run() {
            confirm("למחוק את \"" + n.text + "\"" + (project.free.neighbors(n).isEmpty() ? "?" : " ואת הקווים שלו?"),
                    new Runnable() { public void run() {
                        project.free.remove(n);
                        freeView.select(null);
                        changedAny(false);
                        freeView.invalidate();
                    }});
        }});
        menu(n.text.isEmpty() ? "פריט" : n.text, labels, acts);
    }

    @Override
    public void onFreeChanged() { changedAny(false); freeView.invalidate(); }

    @Override
    public void onFreeStatus(String s) { setStatus(s); }

    // ---------------- טאבים בתוך הקובץ (0.10) ----------------

    /** הטאב הנוכחי; אם אינו (נמחק, הוסתר) — הראשון הגלוי מאותו סוג, או הראשון הגלוי. */
    private Sheet sheet() {
        if (project == null) return null;
        Sheet s = project.sheet(sheetId);
        if (s == null || s.hidden) {
            Sheet.Kind want = part == 0 ? Sheet.Kind.START : part == 1 ? Sheet.Kind.FREE : Sheet.Kind.MAP;
            s = null;
            for (Sheet x : project.sheets) if (!x.hidden && x.kind == want) { s = x; break; }
            if (s == null) for (Sheet x : project.sheets) if (!x.hidden) { s = x; break; }
            if (s == null) { s = project.sheets.get(0); s.hidden = false; }
            sheetId = s.id;
        }
        return s;
    }

    /** הטאב הנוכחי נהיה הפעיל בקובץ, וכיוון ותצוגה לפי המאפיינים שלו (או לפי התוכנה). */
    private void syncSheet() {
        Sheet s = sheet();
        if (s == null) return;
        project.use(s);
        part = s.kind == Sheet.Kind.START ? 0 : s.kind == Sheet.Kind.FREE ? 1 : 2;
        showMap = s.view.equals("map") || (!s.view.equals("text") && prefs.getBoolean("showMap", true));
        vertical = s.dir.equals("v") || (!s.dir.equals("h") && prefs.getBoolean("vertical", true));
        if (s.kind == Sheet.Kind.MAP && (map.compact != !s.layout.equals("tidy") || map.spaceB != s.spaceB || map.spaceD != s.spaceD)) {
            map.compact = !s.layout.equals("tidy");
            map.spaceB = s.spaceB;
            map.spaceD = s.spaceD;
            mapStale = true;
        }
    }

    private boolean locked() {
        Sheet s = sheet();
        return s != null && s.locked;
    }

    private void enterSheet(Sheet s) {
        rememberView();
        wantRestore = true;
        if (s.hidden) s.hidden = false;
        sheetId = s.id;
        viewMode = 0;
        map.select(null, false);
        syncSheet();
        stash();
        render(null, true);
    }

    /** גרפי/טקסט: לטאב שקבע לעצמו — בטאב (נשמר בקובץ); אחרת — לכל התוכנה. */
    private void setShowMap(boolean b) {
        Sheet s = sheet();
        if (s != null && s.kind == Sheet.Kind.MAP && !s.view.isEmpty()) { s.view = b ? "map" : "text"; markDirty(); }
        else prefs.edit().putBoolean("showMap", b).apply();
        showMap = b;
    }

    private void setVertical(boolean b) {
        Sheet s = sheet();
        if (s != null && s.kind == Sheet.Kind.MAP && !s.dir.isEmpty()) { s.dir = b ? "v" : "h"; markDirty(); }
        else prefs.edit().putBoolean("vertical", b).apply();
        vertical = b;
    }

    /** שינוי שאינו צריך בנייה מחדש: לסמן ולשמור. */
    private void markDirty() {
        dirty = true;
        rev++;
        touchUndo();
        stash();
        rebuildTabsOnly();
        if (autosave && uri != null) {
            handler.removeCallbacks(saver);
            handler.postDelayed(saver, 2000);
        }
    }

    private void sheetMenu(final Sheet s) {
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<Runnable> acts = new java.util.ArrayList<>();
        labels.add("מאפיינים…"); acts.add(new Runnable() { public void run() { sheetProps(s); }});
        labels.add("שנה שם"); acts.add(new Runnable() { public void run() {
            askName("שם הטאב", s.name, false, new Named() { public void done(String x) { Sheets.rename(s, x); changedAny(true); }}); }});
        labels.add("העתק (טאב נוסף עם אותו תוכן)"); acts.add(new Runnable() { public void run() {
            Sheet c = Sheets.copy(project, s);
            sheetId = c.id;
            changedAny(true);
            setStatus("נוצר עותק: " + c.name);
        }});
        final int i = project.sheets.indexOf(s);
        if (i > 0) { labels.add("הזז ימינה"); acts.add(new Runnable() { public void run() { Sheets.move(project, s, -1); changedAny(true); }}); }
        if (i < project.sheets.size() - 1) { labels.add("הזז שמאלה"); acts.add(new Runnable() { public void run() { Sheets.move(project, s, 1); changedAny(true); }}); }
        if (Sheets.visible(project) > 1) { labels.add("העבר למגירה (נשמר בקובץ, לא מוצג)"); acts.add(new Runnable() { public void run() {
            s.hidden = true;
            changedAny(true);
        }}); }
        int same = 0;
        for (Sheet x : project.sheets) if (x.kind == s.kind) same++;
        if (same > 1) { labels.add("מחק את הטאב"); acts.add(new Runnable() { public void run() {
            confirm("למחוק את הטאב \"" + s.name + "\" עם כל התוכן שבו?", new Runnable() { public void run() {
                if (Sheets.delete(project, s)) changedAny(true);
            }});
        }}); }
        menu(s.name + " · " + s.kind.label + (s.note.isEmpty() ? "" : " · " + s.note), labels, acts);
    }

    /** מאפייני הטאב: כל שורה מראה את המצב, ונגיעה משנה. אחרי שינוי — החלון נפתח שוב. */
    private void sheetProps(final Sheet s) {
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<Runnable> acts = new java.util.ArrayList<>();
        final Runnable again = new Runnable() { public void run() { changedAny(true); sheetProps(s); }};
        labels.add("שם: " + s.name); acts.add(new Runnable() { public void run() {
            askName("שם הטאב", s.name, false, new Named() { public void done(String x) { Sheets.rename(s, x); again.run(); }}); }});
        labels.add("הערה: " + (s.note.isEmpty() ? "(אין)" : s.note)); acts.add(new Runnable() { public void run() {
            askName("הערה לטאב — למה הוא משמש", s.note, false, new Named() { public void done(String x) { s.note = x.trim(); again.run(); }}); }});
        labels.add(s.locked ? "עריכה: נעול — רק קריאה" : "עריכה: מותרת"); acts.add(new Runnable() { public void run() {
            s.locked = !s.locked; again.run(); }});
        switch (s.kind) {
            case START: {
                StringBuilder ks = new StringBuilder();
                for (Brain.Kind k : Brain.Kind.values()) if (s.kinds.contains(k)) ks.append(ks.length() > 0 ? ", " : "").append(k.label);
                labels.add("סוגי חלקים מותרים: " + ks); acts.add(new Runnable() { public void run() { chooseKinds(s, again); }});
                labels.add("מחיקה בפנקס: " + s.erase.label); acts.add(new Runnable() { public void run() {
                    final Sheet.Erase[] all = Sheet.Erase.values();
                    String[] names = new String[all.length];
                    for (int k = 0; k < all.length; k++) names[k] = all[k].label;
                    new AlertDialog.Builder(Main.this).setTitle("מחיקה בפנקס")
                            .setSingleChoiceItems(names, s.erase.ordinal(), new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface d, int w) { s.erase = all[w]; d.dismiss(); again.run(); }})
                            .show();
                }});
                break;
            }
            case FREE:
                labels.add(s.links ? "קווים: מותר לחבר" : "קווים: לא מחברים"); acts.add(new Runnable() { public void run() {
                    s.links = !s.links; again.run(); }});
                break;
            case MAP:
                labels.add(s.absolute ? "סדר מוחלט: כן — לכל פריט מקום בעץ" : "סדר מוחלט: לא — מותרים פריטים תלושים");
                acts.add(new Runnable() { public void run() { s.absolute = !s.absolute; again.run(); }});
                labels.add("כיוון: " + (s.dir.equals("v") ? "מלמעלה למטה" : s.dir.equals("h") ? "מימין לשמאל" : "לפי התוכנה"));
                acts.add(new Runnable() { public void run() {
                    choose("כיוון העץ בטאב הזה", new String[]{"לפי התוכנה", "מלמעלה למטה", "מימין לשמאל"},
                            s.dir.equals("v") ? 1 : s.dir.equals("h") ? 2 : 0, new Chosen() { public void at(int w) {
                                s.dir = w == 1 ? "v" : w == 2 ? "h" : ""; refitPending = true; again.run(); }});
                }});
                labels.add(s.layout.equals("tidy") ? "פריסה: מרווחת — כל ענף מעל התחום שלו" : "פריסה: צפופה — כמו מפת חשיבה, קווים בעיקול");
                acts.add(new Runnable() { public void run() {
                    s.layout = s.layout.equals("tidy") ? "" : "tidy"; refitPending = true; again.run(); }});
                labels.add("מרווח בין אחים: " + spaceName(s.spaceB));
                acts.add(new Runnable() { public void run() {
                    chooseSpace("מרווח בין אחים", s.spaceB, new Spaced() { public void at(float f) { s.spaceB = f; again.run(); }}); }});
                labels.add("מרווח בין רמות: " + spaceName(s.spaceD));
                acts.add(new Runnable() { public void run() {
                    chooseSpace("מרווח בין רמות", s.spaceD, new Spaced() { public void at(float f) { s.spaceD = f; again.run(); }}); }});
                int moved = 0;
                for (Item r : s.roots) moved += Edit.countOffsets(r);
                if (moved > 0) {
                    labels.add("הזזות ביד: " + moved + " פריטים — נגיעה מחזירה הכול למקום האוטומטי");
                    acts.add(new Runnable() { public void run() {
                        int k = 0;
                        for (Item r : s.roots) k += Edit.countOffsets(r);
                        confirmDo("להחזיר " + k + " פריטים שהוזזו ביד למקום האוטומטי?", "החזר", new Runnable() { public void run() {
                            for (Item r : s.roots) Edit.clearOffsets(r);
                            again.run();
                        }});
                    }});
                }
                labels.add("תצוגה: " + (s.view.equals("map") ? "גרפית" : s.view.equals("text") ? "טקסט" : "לפי התוכנה"));
                acts.add(new Runnable() { public void run() {
                    choose("תצוגה בטאב הזה", new String[]{"לפי התוכנה", "גרפית", "טקסט"},
                            s.view.equals("map") ? 1 : s.view.equals("text") ? 2 : 0, new Chosen() { public void at(int w) {
                                s.view = w == 1 ? "map" : w == 2 ? "text" : ""; again.run(); }});
                }});
                break;
        }
        menu("מאפייני הטאב: " + s.name, labels, acts);
    }

    private interface Chosen { void at(int w); }

    private interface Spaced { void at(float f); }

    private static final float[] SPACES = {0.5f, 1f, 1.5f, 2f, 3f};
    private static final String[] SPACE_NAMES = {"צפוף", "רגיל", "רחב", "רחב מאוד", "רחב ביותר"};

    private static String spaceName(float f) {
        for (int k = 0; k < SPACES.length; k++) if (Math.abs(SPACES[k] - f) < 0.01f) return SPACE_NAMES[k] + " (" + com.aviccc.kvutzot.model.Zoom.format(f) + ")";
        return com.aviccc.kvutzot.model.Zoom.format(f);
    }

    private void chooseSpace(final String title, float cur, final Spaced then) {
        String[] names = new String[SPACES.length + 1];
        int sel = -1;
        for (int k = 0; k < SPACES.length; k++) {
            names[k] = SPACE_NAMES[k] + " (" + com.aviccc.kvutzot.model.Zoom.format(SPACES[k]) + ")";
            if (Math.abs(SPACES[k] - cur) < 0.01f) sel = k;
        }
        names[SPACES.length] = "מספר אחר…";
        choose(title, names, sel, new Chosen() { public void at(int w) {
            if (w < SPACES.length) { then.at(SPACES[w]); return; }
            askName(title + " — מספר (1 = רגיל, בין 0.2 ל-5)", com.aviccc.kvutzot.model.Zoom.format(1f), false, new Named() { public void done(String x) {
                try {
                    float f = Float.parseFloat(x.trim().replace(',', '.'));
                    if (f > 0) then.at(Math.max(0.2f, Math.min(5f, f)));
                } catch (NumberFormatException ignored) { }
            }});
        }});
    }

    private void confirmDo(String q, String yesLabel, final Runnable yes) {
        new AlertDialog.Builder(this).setTitle(q)
                .setPositiveButton(yesLabel, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { yes.run(); }})
                .setNegativeButton("ביטול", null).show();
    }

    @Override
    public void onDragged(Item it) {
        if (locked() || viewResult != null) return;
        changed(null, null);
        setStatus("הוזז: " + it.name + " · להחזיר — ⋯ ← \"החזר למקום האוטומטי\"");
    }

    private void choose(String title, String[] names, int sel, final Chosen then) {
        new AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(names, sel, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { d.dismiss(); then.at(w); }})
                .show();
    }

    private void chooseKinds(final Sheet s, final Runnable after) {
        final Brain.Kind[] all = Brain.Kind.values();
        String[] names = new String[all.length];
        final boolean[] on = new boolean[all.length];
        for (int k = 0; k < all.length; k++) { names[k] = all[k].label; on[k] = s.kinds.contains(all[k]); }
        new AlertDialog.Builder(this).setTitle("סוגי חלקים מותרים (חלקים קיימים נשארים)")
                .setMultiChoiceItems(names, on, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(android.content.DialogInterface d, int w, boolean b) { on[w] = b; }})
                .setPositiveButton("אישור", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        java.util.Set<Brain.Kind> n = java.util.EnumSet.noneOf(Brain.Kind.class);
                        for (int k = 0; k < all.length; k++) if (on[k]) n.add(all[k]);
                        if (n.isEmpty()) { setStatus("צריך לפחות סוג אחד"); return; }
                        s.kinds.clear();
                        s.kinds.addAll(n);
                        after.run();
                    }})
                .setNegativeButton("ביטול", null).show();
    }

    private void addSheet() {
        final Sheet.Kind[] all = Sheet.Kind.values();
        String[] names = new String[all.length];
        for (int k = 0; k < all.length; k++) names[k] = all[k].label;
        new AlertDialog.Builder(this).setTitle("טאב חדש — מאיזה סוג?")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, final int w) {
                        askName("שם הטאב", all[w].label, false, new Named() { public void done(String x) {
                            Sheet s = Sheets.add(project, all[w], x);
                            sheetId = s.id;
                            viewMode = 0;
                            refitPending = true;
                            changedAny(true);
                        }});
                    }}).show();
    }

    private void showHidden() {
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<Runnable> acts = new java.util.ArrayList<>();
        for (final Sheet s : project.sheets) {
            if (!s.hidden) continue;
            labels.add(s.name + " · " + s.kind.label);
            acts.add(new Runnable() { public void run() { s.hidden = false; sheetId = s.id; changedAny(true); }});
        }
        menu("המגירה — טאבים שנשמרים לשלב אחר (נגיעה מחזירה)", labels, acts);
    }

    private interface Placed { void at(Item parent, Division d); }

    /** בחירת מקום בעץ: אב (רשימה לפי העץ), ואם יש לו כמה חלוקות — החלוקה. */
    private void chooseParent(Sheet ms, final Item moving, final Placed then) {
        final java.util.List<Item> items = new java.util.ArrayList<>();
        final java.util.List<String> names = new java.util.ArrayList<>();
        for (Item r : ms.roots) if (!r.loose) listTree(r, 0, moving, items, names);
        if (items.isEmpty()) { setStatus("אין בעץ מקום להציב"); return; }
        new AlertDialog.Builder(this).setTitle("תחת איזה פריט?")
                .setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        final Item par = items.get(w);
                        if (par.divisions.size() <= 1) { then.at(par, null); return; }
                        String[] ds = new String[par.divisions.size()];
                        for (int k = 0; k < ds.length; k++) {
                            String b = par.divisions.get(k).basis;
                            ds[k] = b.isEmpty() ? "צד " + com.aviccc.kvutzot.model.Hebrew.number(k + 1) : b;
                        }
                        new AlertDialog.Builder(Main.this).setTitle("באיזו חלוקה של \"" + par.name + "\"?")
                                .setItems(ds, new android.content.DialogInterface.OnClickListener() {
                                    public void onClick(android.content.DialogInterface d2, int w2) { then.at(par, par.divisions.get(w2)); }})
                                .show();
                    }}).show();
    }

    private static void listTree(Item it, int depth, Item skip, java.util.List<Item> items, java.util.List<String> names) {
        if (it == skip) return;
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < depth; k++) b.append("   ");
        if (depth > 0) b.append("· ");
        items.add(it);
        names.add(b + (it.name.isEmpty() ? "(בלי שם)" : it.name));
        for (Division d : it.divisions) for (Item c : d.children) listTree(c, depth + 1, skip, items, names);
    }

    /** מסיעור המוחות למפה: בוחרים מפה (אם יש כמה); במפה בסדר מוחלט — גם מקום בעץ, אחרת — תלוש. */
    private void toMap(final FreeMap.Node n, final boolean move) {
        final java.util.List<Sheet> maps = new java.util.ArrayList<>();
        for (Sheet s : project.sheets) if (s.kind == Sheet.Kind.MAP && !s.locked) maps.add(s);
        if (maps.isEmpty()) { setStatus("כל המפות נעולות"); return; }
        if (maps.size() == 1) { toMap(n, move, maps.get(0)); return; }
        String[] names = new String[maps.size()];
        for (int k = 0; k < names.length; k++) names[k] = maps.get(k).name + (maps.get(k).absolute ? " · סדר מוחלט" : "");
        new AlertDialog.Builder(this).setTitle("לאיזו מפה?")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { toMap(n, move, maps.get(w)); }}).show();
    }

    private void toMap(final FreeMap.Node n, final boolean move, final Sheet ms) {
        final Runnable done = new Runnable() { public void run() {
            if (move) { project.free.remove(n); freeView.select(null); freeView.invalidate(); }
            changedAny(false);
        }};
        if (!ms.absolute) {
            Item it = new Item(n.text);
            it.loose = true;
            ms.roots.add(it);
            done.run();
            setStatus((move ? "עבר" : "הועתק") + " ל\"" + ms.name + "\" כתלוש: " + n.text);
            return;
        }
        chooseParent(ms, null, new Placed() { public void at(Item parent, Division d) {
            Item it = Edit.addChild(parent, d, n.text);
            parent.collapsed = false;
            done.run();
            setStatus((move ? "עבר" : "הועתק") + " ל\"" + ms.name + "\" תחת \"" + parent.name + "\": " + it.name);
        }});
    }

    /** בטאב נעול: מה שיש על הפריט, לקריאה בלבד. */
    private void readOnlyItem(Item it) {
        StringBuilder b = new StringBuilder();
        java.util.LinkedHashMap<String, java.util.List<String>> groups = new java.util.LinkedHashMap<>();
        for (com.aviccc.kvutzot.model.Model.Info in : it.infos) {
            String g = in.kind == com.aviccc.kvutzot.model.Model.Info.Kind.USER ? (in.name.isEmpty() ? "מידע" : in.name)
                    : in.kind == com.aviccc.kvutzot.model.Model.Info.Kind.ESSENTIAL ? "תכונה עצמית" : "תכונה מקרית";
            if (!groups.containsKey(g)) groups.put(g, new java.util.ArrayList<String>());
            groups.get(g).add(in.text);
        }
        for (java.util.Map.Entry<String, java.util.List<String>> e : groups.entrySet()) {
            b.append(e.getKey()).append(":\n");
            for (String x : e.getValue()) b.append("• ").append(x).append("\n");
            b.append("\n");
        }
        if (!it.tagIds.isEmpty()) {
            b.append("תגיות: ");
            boolean first = true;
            for (String id : it.tagIds) {
                com.aviccc.kvutzot.model.Model.Tag tg = project.tag(id);
                b.append(first ? "" : ", ").append(tg == null ? id : tg.name);
                first = false;
            }
            b.append("\n");
        }
        if (b.length() == 0) b.append("אין מידע על הפריט.");
        new AlertDialog.Builder(this).setTitle(it.name + " · נעול")
                .setMessage(b.toString().trim())
                .setPositiveButton("סגור", null).show();
    }

    // ---------------- 0.11: ביטול, חיפוש, תגיות ובחינות, גיבויים, ייצוא ----------------

    private boolean wantRestore;

    private String viewKey() { return sheetId + "|" + viewMode + "|" + tagLevel + "|" + viewInfo + "|" + levelFrom + "|" + levelTo; }

    /** זוכר איפה המפה עומדת עכשיו, לפני שעוברים לטאב או לתצוגה אחרים. */
    private void rememberView() {
        if (cur == null || project == null || part != 2 || !showMap || mapStale) return;
        float[] v = map.getView();
        if (v != null) cur.views.put(viewKey(), v);
    }

    /** שם הטאב; כששני טאבים באותה כותרת — גם שם הקובץ. */
    private String tabLabel(Doc d) {
        String base = d.label();
        boolean dup = false;
        for (Doc o : docs) if (o != d && o.label().equals(base)) { dup = true; break; }
        String f = d == cur ? fileName : d.fileName;
        if (!dup || f == null || f.isEmpty()) return base;
        if (f.endsWith(".xml")) f = f.substring(0, f.length() - 4);
        return f.equals(base) ? base : base + " · " + f;
    }

    /** פותח את חלון הפריט (מתצוגת הטקסט או מחיפוש). בתצוגה לפי מידע — את הפריט המקורי. */
    private void openItem(Item shown) {
        final Item o = viewResult == null ? shown : viewResult.original(shown);
        if (o == null) return;
        if (locked()) { readOnlyItem(o); return; }
        new ItemWindow(this, project, o, new Runnable() { public void run() { changed(null, null); }}).show();
    }

    // ביטול פעולה: אחרי כל שינוי (ו-0.4 שנייה של שקט) נשמר מצב הקובץ. הקלדה רצופה — צעד אחד.

    private final Runnable snapper = new Runnable() { public void run() { snapshot(); } };

    private void touchUndo() {
        handler.removeCallbacks(snapper);
        handler.postDelayed(snapper, 400);
    }

    private static void resetUndo(Doc d, String text) {
        if (d == null) return;
        d.undo.clear();
        d.redo.clear();
        d.snap = text;
    }

    private void snapshot() {
        handler.removeCallbacks(snapper);
        if (cur == null || project == null) return;
        String now = currentText();
        if (cur.snap == null) { cur.snap = now; return; }
        if (now.equals(cur.snap)) return;
        cur.undo.add(cur.snap);
        if (cur.undo.size() > 30) cur.undo.remove(0);
        cur.snap = now;
        cur.redo.clear();
        invalidateOptionsMenu();
    }

    private void undo(boolean again) {
        if (cur == null || project == null) return;
        snapshot();
        java.util.ArrayList<String> from = again ? cur.redo : cur.undo, to = again ? cur.undo : cur.redo;
        if (from.isEmpty()) { setStatus(again ? "אין מה לבצע שוב" : "אין מה לבטל"); return; }
        String text = from.remove(from.size() - 1);
        try {
            Project p = OpmlIO.read(text);
            to.add(cur.snap);
            cur.snap = text;
            project = p;
            viewResult = null;
            map.select(null, false);
            freeView.select(null);
            dirty = true;
            rev++;
            stash();
            render((again ? "בוצע שוב" : "בוטל") + " · נשארו " + from.size(), false);
            if (autosave && uri != null) {
                handler.removeCallbacks(saver);
                handler.postDelayed(saver, 2000);
            }
            invalidateOptionsMenu();
        } catch (Exception e) { fail("לא ניתן לבטל", e); }
    }

    /** חיפוש בכל המפות שבקובץ: שם, מידע, תגיות, בחינות. נגיעה בתוצאה — קפיצה אל הפריט. */
    private void search() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        final android.widget.EditText ed = new android.widget.EditText(this);
        ed.setSingleLine(true);
        ed.setHint("מה לחפש? (שם, מידע, תגית, בחינה)");
        ed.setTextDirection(View.TEXT_DIRECTION_RTL);
        box.addView(ed);
        final TextView count = new TextView(this);
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        count.setTextColor(0xFF888780);
        box.addView(count);
        final android.widget.ListView list = new android.widget.ListView(this);
        final java.util.List<com.aviccc.kvutzot.model.Search.Hit> hits = new java.util.ArrayList<>();
        final android.widget.BaseAdapter ad = new android.widget.BaseAdapter() {
            public int getCount() { return hits.size(); }
            public Object getItem(int i) { return hits.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, android.view.ViewGroup parent) {
                TextView v = cv instanceof TextView ? (TextView) cv : new TextView(Main.this);
                com.aviccc.kvutzot.model.Search.Hit h = hits.get(i);
                SpannableStringBuilder sb = new SpannableStringBuilder();
                sb.append(h.item.name.isEmpty() ? "(בלי שם)" : h.item.name);
                span(sb, new StyleSpan(Typeface.BOLD), 0, sb.length());
                int s = sb.length();
                String path = com.aviccc.kvutzot.model.Search.path(h.item);
                String where = h.where.equals("שם") ? "" : h.where + ": " + h.snippet;
                String second = where + (!where.isEmpty() && !path.isEmpty() ? "  ·  " : "") + path
                        + (h.sheet != sheet() ? "  ·  בטאב \"" + h.sheet.name + "\"" : "");
                if (!second.isEmpty()) {
                    sb.append("\n").append(second);
                    span(sb, new RelativeSizeSpan(0.8f), s, sb.length());
                    span(sb, new ForegroundColorSpan(0xFF5F5E5A), s, sb.length());
                }
                v.setText(sb);
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                v.setTextColor(0xFF2C2C2A);
                v.setTextDirection(View.TEXT_DIRECTION_RTL);
                v.setPadding(dp(4), dp(8), dp(4), dp(8));
                return v;
            }
        };
        list.setAdapter(ad);
        box.addView(list, new LinearLayout.LayoutParams(-1, dp(320)));
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle("חיפוש").setView(box)
                .setNegativeButton("סגור", null).create();
        ed.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            public void onTextChanged(CharSequence c, int a, int b, int d) { }
            public void afterTextChanged(android.text.Editable e) {
                hits.clear();
                hits.addAll(com.aviccc.kvutzot.model.Search.find(project, e.toString(), 200));
                count.setText(e.toString().trim().isEmpty() ? "" : hits.isEmpty() ? "לא נמצא" : hits.size() >= 200 ? "200 הראשונים" : hits.size() + " נמצאו");
                ad.notifyDataSetChanged();
            }});
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> a, View v, int pos, long id) {
                dlg.dismiss();
                goTo(hits.get(pos).sheet, hits.get(pos).item);
            }});
        if (dlg.getWindow() != null) dlg.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
        ed.requestFocus();
    }

    /** קפיצה אל פריט: עוברים לטאב שלו ולתצוגת העץ, פותחים את האבות שלו, בוחרים אותו ומביאים אותו למסך. */
    private void goTo(Sheet s, Item it) {
        rememberView();
        if (s != null && s != sheet()) { s.hidden = false; sheetId = s.id; }
        viewMode = 0;
        syncSheet();
        if (!showMap) setShowMap(true);
        boolean opened = Edit.expandTo(it);
        map.selectItem(it, null);
        if (opened) changed(it, null);
        else { stash(); render(null, false); }
    }

    /** ניהול תגיות ובחינות: כמה נושאים כל אחת, שינוי שם, תגית על תגית, מחיקה. */
    private void manageTags() {
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<Runnable> acts = new java.util.ArrayList<>();
        project.syncAspects();
        for (final com.aviccc.kvutzot.model.Model.Tag t : project.tags) {
            int use = Edit.itemsWithTag(project, t.id).size();
            StringBuilder on = new StringBuilder();
            for (String id : t.tagIds) { com.aviccc.kvutzot.model.Model.Tag o = project.tag(id); if (o != null) on.append(on.length() > 0 ? ", " : "").append(o.name); }
            labels.add("# " + t.name + "  ·  " + use + (use == 1 ? " פריט" : " פריטים") + (on.length() > 0 ? "  ·  עליה: " + on : ""));
            acts.add(new Runnable() { public void run() { tagActions(t); }});
        }
        for (final java.util.Map.Entry<String, Item> e : new java.util.ArrayList<>(project.aspects.entrySet())) {
            int use = project.aspectUse(e.getKey());
            labels.add("בחינה: " + e.getValue().name + "  ·  " + use + (use == 1 ? " חלוקה" : " חלוקות"));
            acts.add(new Runnable() { public void run() { aspectActions(e.getKey(), e.getValue()); }});
        }
        if (labels.isEmpty()) { setStatus("אין עדיין תגיות או בחינות בקובץ"); return; }
        menu("תגיות (" + project.tags.size() + ") ובחינות (" + project.aspects.size() + ")", labels, acts);
    }

    private void tagActions(final com.aviccc.kvutzot.model.Model.Tag t) {
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<Runnable> acts = new java.util.ArrayList<>();
        final java.util.List<Item> carriers = Edit.itemsWithTag(project, t.id);
        if (!carriers.isEmpty()) {
            labels.add("הפריטים שנושאים אותה (" + carriers.size() + ")…");
            acts.add(new Runnable() { public void run() {
                final java.util.List<String> l = new java.util.ArrayList<>();
                final java.util.List<Runnable> a = new java.util.ArrayList<>();
                for (final Item it : carriers) {
                    String path = com.aviccc.kvutzot.model.Search.path(it);
                    l.add(it.name + (path.isEmpty() ? "" : "   ·   " + path));
                    a.add(new Runnable() { public void run() { goTo(sheetOf(it), it); }});
                }
                menu("# " + t.name, l, a);
            }});
        }
        labels.add("שנה שם…"); acts.add(new Runnable() { public void run() {
            askName("שם חדש לתגית (שם של תגית קיימת — מאחד)", t.name, false, new Named() { public void done(String s) {
                if (s.isEmpty()) return;
                Edit.renameTag(project, t, s);
                changedAny(true);
                manageTags();
            }});
        }});
        labels.add("שים תגית על התגית הזאת…"); acts.add(new Runnable() { public void run() {
            askName("תגית על \"" + t.name + "\" (קיימת או חדשה)", "", false, new Named() { public void done(String s) {
                if (s.isEmpty()) return;
                com.aviccc.kvutzot.model.Model.Tag over = project.obtainTag(s);
                if (Edit.tagOnTag(project, t, over)) { changedAny(true); tagActions(t); }
                else setStatus("אי אפשר: תגית לא מונחת על עצמה, ולא במעגל");
            }});
        }});
        for (final String id : new java.util.ArrayList<>(t.tagIds)) {
            final com.aviccc.kvutzot.model.Model.Tag o = project.tag(id);
            if (o == null) continue;
            labels.add("הסר מעליה: # " + o.name);
            acts.add(new Runnable() { public void run() { t.tagIds.remove(id); changedAny(true); tagActions(t); }});
        }
        labels.add("מחק את התגית" + (carriers.isEmpty() ? "" : " (מ-" + carriers.size() + " פריטים)"));
        acts.add(new Runnable() { public void run() {
            confirm("למחוק את התגית \"" + t.name + "\"" + (carriers.isEmpty() ? "?" : " מכל " + carriers.size() + " הפריטים?"),
                    new Runnable() { public void run() { Edit.deleteTag(project, t); changedAny(true); manageTags(); }});
        }});
        menu("# " + t.name, labels, acts);
    }

    private void aspectActions(final String id, final Item a) {
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<Runnable> acts = new java.util.ArrayList<>();
        final int use = project.aspectUse(id);
        labels.add("פתח (מידע עליה, שינוי שם, ואיפה חילקו לפיה)"); acts.add(new Runnable() { public void run() {
            new ItemWindow(Main.this, project, a, new Runnable() { public void run() { changed(null, null); }}).show(); }});
        labels.add("מחק את הבחינה" + (use == 0 ? "" : " (" + use + " חלוקות יישארו בלי בחינה)"));
        acts.add(new Runnable() { public void run() {
            confirm("למחוק את הבחינה \"" + a.name + "\"?" + (use == 0 ? "" : " " + use + " חלוקות יישארו, בלי בחינה."),
                    new Runnable() { public void run() { Edit.deleteAspect(project, id); changedAny(true); manageTags(); }});
        }});
        menu("בחינה: " + a.name, labels, acts);
    }

    /** הטאב (המפה) שהפריט נמצא בו. */
    private Sheet sheetOf(Item it) {
        Item top = it;
        while (top.parent() != null) top = top.parent();
        for (Sheet s : project.sheets) if (s.kind == Sheet.Kind.MAP && s.roots.contains(top)) return s;
        return null;
    }

    // גיבויים: לפני שמירה, המצב הקודם של הקובץ נשמר בצד — לכל היותר פעם בעשר דקות, עשרה אחרונים לכל קובץ.

    private final java.util.Map<String, Long> lastBackup = new java.util.HashMap<>();

    private java.io.File backupDir(Uri u) {
        return new java.io.File(new java.io.File(getFilesDir(), "backups"), Integer.toHexString(u.toString().hashCode()));
    }

    /** רץ בתור הקבצים, לא במסך. כישלון בגיבוי לא עוצר את השמירה. */
    private void backup(Uri u, String text) {
        try {
            String key = u.toString();
            long now = System.currentTimeMillis();
            Long last = lastBackup.get(key);
            if (last != null && now - last < 10 * 60 * 1000L) return;
            java.io.File dir = backupDir(u);
            dir.mkdirs();
            java.io.File[] old = dir.listFiles();
            if (old == null) old = new java.io.File[0];
            java.util.Arrays.sort(old);
            lastBackup.put(key, now);
            if (old.length > 0 && readFile(old[old.length - 1]).equals(text)) return;
            String name = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(new java.util.Date(now)) + ".xml";
            java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(dir, name));
            try { out.write(text.getBytes(UTF8)); } finally { out.close(); }
            for (int k = 0; k < old.length - 9; k++) old[k].delete();
        } catch (Exception ignored) { }
    }

    private static String readFile(java.io.File f) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), UTF8);
        } finally { in.close(); }
    }

    /** רשימת הגיבויים של הקובץ הפתוח; נגיעה פותחת את הגיבוי בטאב חדש, לא שמור. */
    private void backups() {
        if (uri == null) return;
        java.io.File[] fs = backupDir(uri).listFiles();
        if (fs == null || fs.length == 0) { setStatus("אין עדיין גיבויים לקובץ הזה (נוצרים לפני שמירה, פעם בעשר דקות)"); return; }
        java.util.Arrays.sort(fs);
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<Runnable> acts = new java.util.ArrayList<>();
        final java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US);
        final java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("dd.MM.yyyy  HH:mm", java.util.Locale.US);
        for (int k = fs.length - 1; k >= 0; k--) {
            final java.io.File f = fs[k];
            String when;
            try { when = out.format(in.parse(f.getName().replace(".xml", ""))); } catch (Exception e) { when = f.getName(); }
            final String label = when;
            labels.add(when + "   ·   " + (f.length() / 1024 + 1) + " ק״ב");
            acts.add(new Runnable() { public void run() {
                try {
                    String text = readFile(f);
                    Project p = OpmlIO.read(text);
                    newTab();
                    project = p;
                    part = 2;
                    sheetId = "";
                    uri = null;
                    lastText = null;
                    lastRead = null;
                    dirty = false;
                    viewMode = 0;
                    fileName = "גיבוי מ-" + label.trim() + " (לא שמור)";
                    resetUndo(cur, text);
                    stash();
                    render("כדי לחזור אליו: \"שמור עותק בשם\"", true);
                } catch (Exception e) { fail("לא ניתן לפתוח את הגיבוי", e); }
            }});
        }
        menu("גיבויים — נפתחים בטאב חדש, בלי לגעת בקובץ", labels, acts);
    }

    /** ייצוא המפה כולה (כמו שהיא מוצגת עכשיו, כולל כיווצים ותצוגה) ל-PDF או לתמונה. */
    private void exportMap(final Uri out, boolean pdf) {
        if (project == null || !showMap || part != 2 || map.exportWidth(1f) == 0) { setStatus("אין מפה גרפית על המסך לייצא"); return; }
        try {
            final ByteArrayOutputStream bo = new ByteArrayOutputStream();
            float w1 = map.exportWidth(1f), h1 = map.exportHeight(1f);
            if (pdf) {
                float dens = getResources().getDisplayMetrics().density;
                float s = Math.min(0.75f / dens, 14000f / Math.max(w1, h1));
                android.graphics.pdf.PdfDocument doc = new android.graphics.pdf.PdfDocument();
                android.graphics.pdf.PdfDocument.Page page = doc.startPage(
                        new android.graphics.pdf.PdfDocument.PageInfo.Builder(map.exportWidth(s), map.exportHeight(s), 1).create());
                map.export(page.getCanvas(), s);
                doc.finishPage(page);
                doc.writeTo(bo);
                doc.close();
            } else {
                float s = (float) Math.min(1.0, Math.sqrt(12e6 / (w1 * h1)));
                android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(map.exportWidth(s), map.exportHeight(s), android.graphics.Bitmap.Config.ARGB_8888);
                map.export(new android.graphics.Canvas(bm), s);
                bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bo);
                bm.recycle();
            }
            setStatus("מייצא…");
            io.execute(new Runnable() { public void run() {
                try {
                    OutputStream os = getContentResolver().openOutputStream(out, "w");
                    if (os == null) throw new Exception("לא ניתן לכתוב לקובץ");
                    try { os.write(bo.toByteArray()); os.flush(); } finally { os.close(); }
                    runOnUiThread(new Runnable() { public void run() { setStatus("יוצא: " + displayName(out)); }});
                } catch (Exception e) { fail("הייצוא נכשל", e); }
            }});
        } catch (OutOfMemoryError e) {
            fail("הייצוא נכשל", new Exception("המפה גדולה מדי לתמונה במכשיר הזה. נסה PDF, או כווץ ענפים."));
        } catch (Exception e) { fail("הייצוא נכשל", e); }
    }

    // ---------------- בחינות ----------------

    /** בחירת בחינה: מתוך הקיימות (רשימה במסגרת עם ▾) או חדשה. ריק — חלוקה בלי בחינה. */
    private void askAspect(String title, String initial, final Named then) {
        final android.widget.AutoCompleteTextView f = new android.widget.AutoCompleteTextView(this);
        f.setSingleLine(true);
        f.setText(initial);
        f.setSelectAllOnFocus(true);
        f.setTextDirection(View.TEXT_DIRECTION_RTL);
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Item a : project.aspects.values()) names.add(a.name);
        f.setThreshold(0);
        f.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, names));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        android.graphics.drawable.GradientDrawable frame = new android.graphics.drawable.GradientDrawable();
        frame.setColor(0xFFFFFFFF);
        frame.setStroke(Math.max(1, dp(1)), 0xFFB4B2A9);
        frame.setCornerRadius(dp(6));
        row.setBackground(frame);
        row.setPadding(dp(8), 0, dp(4), 0);
        f.setBackground(null);
        f.setPadding(0, dp(10), 0, dp(10));
        android.graphics.drawable.GradientDrawable drop = new android.graphics.drawable.GradientDrawable();
        drop.setColor(0xFFFFFFFF);
        drop.setStroke(Math.max(1, dp(1)), 0xFFB4B2A9);
        drop.setCornerRadius(dp(6));
        f.setDropDownBackgroundDrawable(drop);
        row.addView(f, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!names.isEmpty()) {
            TextView arrow = new TextView(this);
            arrow.setText("▾");
            arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            arrow.setTextColor(0xFF888780);
            arrow.setPadding(dp(10), dp(6), dp(6), dp(6));
            arrow.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                f.requestFocus();
                if (f.isPopupShowing()) f.dismissDropDown(); else f.showDropDown();
            }});
            row.addView(arrow);
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = new TextView(this);
        hint.setText(names.isEmpty() ? "בחינה חדשה תישמר ברשימת הבחינות של הקובץ."
                : "בחינה קיימת — מהרשימה; שם חדש — בחינה חדשה. שינוי שם של בחינה בכל החלוקות: בכפתור \"בחינה\".");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setTextColor(0xFF888780);
        hint.setPadding(0, dp(6), 0, 0);
        box.addView(hint);
        AlertDialog d = new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton("אישור", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface di, int w) { then.done(f.getText().toString().trim()); }})
                .setNegativeButton("ביטול", null).create();
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    // ---------------- עריכה ----------------

    private interface Named { void done(String s); }

    /** חלון הקלדה. withNext: כפתור "הבא" שמוסיף ופותח שוב (הוספה ברצף), ו-Enter = הבא. */
    private void askName(final String title, String initial, final boolean withNext, final Named then) {
        final android.widget.EditText ed = new android.widget.EditText(this);
        ed.setSingleLine(true);
        ed.setText(initial);
        ed.setSelectAllOnFocus(true);
        ed.setTextDirection(View.TEXT_DIRECTION_RTL);
        ed.setImeOptions(withNext ? android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
                : android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(ed);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton("סיום", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        String s = ed.getText().toString().trim();
                        if (!s.isEmpty() || !withNext) then.done(s);
                    }})
                .setNegativeButton("ביטול", null);
        if (withNext) b.setNeutralButton("הבא", null);
        final AlertDialog dlg = b.create();
        dlg.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        final Runnable next = new Runnable() { public void run() {
            String s = ed.getText().toString().trim();
            if (s.isEmpty()) return;
            then.done(s);
            ed.setText("");
        }};
        dlg.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            public void onShow(android.content.DialogInterface d) {
                if (withNext) dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { next.run(); }});
                ed.requestFocus();
            }});
        ed.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            public boolean onEditorAction(android.widget.TextView v, int id, android.view.KeyEvent e) {
                if (withNext) { next.run(); return true; }
                then.done(ed.getText().toString().trim());
                dlg.dismiss();
                return true;
            }});
        dlg.show();
    }

    /** אחרי כל שינוי: ציור מחדש בלי לזוז, בחירה, ושמירה (אוטומטית — אחרי שתי שניות של שקט). */
    private void changed(Item select, Division selDiv) {
        dirty = true;
        rev++;
        touchUndo();
        stash();
        if (select != null) map.selectItem(select, selDiv);
        render(null, false);
        if (autosave && uri != null) {
            handler.removeCallbacks(saver);
            handler.postDelayed(saver, 2000);
        }
    }

    private boolean guardDirty(final MenuItem mi) {
        if (!dirty || (autosave && uri != null)) {
            if (dirty) { handler.removeCallbacks(saver); save(); }
            return false;
        }
        new AlertDialog.Builder(this).setTitle("יש שינויים שלא נשמרו")
                .setMessage(uri == null ? "הדוגמה לא נשמרת. להמשיך ולאבד את השינויים?" : "להמשיך בלי לשמור?")
                .setPositiveButton("המשך בלי לשמור", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { dirty = false; onOptionsItemSelected(mi); }})
                .setNegativeButton("ביטול", null).show();
        return true;
    }

    private void settings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(18), dp(8), dp(18), 0);
        final android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText("שמירה אוטומטית (שתי שניות אחרי כל שינוי, וביציאה מהאפליקציה)");
        cb.setChecked(autosave);
        box.addView(cb);
        TextView zt = new TextView(this);
        zt.setText("צפיפות סקאלת ההגדלה");
        zt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        zt.setPadding(0, dp(16), 0, dp(4));
        box.addView(zt);
        final android.widget.RadioGroup rg = new android.widget.RadioGroup(this);
        final android.widget.RadioButton half = new android.widget.RadioButton(this);
        half.setText("כל חצי (0.5, 1, 1.5, 2…)");
        half.setId(View.generateViewId());
        final android.widget.RadioButton quarter = new android.widget.RadioButton(this);
        quarter.setText("כל רבע (0.25, 0.5, 0.75, 1…)");
        quarter.setId(View.generateViewId());
        final android.widget.RadioButton other = new android.widget.RadioButton(this);
        other.setText("מספר אחר:");
        other.setId(View.generateViewId());
        rg.addView(half);
        rg.addView(quarter);
        rg.addView(other);
        box.addView(rg);
        final android.widget.EditText custom = new android.widget.EditText(this);
        custom.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        custom.setHint("למשל 0.1");
        box.addView(custom);
        if (Math.abs(zoomStep - 0.5f) < 1e-4) rg.check(half.getId());
        else if (Math.abs(zoomStep - 0.25f) < 1e-4) rg.check(quarter.getId());
        else { rg.check(other.getId()); custom.setText(com.aviccc.kvutzot.model.Zoom.format(zoomStep)); }
        new AlertDialog.Builder(this).setTitle("הגדרות").setView(box)
                .setPositiveButton("אישור", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        autosave = cb.isChecked();
                        int c = rg.getCheckedRadioButtonId();
                        float s = zoomStep;
                        if (c == half.getId()) s = 0.5f;
                        else if (c == quarter.getId()) s = 0.25f;
                        else {
                            try { s = Float.parseFloat(custom.getText().toString().trim()); }
                            catch (NumberFormatException e) { /* נשאר כמו שהיה */ }
                        }
                        zoomStep = com.aviccc.kvutzot.model.Zoom.clampStep(s);
                        prefs.edit().putBoolean("autosave", autosave).putFloat("zoomStep", zoomStep).apply();
                        if (project != null) render(null, false);
                    }})
                .setNegativeButton("ביטול", null).show();
    }

    /** תא ההגדלה: "−", המספר, "+". נגיעה במספר — רשימת כל הערכים לפי הצפיפות. */
    private LinearLayout buildZoomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xF2FFFFFF);
        bg.setStroke(dp(1), 0xFF185FA5);
        bg.setCornerRadius(dp(20));
        bar.setBackground(bg);
        TextView minus = zoomButton("−");
        zoomLabel = zoomButton("1");
        zoomLabel.setMinWidth(dp(56));
        zoomLabel.setTypeface(Typeface.DEFAULT_BOLD);
        TextView plus = zoomButton("+");
        bar.addView(minus);
        bar.addView(zoomLabel);
        bar.addView(plus);
        minus.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            map.setZoom(com.aviccc.kvutzot.model.Zoom.next(map.getZoom(), zoomStep, -1)); }});
        plus.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            map.setZoom(com.aviccc.kvutzot.model.Zoom.next(map.getZoom(), zoomStep, 1)); }});
        zoomLabel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            final java.util.List<Float> vals = com.aviccc.kvutzot.model.Zoom.values(zoomStep);
            String[] names = new String[vals.size() + 1];
            for (int i = 0; i < vals.size(); i++) names[i] = com.aviccc.kvutzot.model.Zoom.format(vals.get(i));
            names[vals.size()] = "התאם למסך";
            new AlertDialog.Builder(Main.this).setTitle("הגדלה")
                    .setItems(names, new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            if (w == vals.size()) map.fit(); else map.setZoom(vals.get(w));
                        }}).show();
        }});
        map.setZoomListener(new MapView.ZoomListener() { public void onZoom(float s) {
            zoomLabel.setText(com.aviccc.kvutzot.model.Zoom.format(s)); }});
        return bar;
    }

    private TextView zoomButton(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setTextColor(0xFF0C447C);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        return t;
    }

    @Override
    public boolean canOpen(MapLayout.Node n) {
        return viewResult == null || (!n.isDivision() && viewResult.original(n.item) != null);
    }

    /** בתצוגה לפי פריט מידע: פותחים את החלון של הפריט המקורי. */
    private boolean openOriginal(MapLayout.Node n) {
        if (locked()) {
            Item o = n.isDivision() ? null : viewResult == null ? n.item : viewResult.original(n.item);
            if (o != null) readOnlyItem(o);
            return true;
        }
        if (viewResult == null) return false;
        final Item orig = n.isDivision() ? null : viewResult.original(n.item);
        if (orig != null) new ItemWindow(this, project, orig, new Runnable() { public void run() { changed(null, null); }}).show();
        return true;
    }

    @Override
    public void onAction(String action, final MapLayout.Node n) {
        if (openOriginal(n)) return;
        final Item it = n.item;
        switch (action) {
            case MapView.A_CHILD:
                askName(n.isDivision() ? "בן חדש ב\"" + n.text + "\"" : "בן חדש ל\"" + it.name + "\"", "", true, new Named() {
                    public void done(String s) {
                        Item c = Edit.addChild(it, n.division, s);
                        if (it.collapsed) it.collapsed = false;
                        changed(c, null);
                    }});
                break;
            case MapView.A_SIBLING:
                if (it.loose && project.absolute()) {
                    setStatus("במפה בסדר מוחלט לא מוסיפים פריטים תלושים. אפשר להציב את הפריט בעץ (⋯)");
                    break;
                }
                final Item[] last = {it};
                askName("אח חדש ל\"" + it.name + "\"", "", true, new Named() {
                    public void done(String s) {
                        Item c = Edit.addSibling(project, last[0], s);
                        if (c != null) { last[0] = c; changed(c, null); }
                    }});
                break;
            case MapView.A_ASPECT: {
                String id = n.division == null ? null : n.division.aspectId;
                Item a = project.aspect(id);
                if (a == null) { onEditNode(n); break; }
                new ItemWindow(this, project, a, new Runnable() { public void run() { changed(it, n.division); }}).show();
                break;
            }
            case MapView.A_EDIT:
                if (n.isDivision()) onEditNode(n);
                else new ItemWindow(this, project, it, new Runnable() { public void run() { changed(it, null); }}).show();
                break;
            case MapView.A_COLLAPSE:
                it.collapsed = !it.collapsed;
                changed(it, null);
                break;
            case MapView.A_MORE: onBranchMenu(n); break;
        }
    }

    @Override
    public void onEditNode(final MapLayout.Node n) {
        if (openOriginal(n)) return;
        if (n.isDivision()) {
            askAspect("החלוקה — לפי איזו בחינה?", n.division.basis, new Named() {
                public void done(String s) { Edit.setAspect(project, n.division, s); changed(n.item, n.division); }});
        } else {
            askName("שם הפריט", n.item.name, false, new Named() {
                public void done(String s) { if (!s.isEmpty()) { n.item.name = s; changed(n.item, null); } }});
        }
    }

    @Override
    public void onBranchMenu(final MapLayout.Node n) {
        if (openOriginal(n)) return;
        final Item it = n.item;
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<Runnable> acts = new java.util.ArrayList<>();
        if (n.isDivision()) {
            final Division d = n.division;
            labels.add("הוסף בן לחלוקה"); acts.add(new Runnable() { public void run() { onAction(MapView.A_CHILD, n); }});
            labels.add("בחר בחינה לחלוקה"); acts.add(new Runnable() { public void run() { onEditNode(n); }});
            if (clip != null) { labels.add("הדבק כבן כאן: " + clip.name); acts.add(new Runnable() { public void run() {
                Item c = Edit.paste(clip, it, d); changed(c, null); }}); }
            labels.add("מחק את החלוקה" + (d.children.isEmpty() ? "" : " ואת " + countDiv(d) + " הפריטים שבה"));
            acts.add(new Runnable() { public void run() {
                confirm("למחוק את החלוקה \"" + n.text + "\"" + (d.children.isEmpty() ? "?" : " ואת כל " + countDiv(d) + " הפריטים שבה?"),
                        new Runnable() { public void run() { Edit.deleteDivision(d); changed(it, null); }});
            }});
            menu("חלוקה: " + n.text, labels, acts);
            return;
        }
        int sibs = it.parentDivision == null ? 0 : it.parentDivision.children.size() - 1;
        String head = it.name + " · " + it.childCount() + " בנים · " + sibs + " אחים";
        labels.add("הוסף חלוקה בצד"); acts.add(new Runnable() { public void run() {
            askAspect("החלוקה החדשה — לפי איזו בחינה? (למשל \"לפי צבע\")", "", new Named() { public void done(String s) {
                Division d = Edit.addSide(it, s);
                Edit.setAspect(project, d, s);
                it.collapsed = false;
                changed(it, d.children.isEmpty() && !d.basis.isEmpty() ? d : null);
            }});
        }});
        labels.add("הוסף בנים ברצף"); acts.add(new Runnable() { public void run() { onAction(MapView.A_CHILD, n); }});
        if (it.parentDivision != null || it.loose) { labels.add("הוסף אחים ברצף"); acts.add(new Runnable() { public void run() { onAction(MapView.A_SIBLING, n); }}); }
        if (it.parentDivision != null) {
            labels.add("הזז לפני"); acts.add(new Runnable() { public void run() { if (Edit.move(it, -1)) changed(it, null); }});
            labels.add("הזז אחרי"); acts.add(new Runnable() { public void run() { if (Edit.move(it, 1)) changed(it, null); }});
        }
        if (it.loose || it.parentDivision != null) {
            labels.add(it.loose ? "הצב בעץ…" : "העבר למקום אחר בעץ…");
            acts.add(new Runnable() { public void run() {
                Sheet ms = project.active(Sheet.Kind.MAP);
                chooseParent(ms, it, new Placed() { public void at(Item parent, Division d) {
                    if (Edit.placeUnder(project, it, parent, d)) { parent.collapsed = false; changed(it, null); }
                    else setStatus("אי אפשר להציב פריט תחת עצמו או תחת בנו");
                }});
            }});
        }
        final int movedHere = Edit.countOffsets(it);
        if (movedHere > 0) {
            labels.add("החזר למקום האוטומטי" + (movedHere > 1 || (it.dx == 0 && it.dy == 0) ? " (" + movedHere + " בענף)" : ""));
            acts.add(new Runnable() { public void run() { Edit.clearOffsets(it); changed(it, null); }});
        }
        labels.add("העתק"); acts.add(new Runnable() { public void run() { clip = Edit.copy(it); setStatus("הועתק: " + it.name); }});
        final boolean canDelete = it.parentDivision != null || it.loose;
        if (canDelete) { labels.add("גזור"); acts.add(new Runnable() { public void run() {
            clip = Edit.copy(it);
            Item parent = it.parent();
            Edit.delete(project, it);
            changed(parent, null);
            setStatus("נגזר: " + clip.name + " · בחר פריט ו\"הדבק\"");
        }}); }
        if (clip != null) { labels.add("הדבק כבן: " + clip.name); acts.add(new Runnable() { public void run() {
            Item c = Edit.paste(clip, it, null); it.collapsed = false; changed(c, null); }}); }
        if (canDelete) {
            labels.add("מחק" + (it.childCount() > 0 ? " (עם " + (Edit.countBranch(it) - 1) + " הפריטים שמתחתיו)" : ""));
            acts.add(new Runnable() { public void run() {
                confirm("למחוק את \"" + it.name + "\"" + (it.childCount() > 0 ? " ואת כל " + (Edit.countBranch(it) - 1) + " הפריטים שמתחתיו?" : "?"),
                        new Runnable() { public void run() {
                            Item parent = it.parent();
                            Edit.delete(project, it);
                            changed(parent, null);
                        }});
            }});
        }
        menu(head, labels, acts);
    }

    private static int countDiv(Division d) {
        int c = 0;
        for (Item i : d.children) c += Edit.countBranch(i);
        return c;
    }

    private void menu(String title, java.util.List<String> labels, final java.util.List<Runnable> acts) {
        new AlertDialog.Builder(this).setTitle(title)
                .setItems(labels.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { acts.get(w).run(); }})
                .show();
    }

    private void confirm(String q, final Runnable yes) {
        new AlertDialog.Builder(this).setTitle(q)
                .setPositiveButton("מחק", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { yes.run(); }})
                .setNegativeButton("ביטול", null).show();
    }
}
