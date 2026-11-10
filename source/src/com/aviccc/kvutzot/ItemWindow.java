package com.aviccc.kvutzot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.aviccc.kvutzot.model.Edit;
import com.aviccc.kvutzot.model.Model.Division;
import com.aviccc.kvutzot.model.Model.Info;
import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.Model.Tag;
import com.aviccc.kvutzot.model.TextLines;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * החלון על הפריט (שלב 6): כל המידע עליו, וניתן לעריכה. כל שינוי נשמר מיד בפריט
 * (ומשם — בשמירה האוטומטית או הידנית).
 */
public class ItemWindow {
    private static final int BLUE = 0xFF185FA5, GREY = 0xFF5F5E5A, LIGHT = 0xFFF1EFE8, DARK = 0xFF2C2C2A;

    private final Activity act;
    private final Project p;
    private final Item it;
    private final Runnable onChange;
    private final float dens;
    private LinearLayout body;
    private AlertDialog dlg;

    public ItemWindow(Activity act, Project p, Item it, Runnable onChange) {
        this.act = act; this.p = p; this.it = it; this.onChange = onChange;
        this.dens = act.getResources().getDisplayMetrics().density;
    }

    private int dp(float v) { return Math.round(v * dens); }

    public void show() {
        ScrollView sv = new ScrollView(act);
        body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        body.setPadding(dp(18), dp(6), dp(18), dp(10));
        sv.addView(body);
        dlg = new AlertDialog.Builder(act).setView(sv).setPositiveButton("סגור", null).create();
        rebuild();
        dlg.show();
    }

    private void changed() {
        onChange.run();
        rebuild();
    }

    // ---------------- בניית החלון ----------------

