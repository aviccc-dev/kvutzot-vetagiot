package com.aviccc.kvutzot.model;

import com.aviccc.kvutzot.model.Model.Division;
import com.aviccc.kvutzot.model.Model.Info;
import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.Model.Tag;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * תצוגה לפי פריט מידע (גרסה 0.5): בונה עץ מדומה מתוך המידע הנלווה, והעץ המקורי לא משתנה.
 * "אם תגיות — כל העץ ישתנה באופן שהתגיות יובילו את העץ; אם הגדרות — רשימת הגדרות לכל פריט, עץ שטוח."
 */
public final class Views {
    private Views() {}

    public enum Sort { MAP, NAME }

    public static final class Result {
        public final Project project = new Project();
        /** מפריט בעץ המדומה לפריט המקורי (בתוויות קבוצה — אין). */
        public final Map<Item, Item> origin = new IdentityHashMap<>();
        public String title;

        public Item original(Item virtual) { return origin.get(virtual); }
    }

    /** כל הפריטים לפי סדר המפה (אב לפני בניו), כולל תלושים. */
    public static List<Item> allItems(Project p) {
        List<Item> l = new ArrayList<>();
        for (Item r : p.roots) if (!r.loose) walk(r, l);
        for (Item r : p.roots) if (r.loose) walk(r, l);
        return l;
    }

    private static void walk(Item it, List<Item> l) {
        l.add(it);
        for (Division d : it.divisions) for (Item c : d.children) walk(c, l);
    }

    private static List<Item> sorted(List<Item> items, Sort s) {
        List<Item> l = new ArrayList<>(items);
        if (s == Sort.NAME) {
            final Collator c = Collator.getInstance(Locale.forLanguageTag("he"));
            Collections.sort(l, new Comparator<Item>() {
                public int compare(Item a, Item b) { return c.compare(a.name, b.name); }
            });
        }
        return l;
    }

    /** עותק תצוגה של פריט (בלי בנים), עם המידע והתגיות שלו, ורישום המקור. */
    private static Item leaf(Result r, Division into, Item orig, String name) {
        Item v = into.add(name == null ? orig.name : name);
        v.infos.addAll(orig.infos);
        v.tagIds.addAll(orig.tagIds);
        r.origin.put(v, orig);
        return v;
    }

    private static Item root(Result r, String title) {
        Item root = new Item(title);
        r.project.roots.add(root);
        r.title = title;
        return root;
    }

    /** התגיות של הפריט ברמה המבוקשת: 1 = התגיות שלו; 2 = התגיות שעל התגיות שלו; וכן הלאה. */
    public static Set<String> tagsAtLevel(Project p, Item it, int level) {
        Set<String> cur = new LinkedHashSet<>(it.tagIds);
        for (int k = 1; k < level; k++) {
            Set<String> next = new LinkedHashSet<>();
            for (String id : cur) {
                Tag t = p.tag(id);
                if (t != null) next.addAll(t.tagIds);
            }
            cur = next;
        }
        return cur;
    }

    /** רמת התגיות הגבוהה שקיימת בפרויקט (1 אם אין תגית על תגית). */
    public static int maxTagLevel(Project p) {
        int max = 1;
        for (Item it : allItems(p))
            for (int k = 2; k <= 6; k++) if (!tagsAtLevel(p, it, k).isEmpty()) max = Math.max(max, k);
        return max;
    }

    /** לפי תגיות: תגית ← הפריטים שנושאים אותה. פריט עם כמה תגיות — תחת כולן. */
    public static Result byTags(Project p, int level, Sort sort) {
        Result r = new Result();
        r.project.tags.addAll(p.tags);
        Item root = root(r, "לפי תגיות" + (level > 1 ? " (רמה " + level + ")" : ""));
        List<Item> items = sorted(allItems(p), sort);
        Map<String, List<Item>> groups = new LinkedHashMap<>();
        for (Tag t : p.tags) groups.put(t.id, new ArrayList<Item>());
        List<Item> none = new ArrayList<>();
        for (Item it : items) {
            Set<String> ts = tagsAtLevel(p, it, level);
            if (ts.isEmpty()) none.add(it);
            for (String id : ts) {
                List<Item> g = groups.get(id);
                if (g == null) { g = new ArrayList<>(); groups.put(id, g); }
                g.add(it);
            }
        }
        for (Map.Entry<String, List<Item>> e : groups.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            Tag t = p.tag(e.getKey());
            Item g = root.firstDivision().add("# " + (t == null ? e.getKey() : t.name) + " (" + e.getValue().size() + ")");
            for (Item it : e.getValue()) leaf(r, g.firstDivision(), it, null);
        }
        if (!none.isEmpty()) {
            Item g = root.firstDivision().add("בלי תגית (" + none.size() + ")");
            for (Item it : none) leaf(r, g.firstDivision(), it, null);
        }
        return r;
    }

    /** שמות המידע שהמשתמש המציא ונמצאים בשימוש בפרויקט. */
    public static List<String> usedInfoNames(Project p) {
        Set<String> s = new LinkedHashSet<>();
        for (Item it : allItems(p))
            for (Info in : it.infos) if (in.kind == Info.Kind.USER) s.add(in.name);
        return new ArrayList<>(s);
    }

