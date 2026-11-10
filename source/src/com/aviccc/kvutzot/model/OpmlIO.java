package com.aviccc.kvutzot.model;

import com.aviccc.kvutzot.model.Model.Division;
import com.aviccc.kvutzot.model.Model.Info;
import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.Model.Tag;

import java.util.Map;

/**
 * קריאה וכתיבה של קובץ הנתונים: XML בהתאמה ל-OPML.
 *
 * פריט = outline. חלוקה עם שם בסיס, או כשיש לפריט יותר מחלוקה אחת, נכתבת כ-outline
 * עם kvDivision="yes" ושם הבסיס ב-text. חלוקה יחידה בלי שם נכתבת בלי עטיפה,
 * כך שתוכנות מפות אחרות רואות עץ רגיל. מידע נלווה = kvInfo. תגיות = kvTags (מזהים).
 */
public final class OpmlIO {
    private OpmlIO() {}

    public static final String YES = "yes";

    /**
     * שורת הפתיחה של קובץ המפה (10.11.2026). Dropbox מנקה את התגים מכל קובץ שמתחיל ב-"<",
     * ולכן הקובץ מתחיל בשורת טקסט רגילה; אחריה XML תקני. מחיקת השורה נותנת OPML תקני.
     */
    public static final String HEADER = "קבוצות ותגיות · קובץ מפה";

    /** מסיר שורת פתיחה שאינה XML (אם יש), ומחזיר את ה-XML עצמו. */
    public static String stripHeader(String text) {
        String s = text;
        if (s.length() > 0 && s.charAt(0) == '\uFEFF') s = s.substring(1);
        int k = 0;
        while (k < s.length() && Character.isWhitespace(s.charAt(k))) k++;
        if (k >= s.length() || s.charAt(k) == '<') return s;
        int nl = s.indexOf('\n', k);
        return nl < 0 ? "" : s.substring(nl + 1);
    }

    // ---------------- קריאה ----------------

    public static Project read(String text) throws Xml.ParseException {
        Xml.Node root = Xml.parse(stripHeader(text));
        if (!root.name.equals("opml")) throw new Xml.ParseException("השורש אינו opml", 0);
        Project p = new Project();
        Xml.Node head = root.child("head");
        if (head != null) {
            for (Xml.Node h : head.children) {
                switch (h.name) {
                    case "title": p.title = h.text.toString().trim(); break;
                    case "kv": p.absolute = YES.equals(h.attr("absolute")); break;
                    case "kvTags":
                        for (Xml.Node tn : h.all("kvTag")) {
                            String id = tn.attr("id");
                            if (id == null || id.isEmpty() || p.tag(id) != null) continue;
                            Tag t = new Tag(id, nz(tn.attr("name")));
                            addIds(t.tagIds, tn.attr("tags"));
                            p.tags.add(t);
                        }
                        break;
                    default: p.headExtra.add(h.copy());
                }
            }
        }
        Xml.Node body = root.child("body");
        if (body != null) {
            for (Xml.Node o : body.all("outline")) {
                Item it = readItem(o, p);
                it.loose = YES.equals(o.attr("kvLoose"));
                p.roots.add(it);
            }
        }
        // מזהי תגיות שאין להם תגית מוגדרת: יוצרים תגית בשם המזהה, כדי לא לאבד מידע.
        for (Item r : p.roots) fixTags(r, p);
        return p;
    }

    private static Item readItem(Xml.Node o, Project p) {
        Item it = new Item(nz(o.attr("text")));
        for (Map.Entry<String, String> e : o.attrs.entrySet()) {
            String k = e.getKey();
            switch (k) {
                case "text": case "kvLoose": break;
                case "kvCollapsed": it.collapsed = YES.equals(e.getValue()); break;
                case "kvTags": addIds(it.tagIds, e.getValue()); break;
                default: it.extra.put(k, e.getValue());
            }
        }
        Division plain = null;
        for (Xml.Node c : o.children) {
            if (c.name.equals("outline")) {
                if (YES.equals(c.attr("kvDivision"))) {
                    Division d = it.addDivision(nz(c.attr("text")));
                    for (Map.Entry<String, String> e : c.attrs.entrySet())
                        if (!e.getKey().equals("text") && !e.getKey().equals("kvDivision"))
                            d.extra.put(e.getKey(), e.getValue());
                    for (Xml.Node cc : c.all("outline")) attach(d, readItem(cc, p));
                } else {
                    if (plain == null) {
                        plain = new Division("");
                        plain.owner = it;
                        it.divisions.add(0, plain);
                    }
                    attach(plain, readItem(c, p));
                }
            } else if (c.name.equals("kvInfo")) {
                Info.Kind k = Info.Kind.of(nz(c.attr("kind")));
                if (k == null) { it.extraNodes.add(c.copy()); continue; }
                it.infos.add(new Info(k, c.attr("name"), c.text.toString().trim()));
            } else {
                it.extraNodes.add(c.copy());
            }
        }
        return it;
    }

