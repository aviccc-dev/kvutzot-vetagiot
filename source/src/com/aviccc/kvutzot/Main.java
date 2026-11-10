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

import com.aviccc.kvutzot.model.Edit;
import com.aviccc.kvutzot.model.MapLayout;
import com.aviccc.kvutzot.model.Model.Division;
import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.OpmlIO;
import com.aviccc.kvutzot.model.TextLines;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/** גרסה 0.1: פתיחת קובץ, תצוגת טקסט, שמירה, וזיהוי שינוי מבחוץ. */
public class Main extends Activity implements MapView.Host {
    private static final int REQ_OPEN = 1, REQ_CREATE = 2, REQ_SAVE_AS = 3, REQ_EXPORT = 4;
    private static final int M_OPEN = 1, M_NEW = 2, M_SAVE = 3, M_SAVE_AS = 4, M_RELOAD = 5,
            M_EX_TRI = 6, M_EX_TOYS = 7, M_ABOUT = 8, M_EXPORT = 9,
            M_VIEW = 10, M_DIR = 11, M_FIT = 12, M_LOOSE = 13, M_SETTINGS = 14;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private SharedPreferences prefs;
    private TextView status, body;
    /** הקובץ הפתוח; null כשמוצגת דוגמה שלא נשמרה. */
    private Uri uri;
    private String fileName = "";
    /** הטקסט כפי שנטען או נשמר לאחרונה, להשוואה עם שינוי מבחוץ. */
    private String lastText;
    private Project project;
    private boolean asking;
    private ScrollView textScroll;
    private MapView map;
    private boolean showMap, vertical;
    /** יש שינויים שלא נשמרו. */
    private boolean dirty;
    private boolean autosave;
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
        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        status.setTextColor(Color.rgb(95, 94, 90));
        int pad = dp(10);
        status.setPadding(pad, dp(6), pad, dp(6));
        status.setBackgroundColor(Color.rgb(241, 239, 232));
        root.addView(status);
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
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        showMap = prefs.getBoolean("showMap", true);
        vertical = prefs.getBoolean("vertical", true);
        autosave = prefs.getBoolean("autosave", true);
        applyView();

        String saved = prefs.getString("uri", null);
        if (saved != null) {
            uri = Uri.parse(saved);
            load(uri, false);
        } else {
            showWelcome();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
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
        boolean m = showMap && project != null;
        map.setVisibility(m ? View.VISIBLE : View.GONE);
        textScroll.setVisibility(m ? View.GONE : View.VISIBLE);
        invalidateOptionsMenu();
    }

    private void showWelcome() {
        setTitle("קבוצות ותגיות");
        status.setText("אין קובץ פתוח");
        body.setText("פתח קובץ, צור קובץ חדש, או פתח דוגמה מהתפריט (⋮).\n\n"
                + "כדי לבדוק שמירה ב-Dropbox: פתח את דוגמת המשולש ובחר \"שמור עותק בשם\".");
    }

