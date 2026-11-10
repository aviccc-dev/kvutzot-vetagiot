package com.aviccc.kvutzot.model;

import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.Model.Sheet;

import java.util.List;

/** פעולות על הטאבים שבקובץ (0.10): הוספה, העתקה, שינוי שם, הזזה, מחיקה. בלי אנדרואיד. */
public final class Sheets {
    private Sheets() {}

    /** טאב חדש וריק מהסוג הזה, בסוף. מפה חדשה מקבלת ראש בשם הטאב. */
    public static Sheet add(Project p, Sheet.Kind k, String name) {
        Sheet s = new Sheet(k, p.newSheetId(), name == null || name.trim().isEmpty() ? k.label : name.trim());
        if (k == Sheet.Kind.MAP) {
            s.absolute = true;
            s.roots.add(new Item(p.title.isEmpty() ? "נושא" : p.title));
        }
        p.sheets.add(s);
        return s;
    }

    /** עותק של הטאב — עם התוכן והמאפיינים — מיד אחריו. */
    public static Sheet copy(Project p, Sheet src) {
        Sheet s = new Sheet(src.kind, p.newSheetId(), src.name + " (עותק)");
        s.note = src.note;
        s.locked = false;
        s.hidden = false;
        s.kinds.clear();
        s.kinds.addAll(src.kinds);
        s.erase = src.erase;
        s.links = src.links;
        s.absolute = src.absolute;
        s.dir = src.dir;
        s.view = src.view;
        s.layout = src.layout;
        switch (src.kind) {
            case MAP:
                for (Item r : src.roots) {
                    Item c = Edit.copy(r);
                    c.loose = r.loose;
                    s.roots.add(c);
                }
                break;
            case START: {
                Xml.Node tmp = new Xml.Node("tmp");
                BrainIO.writeSections(src.brain, tmp);
                BrainIO.readSections(tmp, s.brain);
                s.brain.title = src.brain.title;
                break;
            }
            case FREE:
                for (FreeMap.Node n : src.free.nodes) s.free.addWithId(n.id, n.text, n.x, n.y);
                for (FreeMap.Link l : src.free.links) s.free.link(s.free.node(l.a), s.free.node(l.b));
                break;
        }
        p.sheets.add(p.sheets.indexOf(src) + 1, s);
        return s;
    }

    public static void rename(Sheet s, String name) {
        if (name != null && !name.trim().isEmpty()) s.name = name.trim();
    }

    /** הזזה בשורת הטאבים: -1 לפני, +1 אחרי. */
    public static boolean move(Project p, Sheet s, int dir) {
        int i = p.sheets.indexOf(s), j = i + dir;
        if (i < 0 || j < 0 || j >= p.sheets.size()) return false;
        p.sheets.remove(i);
        p.sheets.add(j, s);
        return true;
    }

    /** מחיקת טאב. את האחרון מסוגו לא מוחקים (כל קובץ — לפחות אחד מכל סוג). */
    public static boolean delete(Project p, Sheet s) {
        int same = 0;
        for (Sheet x : p.sheets) if (x.kind == s.kind) same++;
        if (same <= 1 || !p.sheets.remove(s)) return false;
        Sheet act = p.active(s.kind);
        if (act == null || act == s || !p.sheets.contains(act)) p.use(p.first(s.kind));
        return true;
    }

    /** כמה טאבים מוצגים (לא מוסתרים). */
    public static int visible(Project p) {
        int n = 0;
        for (Sheet s : p.sheets) if (!s.hidden) n++;
        return n;
    }

    /** במפה בסדר מוחלט: הפריטים שעוד אין להם מקום בעץ (תלושים). */
    public static List<Item> misplaced(Project p) {
        return p.looseItems();
    }
}
