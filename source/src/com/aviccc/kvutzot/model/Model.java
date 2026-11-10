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
        /** טאב 1, "התחלת נושא": מה שעולה בראש — רשימות, טבלאות, כתבן. */
        public Brain start = new Brain();
        /** טאב 2, "סיעור מוחות": מפה גמישה. */
        public FreeMap free = new FreeMap();
        /**
         * הבחינות (0.9): "זוויות ההסתכלות" שלפיהן מחלקים. אובייקט בקובץ, כמו תגית, עם מקום פתוח
         * למידע (שם, מידע, תגיות — כמו פריט). כל חלוקה מצביעה על בחינה, ושמה מוצג בראש החלוקה.
         */
        public final LinkedHashMap<String, Item> aspects = new LinkedHashMap<>();

        public Item aspect(String id) { return id == null ? null : aspects.get(id); }

        public String aspectIdByName(String name) {
            for (java.util.Map.Entry<String, Item> e : aspects.entrySet()) if (e.getValue().name.equals(name)) return e.getKey();
            return null;
        }

        /** בחינה קיימת בשם הזה, או חדשה. מחזיר את המזהה. */
        public String obtainAspect(String name) {
            String id = aspectIdByName(name);
            if (id != null) return id;
            int n = aspects.size() + 1;
            while (aspects.containsKey("b" + n)) n++;
            id = "b" + n;
            aspects.put(id, new Item(name));
            return id;
        }

        /** כמה חלוקות לפי הבחינה. */
        public int aspectUse(String id) {
            int[] n = {0};
            for (Item r : roots) countAspect(r, id, n);
            return n[0];
        }

        private static void countAspect(Item it, String id, int[] n) {
            for (Division d : it.divisions) {
                if (id.equals(d.aspectId)) n[0]++;
                for (Item c : d.children) countAspect(c, id, n);
            }
        }

        /**
         * יישור החלוקות והבחינות: חלוקה עם בחינה מקבלת את שמה; חלוקה עם שם בלי בחינה — מקבלת בחינה
         * (קיימת בשם הזה, או חדשה); שתי בחינות באותו שם — מתאחדות (המידע והתגיות נאספים לראשונה).
         */
        public void syncAspects() {
            java.util.Map<String, String> merge = new java.util.HashMap<>();
            java.util.Map<String, String> firstByName = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, Item> e : new ArrayList<>(aspects.entrySet())) {
                String nm = e.getValue().name;
                String first = firstByName.get(nm);
                if (first == null) { firstByName.put(nm, e.getKey()); continue; }
                Item keep = aspects.get(first), gone = e.getValue();
                keep.infos.addAll(gone.infos);
                for (String tg : gone.tagIds) if (!keep.tagIds.contains(tg)) keep.tagIds.add(tg);
                aspects.remove(e.getKey());
                merge.put(e.getKey(), first);
            }
            for (Item r : roots) syncAspects(r, merge);
        }

        private void syncAspects(Item it, java.util.Map<String, String> merge) {
            for (Division d : it.divisions) {
                if (merge.containsKey(d.aspectId)) d.aspectId = merge.get(d.aspectId);
                Item a = aspect(d.aspectId);
                if (a != null) d.basis = a.name;
                else if (!d.basis.isEmpty()) d.aspectId = obtainAspect(d.basis);
                else d.aspectId = "";
                for (Item c : d.children) syncAspects(c, merge);
            }
        }

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
        /** הבחינה שלפיה החלוקה (0.9); ריק — חלוקה בלי בחינה. */
        public String aspectId = "";
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
