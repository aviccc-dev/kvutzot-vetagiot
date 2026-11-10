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
        public final List<Tag> tags = new ArrayList<>();
        /**
         * הטאבים שבקובץ (0.10): כמה מכל סוג — "התהליך עשוי להיות מסורבל ולהצריך כמה טיוטות
         * לכיווני חשיבה". לכל טאב שם, הערה ומאפיינים.
         */
        public final List<Sheet> sheets = new ArrayList<>();
        /** פריטים בלי הורה של המפה הפעילה: הראשון שאינו תלוש הוא ראש המפה. */
        public List<Item> roots;
        /** צמתים בראש הקובץ שהתוכנה לא מכירה, נשמרים כמו שהם. */
        public final List<Xml.Node> headExtra = new ArrayList<>();
        /** "התחלת נושא" הפעילה: מה שעולה בראש — רשימות, טבלאות, כתבן. */
        public Brain start;
        /** "סיעור מוחות" הפעיל: מפה גמישה. */
        public FreeMap free;

        /** קובץ חדש: שלושה טאבים — התחלת נושא, סיעור מוחות, מפה. */
        public Project() {
            sheets.add(new Sheet(Sheet.Kind.START, "s1", Sheet.Kind.START.label));
            sheets.add(new Sheet(Sheet.Kind.FREE, "s2", Sheet.Kind.FREE.label));
            sheets.add(new Sheet(Sheet.Kind.MAP, "s3", Sheet.Kind.MAP.label));
            for (Sheet s : sheets) use(s);
        }

        /** הופך את הטאב לפעיל מסוגו (המפה / הסיעור / ההתחלה שהפעולות חלות עליהם). */
        public void use(Sheet s) {
            if (s.kind == Sheet.Kind.MAP) roots = s.roots;
            else if (s.kind == Sheet.Kind.START) start = s.brain;
            else free = s.free;
        }

        /** הטאב הפעיל מהסוג הזה. */
        public Sheet active(Sheet.Kind k) {
            for (Sheet s : sheets) if (s.kind == k && (k == Sheet.Kind.MAP ? s.roots == roots : k == Sheet.Kind.START ? s.brain == start : s.free == free)) return s;
            return first(k);
        }

        public Sheet first(Sheet.Kind k) {
            for (Sheet s : sheets) if (s.kind == k) return s;
            return null;
        }

        public Sheet sheet(String id) {
            for (Sheet s : sheets) if (s.id.equals(id)) return s;
            return null;
        }

        public String newSheetId() {
            int n = sheets.size() + 1;
            while (sheet("s" + n) != null) n++;
            return "s" + n;
        }

        /** כל ראשי המפות שבקובץ (לתגיות ולבחינות המשותפות). */
        public List<Item> allRoots() {
            List<Item> l = new ArrayList<>();
            for (Sheet s : sheets) if (s.kind == Sheet.Kind.MAP) l.addAll(s.roots);
            return l;
        }

        /** המפה הפעילה מסודרת בסדר מוחלט (מאפיין של הטאב). */
        public boolean absolute() {
            Sheet s = active(Sheet.Kind.MAP);
            return s != null && s.absolute;
        }
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
            for (Item r : allRoots()) countAspect(r, id, n);
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
            for (Item r : allRoots()) syncAspects(r, merge);
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

    /**
     * טאב בתוך הקובץ (0.10), עם המאפיינים שלו. התוכן לפי הסוג: מפה — עץ; סיעור מוחות — מפה גמישה;
     * התחלת נושא — חלקים (כתבן, גיליון, פנקס).
     */
    public static final class Sheet {
        public enum Kind {
            START("start", "התחלת נושא"), FREE("free", "סיעור מוחות"), MAP("map", "מפה");
            public final String code, label;
            Kind(String code, String label) { this.code = code; this.label = label; }
            public static Kind of(String c) {
                for (Kind k : values()) if (k.code.equals(c)) return k;
                return null;
            }
        }

        /** מחיקה בפנקס: גם פס וגם מחיקה, רק פס (הכול נשאר), או רק מחיקה. */
        public enum Erase {
            BOTH("both", "פס ומחיקה"), STRIKE("strike", "רק פס — הכול נשאר"), DELETE("delete", "רק מחיקה");
            public final String code, label;
            Erase(String code, String label) { this.code = code; this.label = label; }
            public static Erase of(String c) {
                for (Erase e : values()) if (e.code.equals(c)) return e;
                return BOTH;
            }
        }

        public final Kind kind;
        public final String id;
        public String name;
        /** הערה קצרה: למה הטאב משמש. */
        public String note = "";
        /** נעול: רק קוראים. */
        public boolean locked;
        /** מוסתר משורת הטאבים (נשאר בקובץ). */
        public boolean hidden;

        // התחלת נושא
        public Brain brain;
        /** סוגי החלקים המותרים (ריק = כולם). */
        public final java.util.Set<Brain.Kind> kinds = java.util.EnumSet.allOf(Brain.Kind.class);
        public Erase erase = Erase.BOTH;

        // סיעור מוחות
        public FreeMap free;
        /** מותר לחבר בקווים. */
        public boolean links = true;

        // מפה
        public List<Item> roots;
        /** סדר מוחלט: בלי פריטים תלושים — לכל פריט מקום בעץ. */
        public boolean absolute;
        /** כיוון: "" — לפי התוכנה, "v" — מלמעלה למטה, "h" — מימין לשמאל. */
        public String dir = "";
        /** תצוגה: "" — לפי התוכנה, "map" — גרפית, "text" — טקסט. */
        public String view = "";
        /** פריסה: "" — צפופה, כמו מפת חשיבה (0.10.2); "tidy" — מרווחת, כל ענף מעל התחום שלו. */
        public String layout = "";

        public Sheet(Kind kind, String id, String name) {
            this.kind = kind;
            this.id = id;
            this.name = name == null ? "" : name;
            if (kind == Kind.START) brain = new Brain();
            else if (kind == Kind.FREE) free = new FreeMap();
            else roots = new ArrayList<>();
        }
    }
}
