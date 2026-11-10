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

    // ---------------- מידע נלווה ותגיות ----------------

    /** הוספת תגית לפריט לפי שם (תגית קיימת, או חדשה). מחזיר את התגית. */
    public static Model.Tag addTag(Project p, Item it, String name) {
        Model.Tag t = p.obtainTag(name.trim());
        if (!it.tagIds.contains(t.id)) it.tagIds.add(t.id);
        return t;
    }

    public static void removeTag(Item it, String tagId) { it.tagIds.remove(tagId); }

    /**
     * שינוי שם תגית — חל על כל הפריטים. אם כבר יש תגית בשם החדש, שתיהן מתאחדות:
     * כל הפריטים (והתגיות) שהיו עם הישנה עוברים לקיימת, והישנה נמחקת. מחזיר את התגית שנשארה.
     */
    public static Model.Tag renameTag(Project p, Model.Tag t, String name) {
        name = name.trim();
        Model.Tag other = p.tagByName(name);
        if (other == null || other == t) { t.name = name; return t; }
        for (Item r : p.roots) replaceTag(r, t.id, other.id);
        for (Model.Tag x : p.tags) replaceId(x.tagIds, t.id, other.id);
        for (String id : t.tagIds) if (!other.tagIds.contains(id) && !id.equals(other.id)) other.tagIds.add(id);
        p.tags.remove(t);
        return other;
    }

    private static void replaceTag(Item it, String from, String to) {
        replaceId(it.tagIds, from, to);
        for (Division d : it.divisions) for (Item c : d.children) replaceTag(c, from, to);
    }

    private static void replaceId(java.util.List<String> ids, String from, String to) {
        int i = ids.indexOf(from);
        if (i < 0) return;
        if (ids.contains(to)) ids.remove(i); else ids.set(i, to);
    }

    /** כמה פריטים נושאים את התגית. */
    public static int tagUse(Project p, String tagId) {
        int n = 0;
        for (Item r : p.roots) n += tagUse(r, tagId);
        return n;
    }

    private static int tagUse(Item it, String tagId) {
        int n = it.tagIds.contains(tagId) ? 1 : 0;
        for (Division d : it.divisions) for (Item c : d.children) n += tagUse(c, tagId);
        return n;
    }

    /** שמות המידע שהמשתמש המציא, בכל הפרויקט (להצעה כשמוסיפים), ובראשם הנפוצים. */
    public static java.util.List<String> userInfoNames(Project p) {
        java.util.LinkedHashSet<String> s = new java.util.LinkedHashSet<>();
        s.add("הגדרה");
        s.add("דוגמה");
        s.add("הערה");
        for (Item r : p.roots) collectNames(r, s);
        return new java.util.ArrayList<>(s);
    }

    private static void collectNames(Item it, java.util.Set<String> s) {
        for (Info in : it.infos) if (in.kind == Info.Kind.USER && !in.name.isEmpty()) s.add(in.name);
        for (Division d : it.divisions) for (Item c : d.children) collectNames(c, s);
    }

    /** הוספת מידע: אחרי האחרון מאותו סוג (ואותו שם), כדי שהקבוצות יישארו יחד. */
    public static Info addInfo(Item it, Info.Kind k, String name, String text) {
        Info n = new Info(k, name, text);
        int at = -1;
        for (int i = 0; i < it.infos.size(); i++) {
            Info x = it.infos.get(i);
            if (x.kind == k && (k != Info.Kind.USER || x.name.equals(n.name))) at = i;
        }
        it.infos.add(at < 0 ? it.infos.size() : at + 1, n);
        return n;
    }

    /** העברת תכונה בין עצמית למקרית (או שינוי סוג), למקום המתאים. */
    public static void setKind(Item it, Info in, Info.Kind k) {
        if (in.kind == k) return;
        it.infos.remove(in);
        Info moved = addInfo(it, k, k == Info.Kind.USER ? in.name : "", in.text);
        it.infos.set(it.infos.indexOf(moved), in);
        in.kind = k;
        if (k != Info.Kind.USER) in.name = "";
    }
}
