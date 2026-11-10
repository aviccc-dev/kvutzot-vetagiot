package com.aviccc.kvutzot.model;

import com.aviccc.kvutzot.model.Model.Division;
import com.aviccc.kvutzot.model.Model.Info;
import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;

/** פעולות עריכה על המפה. בלי אנדרואיד, כדי שאפשר לבדוק. */
public final class Edit {
    private Edit() {}

    /** בן חדש בסוף החלוקה (או בחלוקה הראשונה של הפריט). */
    public static Item addChild(Item parent, Division into, String name) {
        Division d = into != null ? into : parent.firstDivision();
        return d.add(name);
    }

    /** אח חדש מיד אחרי הפריט. לשורש אין אחים: מחזיר null. */
    public static Item addSibling(Project p, Item it, String name) {
        Division d = it.parentDivision;
        if (d == null) {
            if (!it.loose) return null;
            Item n = new Item(name);
            n.loose = true;
            p.roots.add(p.roots.indexOf(it) + 1, n);
            return n;
        }
        Item n = new Item(name);
        n.parentDivision = d;
        d.children.add(d.children.indexOf(it) + 1, n);
        return n;
    }

    /**
     * חלוקה נוספת בצד. אם לפריט כבר יש בנים בחלוקה יחידה בלי שם — הם נשארים בצד א,
     * והחלוקה החדשה היא צד ב. בפריט בלי בנים — החלוקה הראשונה מקבלת את השם.
     */
    public static Division addSide(Item it, String basis) {
        if (it.divisions.isEmpty()) return it.addDivision(basis);
        if (it.divisions.size() == 1) {
            Division only = it.divisions.get(0);
            if (only.children.isEmpty() && only.basis.isEmpty()) { only.basis = basis; return only; }
        }
        return it.addDivision(basis);
    }

    /** פריט תלוש חדש. */
    public static Item addLoose(Project p, String name) {
        Item n = new Item(name);
        n.loose = true;
        p.roots.add(n);
        return n;
    }

    /** מחיקת ענף. את המפה הראשית אי אפשר למחוק. מחזיר אם נמחק. */
    public static boolean delete(Project p, Item it) {
        if (it.parentDivision != null) {
            it.parentDivision.children.remove(it);
            it.parentDivision = null;
            return true;
        }
        if (it.loose) return p.roots.remove(it);
        return false;
    }

    /** מחיקת צד עם כל בניו. חלוקה יחידה בלי שם לא נמחקת כך. */
    public static boolean deleteDivision(Division d) {
        Item o = d.owner;
        if (o == null || !o.divisions.contains(d)) return false;
        o.divisions.remove(d);
        d.owner = null;
        return true;
    }

    public static int countBranch(Item it) { return 1 + TextLines.descendants(it); }

    /** עותק עמוק של ענף (לגזור/העתק/הדבק). התגיות נשארות אותן תגיות. */
    public static Item copy(Item src) {
        Item n = new Item(src.name);
        n.collapsed = src.collapsed;
        for (Info in : src.infos) n.infos.add(new Info(in.kind, in.name, in.text));
        n.tagIds.addAll(src.tagIds);
        n.extra.putAll(src.extra);
        for (Xml.Node x : src.extraNodes) n.extraNodes.add(x.copy());
        for (Division d : src.divisions) {
            Division nd = n.addDivision(d.basis);
            nd.extra.putAll(d.extra);
            for (Item c : d.children) {
                Item cc = copy(c);
                cc.parentDivision = nd;
                nd.children.add(cc);
            }
        }
        return n;
    }

    /** הדבקה כבן (בסוף החלוקה). מדביקים עותק, כך שאפשר להדביק שוב. */
    public static Item paste(Item clip, Item parent, Division into) {
        Item n = copy(clip);
        Division d = into != null ? into : parent.firstDivision();
        n.parentDivision = d;
        d.children.add(n);
        return n;
    }

    /** האם a הוא b או צאצא שלו (כדי לא להדביק ענף לתוך עצמו). */
    public static boolean within(Item a, Item b) {
        for (Item x = a; x != null; x = x.parent()) if (x == b) return true;
        return false;
    }

    /** הזזה בין האחים: -1 למעלה/ימינה, +1 למטה/שמאלה. */
    public static boolean move(Item it, int dir) {
        Division d = it.parentDivision;
        if (d == null) return false;
        int i = d.children.indexOf(it), j = i + dir;
        if (j < 0 || j >= d.children.size()) return false;
        d.children.remove(i);
        d.children.add(j, it);
        return true;
    }
}