    private void rebuild() {
        body.removeAllViews();
        TextView name = text(it.name.isEmpty() ? "(בלי שם)" : it.name, 22, DARK, true);
        name.setPadding(0, dp(6), 0, dp(2));
        name.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { renameItem(); }});
        body.addView(name);
        body.addView(text(place(), 13, GREY, false));

        aspects();

        // מידע שהמשתמש ממציא, לפי שמות
        Map<String, List<Info>> groups = new LinkedHashMap<>();
        for (Info in : it.infos) if (in.kind == Info.Kind.USER) {
            List<Info> l = groups.get(in.name);
            if (l == null) { l = new ArrayList<>(); groups.put(in.name, l); }
            l.add(in);
        }
        header("מידע", "+ מידע חדש", new Runnable() { public void run() { editInfo(null, Info.Kind.USER, ""); }});
        if (groups.isEmpty()) body.addView(hint("אין עדיין. למשל הגדרה, דוגמה, הערה."));
        for (final Map.Entry<String, List<Info>> g : groups.entrySet()) {
            final String gname = g.getKey().isEmpty() ? "מידע" : g.getKey();
            sub(gname + (g.getValue().size() > 1 ? " (" + g.getValue().size() + ")" : ""), "+",
                    new Runnable() { public void run() { editInfo(null, Info.Kind.USER, g.getKey()); }});
            for (final Info in : g.getValue()) row(in.text, new Runnable() { public void run() { editInfo(in, in.kind, in.name); }});
        }

        // תגיות
        header("תגיות", "+ תגית", new Runnable() { public void run() { addTag(); }});
        if (it.tagIds.isEmpty()) body.addView(hint("אין תגיות."));
        for (final String id : new ArrayList<>(it.tagIds)) {
            final Tag t = p.tag(id);
            if (t == null) continue;
            int others = Edit.tagUse(p, id) - 1;
            row("# " + t.name + (others > 0 ? "   ·   עוד " + others + " פריטים" : ""),
                    new Runnable() { public void run() { tagMenu(t); }});
        }

        // תכונות
        header("תכונות עצמיות (מחויבות)", "+ הוסף", new Runnable() { public void run() { editInfo(null, Info.Kind.ESSENTIAL, ""); }});
        List<Info> ess = it.infos(Info.Kind.ESSENTIAL);
        if (ess.isEmpty()) body.addView(hint("אין."));
        for (final Info in : ess) row(in.text, new Runnable() { public void run() { editInfo(in, in.kind, ""); }});
        header("תכונות מקריות (שאינן מחויבות)", "+ הוסף", new Runnable() { public void run() { editInfo(null, Info.Kind.INCIDENTAL, ""); }});
        List<Info> inc = it.infos(Info.Kind.INCIDENTAL);
        if (inc.isEmpty()) body.addView(hint("אין."));
        for (final Info in : inc) row(in.text, new Runnable() { public void run() { editInfo(in, in.kind, ""); }});
    }

    /** האם הפריט הוא בחינה (נפתח מתצוגת הבחינות). */
    private String aspectIdOfItem() {
        for (Map.Entry<String, Item> e : p.aspects.entrySet()) if (e.getValue() == it) return e.getKey();
        return null;
    }

    /**
     * בחינות (0.10.4): לפי מה הפריט נחלק מאביו, ולפי מה הוא מחלק את בניו. נגיעה — בחירה או שינוי,
     * ופתיחת הבחינה עצמה (יש לה מקום למידע כמו לפריט). בבחינה — איפה חילקו לפיה.
     */
    private void aspects() {
        String aid = aspectIdOfItem();
        if (aid != null) {
            header("חילקו לפיה", "", new Runnable() { public void run() { }});
            List<Item> owners = new ArrayList<>();
            for (Item r : p.allRoots()) owners(r, aid, owners);
            if (owners.isEmpty()) body.addView(hint("עוד לא חילקו לפיה."));
            for (final Item o : owners) {
                Item par = o.parent();
                row(o.name + (par == null ? "" : "   ·   תחת \"" + par.name + "\""), new Runnable() { public void run() {
                    new ItemWindow(act, p, o, onChange).show(); }});
            }
            return;
        }
        header("בחינות", "+ חלוקה", new Runnable() { public void run() {
            chooseAspect("חלוקה חדשה — לפי איזו בחינה?", "", new Named() { public void done(String s) {
                Division d = Edit.addSide(it, "");
                Edit.setAspect(p, d, s);
                changed();
            }});
        }});
        final Division pd = it.parentDivision;
        if (pd != null) {
            sub("נחלק מאביו \"" + it.parent().name + "\" לפי", "", new Runnable() { public void run() { }});
            row(aspectLabel(pd), new Runnable() { public void run() { aspectMenu(pd); }});
        }
        boolean any = false;
        for (int k = 0; k < it.divisions.size(); k++) {
            final Division d = it.divisions.get(k);
            if (d.children.isEmpty() && d.aspectId.isEmpty() && d.basis.isEmpty()) continue;
            if (!any) sub("מחלק את בניו לפי", "", new Runnable() { public void run() { }});
            any = true;
            String side = it.divisions.size() > 1 ? "צד " + com.aviccc.kvutzot.model.Hebrew.number(k + 1) + ": " : "";
            row(side + aspectLabel(d) + "   ·   " + d.children.size() + " בנים", new Runnable() { public void run() { aspectMenu(d); }});
        }
        if (pd == null && !any) body.addView(hint("אין חלוקות."));
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

    private void aspectMenu(final Division d) {
        final Item a = d.aspectId.isEmpty() ? null : p.aspect(d.aspectId);
        List<String> items = new ArrayList<>();
        items.add(a == null ? "קבע בחינה…" : "שנה בחינה לחלוקה הזאת…");
        if (a != null) items.add("פתח את הבחינה \"" + a.name + "\" (מידע עליה, ואיפה חילקו לפיה)");
        if (a != null || !d.basis.isEmpty()) items.add("בלי בחינה");
        new AlertDialog.Builder(act).setTitle("בחינת החלוקה")
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int w) {
                        if (w == 0) {
                            chooseAspect("לפי איזו בחינה?", a == null ? d.basis : a.name, new Named() { public void done(String s) {
                                Edit.setAspect(p, d, s);
                                changed();
                            }});
                        } else if (w == 1 && a != null) {
                            new ItemWindow(act, p, a, onChange).show();
                        } else {
                            Edit.setAspect(p, d, "");
                            changed();
                        }
                    }}).show();
    }

    private interface Named { void done(String s); }

    private void chooseAspect(String title, String initial, final Named then) {
        final AutoCompleteTextView f = new AutoCompleteTextView(act);
        f.setSingleLine(true);
        f.setHint("שם הבחינה");
        f.setText(initial);
        f.setSelection(f.getText().length());
        f.setTextDirection(View.TEXT_DIRECTION_RTL);
        List<String> names = new ArrayList<>();
        for (Item x : p.aspects.values()) if (!names.contains(x.name)) names.add(x.name);
        f.setThreshold(0);
        f.setAdapter(new ArrayAdapter<>(act, android.R.layout.simple_dropdown_item_1line, names));
        f.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) { if (has) f.showDropDown(); }});
        AlertDialog d = new AlertDialog.Builder(act).setTitle(title + (names.isEmpty() ? "" : " (קיימת או חדשה)"))
                .setView(wrap(names.isEmpty() ? f : listBox(f)))
                .setPositiveButton("אישור", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int w) {
                        String s = f.getText().toString().trim();
                        if (!s.isEmpty()) then.done(s);
                    }})
                .setNegativeButton("ביטול", null).create();
        keyboard(d);
        d.show();
    }

    private String place() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Item> e : p.aspects.entrySet())
            if (e.getValue() == it) {
                int use = p.aspectUse(e.getKey());
                return "בחינה · " + (use == 0 ? "עוד לא חילקו לפיה" : "חילקו לפיה " + use + (use == 1 ? " פעם" : " פעמים"))
                        + " · נגיעה בשם — שינוי בכל החלוקות";
            }
        Item par = it.parent();
        if (par != null) {
            b.append("תחת \"").append(par.name).append("\"");
            Division d = it.parentDivision;
            if (!d.basis.isEmpty()) b.append(" · ").append(d.basis);
        } else {
            b.append(it.loose ? "פריט תלוש" : "ראש המפה");
        }
        int depth = 0;
        for (Item x = par; x != null; x = x.parent()) depth++;
        b.append(" · עומק ").append(depth);
        b.append(" · ").append(it.childCount()).append(" בנים");
        int all = TextLines.descendants(it);
        if (all > it.childCount()) b.append(" (").append(all).append(" בסך הכול)");
        return b.toString();
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView hint(String s) {
        TextView t = text(s, 14, 0xFF888780, false);
        t.setPadding(0, dp(2), 0, dp(4));
        return t;
    }

    private void header(String title, String action, final Runnable onAdd) {
        LinearLayout l = new LinearLayout(act);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, dp(16), 0, dp(4));
        TextView t = text(title, 16, BLUE, true);
        l.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView a = text(action, 15, BLUE, false);
        a.setPadding(dp(10), dp(6), dp(4), dp(6));
        a.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onAdd.run(); }});
        l.addView(a);
        body.addView(l);
        View line = new View(act);
        line.setBackgroundColor(0xFFB5D4F4);
        body.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
    }

    private void sub(String title, String action, final Runnable onAdd) {
        LinearLayout l = new LinearLayout(act);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, dp(8), 0, 0);
        l.addView(text(title, 14, GREY, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView a = text(action, 17, BLUE, false);
        a.setPadding(dp(12), 0, dp(4), 0);
        a.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onAdd.run(); }});
        l.addView(a);
        body.addView(l);
    }

    private void row(String s, final Runnable onTap) {
        TextView t = text(s.isEmpty() ? "(ריק)" : s, 16, DARK, false);
        t.setBackgroundColor(LIGHT);
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onTap.run(); }});
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        body.addView(t, lp);
    }

    // ---------------- פעולות ----------------

    private void renameItem() {
        final EditText ed = field(it.name, false);
        AlertDialog d = new AlertDialog.Builder(act).setTitle("שם הפריט").setView(wrap(ed))
                .setPositiveButton("שמור", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int w) {
                        String s = ed.getText().toString().trim();
                        if (!s.isEmpty()) { it.name = s; changed(); }
                    }})
                .setNegativeButton("ביטול", null).create();
        keyboard(d);
        d.show();
    }

    /** הוספה (in == null) או עריכה של מידע. במידע של המשתמש — גם שם התא, עם הצעות. */
    private void editInfo(final Info in, final Info.Kind kind, String name) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final AutoCompleteTextView nameField;
        if (kind == Info.Kind.USER) {
            nameField = new AutoCompleteTextView(act);
            nameField.setSingleLine(true);
            nameField.setHint("שם (למשל הגדרה, דוגמה)");
            nameField.setText(name);
            nameField.setTextDirection(View.TEXT_DIRECTION_RTL);
            nameField.setThreshold(0);
            nameField.setAdapter(new ArrayAdapter<>(act, android.R.layout.simple_dropdown_item_1line, Edit.userInfoNames(p)));
            nameField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean f) { if (f && nameField.getText().length() == 0) nameField.showDropDown(); }});
            box.addView(listBox(nameField));
        } else nameField = null;
        final EditText textField = field(in == null ? "" : in.text, true);
        textField.setHint(kind == Info.Kind.USER ? "התוכן" : "התכונה");
        box.addView(textField);
        String title = in != null ? "עריכה" : kind == Info.Kind.ESSENTIAL ? "תכונה עצמית חדשה"
                : kind == Info.Kind.INCIDENTAL ? "תכונה מקרית חדשה" : "מידע חדש";
        AlertDialog.Builder b = new AlertDialog.Builder(act).setTitle(title).setView(box)
                .setPositiveButton("שמור", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int w) {
                        String t = textField.getText().toString().trim();
                        String n = nameField == null ? "" : nameField.getText().toString().trim();
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
                    }})
                .setNegativeButton("ביטול", null);
        if (in != null) {
            if (kind == Info.Kind.USER) {
                b.setNeutralButton("מחק", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int w) { it.infos.remove(in); changed(); }});
            } else {
                final Info.Kind other = kind == Info.Kind.ESSENTIAL ? Info.Kind.INCIDENTAL : Info.Kind.ESSENTIAL;
                b.setNeutralButton(kind == Info.Kind.ESSENTIAL ? "העבר למקריות" : "העבר לעצמיות",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface di, int w) {
                                String t = textField.getText().toString().trim();
                                if (!t.isEmpty()) in.text = t;
                                Edit.setKind(it, in, other);
                                changed();
                            }});
            }
        }
        final AlertDialog d = b.create();
        if (in != null && kind != Info.Kind.USER) {
            // הכפתור השלישי תפוס ב"העבר", לכן המחיקה כקישור בתוך החלון
            TextView del = text("מחק את התכונה", 14, 0xFFA32D2D, false);
            del.setPadding(0, dp(10), 0, dp(4));
            del.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                it.infos.remove(in); d.dismiss(); changed(); }});
            box.addView(del);
        }
        keyboard(d);
        d.show();
    }

    private void addTag() {
        final AutoCompleteTextView f = new AutoCompleteTextView(act);
        f.setSingleLine(true);
        f.setHint("שם התגית");
        f.setTextDirection(View.TEXT_DIRECTION_RTL);
        List<String> names = new ArrayList<>();
        for (Tag t : p.tags) if (!it.tagIds.contains(t.id)) names.add(t.name);
        f.setThreshold(0);
        f.setAdapter(new ArrayAdapter<>(act, android.R.layout.simple_dropdown_item_1line, names));
        f.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) { if (has) f.showDropDown(); }});
        AlertDialog d = new AlertDialog.Builder(act).setTitle("תגית" + (names.isEmpty() ? " חדשה" : " (קיימת או חדשה)"))
                .setView(wrap(names.isEmpty() ? f : listBox(f)))
                .setPositiveButton("הוסף", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int w) {
                        String s = f.getText().toString().trim();
                        if (!s.isEmpty()) { Edit.addTag(p, it, s); changed(); }
                    }})
                .setNegativeButton("ביטול", null).create();
        keyboard(d);
        d.show();
    }

    private void tagMenu(final Tag t) {
        int use = Edit.tagUse(p, t.id);
        String[] items = {"הסר מהפריט הזה", "שנה שם (בכל " + use + " הפריטים)"};
        new AlertDialog.Builder(act).setTitle("# " + t.name)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int w) {
                        if (w == 0) { Edit.removeTag(it, t.id); changed(); return; }
                        final EditText ed = field(t.name, false);
                        AlertDialog d = new AlertDialog.Builder(act).setTitle("שם חדש לתגית")
                                .setMessage("אם כבר יש תגית בשם הזה, שתיהן יתאחדו.")
                                .setView(wrap(ed))
                                .setPositiveButton("שמור", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface di2, int w2) {
                                        String s = ed.getText().toString().trim();
                                        if (!s.isEmpty()) { Edit.renameTag(p, t, s); changed(); }
                                    }})
                                .setNegativeButton("ביטול", null).create();
                        keyboard(d);
                        d.show();
                    }})
                .show();
    }

    // ---------------- עזר ----------------

    private EditText field(String initial, boolean multi) {
        EditText ed = new EditText(act);
        ed.setText(initial);
        ed.setTextDirection(View.TEXT_DIRECTION_RTL);
        if (multi) {
            ed.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            ed.setMinLines(2);
            ed.setMaxLines(8);
        } else {
            ed.setSingleLine(true);
            ed.setSelectAllOnFocus(true);
        }
        return ed;
    }

    /**
     * שדה עם רשימה: מסגרת עדינה וחץ קטן ▾, כדי שרואים שיש שם רשימה. נגיעה בחץ פותחת אותה.
     */
    private View listBox(final AutoCompleteTextView f) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        android.graphics.drawable.GradientDrawable frame = new android.graphics.drawable.GradientDrawable();
        frame.setColor(0xFFFFFFFF);
        frame.setStroke(Math.max(1, dp(0.8f)), 0xFFB4B2A9);
        frame.setCornerRadius(dp(6));
        row.setBackground(frame);
        row.setPadding(dp(8), 0, dp(4), 0);
        f.setBackground(null);
        f.setPadding(0, dp(10), 0, dp(10));
        android.graphics.drawable.GradientDrawable drop = new android.graphics.drawable.GradientDrawable();
        drop.setColor(0xFFFFFFFF);
        drop.setStroke(Math.max(1, dp(0.8f)), 0xFFB4B2A9);
        drop.setCornerRadius(dp(6));
        f.setDropDownBackgroundDrawable(drop);
        row.addView(f, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = text("▾", 16, 0xFF888780, false);
        arrow.setPadding(dp(10), dp(6), dp(6), dp(6));
        arrow.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            f.requestFocus();
            if (f.isPopupShowing()) f.dismissDropDown(); else { f.setText(f.getText()); f.showDropDown(); }
        }});
        row.addView(arrow);
        return row;
    }

    private View wrap(View v) {
        LinearLayout box = new LinearLayout(act);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(v, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    private static void keyboard(AlertDialog d) {
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

}