    // ---------------- תפריט ----------------

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, M_VIEW, 0, "טקסט").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        m.add(0, M_OPEN, 0, "פתח קובץ").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        m.add(0, M_DIR, 1, "כיוון המפה");
        m.add(0, M_FIT, 1, "התאם למסך");
        m.add(0, M_NEW, 1, "קובץ חדש");
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
        m.findItem(M_RELOAD).setEnabled(uri != null);
        m.findItem(M_EXPORT).setEnabled(project != null);
        m.findItem(M_VIEW).setTitle(showMap ? "טקסט" : "מפה").setVisible(project != null);
        m.findItem(M_DIR).setTitle(vertical ? "כיוון: מימין לשמאל" : "כיוון: מלמעלה למטה").setVisible(project != null && showMap);
        m.findItem(M_FIT).setVisible(project != null && showMap);
        m.findItem(M_LOOSE).setVisible(project != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {
        switch (mi.getItemId()) {
            case M_OPEN: {
                if (guardDirty(mi)) return true;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_OPEN);
                return true;
            }
            case M_NEW: if (!guardDirty(mi)) create(REQ_CREATE, "מפה חדשה.xml"); return true;
            case M_SAVE_AS: create(REQ_SAVE_AS, (project.title.isEmpty() ? "מפה" : project.title) + ".xml"); return true;
            case M_SAVE: save(); return true;
            case M_VIEW:
                showMap = !showMap;
                prefs.edit().putBoolean("showMap", showMap).apply();
                applyView();
                return true;
            case M_DIR:
                vertical = !vertical;
                prefs.edit().putBoolean("vertical", vertical).apply();
                map.setProject(project, vertical, true);
                invalidateOptionsMenu();
                return true;
            case M_FIT: map.fit(); return true;
            case M_LOOSE:
                askName("פריט תלוש חדש", "", true, new Named() { public void done(String s) {
                    Item n = Edit.addLoose(project, s);
                    changed(n, null);
                }});
                return true;
            case M_SETTINGS: settings(); return true;
            case M_EXPORT: create(REQ_EXPORT, (project.title.isEmpty() ? "מפה" : project.title) + ".opml"); return true;
            case M_RELOAD: if (!guardDirty(mi)) load(uri, false); return true;
            case M_EX_TRI: if (!guardDirty(mi)) example("triangle.xml", "משולש"); return true;
            case M_EX_TOYS: if (!guardDirty(mi)) example("toys.xml", "צעצועים"); return true;
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
            load(u, true);
        } else if (req == REQ_CREATE) {
            Project p = new Project();
            p.title = "מפה חדשה";
            p.roots.add(new Item("נושא"));
            writeThenLoad(u, OpmlIO.write(p));
        } else if (req == REQ_SAVE_AS) {
            writeThenLoad(u, OpmlIO.write(project));
        } else if (req == REQ_EXPORT) {
            final Uri out = u;
            final String text = OpmlIO.writeOpml(project);
            new Thread(new Runnable() { public void run() {
                try {
                    writeText(out, text);
                    runOnUiThread(new Runnable() { public void run() { status.setText("יוצא ל-OPML: " + displayName(out)); }});
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
        status.setText("טוען…");
        new Thread(new Runnable() { public void run() {
            try {
                final String text = readText(u);
                final Project p = OpmlIO.read(text);
                final String name = displayName(u);
                runOnUiThread(new Runnable() { public void run() {
                    uri = u;
                    fileName = name;
                    lastText = text;
                    project = p;
                    dirty = false;
                    if (remember || prefs.getString("uri", null) == null)
                        prefs.edit().putString("uri", u.toString()).apply();
                    render("נטען", true);
                }});
            } catch (final SecurityException e) {
                fail("אין הרשאה לקובץ. פתח אותו שוב מהתפריט.", e);
            } catch (final Exception e) {
                fail("לא ניתן לטעון את הקובץ", e);
            }
        }}).start();
    }

    /** כתיבה לקובץ חדש, ומעבר אליו מתוך הזיכרון (בלי לקרוא שוב: Dropbox מחליף כתובות). */
    private void writeThenLoad(final Uri u, final String text) {
        status.setText("שומר…");
        new Thread(new Runnable() { public void run() {
            try {
                writeText(u, text);
                final Project p = OpmlIO.read(text);
                final String name = displayName(u);
                final boolean ok = readsBack(u, text);
                runOnUiThread(new Runnable() { public void run() {
                    prefs.edit().putString("uri", u.toString()).apply();
                    uri = u;
                    fileName = name;
                    lastText = text;
                    project = p;
                    dirty = false;
                    render(ok ? "נשמר" : STALE, true);
                }});
            } catch (Exception e) {
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
        final String text = OpmlIO.write(project);
        status.setText("שומר…");
        new Thread(new Runnable() { public void run() {
            try {
                writeText(u, text);
                final boolean ok = readsBack(u, text);
                runOnUiThread(new Runnable() { public void run() {
                    lastText = text;
                    if (OpmlIO.write(project).equals(text)) dirty = false;
                    render(ok ? "נשמר" : STALE, false);
                }});
            } catch (Exception e) {
                fail("השמירה נכשלה", e);
            }
        }}).start();
    }

    private void fail(final String what, final Exception e) {
        runOnUiThread(new Runnable() { public void run() {
            status.setText(what);
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
            project = OpmlIO.read(new String(bo.toByteArray(), UTF8));
            uri = null;
            lastText = null;
            dirty = false;
            fileName = "דוגמה: " + name + " (לא שמורה)";
            render("כדי לשמור: \"שמור עותק בשם\"", true);
        } catch (Exception e) {
            fail("לא ניתן לפתוח את הדוגמה", e);
        }
    }

    // ---------------- תצוגת טקסט ----------------

    private void render(String note, boolean refit) {
        Project p = project;
        setTitle(p.title.isEmpty() ? "קבוצות ותגיות" : p.title);
        int items = TextLines.countItems(p);
        status.setText(fileName + " · " + items + " פריטים · "
                + (p.absolute ? "מפה מוחלטת" : "מפה לא מוחלטת")
                + (dirty ? (uri == null ? " · לא שמור" : autosave ? " · שומר…" : " · לא נשמר") : "")
                + (note == null ? "" : " · " + note));
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
        map.setProject(p, vertical, refit);
        applyView();
    }

    private static void span(SpannableStringBuilder sb, Object what, int s, int e) {
        if (e > s) sb.setSpan(what, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
        final android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText("שמירה אוטומטית (שתי שניות אחרי כל שינוי, וביציאה מהאפליקציה)");
        cb.setChecked(autosave);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        box.addView(cb);
        new AlertDialog.Builder(this).setTitle("הגדרות").setView(box)
                .setPositiveButton("אישור", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        autosave = cb.isChecked();
                        prefs.edit().putBoolean("autosave", autosave).apply();
                        if (project != null) render(null, false);
                    }})
                .setNegativeButton("ביטול", null).show();
    }

    @Override
    public void onAction(String action, final MapLayout.Node n) {
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
        if (n.isDivision()) {
            askName("שם החלוקה (הבחינה)", n.division.basis, false, new Named() {
                public void done(String s) { n.division.basis = s; changed(n.item, n.division); }});
        } else {
            askName("שם הפריט", n.item.name, false, new Named() {
                public void done(String s) { if (!s.isEmpty()) { n.item.name = s; changed(n.item, null); } }});
        }
    }

    @Override
    public void onBranchMenu(final MapLayout.Node n) {
        final Item it = n.item;
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<Runnable> acts = new java.util.ArrayList<>();
        if (n.isDivision()) {
            final Division d = n.division;
            labels.add("הוסף בן לחלוקה"); acts.add(new Runnable() { public void run() { onAction(MapView.A_CHILD, n); }});
            labels.add("שנה את שם החלוקה"); acts.add(new Runnable() { public void run() { onEditNode(n); }});
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
            askName("שם החלוקה החדשה (למשל \"לפי צבע\")", "", false, new Named() { public void done(String s) {
                Division d = Edit.addSide(it, s);
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
        labels.add("העתק"); acts.add(new Runnable() { public void run() { clip = Edit.copy(it); status.setText("הועתק: " + it.name); }});
        final boolean canDelete = it.parentDivision != null || it.loose;
        if (canDelete) { labels.add("גזור"); acts.add(new Runnable() { public void run() {
            clip = Edit.copy(it);
            Item parent = it.parent();
            Edit.delete(project, it);
            changed(parent, null);
            status.setText("נגזר: " + clip.name + " · בחר פריט ו\"הדבק\"");
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
