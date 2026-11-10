package com.aviccc.kvutzot.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** מבנה הנתונים: פרויקט, תגית, פריט, חלוקה ומידע נלווה. */
public final class Model {
    private Model() {}

    /** קובץ אחד = פרויקט אחד: מפה ראשית, פריטים תלושים ותגיות. */
    public static final class Project {
        public String title = "";
        /** הגדרת המפה: מוחלטת או לא. בגרסה 1 נשמרת ומוצגת בלבד. */
        public boolean absolute = false;
        public final List<Tag> tags = new ArrayList<>();
        /** פריטים בלי הורה: הראשון שאינו תלוש הוא המפה הראשית. */
        public final List<Item> roots = new ArrayList<>();
        /** צמתים בראש הקובץ שהתוכנה לא מכירה, נשמרים כמו שהם. */
        public final List<Xml.Node> headExtra = new ArrayList<>();

        public Tag tag(String id) {
            for (Tag t : tags) if (t.id.equals(id)) return t;
            return null;
        }

        public Tag tagByName(String name) {
            for (Tag t : tags) if (t.name.equals(name)) return t;
            return null;
        }

        /** מחזיר תגית קיימת בשם הזה, או יוצר חדשה. */
        public Tag obtainTag(String name) {
            Tag t = tagByName(name);
            if (t != null) return t;
            int n = tags.size() + 1;
            while (tag("t" + n) != null) n++;
            t = new Tag("t" + n, name);
            tags.add(t);
            return t;
        }

        public Item mainRoot() {
            for (Item r : roots) if (!r.loose) return r;
            return null;
        }

        public List<Item> looseItems() {
            List<Item> l = new ArrayList<>();
            for (Item r : roots) if (r.loose) l.add(r);
            return l;
        }
    }

    /** תגית: אובייקט אחד בפרויקט. שינוי שמה חל על כל הפריטים. */
    public static final class Tag {
        public final String id;
        public String name;
        /** תגיות על התגית (תגית על תגית), בלי הגבלה. */
        public final List<String> tagIds = new ArrayList<>();

        public Tag(String id, String name) { this.id = id; this.name = name; }
    }

    /** מידע נלווה לפריט. */
    public static final class Info {
        public enum Kind {
            /** מה שהמשתמש ממציא: שם וטקסט (למשל הגדרה, דוגמה). */
            USER("user"),
            /** תכונה עצמית = מחויבת. */
            ESSENTIAL("essential"),
            /** תכונה מקרית = שאינה מחויבת. */
            INCIDENTAL("incidental");

            public final String code;
            Kind(String code) { this.code = code; }

            public static Kind of(String code) {
                for (Kind k : values()) if (k.code.equals(code)) return k;
                return null;
            }
        }

        public Kind kind;
        /** שם התא, רק במידע שהמשתמש ממציא. */
        public String name;
        public String text;

        public Info(Kind kind, String name, String text) {
            this.kind = kind;
            this.name = name == null ? "" : name;
            this.text = text == null ? "" : text;
        }
    }

    /** חלוקה של פריט: בסיס (שם הבחינה) ובנים לפי הסדר. כמה חלוקות של אותו פריט הן צדדים. */
    public static final class Division {
        public String basis;
        public final List<Item> children = new ArrayList<>();
        public Item owner;
        public final LinkedHashMap<String, String> extra = new LinkedHashMap<>();

        public Division(String basis) { this.basis = basis == null ? "" : basis; }

        /** מספר הצד אצל הבעלים: 1 לראשון. */
        public int side() { return owner == null ? 1 : owner.divisions.indexOf(this) + 1; }

        public Item add(String name) {
            Item it = new Item(name);
            it.parentDivision = this;
            children.add(it);
            return it;
        }
    }

    /** פריט: שם, מידע נלווה, תגיות וחלוקות. */
    public static final class Item {
        public String name;
        public boolean collapsed;
        /** פריט תלוש: בלי הורה, מחוץ למפה הראשית. */
        public boolean loose;
        public final List<Info> infos = new ArrayList<>();
        public final List<String> tagIds = new ArrayList<>();
        public final List<Division> divisions = new ArrayList<>();
        public Division parentDivision;
        /** תכונות וצמתים שהתוכנה לא מכירה (למשל מתוכנת מפות אחרת), נשמרים כמו שהם. */
        public final LinkedHashMap<String, String> extra = new LinkedHashMap<>();
        public final List<Xml.Node> extraNodes = new ArrayList<>();

        public Item(String name) { this.name = name == null ? "" : name; }

        public Item parent() { return parentDivision == null ? null : parentDivision.owner; }

        public Division addDivision(String basis) {
            Division d = new Division(basis);
            d.owner = this;
            divisions.add(d);
            return d;
        }

        /** החלוקה הראשונה, נוצרת אם אין. */
        public Division firstDivision() {
            return divisions.isEmpty() ? addDivision("") : divisions.get(0);
        }

        public Item addChild(String name) { return firstDivision().add(name); }

        public int childCount() {
            int n = 0;
            for (Division d : divisions) n += d.children.size();
            return n;
        }

        public List<Info> infos(Info.Kind k) {
            List<Info> r = new ArrayList<>();
            for (Info in : infos) if (in.kind == k) r.add(in);
            return r;
        }
    }
}
