package com.aviccc.kvutzot.model;

import com.aviccc.kvutzot.model.Model.Division;
import com.aviccc.kvutzot.model.Model.Info;
import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.Model.Sheet;
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
        if (root.name.equals("kvBrainstorm")) {
            // קובץ סיעור מוחות נפרד מגרסה 0.7: נכנס לטאב "התחלת נושא" של קובץ אחד
            Project p = new Project();
            Brain b = new Brain();
            Xml.Node t = root.child("title");
            if (t != null) b.title = t.text.toString().trim();
            BrainIO.readSections(root, b);
            p.title = b.title;
            p.start.title = b.title;
            p.start.sections.addAll(b.sections);
            p.roots.add(new Item(b.title.isEmpty() ? "נושא" : b.title));
            return p;
        }
        if (!root.name.equals("opml")) throw new Xml.ParseException("השורש אינו opml", 0);
        Project p = new Project();
        Xml.Node head = root.child("head");
        Xml.Node sheetsNode = head == null ? null : head.child("kvSheets");
        Sheet bodySheet = p.first(Sheet.Kind.MAP);
        if (sheetsNode != null) {
            p.sheets.clear();
            bodySheet = null;
            for (Xml.Node sn : sheetsNode.all("kvSheet")) {
                Sheet.Kind k = Sheet.Kind.of(nz(sn.attr("kind")));
                if (k == null) continue;
                String id = nz(sn.attr("id"));
                if (id.isEmpty() || p.sheet(id) != null) id = p.newSheetId();
                Sheet s = new Sheet(k, id, nz(sn.attr("name")));
                s.note = nz(sn.attr("note"));
                s.locked = YES.equals(sn.attr("locked"));
                s.hidden = YES.equals(sn.attr("hidden"));
                if (k == Sheet.Kind.START) {
                    String ks = sn.attr("kinds");
                    if (ks != null && !ks.trim().isEmpty()) {
                        s.kinds.clear();
                        for (String c : ks.trim().split("\\s+")) { Brain.Kind bk = Brain.Kind.of(c); if (bk != null) s.kinds.add(bk); }
                        if (s.kinds.isEmpty()) s.kinds.addAll(java.util.EnumSet.allOf(Brain.Kind.class));
                    }
                    s.erase = Sheet.Erase.of(nz(sn.attr("erase")));
                    BrainIO.readSections(sn, s.brain);
                } else if (k == Sheet.Kind.FREE) {
                    s.links = !"no".equals(sn.attr("links"));
                    readFree(sn, s.free);
                } else {
                    s.absolute = YES.equals(sn.attr("absolute"));
                    s.dir = nz(sn.attr("dir"));
                    s.view = nz(sn.attr("view"));
                    s.layout = nz(sn.attr("layout"));
                    if (YES.equals(sn.attr("body")) && bodySheet == null) bodySheet = s;
                    else readRoots(sn, s.roots, p);
                }
                p.sheets.add(s);
            }
            if (bodySheet == null) bodySheet = p.first(Sheet.Kind.MAP);
            if (bodySheet == null) { bodySheet = new Sheet(Sheet.Kind.MAP, p.newSheetId(), Sheet.Kind.MAP.label); p.sheets.add(bodySheet); }
            for (Sheet.Kind k : Sheet.Kind.values()) if (p.first(k) == null) p.sheets.add(new Sheet(k, p.newSheetId(), k.label));
            for (Sheet.Kind k : Sheet.Kind.values()) p.use(p.first(k));
        }
        if (head != null) {
            for (Xml.Node h : head.children) {
                switch (h.name) {
                    case "title": p.title = h.text.toString().trim(); break;
                    case "kv": if (sheetsNode == null) bodySheet.absolute = YES.equals(h.attr("absolute")); break;
                    case "kvSheets": break;
                    case "kvStart": if (sheetsNode == null) BrainIO.readSections(h, p.start); break;
                    case "kvAspects":
                        for (Xml.Node an : h.all("kvAspect")) {
                            String id = an.attr("id");
                            if (id == null || id.isEmpty() || p.aspects.containsKey(id)) continue;
                            Item a = new Item(nz(an.attr("name")));
                            addIds(a.tagIds, an.attr("kvTags"));
                            for (Xml.Node in : an.all("kvInfo")) {
                                Info.Kind k = Info.Kind.of(nz(in.attr("kind")));
                                if (k != null) a.infos.add(new Info(k, in.attr("name"), in.text.toString().trim()));
                            }
                            p.aspects.put(id, a);
                        }
                        break;
                    case "kvFree": if (sheetsNode == null) readFree(h, p.free); break;
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
        if (body != null) readRoots(body, bodySheet.roots, p);
        for (Sheet s : p.sheets) if (s.kind == Sheet.Kind.MAP && s.roots.isEmpty()) s.roots.add(new Item(p.title.isEmpty() ? "נושא" : p.title));
        // מזהי תגיות שאין להם תגית מוגדרת: יוצרים תגית בשם המזהה, כדי לא לאבד מידע.
        for (Item r : p.allRoots()) fixTags(r, p);
        for (Item a : p.aspects.values()) for (String id : a.tagIds) if (p.tag(id) == null) p.tags.add(new Tag(id, id));
        p.syncAspects();
        return p;
    }

    private static void readRoots(Xml.Node parent, java.util.List<Item> into, Project p) {
        for (Xml.Node o : parent.all("outline")) {
            Item it = readItem(o, p);
            it.loose = YES.equals(o.attr("kvLoose"));
            into.add(it);
        }
    }

    private static void readFree(Xml.Node h, FreeMap f) {
        for (Xml.Node n : h.all("node"))
            f.addWithId(n.attr("id"), n.attr("text"), num(n.attr("x")), num(n.attr("y")));
        for (Xml.Node l : h.all("link")) {
            FreeMap.Node a1 = f.node(nz(l.attr("a"))), b1 = f.node(nz(l.attr("b")));
            f.link(a1, b1);
        }
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
                    d.aspectId = nz(c.attr("kvAspect"));
                    for (Map.Entry<String, String> e : c.attrs.entrySet())
                        if (!e.getKey().equals("text") && !e.getKey().equals("kvDivision") && !e.getKey().equals("kvAspect"))
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

    private static float num(String s) {
        try { return s == null ? 0 : Float.parseFloat(s); } catch (NumberFormatException e) { return 0; }
    }

    private static String fmt(float f) {
        return f == Math.round(f) ? String.valueOf(Math.round(f)) : String.valueOf(Math.round(f * 10) / 10f);
    }

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
        p.syncAspects();
        Xml.Node root = new Xml.Node("opml");
        root.attrs.put("version", "2.0");
        Xml.Node head = new Xml.Node("head");
        root.children.add(head);
        Xml.Node title = new Xml.Node("title");
        title.text.append(p.title);
        head.children.add(title);
        Sheet bodySheet = p.first(Sheet.Kind.MAP);
        Xml.Node kv = new Xml.Node("kv");
        kv.attrs.put("version", "2");
        kv.attrs.put("absolute", bodySheet != null && bodySheet.absolute ? YES : "no");
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
        if (!p.aspects.isEmpty()) {
            Xml.Node as = new Xml.Node("kvAspects");
            for (Map.Entry<String, Item> e : p.aspects.entrySet()) {
                Xml.Node an = new Xml.Node("kvAspect");
                an.attrs.put("id", e.getKey());
                an.attrs.put("name", e.getValue().name);
                if (!e.getValue().tagIds.isEmpty()) an.attrs.put("kvTags", join(e.getValue().tagIds));
                for (Info in : e.getValue().infos) {
                    Xml.Node n = new Xml.Node("kvInfo");
                    n.attrs.put("kind", in.kind.code);
                    if (in.kind == Info.Kind.USER) n.attrs.put("name", in.name);
                    n.text.append(in.text);
                    an.children.add(n);
                }
                as.children.add(an);
            }
            head.children.add(as);
        }
        // הטאבים: כל אחד עם המאפיינים והתוכן שלו; המפה הראשונה — בגוף הקובץ (OPML רגיל)
        Xml.Node sh = new Xml.Node("kvSheets");
        for (Sheet s : p.sheets) {
            Xml.Node sn = new Xml.Node("kvSheet");
            sn.attrs.put("id", s.id);
            sn.attrs.put("kind", s.kind.code);
            sn.attrs.put("name", s.name);
            if (!s.note.isEmpty()) sn.attrs.put("note", s.note);
            if (s.locked) sn.attrs.put("locked", YES);
            if (s.hidden) sn.attrs.put("hidden", YES);
            if (s.kind == Sheet.Kind.START) {
                if (s.kinds.size() < Brain.Kind.values().length) {
                    java.util.List<String> ks = new java.util.ArrayList<>();
                    for (Brain.Kind bk : s.kinds) ks.add(bk.code);
                    sn.attrs.put("kinds", join(ks));
                }
                if (s.erase != Sheet.Erase.BOTH) sn.attrs.put("erase", s.erase.code);
                BrainIO.writeSections(s.brain, sn);
            } else if (s.kind == Sheet.Kind.FREE) {
                if (!s.links) sn.attrs.put("links", "no");
                writeFree(s.free, sn);
            } else {
                if (s.absolute) sn.attrs.put("absolute", YES);
                if (!s.dir.isEmpty()) sn.attrs.put("dir", s.dir);
                if (!s.view.isEmpty()) sn.attrs.put("view", s.view);
                if (!s.layout.isEmpty()) sn.attrs.put("layout", s.layout);
                if (s == bodySheet) sn.attrs.put("body", YES);
                else writeRoots(s.roots, sn);
            }
            sh.children.add(sn);
        }
        head.children.add(sh);
        for (Xml.Node h : p.headExtra) head.children.add(h.copy());
        Xml.Node body = new Xml.Node("body");
        root.children.add(body);
        if (bodySheet != null) writeRoots(bodySheet.roots, body);
        StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        Xml.write(root, out, 0);
        return out.toString();
    }

    private static void writeRoots(java.util.List<Item> roots, Xml.Node into) {
        for (Item r : roots) {
            Xml.Node o = itemNode(r);
            if (r.loose) o.attrs.put("kvLoose", YES);
            into.children.add(o);
        }
    }

    private static void writeFree(FreeMap f, Xml.Node fr) {
        for (FreeMap.Node n : f.nodes) {
            Xml.Node x = new Xml.Node("node");
            x.attrs.put("id", n.id);
            x.attrs.put("x", fmt(n.x));
            x.attrs.put("y", fmt(n.y));
            x.attrs.put("text", n.text);
            fr.children.add(x);
        }
        for (FreeMap.Link l : f.links) {
            Xml.Node x = new Xml.Node("link");
            x.attrs.put("a", l.a);
            x.attrs.put("b", l.b);
            fr.children.add(x);
        }
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
                if (!d.aspectId.isEmpty()) dn.attrs.put("kvAspect", d.aspectId);
                dn.attrs.putAll(d.extra);
                for (Item c : d.children) dn.children.add(itemNode(c));
                o.children.add(dn);
            }
        }
        return o;
    }
}