    private static void attach(Division d, Item child) {
        child.parentDivision = d;
        d.children.add(child);
    }

    private static void fixTags(Item it, Project p) {
        for (String id : it.tagIds) if (p.tag(id) == null) p.tags.add(new Tag(id, id));
        for (Division d : it.divisions) for (Item c : d.children) fixTags(c, p);
    }

    private static void addIds(java.util.List<String> into, String v) {
        if (v == null) return;
        for (String s : v.trim().split("\\s+")) if (!s.isEmpty() && !into.contains(s)) into.add(s);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /** String.join לא קיים באנדרואיד 5. */
    private static String join(java.util.List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) { if (b.length() > 0) b.append(' '); b.append(s); }
        return b.toString();
    }

    // ---------------- כתיבה ----------------

    /** קובץ המפה: שורת פתיחה ואחריה XML. */
    public static String write(Project p) {
        return HEADER + "\n" + writeOpml(p);
    }

    /** OPML תקני בלבד, בלי שורת פתיחה: לייצוא לתוכנות אחרות. */
    public static String writeOpml(Project p) {
        Xml.Node root = new Xml.Node("opml");
        root.attrs.put("version", "2.0");
        Xml.Node head = new Xml.Node("head");
        root.children.add(head);
        Xml.Node title = new Xml.Node("title");
        title.text.append(p.title);
        head.children.add(title);
        Xml.Node kv = new Xml.Node("kv");
        kv.attrs.put("version", "1");
        kv.attrs.put("absolute", p.absolute ? YES : "no");
        head.children.add(kv);
        if (!p.tags.isEmpty()) {
            Xml.Node tags = new Xml.Node("kvTags");
            for (Tag t : p.tags) {
                Xml.Node tn = new Xml.Node("kvTag");
                tn.attrs.put("id", t.id);
                tn.attrs.put("name", t.name);
                if (!t.tagIds.isEmpty()) tn.attrs.put("tags", join(t.tagIds));
                tags.children.add(tn);
            }
            head.children.add(tags);
        }
        for (Xml.Node h : p.headExtra) head.children.add(h.copy());
        Xml.Node body = new Xml.Node("body");
        root.children.add(body);
        for (Item r : p.roots) {
            Xml.Node o = itemNode(r);
            if (r.loose) o.attrs.put("kvLoose", YES);
            body.children.add(o);
        }
        StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        Xml.write(root, out, 0);
        return out.toString();
    }

    private static Xml.Node itemNode(Item it) {
        Xml.Node o = new Xml.Node("outline");
        o.attrs.put("text", it.name);
        if (!it.tagIds.isEmpty()) o.attrs.put("kvTags", join(it.tagIds));
        if (it.collapsed) o.attrs.put("kvCollapsed", YES);
        o.attrs.putAll(it.extra);
        for (Info in : it.infos) {
            Xml.Node n = new Xml.Node("kvInfo");
            n.attrs.put("kind", in.kind.code);
            if (in.kind == Info.Kind.USER) n.attrs.put("name", in.name);
            n.text.append(in.text);
            o.children.add(n);
        }
        for (Xml.Node x : it.extraNodes) o.children.add(x.copy());
        boolean single = it.divisions.size() == 1 && it.divisions.get(0).basis.isEmpty()
                && it.divisions.get(0).extra.isEmpty();
        for (Division d : it.divisions) {
            if (single) {
                for (Item c : d.children) o.children.add(itemNode(c));
            } else {
                Xml.Node dn = new Xml.Node("outline");
                dn.attrs.put("kvDivision", YES);
                dn.attrs.put("text", d.basis);
                dn.attrs.putAll(d.extra);
                for (Item c : d.children) dn.children.add(itemNode(c));
                o.children.add(dn);
            }
        }
        return o;
    }
}