    /** לפי מידע בשם מסוים (למשל הגדרה): עץ שטוח — פריט ← הערכים שלו. בסוף — מי שאין לו. */
    public static Result byInfoName(Project p, String name, Sort sort) {
        Result r = new Result();
        r.project.tags.addAll(p.tags);
        Item root = root(r, "לפי " + (name.isEmpty() ? "מידע" : name));
        List<Item> none = new ArrayList<>();
        for (Item it : sorted(allItems(p), sort)) {
            List<String> vals = new ArrayList<>();
            for (Info in : it.infos) if (in.kind == Info.Kind.USER && in.name.equals(name)) vals.add(in.text);
            if (vals.isEmpty()) { none.add(it); continue; }
            Item v = root.firstDivision().add(it.name);
            r.origin.put(v, it);
            for (String val : vals) {
                Item x = v.firstDivision().add(val);
                r.origin.put(x, it);
            }
        }
        if (!none.isEmpty()) {
            Item g = root.firstDivision().add("בלי " + (name.isEmpty() ? "מידע" : name) + " (" + none.size() + ")");
            for (Item it : none) leaf(r, g.firstDivision(), it, null);
        }
        return r;
    }

    /** לפי תכונות: תכונה (לפי הנוסח) ← הפריטים שיש להם אותה, וליד כל אחד — מחויבת או מקרית. */
    public static Result byProperties(Project p, Sort sort) {
        Result r = new Result();
        r.project.tags.addAll(p.tags);
        Item root = root(r, "לפי תכונות");
        Map<String, List<Object[]>> groups = new LinkedHashMap<>();
        List<Item> none = new ArrayList<>();
        for (Item it : sorted(allItems(p), sort)) {
            boolean any = false;
            for (Info in : it.infos) {
                if (in.kind == Info.Kind.USER) continue;
                any = true;
                String key = in.text.trim();
                List<Object[]> g = groups.get(key);
                if (g == null) { g = new ArrayList<>(); groups.put(key, g); }
                g.add(new Object[]{it, in.kind});
            }
            if (!any) none.add(it);
        }
        for (Map.Entry<String, List<Object[]>> e : groups.entrySet()) {
            Item g = root.firstDivision().add(e.getKey() + " (" + e.getValue().size() + ")");
            for (Object[] o : e.getValue()) {
                Item it = (Item) o[0];
                String status = o[1] == Info.Kind.ESSENTIAL ? "מחויבת" : "מקרית";
                leaf(r, g.firstDivision(), it, it.name + " · " + status);
            }
        }
        if (!none.isEmpty()) {
            Item g = root.firstDivision().add("בלי תכונות (" + none.size() + ")");
            for (Item it : none) leaf(r, g.firstDivision(), it, null);
        }
        return r;
    }

    /**
     * לפי בחינות: בחינה ← הפריטים שחולקו לפיה. נגיעה בקבוצה פותחת את הבחינה עצמה,
     * ובפריט — את הפריט.
     */
    public static Result byAspects(Project p, Sort sort) {
        p.syncAspects();
        Result r = new Result();
        r.project.tags.addAll(p.tags);
        Item root = root(r, "לפי בחינות");
        Map<String, List<Item>> owners = new LinkedHashMap<>();
        for (String id : p.aspects.keySet()) owners.put(id, new ArrayList<Item>());
        for (Item it : sorted(allItems(p), sort))
            for (Division d : it.divisions)
                if (!d.aspectId.isEmpty() && owners.containsKey(d.aspectId) && !owners.get(d.aspectId).contains(it))
                    owners.get(d.aspectId).add(it);
        for (Map.Entry<String, List<Item>> e : owners.entrySet()) {
            Item a = p.aspect(e.getKey());
            Item g = root.firstDivision().add(a.name + " (" + e.getValue().size() + ")");
            g.infos.addAll(a.infos);
            g.tagIds.addAll(a.tagIds);
            r.origin.put(g, a);
            for (Item it : e.getValue()) leaf(r, g.firstDivision(), it, null);
        }
        return r;
    }

    /**
     * "מרמה עד רמה" (0.9.2): רק הרמות from עד to, בלי מה שמעליהן. השורש ברמה 0.
     * הפריטים שברמה from מקובצים לפי האב שלהם — האב מופיע רק כתווית החלוקה, לא כפריט.
     * מה שמתחת ל-to מכווץ (רואים כמה מוסתר). העץ המקורי לא משתנה.
     */
    public static Result byLevels(Project p, int from, int to) {
        if (from < 1) from = 1;
        if (to < from) to = from;
        Result r = new Result();
        r.project.tags.addAll(p.tags);
        Item root = root(r, from == to ? "רמה " + from : "רמות " + from + "–" + to);
        root.divisions.clear();
        List<Item> parents = new ArrayList<>();
        for (Item it : allItems(p)) if (!it.loose && depthOf(it) == from - 1 && it.childCount() > 0) parents.add(it);
        for (Item par : parents)
            for (Division d : par.divisions) {
                if (d.children.isEmpty()) continue;
                Division g = root.addDivision(d.basis.isEmpty() ? par.name : par.name + " · " + d.basis);
                for (Item c : d.children) deep(r, g, c, to - from);
            }
        if (root.divisions.isEmpty()) root.addDivision("");
        return r;
    }

    private static int depthOf(Item it) {
        int k = 0;
        for (Item x = it.parent(); x != null; x = x.parent()) k++;
        return k;
    }

    /** עותק תצוגה עם הבנים; בעומק left=0 — מכווץ. */
    private static Item deep(Result r, Division into, Item orig, int left) {
        Item v = leaf(r, into, orig, null);
        if (left <= 0 && orig.childCount() > 0) v.collapsed = true;
        for (Division d : orig.divisions) {
            Division nd = v.addDivision(d.basis);
            nd.aspectId = d.aspectId;
            for (Item c : d.children) deep(r, nd, c, left - 1);
        }
        return v;
    }
}
