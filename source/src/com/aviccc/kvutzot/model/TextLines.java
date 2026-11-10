package com.aviccc.kvutzot.model;

import com.aviccc.kvutzot.model.Model.Division;
import com.aviccc.kvutzot.model.Model.Info;
import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.Model.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * תצוגת הטקסט: הופכת את הפרויקט לרשימת שורות עם עומק, סימון ומידע.
 * בלי אנדרואיד, כדי שאפשר לבדוק אותה. העיצוב (גודל, צבע, הזחה) נעשה במסך.
 *
 * סימון זמני (10.11.2026): מספר העומק, ואות הצד כשלאב יש יותר מחלוקה אחת.
 * המספור הקטלוגי המלא עוד לא הוחלט.
 */
public final class TextLines {
    private TextLines() {}

    public enum Kind { ITEM, DIVISION, HEADER }

    public static final class Line {
        public final Kind kind;
        public final int depth;
        public final String mark;
        public final String text;
        public final String info;
        /** בפריט מכווץ: כמה צאצאים מוסתרים. */
        public final int hidden;
        public final Item item;

        Line(Kind kind, int depth, String mark, String text, String info, int hidden, Item item) {
            this.kind = kind; this.depth = depth; this.mark = mark; this.text = text;
            this.info = info; this.hidden = hidden; this.item = item;
        }
    }

    public static List<Line> build(Project p) {
        List<Line> out = new ArrayList<>();
        for (Item r : p.roots) if (!r.loose) item(p, r, 0, "0", out);
        List<Item> loose = p.looseItems();
        if (!loose.isEmpty()) {
            out.add(new Line(Kind.HEADER, 0, "", "תלושים (" + loose.size() + ")", "", 0, null));
            for (Item r : loose) item(p, r, 0, "תלוש", out);
        }
        return out;
    }

    private static void item(Project p, Item it, int depth, String mark, List<Line> out) {
        int hidden = it.collapsed ? descendants(it) : 0;
        out.add(new Line(Kind.ITEM, depth, mark, it.name, info(p, it), hidden, it));
        if (it.collapsed) return;
        boolean sides = it.divisions.size() > 1;
        for (Division d : it.divisions) {
            String letter = sides ? Hebrew.number(d.side()) : "";
            if (sides || !d.basis.isEmpty())
                out.add(new Line(Kind.DIVISION, depth + 1, letter,
                        d.basis.isEmpty() ? "(חלוקה בלי שם)" : d.basis, "", 0, it));
            String childMark = (depth + 1) + (sides ? " " + letter : "");
            for (Item c : d.children) item(p, c, depth + 1, childMark, out);
        }
    }

    public static int descendants(Item it) {
        int n = 0;
        for (Division d : it.divisions) for (Item c : d.children) n += 1 + descendants(c);
        return n;
    }

    /** כל הפריטים בפרויקט, כולל תלושים וצאצאיהם. */
    public static int countItems(Project p) {
        int n = 0;
        for (Item r : p.roots) n += 1 + descendants(r);
        return n;
    }

    /** המידע הנלווה בשורה אחת, לסוגריים. */
    public static String info(Project p, Item it) {
        StringBuilder b = new StringBuilder();
        for (Info in : it.infos) {
            String label;
            switch (in.kind) {
                case ESSENTIAL: label = "עצמית"; break;
                case INCIDENTAL: label = "מקרית"; break;
                default: label = in.name.isEmpty() ? "מידע" : in.name;
            }
            sep(b).append(label).append(": ").append(flat(in.text));
        }
        if (!it.tagIds.isEmpty()) {
            sep(b).append("תגיות: ");
            boolean first = true;
            for (String id : it.tagIds) {
                Tag t = p.tag(id);
                if (!first) b.append(", ");
                b.append(t == null ? id : t.name);
                first = false;
            }
        }
        return b.toString();
    }

    private static StringBuilder sep(StringBuilder b) {
        if (b.length() > 0) b.append(" · ");
        return b;
    }

    private static String flat(String s) { return s.replaceAll("\\s+", " ").trim(); }
}
