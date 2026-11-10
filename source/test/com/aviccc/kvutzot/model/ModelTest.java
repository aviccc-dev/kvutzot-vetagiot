package com.aviccc.kvutzot.model;

import com.aviccc.kvutzot.model.Model.Division;
import com.aviccc.kvutzot.model.Model.Info;
import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;
import com.aviccc.kvutzot.model.Model.Tag;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** בדיקות מבנה הנתונים והקובץ. מריצים: java ModelTest <תיקיית הדוגמאות>. */
public class ModelTest {
    static int pass, fail;

    static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("נכשל: " + what); }
    }

    static void eq(Object a, Object b, String what) {
        boolean ok = a == null ? b == null : a.equals(b);
        if (!ok) what += " — צפוי [" + b + "] התקבל [" + a + "]";
        check(ok, what);
    }

    static String file(String dir, String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get(dir, name)), StandardCharsets.UTF_8);
    }

    /** טעינה, שמירה, טעינה ושמירה שוב: שתי השמירות זהות. */
    static Project roundTrip(String xml, String what) throws Exception {
        Project p = OpmlIO.read(xml);
        String w1 = OpmlIO.write(p);
        String w2 = OpmlIO.write(OpmlIO.read(w1));
        eq(w2, w1, what + ": שמירה חוזרת זהה");
        return OpmlIO.read(w1);
    }

    public static void main(String[] a) throws Exception {
        String dir = a.length > 0 ? a[0] : "examples";

        // --- המשולש ---
        Project p = roundTrip(file(dir, "משולש.xml"), "משולש");
        eq(p.title, "משולש", "כותרת");
        check(!p.absolute, "המפה לא מוחלטת");
        Item tri = p.mainRoot();
        eq(tri.name, "משולש", "שם השורש");
        eq(tri.divisions.size(), 2, "שתי חלוקות במקביל");
        eq(tri.divisions.get(0).basis, "לפי הצלעות", "בסיס חלוקה א");
        eq(tri.divisions.get(1).side(), 2, "מספר צד של החלוקה השנייה");
        eq(tri.childCount(), 6, "שישה בנים");
        eq(tri.infos(Info.Kind.USER).size(), 5, "מידע שהמשתמש ממציא");
        eq(tri.infos(Info.Kind.ESSENTIAL).size(), 3, "תכונות עצמיות");
        eq(tri.infos(Info.Kind.INCIDENTAL).size(), 5, "תכונות מקריות");
        eq(tri.tagIds.size(), 5, "תגיות על המשולש");
        eq(p.looseItems().size(), 5, "פריטים תלושים");
        Item eqSides = tri.divisions.get(0).children.get(0);
        eq(eqSides.parent(), tri, "הורה של שווה צלעות");
        eq(eqSides.parentDivision.side(), 1, "שווה צלעות בצד א");
        eq(eqSides.infos.get(0).text, "במקור: \"שווה בכל חלקיו\", אולי אותו דבר", "טקסט עם מרכאות");

        // --- צעצועים: צד בתוך צד ---
        Project toys = roundTrip(file(dir, "צעצועים.xml"), "צעצועים");
        Item plastic = toys.mainRoot().divisions.get(1).children.get(0);
        eq(plastic.name, "פלסטיק", "פלסטיק בצד ב");
        eq(plastic.divisions.size(), 2, "לפלסטיק שני צדדים");
        eq(plastic.divisions.get(1).children.get(0).name, "ממוחזר", "בן בצד ב של פלסטיק");

        // --- חלוקה יחידה בלי שם נכתבת בלי עטיפה ---
        Project s = new Project();
        Item r = new Item("שורש");
        s.roots.add(r);
        r.addChild("בן 1").addChild("נכד");
        r.addChild("בן 2");
        String w = OpmlIO.write(s);
        check(!w.contains("kvDivision"), "חלוקה יחידה בלי עטיפה");
        Project s2 = roundTrip(w, "עץ פשוט");
        eq(s2.mainRoot().childCount(), 2, "שני בנים");
        eq(s2.mainRoot().divisions.get(0).children.get(0).divisions.get(0).children.get(0).name, "נכד", "נכד");

        // --- צד ריק נשמר, כדי שמספור הצדדים לא ישתנה ---
        Project e = new Project();
        Item er = new Item("א");
        e.roots.add(er);
        er.addDivision("");
        er.addDivision("שני").add("x");
        Project e2 = roundTrip(OpmlIO.write(e), "צד ריק");
        eq(e2.mainRoot().divisions.size(), 2, "צד ריק נשמר");
        eq(e2.mainRoot().divisions.get(1).children.get(0).parentDivision.side(), 2, "x בצד ב");

        // --- תווים מיוחדים וטקסט בכמה שורות ---
        Project sp = new Project();
        sp.title = "בדיקה <&> \"מרכאות\"";
        Item si = new Item("a < b & c > d \"q\" 'x'");
        si.infos.add(new Info(Info.Kind.USER, "שם \"עם\" <סימנים>", "שורה 1\nשורה 2\n\tהזחה"));
        si.collapsed = true;
        sp.roots.add(si);
        Project sp2 = roundTrip(OpmlIO.write(sp), "תווים מיוחדים");
        eq(sp2.title, sp.title, "כותרת עם סימנים");
        eq(sp2.mainRoot().name, si.name, "שם עם סימנים");
        eq(sp2.mainRoot().infos.get(0).name, "שם \"עם\" <סימנים>", "שם מידע עם סימנים");
        eq(sp2.mainRoot().infos.get(0).text, "שורה 1\nשורה 2\n\tהזחה", "טקסט בכמה שורות");
        check(sp2.mainRoot().collapsed, "מצב כיווץ נשמר");

        // --- תגיות: אובייקט אחד, שינוי שם חל על הכול; תגית על תגית ---
        Project tp = new Project();
        Item t1 = new Item("x"), t2 = new Item("y");
        tp.roots.add(t1);
        tp.roots.add(t2);
        t2.loose = true;
        Tag g = tp.obtainTag("גאומטריה");
        Tag imp = tp.obtainTag("חשוב");
        g.tagIds.add(imp.id);
        t1.tagIds.add(g.id);
        t2.tagIds.add(g.id);
        eq(tp.obtainTag("גאומטריה"), g, "אותה תגית לא נוצרת פעמיים");
        g.name = "הנדסה";
        Project tp2 = roundTrip(OpmlIO.write(tp), "תגיות");
        eq(tp2.tag(tp2.roots.get(0).tagIds.get(0)).name, "הנדסה", "שינוי שם תגית בפריט 1");
        eq(tp2.tag(tp2.roots.get(1).tagIds.get(0)).name, "הנדסה", "שינוי שם תגית בפריט 2");
        eq(tp2.tag(tp2.tagByName("הנדסה").tagIds.get(0)).name, "חשוב", "תגית על תגית");
        check(tp2.roots.get(1).loose, "פריט תלוש נשמר");

        // --- קובץ OPML מתוכנה אחרת: מה שלא מוכר נשמר ---
        String foreign = "<?xml version=\"1.0\"?>\n<!-- הערה -->\n<opml version=\"2.0\"><head><title>זר</title>"
                + "<dateCreated>today</dateCreated></head><body>"
                + "<outline text=\"שורש\" _note=\"פתק\" color=\"red\"><outline text=\"בן\"/>"
                + "<outline text=\"&#1488;&amp;&#x5D1;\"/><![CDATA[x]]></outline></body></opml>";
        Project f = OpmlIO.read(foreign);
        eq(f.mainRoot().childCount(), 2, "בנים מקובץ זר");
        eq(f.mainRoot().extra.get("_note"), "פתק", "תכונה זרה נשמרת");
        eq(f.mainRoot().divisions.get(0).children.get(1).name, "א&ב", "ישויות מספריות");
        String fw = OpmlIO.write(f);
        check(fw.contains("dateCreated"), "צומת זר בראש נשמר");
        check(fw.contains("color=\"red\""), "תכונה זרה נכתבת");
        roundTrip(fw, "קובץ זר");

        // --- קבצים שבורים: הודעה ולא קריסה ---
        String[] bad = {"", "<opml>", "<opml><head></opml>", "<x/>", "<opml a=b/>", "<opml>&zz;</opml>", "<opml/>extra"};
        for (String b : bad) {
            boolean thrown = false;
            try { OpmlIO.read(b); } catch (Xml.ParseException ex) { thrown = true; }
            check(thrown, "קובץ שבור נדחה: [" + b + "]");
        }
        // BOM בתחילת הקובץ
        eq(OpmlIO.read("﻿<opml><body><outline text=\"a\"/></body></opml>").mainRoot().name, "a", "BOM");

        // --- מספור עברי ---
        int[] n = {1, 2, 9, 10, 11, 15, 16, 19, 20, 22, 100, 123, 400, 500};
        String[] h = {"א", "ב", "ט", "י", "י\"א", "ט\"ו", "ט\"ז", "י\"ט", "כ", "כ\"ב", "ק", "קכ\"ג", "ת", "ת\"ק"};
        for (int k = 0; k < n.length; k++) eq(Hebrew.number(n[k]), h[k], "מספר עברי " + n[k]);

        System.out.println("עברו " + pass + ", נכשלו " + fail);
        if (fail > 0) System.exit(1);
    }
}
