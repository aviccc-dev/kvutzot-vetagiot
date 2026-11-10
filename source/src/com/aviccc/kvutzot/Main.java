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

import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.OpmlIO;
import com.aviccc.kvutzot.model.TextLines;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/** גרסה 0.1: פתיחת קובץ, תצוגת טקסט, שמירה, וזיהוי שינוי מבחוץ. */
public class Main extends Activity {
    private static final int REQ_OPEN = 1, REQ_CREATE = 2, REQ_SAVE_AS = 3, REQ_EXPORT = 4;
    private static final int M_OPEN = 1, M_NEW = 2, M_SAVE = 3, M_SAVE_AS = 4, M_RELOAD = 5,
            M_EX_TRI = 6, M_EX_TOYS = 7, M_ABOUT = 8, M_EXPORT = 9;
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
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        String saved = prefs.getString("uri", null);
        if (saved != null) {
            uri = Uri.parse(saved);
            load(uri, false);
        } else {
            showWelcome();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkExternalChange();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void showWelcome() {
        setTitle("קבוצות ותגיות");
        status.setText("אין קובץ פתוח");
        body.setText("פתח קובץ, צור קובץ חדש, או פתח דוגמה מהתפריט (⋮).\n\n"
                + "כדי לבדוק שמירה ב-Dropbox: פתח את דוגמת המשולש ובחר \"שמור עותק בשם\".");
    }

    // ---------------- תפריט ----------------

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, M_OPEN, 0, "פתח קובץ").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        m.add(0, M_NEW, 1, "קובץ חדש");
        m.add(0, M_SAVE, 2, "שמור");
        m.add(0, M_SAVE_AS, 3, "שמור עותק בשם");
        m.add(0, M_RELOAD, 4, "טען מחדש");
        m.add(0, M_EXPORT, 4, "ייצוא ל-OPML (לתוכנות אחרות)");
        m.add(0, M_EX_TRI, 5, "דוגמה: משולש");
        m.add(0, M_EX_TOYS, 6, "דוגמה: צעצועים");
        m.add(0, M_ABOUT, 7, "אודות");
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu m) {
        m.findItem(M_SAVE).setEnabled(uri != null && project != null);
        m.findItem(M_SAVE_AS).setEnabled(project != null);
        m.findItem(M_RELOAD).setEnabled(uri != null);
        m.findItem(M_EXPORT).setEnabled(project != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {
        switch (mi.getItemId()) {
            case M_OPEN: {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_OPEN);
                return true;
            }
            case M_NEW: create(REQ_CREATE, "מפה חדשה.xml"); return true;
            case M_SAVE_AS: create(REQ_SAVE_AS, (project.title.isEmpty() ? "מפה" : project.title) + ".xml"); return true;
            case M_SAVE: save(); return true;
            case M_EXPORT: create(REQ_EXPORT, (project.title.isEmpty() ? "מפה" : project.title) + ".opml"); return true;
            case M_RELOAD: load(uri, false); return true;
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
            load(u, true);
        } else if (req == REQ_CREATE) {
            Project p = new Project();
            p.title = "מפה חדשה";
            p.roots.add(new com.aviccc.kvutzot.model.Model.Item("נושא"));
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
        if (in == null) throw new Exception("לא ניתן לפתוח את הקובץ");
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), UTF8);
        } finally { in.close(); }
    }

    private void writeText(Uri u, String text) throws Exception {
        OutputStream out = getContentResolver().openOutputStream(u, "wt");
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
                    if (remember || prefs.getString("uri", null) == null)
                        prefs.edit().putString("uri", u.toString()).apply();
                    render("נטען");
                }});
            } catch (final SecurityException e) {
                fail("אין הרשאה לקובץ. פתח אותו שוב מהתפריט.", e);
            } catch (final Exception e) {
                fail("לא ניתן לטעון את הקובץ", e);
            }
        }}).start();
    }

    private void writeThenLoad(final Uri u, final String text) {
        status.setText("שומר…");
        new Thread(new Runnable() { public void run() {
            try {
                writeText(u, text);
                runOnUiThread(new Runnable() { public void run() {
                    prefs.edit().putString("uri", u.toString()).apply();
                    load(u, true);
                }});
            } catch (Exception e) {
                fail("השמירה נכשלה", e);
            }
        }}).start();
    }

    private void save() {
        if (uri == null || project == null) return;
        final Uri u = uri;
        final String text = OpmlIO.write(project);
        status.setText("שומר…");
        new Thread(new Runnable() { public void run() {
            try {
                writeText(u, text);
                runOnUiThread(new Runnable() { public void run() {
                    lastText = text;
                    render("נשמר");
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
            fileName = "דוגמה: " + name + " (לא שמורה)";
            render("כדי לשמור: \"שמור עותק בשם\"");
        } catch (Exception e) {
            fail("לא ניתן לפתוח את הדוגמה", e);
        }
    }

    // ---------------- תצוגת טקסט ----------------

    private void render(String note) {
        Project p = project;
        setTitle(p.title.isEmpty() ? "קבוצות ותגיות" : p.title);
        int items = TextLines.countItems(p);
        status.setText(fileName + " · " + items + " פריטים · "
                + (p.absolute ? "מפה מוחלטת" : "מפה לא מוחלטת") + (note == null ? "" : " · " + note));
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
        invalidateOptionsMenu();
    }

    private static void span(SpannableStringBuilder sb, Object what, int s, int e) {
        if (e > s) sb.setSpan(what, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
