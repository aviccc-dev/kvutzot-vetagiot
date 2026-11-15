package com.aviccc.kvutzot.model;

import com.aviccc.kvutzot.model.Model.Division;
import com.aviccc.kvutzot.model.Model.Info;
import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.Model.Sheet;
import com.aviccc.kvutzot.model.Model.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** חיפוש (0.11): בשם הפריט, במידע שעליו, בתגיות שלו ובשם הבחינה של החלוקות שלו — בכל המפות שבקובץ. */
public final class Search {
    private Search() {}

    public static final class Hit {
        public final Sheet sheet;
        public final Item item;
        /** איפה נמצא: "שם", שם המידע, "תגית", "בחינה". */
        public final String where;
        /** הטקסט שבו נמצא (מקוצר סביב המקום). */
        public final String snippet;

        Hit(Sheet sheet, Item item, String where, String snippet) {
            this.sheet = sheet; this.item = item; this.where = where; this.snippet = snippet;
        }
    }

    /** כל המילים שבשאלה צריכות להימצא (בכל סדר), בלי הבדל בין אותיות גדולות לקטנות. קודם מי שנמצא בשם. */
    public static List<Hit> find(Project p, String query, int max) {
        List<Hit> byName = new ArrayList<>(), other = new ArrayList<>();
        String[] words = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (query.trim().isEmpty()) return byName;
        for (Sheet s : p.sheets) {
            if (s.kind != Sheet.Kind.MAP) continue;
            for (Item r : s.roots) walk(p, s, r, words, byName, other);
        }
        byName.addAll(other);
        return byName.size() > max ? new ArrayList<>(byName.subList(0, max)) : byName;
    }

    private static void walk(Project p, Sheet s, Item it, String[] words, List<Hit> byName, List<Hit> other) {
        if (has(it.name, words)) byName.add(new Hit(s, it, "שם", it.name));
        else {
            Hit h = null;
            for (Info in : it.infos)
                if (has(in.text, words)) {
                    String w = in.kind == Info.Kind.ESSENTIAL ? "תכונה עצמית" : in.kind == Info.Kind.INCIDENTAL ? "תכונה מקרית"
                            : in.name.isEmpty() ? "מידע" : in.name;
                    h = new Hit(s, it, w, around(in.text, words[0]));
                    break;
                }
            if (h == null) for (String id : it.tagIds) {
                Tag t = p.tag(id);
                if (t != null && has(t.name, words)) { h = new Hit(s, it, "תגית", "# " + t.name); break; }
            }
            if (h == null) for (Division d : it.divisions)
                if (!d.basis.isEmpty() && has(d.basis, words)) { h = new Hit(s, it, "בחינה", d.basis); break; }
            if (h != null) other.add(h);
        }
        for (Division d : it.divisions) for (Item c : d.children) walk(p, s, c, words, byName, other);
    }

    private static boolean has(String text, String[] words) {
        String t = text.toLowerCase(Locale.ROOT);
        for (String w : words) if (!t.contains(w)) return false;
        return true;
    }

    /** קטע קצר מהטקסט סביב המילה. */
    static String around(String text, String word) {
        String flat = text.replaceAll("\\s+", " ").trim();
        if (flat.length() <= 70) return flat;
        int i = Math.max(0, flat.toLowerCase(Locale.ROOT).indexOf(word));
        int from = Math.max(0, i - 25), to = Math.min(flat.length(), from + 70);
        return (from > 0 ? "…" : "") + flat.substring(from, to) + (to < flat.length() ? "…" : "");
    }

    /** הדרך אל הפריט: שמות האבות מהשורש, מופרדים ב-‹. */
    public static String path(Item it) {
        StringBuilder b = new StringBuilder();
        for (Item x = it.parent(); x != null; x = x.parent()) b.insert(0, x.name + (b.length() > 0 ? " ‹ " : ""));
        return b.toString();
    }
}
