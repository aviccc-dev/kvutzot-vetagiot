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
import com.aviccc.kvutzot.model.OpmlIO;
import com.aviccc.kvutzot.model.TextLines;
import com.aviccc.kvutzot.model.Views;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/** גרסה 0.1: פתיחת קובץ, תצוגת טקסט, שמירה, וזיהוי שינוי מבחוץ. */
public class Main extends Activity implements MapView.Host, FreeView.Host {
    private static final int REQ_OPEN = 1, REQ_CREATE = 2, REQ_SAVE_AS = 3, REQ_EXPORT = 4, REQ_BRAIN = 5;
    private static final int M_OPEN = 1, M_NEW = 2, M_SAVE = 3, M_SAVE_AS = 4, M_RELOAD = 5,
            M_EX_TRI = 6, M_EX_TOYS = 7, M_ABOUT = 8, M_EXPORT = 9,
            M_VIEW = 10, M_DIR = 11, M_FIT = 12, M_LOOSE = 13, M_SETTINGS = 14, M_SHOW = 15, M_SORT = 16, M_TABS = 17, M_CLOSE = 18, M_BRAIN = 19, M_TITLE = 20;
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
        Project project;
        /** הטאב בתוך הקובץ: 0 התחלת נושא, 1 סיעור מוחות, 2 מפה. */
        int part = 2;
        boolean dirty;
        int viewMode, tagLevel = 1;
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
    private Project project;
    /** הטאב בתוך הקובץ: 0 התחלת נושא, 1 סיעור מוחות (מפה גמישה), 2 מפה. */
    private int part = 2;
    private BrainView brainView;
    private ScrollView brainScroll;
    private FreeView freeView;
    private TextView freeAdd;
    private boolean asking;
    private ScrollView textScroll;
    private MapView map;
    private boolean showMap, vertical;
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
        if (freeAdd != null) freeAdd.setVisibility(has && part == 1 ? View.VISIBLE : View.GONE);
        if (has && part != 2) {
            map.setVisibility(View.GONE);
            textScroll.setVisibility(View.GONE);
            if (zoomBar != null) zoomBar.setVisibility(View.GONE);
            invalidateOptionsMenu();
            return;
        }
        boolean m = showMap && project != null;
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
        m.add(0, M_OPEN, 0, "פתח קובץ (בטאב חדש)");
        m.add(0, M_TABS, 0, "ניהול טאבים");
        m.add(0, M_CLOSE, 0, "סגור את הטאב");
        m.add(0, M_DIR, 1, "כיוון המפה");
        m.add(0, M_FIT, 1, "התאם למסך");
        m.add(0, M_NEW, 1, "מפה חדשה");
        m.add(0, M_TITLE, 1, "שנה כותרת");
        m.add(0, M_SAVE, 2, "שמור");
        m.add(0, M_SAVE_AS, 3, "שמור עותק בשם");
        m.add(0, M_RELOAD, 4, "טען מחדש");
        m.add(0, M_EXPORT, 4, "ייצוא ל-OPML (לתוכנות אחרות)");
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
        m.findItem(M_CLOSE).setVisible(cur != null);
        m.findItem(M_DIR).setTitle(vertical ? "כיוון: מימין לשמאל" : "כיוון: מלמעלה למטה").setVisible(project != null && showMap && part == 2);
        m.findItem(M_FIT).setVisible(project != null && showMap && part == 2);
        m.findItem(M_LOOSE).setVisible(project != null && viewMode == 0 && part == 2);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {
        switch (mi.getItemId()) {
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
            case M_VIEW:
                showMap = !showMap;
                prefs.edit().putBoolean("showMap", showMap).apply();
                applyView();
                return true;
            case M_DIR:
                vertical = !vertical;
                prefs.edit().putBoolean("vertical", vertical).apply();
                render(null, true);
                invalidateOptionsMenu();
                return true;
            case M_FIT: map.fit(); return true;
            case M_SHOW: chooseView(); return true;
            case M_SORT:
                viewSort = viewSort == Views.Sort.MAP ? Views.Sort.NAME : Views.Sort.MAP;
                render(null, true);
                return true;
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

    private void create(int req, String title) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/xml");
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
            newTab();
            part = 0;
            writeThenLoad(u, OpmlIO.write(p));
        } else if (req == REQ_SAVE_AS) {
            writeThenLoad(u, currentText());
        } else if (req == REQ_EXPORT) {
            final Uri out = u;
            final String text = OpmlIO.writeOpml(project);
            new Thread(new Runnable() { public void run() {
                try {
                    writeText(out, text);
                    runOnUiThread(new Runnable() { public void run() { setStatus("יוצא ל-OPML: " + displayName(out)); }});
                } catch (Exception e) { fail("הייצוא נכשל", e); }
            }}).start();
        }
    }

