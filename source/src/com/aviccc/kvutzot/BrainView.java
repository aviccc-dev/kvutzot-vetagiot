package com.aviccc.kvutzot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.aviccc.kvutzot.model.Brain;
import com.aviccc.kvutzot.model.Hebrew;

import java.util.ArrayList;
import java.util.List;

/** מסך סיעור המוחות: החלקים בזה אחר זה — כתבן, גיליון, פנקס. */
public class BrainView extends LinearLayout {

    public interface Listener {
        /** שינוי תוכן (הקלדה): לסמן ולשמור, בלי לבנות מחדש. */
        void changed();
        /** שינוי מבנה (שורה, עמודה, חלק): לשמור ולבנות מחדש. */
        void structure();
    }

    private static final int BLUE = 0xFF185FA5, GREY = 0xFF888780, DARK = 0xFF2C2C2A, FRAME = 0xFFD3D1C7;
    private final Activity act;
    private final float dens;
    private Brain brain;
    private Listener listener;

    public BrainView(Activity act) {
        super(act);
        this.act = act;
        dens = act.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setLayoutDirection(LAYOUT_DIRECTION_RTL);
        setPadding(dp(12), dp(8), dp(12), dp(60));
    }

    private int dp(float v) { return Math.round(v * dens); }

    public void setBrain(Brain b, Listener l) {
        brain = b;
        listener = l;
        build();
    }

    public void build() {
        removeAllViews();
        if (brain == null) return;
        for (int i = 0; i < brain.sections.size(); i++) {
            Brain.Section s = brain.sections.get(i);
            header(i, s);
            switch (s.kind) {
                case TEXT: textEditor(s); break;
                case GRID: gridEditor(s); break;
                case NOTEBOOK: notebook(s); break;
            }
        }
        TextView add = label("+ הוסף חלק", 15, BLUE, false);
        add.setPadding(0, dp(18), 0, dp(8));
        add.setOnClickListener(new OnClickListener() { public void onClick(View v) { addSectionMenu(); }});
        addView(add);
    }

    private TextView label(String s, float size, int color, boolean bold) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        t.setTextDirection(TEXT_DIRECTION_RTL);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable frame(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(Math.max(1, dp(0.8f)), FRAME);
        g.setCornerRadius(dp(6));
        return g;
    }

    private void menu(String title, final List<String> labels, final List<Runnable> acts) {
        new AlertDialog.Builder(act).setTitle(title)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { acts.get(w).run(); }})
                .show();
    }

    private void ask(String title, String initial, boolean multi, final Done then) {
        final EditText ed = new EditText(act);
        ed.setText(initial);
        ed.setTextDirection(TEXT_DIRECTION_RTL);
        if (multi) {
            ed.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            ed.setMaxLines(8);
        } else ed.setSingleLine(true);
        LinearLayout box = new LinearLayout(act);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(ed, new LayoutParams(-1, -2));
        AlertDialog d = new AlertDialog.Builder(act).setTitle(title).setView(box)
                .setPositiveButton("שמור", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int w) { then.done(ed.getText().toString()); }})
                .setNegativeButton("ביטול", null).create();
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    private interface Done { void done(String s); }

    // ---------------- כותרת חלק ----------------

    private void header(final int idx, final Brain.Section s) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(idx == 0 ? 4 : 18), 0, dp(4));
        String t = s.title.isEmpty() ? s.kind.label : s.title + "  ·  " + s.kind.label;
        row.addView(label(t, 15, BLUE, true), new LayoutParams(0, -2, 1f));
        TextView more = label("⋯", 20, BLUE, false);
        more.setPadding(dp(12), 0, dp(4), 0);
        more.setOnClickListener(new OnClickListener() { public void onClick(View v) {
            List<String> l = new ArrayList<>();
            List<Runnable> a = new ArrayList<>();
            l.add("שם לחלק"); a.add(new Runnable() { public void run() {
                ask("שם לחלק", s.title, false, new Done() { public void done(String x) { s.title = x.trim(); listener.structure(); }}); }});
            if (idx > 0) { l.add("הזז למעלה"); a.add(new Runnable() { public void run() { swap(idx, idx - 1); }}); }
            if (idx < brain.sections.size() - 1) { l.add("הזז למטה"); a.add(new Runnable() { public void run() { swap(idx, idx + 1); }}); }
            if (brain.sections.size() > 1) { l.add("מחק את החלק"); a.add(new Runnable() { public void run() {
                new AlertDialog.Builder(act).setTitle("למחוק את החלק \"" + (s.title.isEmpty() ? s.kind.label : s.title) + "\" וכל מה שבו?")
                        .setPositiveButton("מחק", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { brain.sections.remove(s); listener.structure(); }})
                        .setNegativeButton("ביטול", null).show(); }}); }
            menu(s.kind.label, l, a);
        }});
        row.addView(more);
        addView(row);
    }

    private void swap(int a, int b) {
        Brain.Section x = brain.sections.get(a);
        brain.sections.set(a, brain.sections.get(b));
        brain.sections.set(b, x);
        listener.structure();
    }

    private void addSectionMenu() {
        List<String> l = new ArrayList<>();
        List<Runnable> a = new ArrayList<>();
        for (final Brain.Kind k : Brain.Kind.values()) {
            l.add(k.label);
            a.add(new Runnable() { public void run() { brain.addSection(k); listener.structure(); }});
        }
        menu("חלק חדש — מאיזה סוג?", l, a);
    }

    // ---------------- כתבן ----------------

    private void textEditor(final Brain.Section s) {
        EditText ed = new EditText(act);
        ed.setText(s.text);
        ed.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        ed.setTextDirection(TEXT_DIRECTION_RTL);
        ed.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        ed.setGravity(Gravity.TOP | Gravity.START);
        ed.setMinLines(6);
        ed.setBackground(frame(0xFFFFFFFF));
        ed.setPadding(dp(10), dp(8), dp(10), dp(8));
        ed.addTextChangedListener(new Watcher() { void now(String t) { s.text = t; listener.changed(); }});
        addView(ed, new LayoutParams(-1, -2));
    }

    private abstract static class Watcher implements TextWatcher {
        abstract void now(String t);
        public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
        public void onTextChanged(CharSequence c, int a, int b, int d) { }
        public void afterTextChanged(Editable e) { now(e.toString()); }
    }

    // ---------------- גיליון ----------------

    private void gridEditor(final Brain.Section s) {
        HorizontalScrollView hs = new HorizontalScrollView(act);
        TableLayout table = new TableLayout(act);
        table.setLayoutDirection(LAYOUT_DIRECTION_RTL);
        // שורת כותרות העמודות: נגיעה — פעולות על העמודה
        TableRow head = new TableRow(act);
        head.addView(cornerCell());
        for (int c = 0; c < s.cols(); c++) {
            final int col = c;
            TextView h = label(Hebrew.number(c + 1), 13, GREY, true);
            h.setGravity(Gravity.CENTER);
            h.setPadding(0, dp(4), 0, dp(4));
            h.setOnClickListener(new OnClickListener() { public void onClick(View v) { colMenu(s, col); }});
            head.addView(h);
        }
        table.addView(head);
        for (int r = 0; r < s.grid.size(); r++) {
            final int row = r;
            TableRow tr = new TableRow(act);
            TextView num = label(String.valueOf(r + 1), 13, GREY, true);
            num.setGravity(Gravity.CENTER);
            num.setPadding(dp(6), 0, dp(6), 0);
            num.setOnClickListener(new OnClickListener() { public void onClick(View v) { rowMenu(s, row); }});
            tr.addView(num);
            for (int c = 0; c < s.cols(); c++) {
                final int col = c;
                EditText cell = new EditText(act);
                cell.setText(s.grid.get(r).get(c));
                cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                cell.setTextDirection(TEXT_DIRECTION_RTL);
                cell.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                cell.setMaxLines(4);
                cell.setWidth(dp(120));
                cell.setBackground(frame(0xFFFFFFFF));
                cell.setPadding(dp(6), dp(6), dp(6), dp(6));
                cell.addTextChangedListener(new Watcher() { void now(String t) {
                    if (row < s.grid.size() && col < s.grid.get(row).size()) { s.grid.get(row).set(col, t); listener.changed(); }
                }});
                TableRow.LayoutParams lp = new TableRow.LayoutParams(dp(120), -2);
                lp.setMargins(dp(1), dp(1), dp(1), dp(1));
                tr.addView(cell, lp);
            }
            table.addView(tr);
        }
        hs.addView(table);
        addView(hs);
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(HORIZONTAL);
        TextView addRow = label("+ שורה", 14, BLUE, false);
        addRow.setPadding(dp(4), dp(8), dp(16), dp(4));
        addRow.setOnClickListener(new OnClickListener() { public void onClick(View v) { s.addRow(s.grid.size()); listener.structure(); }});
        TextView addCol = label("+ עמודה", 14, BLUE, false);
        addCol.setPadding(dp(4), dp(8), dp(16), dp(4));
        addCol.setOnClickListener(new OnClickListener() { public void onClick(View v) { s.addCol(s.cols()); listener.structure(); }});
        btns.addView(addRow);
        btns.addView(addCol);
        TextView hint = label("נגיעה במספר שורה או באות עמודה — הוספה ומחיקה", 12, GREY, false);
        hint.setPadding(0, dp(8), 0, 0);
        btns.addView(hint);
        addView(btns);
    }

    private View cornerCell() {
        View v = new View(act);
        return v;
    }

    private void rowMenu(final Brain.Section s, final int r) {
        List<String> l = new ArrayList<>();
        List<Runnable> a = new ArrayList<>();
        l.add("הוסף שורה מעל"); a.add(new Runnable() { public void run() { s.addRow(r); listener.structure(); }});
        l.add("הוסף שורה מתחת"); a.add(new Runnable() { public void run() { s.addRow(r + 1); listener.structure(); }});
        if (s.grid.size() > 1) { l.add("מחק את השורה"); a.add(new Runnable() { public void run() { s.removeRow(r); listener.structure(); }}); }
        menu("שורה " + (r + 1), l, a);
    }

    private void colMenu(final Brain.Section s, final int c) {
        List<String> l = new ArrayList<>();
        List<Runnable> a = new ArrayList<>();
        l.add("הוסף עמודה לפני"); a.add(new Runnable() { public void run() { s.addCol(c); listener.structure(); }});
        l.add("הוסף עמודה אחרי"); a.add(new Runnable() { public void run() { s.addCol(c + 1); listener.structure(); }});
        if (s.cols() > 1) { l.add("מחק את העמודה"); a.add(new Runnable() { public void run() { s.removeCol(c); listener.structure(); }}); }
        menu("עמודה " + Hebrew.number(c + 1), l, a);
    }

    // ---------------- פנקס ----------------

    private void notebook(final Brain.Section s) {
        for (int i = 0; i < s.lines.size(); i++) {
            final int idx = i;
            final Brain.Line ln = s.lines.get(i);
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(frame(ln.struck ? 0xFFF1EFE8 : 0xFFFFFFFF));
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            TextView num = label(String.valueOf(i + 1), 12, GREY, false);
            num.setMinWidth(dp(22));
            row.addView(num);
            TextView tx = label(ln.text.isEmpty() ? "(ריקה)" : ln.text, 16, ln.struck ? GREY : DARK, false);
            if (ln.struck) tx.setPaintFlags(tx.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            row.addView(tx, new LayoutParams(0, -2, 1f));
            row.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                ask("שורה " + (idx + 1), ln.text, true, new Done() { public void done(String x) { ln.text = x; listener.structure(); }});
            }});
            row.setOnLongClickListener(new OnLongClickListener() { public boolean onLongClick(View v) { lineMenu(s, idx); return true; }});
            LayoutParams lp = new LayoutParams(-1, -2);
            lp.topMargin = dp(3);
            addView(row, lp);
        }
        final EditText add = new EditText(act);
        add.setHint("שורה חדשה… (Enter מוסיף)");
        add.setSingleLine(true);
        add.setTextDirection(TEXT_DIRECTION_RTL);
        add.setImeOptions(EditorInfo.IME_ACTION_DONE);
        add.setBackground(frame(0xFFFFFFFF));
        add.setPadding(dp(10), dp(8), dp(10), dp(8));
        add.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                String t = add.getText().toString().trim();
                if (t.isEmpty()) return true;
                s.lines.add(new Brain.Line(t, false));
                pendingFocus = s;
                listener.structure();
                return true;
            }});
        LayoutParams lp = new LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        addView(add, lp);
        TextView hint = label("נגיעה בשורה — עריכה · לחיצה ארוכה — פס, הזזה, מחיקה", 12, GREY, false);
        hint.setPadding(0, dp(4), 0, 0);
        addView(hint);
        if (pendingFocus == s) {
            pendingFocus = null;
            add.post(new Runnable() { public void run() { add.requestFocus(); }});
        }
    }

    private Brain.Section pendingFocus;

    private void lineMenu(final Brain.Section s, final int i) {
        final Brain.Line ln = s.lines.get(i);
        final int n = s.lines.size();
        List<String> l = new ArrayList<>();
        List<Runnable> a = new ArrayList<>();
        l.add(ln.struck ? "הסר את הפס" : "העבר פס (השורה נשארת)");
        a.add(new Runnable() { public void run() { ln.struck = !ln.struck; listener.structure(); }});
        if (i > 0) {
            l.add("הזז למעלה"); a.add(new Runnable() { public void run() { s.moveLine(i, i - 1); listener.structure(); }});
            l.add("הזז לראש"); a.add(new Runnable() { public void run() { s.moveLine(i, 0); listener.structure(); }});
        }
        if (i < n - 1) {
            l.add("הזז למטה"); a.add(new Runnable() { public void run() { s.moveLine(i, i + 1); listener.structure(); }});
            l.add("הזז לסוף"); a.add(new Runnable() { public void run() { s.moveLine(i, n - 1); listener.structure(); }});
        }
        if (n > 2) {
            l.add("הזז למקום…"); a.add(new Runnable() { public void run() {
                ask("למקום מספר (1–" + n + ")", String.valueOf(i + 1), false, new Done() { public void done(String x) {
                    try { s.moveLine(i, Math.max(1, Math.min(n, Integer.parseInt(x.trim()))) - 1); listener.structure(); }
                    catch (NumberFormatException ignored) { }
                }});
            }});
        }
        l.add("הוסף שורה מעל"); a.add(new Runnable() { public void run() {
            ask("שורה חדשה מעל " + (i + 1), "", true, new Done() { public void done(String x) {
                if (!x.trim().isEmpty()) { s.lines.add(i, new Brain.Line(x.trim(), false)); listener.structure(); } }}); }});
        l.add("מחק לגמרי"); a.add(new Runnable() { public void run() {
            new AlertDialog.Builder(act).setTitle("למחוק את השורה לגמרי?")
                    .setMessage(ln.text)
                    .setPositiveButton("מחק", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { s.lines.remove(ln); listener.structure(); }})
                    .setNegativeButton("ביטול", null).show(); }});
        menu("שורה " + (i + 1), l, a);
    }
}