    // ---------------- קריאה וכתיבה ----------------

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
        new Thread(new Runnable() { public void run() {
            try {
                final String text = readText(u);
                final Project p = OpmlIO.read(text);
                final String name = displayName(u);
                runOnUiThread(new Runnable() { public void run() {
                    busy--;
                    if (target != cur) {
                        target.uri = u; target.fileName = name; target.lastText = text;
                        target.project = p; target.dirty = false; target.viewMode = 0;
                        saveTabs();
                        return;
                    }
                    uri = u;
                    fileName = name;
                    lastText = text;
                    project = p;
                    dirty = false;
                    viewMode = 0;
                    stash();
                    saveTabs();
                    render("נטען", true);
                }});
            } catch (final SecurityException e) {
                done();
                fail("אין הרשאה לקובץ. פתח אותו שוב מהתפריט.", e);
            } catch (final Exception e) {
                done();
                fail("לא ניתן לטעון את הקובץ", e);
            }
        }}).start();
    }

    /** כתיבה לקובץ חדש, ומעבר אליו מתוך הזיכרון (בלי לקרוא שוב: Dropbox מחליף כתובות). */
    private void writeThenLoad(final Uri u, final String text) {
        setStatus("שומר…");
        final Doc target = cur;
        busy++;
        new Thread(new Runnable() { public void run() {
            try {
                writeText(u, text);
                final Project p = OpmlIO.read(text);
                final String name = displayName(u);
                final boolean ok = readsBack(u, text);
                runOnUiThread(new Runnable() { public void run() {
                    busy--;
                    if (target != cur) {
                        target.uri = u; target.fileName = name; target.lastText = text;
                        target.project = p; target.dirty = false; target.viewMode = 0;
                        saveTabs();
                        return;
                    }
                    uri = u;
                    fileName = name;
                    lastText = text;
                    project = p;
                    dirty = false;
                    viewMode = 0;
                    stash();
                    saveTabs();
                    render(ok ? "נשמר" : STALE, true);
                }});
            } catch (Exception e) {
                done();
                fail("השמירה נכשלה", e);
            }
        }}).start();
    }

    private static final String STALE = "נשמר, אבל השירות החליף את הקובץ. לפני השמירה הבאה: פתח אותו שוב מ\"פתח קובץ\"";

    /** בדיקה אחרי כתיבה: הקובץ נקרא בחזרה מאותה כתובת וזהה. */
    private boolean readsBack(Uri u, String text) {
        try { return readText(u).equals(text); } catch (Exception e) { return false; }
    }

    private void save() {
        if (uri == null || project == null) return;
        final Uri u = uri;
        final Doc target = cur;
        final Project proj = project;
        final String text = currentText();
        setStatus("שומר…");
        busy++;
        new Thread(new Runnable() { public void run() {
            try {
                writeText(u, text);
                final boolean ok = readsBack(u, text);
                runOnUiThread(new Runnable() { public void run() {
                    busy--;
                    if (target != cur) {
                        if (target != null) {
                            target.lastText = text;
                            if (OpmlIO.write(proj).equals(text)) target.dirty = false;
                        }
                        return;
                    }
                    lastText = text;
                    if (currentText().equals(text)) dirty = false;
                    stash();
                    if (part != 2) {
                        // בטאבים 1-2 לא בונים מחדש אחרי שמירה — כדי לא להפריע להקלדה ולגרירה
                        setStatus(partSummary() + (dirty ? " · שומר…" : " · " + (ok ? "נשמר" : STALE)));
                        rebuildTabsOnly();
                        return;
                    }
                    render(ok ? "נשמר" : STALE, false);
                }});
            } catch (Exception e) {
                done();
                fail("השמירה נכשלה", e);
            }
        }}).start();
    }

    private String currentText() {
        return OpmlIO.write(project);
    }

    /** שינוי בסיעור המוחות: לסמן, לשמור (אוטומטית), ולבנות מחדש רק כשהמבנה השתנה. */
    private void brainChanged(boolean structure) { changedAny(structure); }

    /** שינוי בטאבים 1-2 (או בכותרת): לסמן, לשמור (אוטומטית), ולבנות מחדש רק כשהמבנה השתנה. */
    private void changedAny(boolean structure) {
        dirty = true;
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
        final String known = lastText;
        new Thread(new Runnable() { public void run() {
            try {
                final String now = readText(u);
                if (now.equals(known)) return;
                runOnUiThread(new Runnable() { public void run() {
                    if (asking || !u.equals(uri)) return;
                    asking = true;
                    new AlertDialog.Builder(Main.this)
                            .setTitle("הקובץ השתנה מבחוץ")
                            .setMessage("לטעון את הגרסה החדשה? (מה שמוצג עכשיו יוחלף)")
                            .setPositiveButton("טען", new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface d, int w) { asking = false; load(u, false); }})
                            .setNegativeButton("לא עכשיו", new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface d, int w) { asking = false; lastText = now; }})
                            .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                                public void onCancel(android.content.DialogInterface d) { asking = false; }})
                            .show();
                }});
            } catch (Exception ignored) { }
        }}).start();
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
            uri = null;
            lastText = null;
            dirty = false;
            viewMode = 0;
            fileName = "דוגמה: " + name + " (לא שמורה)";
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
        }
        return viewResult == null ? project : viewResult.project;
    }

    private void chooseView() {
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<Runnable> acts = new java.util.ArrayList<>();
        labels.add("רגילה (העץ)"); acts.add(new Runnable() { public void run() { setView(0, 1, ""); }});
        labels.add("לפי תגיות"); acts.add(new Runnable() { public void run() { setView(1, 1, ""); }});
        int maxLevel = Views.maxTagLevel(project);
        for (int k = 2; k <= maxLevel; k++) {
            final int lv = k;
            labels.add("לפי תגיות על תגיות (רמה " + k + ")");
            acts.add(new Runnable() { public void run() { setView(1, lv, ""); }});
        }
        for (final String n : Views.usedInfoNames(project)) {
            labels.add("לפי " + (n.isEmpty() ? "מידע בלי שם" : n));
            acts.add(new Runnable() { public void run() { setView(2, 1, n); }});
        }
        labels.add("לפי תכונות (עצמיות ומקריות)"); acts.add(new Runnable() { public void run() { setView(3, 1, ""); }});
        menu("תצוגה לפי…", labels, acts);
    }

    private void setView(int mode, int level, String info) {
        viewMode = mode;
        tagLevel = level;
        viewInfo = info;
        map.select(null, false);
        render(null, true);
    }

    private void render(String note, boolean refit) {
        if (part != 2) {
            setTitle(project.title.isEmpty() ? "קבוצות ותגיות" : project.title);
            setStatus(partSummary()
                    + (dirty ? (uri == null ? " · לא שמור" : autosave ? " · שומר…" : " · לא נשמר") : "")
                    + (note == null ? "" : " · " + note));
            stash();
            if (part == 0) {
                int y = brainScroll.getScrollY();
                brainView.setBrain(project.start, new BrainView.Listener() {
                    public void changed() { changedAny(false); }
                    public void structure() { changedAny(true); }
                });
                brainScroll.scrollTo(0, y);
            } else {
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
                + (project.absolute ? " · מוחלטת" : "")
                + (dirty ? (uri == null ? " · לא שמור" : autosave ? " · שומר…" : " · לא נשמר") : "")
                + (note == null ? "" : " · " + note));
        stash();
        rebuildStrips();
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
        map.setReadOnly(viewResult != null);
        map.setProject(p, vertical, refit);
        applyView();
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
        cur.uri = uri; cur.fileName = fileName; cur.lastText = lastText; cur.project = project; cur.part = part;
        cur.dirty = dirty; cur.viewMode = viewMode; cur.tagLevel = tagLevel; cur.viewInfo = viewInfo;
        cur.viewSort = viewSort;
    }

    private void unstash(Doc d) {
        uri = d.uri; fileName = d.fileName; lastText = d.lastText; project = d.project; part = d.part;
        dirty = d.dirty; viewMode = d.viewMode; tagLevel = d.tagLevel; viewInfo = d.viewInfo;
        viewSort = d.viewSort;
    }

    /** טאב חדש וריק, והופך לנוכחי (מה שהיה — נשמר בטאב שלו). */
    private void newTab() {
        leaveCurrent();
        Doc d = new Doc();
        docs.add(d);
        cur = d;
        unstash(d);
        map.select(null, false);
    }

    /** לפני מעבר: שמירת המצב, ושמירה לקובץ אם יש שינויים ושמירה אוטומטית. */
    private void leaveCurrent() {
        if (cur == null) return;
        stash();
        handler.removeCallbacks(saver);
        if (dirty && autosave && uri != null) save();
    }

    private void switchTo(Doc d) {
        if (d == cur) { rebuildStrips(); return; }
        if (busy > 0) { setStatus("רגע… שומר או טוען"); return; }
        leaveCurrent();
        cur = d;
        unstash(d);
        map.select(null, false);
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
            names[i] = d.label() + (d == cur ? "  (פתוח עכשיו)" : "") + (d.dirty ? "  · לא נשמר" : "");
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
            labels.add("עבור אל: " + d.label());
            acts.add(new Runnable() { public void run() { d.spread = true; switchTo(d); }});
        }
        for (final Doc d : new java.util.ArrayList<>(docs)) {
            labels.add("סגור: " + d.label());
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
            d.fileName = displayName(d.uri);
            docs.add(d);
        }
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
            TextView t = chip(tabRow, d.label() + (unsaved ? " •" : ""), d == cur, !d.spread, new View.OnClickListener() {
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
        final String[] parts = {"התחלת נושא", "סיעור מוחות", "מפה"};
        for (int k = 0; k < 3; k++) {
            final int pk = k;
            chip(viewRow, parts[k], part == k, false, new View.OnClickListener() { public void onClick(View v) {
                if (part == pk) return;
                part = pk;
                map.select(null, false);
                stash();
                render(null, true);
            }});
        }
        if (part != 2) return;
        gap(viewRow);
        chip(viewRow, "גרפי", showMap, false, new View.OnClickListener() { public void onClick(View v) {
            showMap = true; prefs.edit().putBoolean("showMap", true).apply(); applyView(); rebuildStrips(); }});
        chip(viewRow, "טקסט", !showMap, false, new View.OnClickListener() { public void onClick(View v) {
            showMap = false; prefs.edit().putBoolean("showMap", false).apply(); applyView(); rebuildStrips(); }});
        gap(viewRow);
        chip(viewRow, "עץ", viewMode == 0, false, new View.OnClickListener() { public void onClick(View v) { setView(0, 1, ""); }});
        chip(viewRow, "# תגיות", viewMode == 1 && tagLevel == 1, false, new View.OnClickListener() { public void onClick(View v) { setView(1, 1, ""); }});
        int maxLevel = Views.maxTagLevel(project);
        for (int k = 2; k <= maxLevel; k++) {
            final int lv = k;
            chip(viewRow, "# תגיות " + k, viewMode == 1 && tagLevel == k, false, new View.OnClickListener() {
                public void onClick(View v) { setView(1, lv, ""); }});
        }
        for (final String n : Views.usedInfoNames(project))
            chip(viewRow, n.isEmpty() ? "מידע" : n, viewMode == 2 && viewInfo.equals(n), false, new View.OnClickListener() {
                public void onClick(View v) { setView(2, 1, n); }});
        chip(viewRow, "תכונות", viewMode == 3, false, new View.OnClickListener() { public void onClick(View v) { setView(3, 1, ""); }});
        if (!project.aspects.isEmpty())
            chip(viewRow, "בחינות", viewMode == 4, false, new View.OnClickListener() { public void onClick(View v) { setView(4, 1, ""); }});
        if (viewMode != 0) {
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
        labels.add("העבר למפה (כפריט תלוש)"); acts.add(new Runnable() { public void run() {
            Item it = Edit.addLoose(project, n.text);
            project.free.remove(n);
            freeView.select(null);
            changedAny(false);
            freeView.invalidate();
            setStatus("עבר למפה כתלוש: " + it.name);
        }});
        labels.add("העתק למפה (ונשאר כאן)"); acts.add(new Runnable() { public void run() {
            Edit.addLoose(project, n.text);
            changedAny(false);
            setStatus("הועתק למפה כתלוש: " + n.text);
        }});
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
