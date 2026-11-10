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

    static int grown = 0;

    static void grow(Item it, int depth, java.util.Random r) {
        if (depth >= 4 || grown > 120) return;
        int sides = r.nextInt(5) == 0 ? 2 : 1;
        for (int s = 0; s < sides; s++) {
            Division d = it.addDivision(sides > 1 ? "בחינה " + (s + 1) : "");
            int n = 1 + r.nextInt(4);
            for (int i = 0; i < n; i++) {
                StringBuilder name = new StringBuilder("פריט");
                for (int k = r.nextInt(6); k > 0; k--) name.append(" מילה");
                Item c = d.add(name.toString());
                grown++;
                if (r.nextInt(7) == 0) c.collapsed = true;
                grow(c, depth + 1, r);
            }
        }
    }

    static boolean noOverlap(MapLayout ml) {
        for (int i = 0; i < ml.nodes.size(); i++)
            for (int j = i + 1; j < ml.nodes.size(); j++) {
                MapLayout.Node a = ml.nodes.get(i), b = ml.nodes.get(j);
                boolean sep = a.x + a.w <= b.x + 0.01 || b.x + b.w <= a.x + 0.01
                        || a.y + a.h <= b.y + 0.01 || b.y + b.h <= a.y + 0.01;
                if (!sep) {
                    System.out.println("חפיפה: [" + a.text + "] [" + b.text + "]");
                    return false;
                }
            }
        return true;
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

        // --- תצוגת הטקסט ---
        java.util.List<TextLines.Line> L = TextLines.build(p);
        eq(L.get(0).mark, "0", "שורש מסומן 0");
        eq(L.get(0).text, "משולש", "שורה ראשונה");
        check(L.get(0).info.startsWith("הגדרה: שטח סגור"), "מידע בשורת השורש");
        check(L.get(0).info.contains("תגיות: גאומטריה, צורות"), "שמות תגיות במידע");
        eq(L.get(1).kind, TextLines.Kind.DIVISION, "שורת חלוקה");
        eq(L.get(1).mark, "א", "חלוקה א");
        eq(L.get(1).text, "לפי הצלעות", "בסיס בשורת החלוקה");
        eq(L.get(2).mark, "1 א", "שווה צלעות: עומק 1 צד א");
        eq(L.get(2).depth, 1, "עומק שווה צלעות");
        eq(L.get(5).mark, "ב", "חלוקה ב");
        eq(L.get(6).mark, "1 ב", "ישר זוית: עומק 1 צד ב");
        eq(L.get(9).kind, TextLines.Kind.HEADER, "כותרת תלושים");
        eq(L.size(), 15, "מספר שורות במשולש");
        eq(TextLines.countItems(p), 12, "מספר פריטים במשולש");
        java.util.List<TextLines.Line> T = TextLines.build(toys);
        TextLines.Line hard = null;
        for (TextLines.Line l : T) if (l.text.equals("קשיח")) hard = l;
        eq(hard.mark, "2 א", "קשיח: עומק 2 צד א של פלסטיק");
        java.util.List<TextLines.Line> S = TextLines.build(s2);
        eq(S.size(), 4, "עץ פשוט בלי שורות חלוקה");
        eq(S.get(1).mark, "1", "בלי אות כשיש חלוקה אחת");
        s2.mainRoot().collapsed = true;
        S = TextLines.build(s2);
        eq(S.size(), 1, "מכווץ: רק השורש");
        eq(S.get(0).hidden, 3, "מכווץ: שלושה מוסתרים");

        // --- שורת פתיחה ---
        String full = OpmlIO.write(p);
        check(full.startsWith(OpmlIO.HEADER + "\n<?xml"), "הקובץ מתחיל בשורת פתיחה ואחריה XML");
        String opml = OpmlIO.writeOpml(p);
        check(opml.startsWith("<?xml"), "ייצוא OPML בלי שורת פתיחה");
        eq(full, OpmlIO.HEADER + "\n" + opml, "ההבדל הוא רק שורת הפתיחה");
        eq(OpmlIO.write(OpmlIO.read(opml)), full, "קריאת OPML בלי שורת פתיחה");
        eq(OpmlIO.write(OpmlIO.read("\uFEFF" + full)), full, "שורת פתיחה עם BOM");
        eq(OpmlIO.write(OpmlIO.read("כל טקסט אחר\r\n" + opml)), full, "שורת פתיחה אחרת ו-CRLF");
        eq(OpmlIO.write(OpmlIO.read("\n\n  " + opml)), full, "רווחים לפני ה-XML");
        boolean thrownH = false;
        try { OpmlIO.read("רק שורת פתיחה"); } catch (Xml.ParseException ex) { thrownH = true; }
        check(thrownH, "שורת פתיחה בלי XML נדחית");

        // --- פריסת המפה ---
        MapLayout.Measurer fake = new MapLayout.Measurer() {
            public float[] measure(MapLayout.Node n) {
                float w = Math.min(160, 12 + n.text.length() * 9);
                float lines = (float) Math.ceil(n.text.length() * 9 / 148.0);
                return new float[]{w, n.isDivision() ? 22 : 14 + 20 * Math.max(1, lines)};
            }
        };
        java.util.Random rnd = new java.util.Random(7);
        Project big = new Project();
        Item bigRoot = new Item("שורש גדול");
        big.roots.add(bigRoot);
        grow(bigRoot, 0, rnd);
        big.roots.add(new Item("תלוש ארוך מאוד מאוד מאוד מאוד"));
        big.roots.get(1).loose = true;
        Project[] ps = {p, toys, big, s2};
        String[] names = {"משולש", "צעצועים", "עץ אקראי", "מכווץ"};
        for (int k = 0; k < ps.length; k++) {
            for (boolean vert : new boolean[]{true, false}) {
                MapLayout ml = MapLayout.build(ps[k], vert, fake, 30, 12);
                String wn = names[k] + (vert ? " למטה" : " לשמאל");
                check(noOverlap(ml), wn + ": אין חפיפות");
                boolean inside = true;
                for (MapLayout.Node n : ml.nodes)
                    if (n.x < -0.01 || n.y < -0.01 || n.x + n.w > ml.width + 0.01 || n.y + n.h > ml.height + 0.01) inside = false;
                check(inside, wn + ": הכול בתוך הגבולות");
                boolean down = true;
                for (MapLayout.Node n : ml.nodes) for (MapLayout.Node c : n.children) {
                    if (vert && !(c.y >= n.y + n.h)) down = false;
                    if (!vert && !(c.x + c.w <= n.x)) down = false;
                }
                check(down, wn + ": בנים מתחת/משמאל לאב");
                boolean order = true;
                for (MapLayout.Node n : ml.nodes) for (int i = 1; i < n.children.size(); i++) {
                    MapLayout.Node a1 = n.children.get(i - 1), a2 = n.children.get(i);
                    if (vert && !(a1.x > a2.x)) order = false;
                    if (!vert && !(a1.y < a2.y)) order = false;
                }
                check(order, wn + ": הבן הראשון מימין/למעלה");
            }
        }
        MapLayout tl = MapLayout.build(p, true, fake, 30, 12);
        eq(tl.roots.size(), 6, "שורש ראשי וחמישה תלושים");
        eq(tl.roots.get(0).children.size(), 2, "שתי תוויות חלוקה מתחת למשולש");
        check(tl.roots.get(0).children.get(0).isDivision(), "תווית חלוקה");
        eq(tl.roots.get(0).children.get(1).mark, "ב", "תווית צד ב");
        eq(tl.roots.get(0).children.get(0).children.get(0).hue, 1, "גוון ענף ראשון");
        eq(tl.roots.get(0).children.get(1).children.get(2).hue, 6, "גוון ענף שישי");
        eq(tl.roots.get(0).hue, 0, "שורש אפור");
        eq(tl.roots.get(0).children.get(0).children.get(0).depth, 1, "עומק בלי לספור תוויות");

        // --- עריכה ---
        Project ed = OpmlIO.read(file(dir, "משולש.xml"));
        Item edRoot = ed.mainRoot();
        Item right = edRoot.divisions.get(1).children.get(0);
        Item sib = Edit.addSibling(ed, right, "אח חדש");
        eq(edRoot.divisions.get(1).children.indexOf(sib), 1, "אח מיד אחרי");
        check(Edit.addSibling(ed, edRoot, "x") == null, "לשורש אין אחים");
        Item loose1 = ed.looseItems().get(0);
        Item looseSib = Edit.addSibling(ed, loose1, "תלוש חדש");
        check(looseSib.loose && ed.roots.indexOf(looseSib) == ed.roots.indexOf(loose1) + 1, "אח לתלוש הוא תלוש אחריו");
        Item kid = Edit.addChild(right, null, "בן חדש");
        eq(right.childCount(), 1, "בן חדש");
        Division side = Edit.addSide(right, "לפי משהו");
        eq(right.divisions.size(), 2, "צד חדש ליד הבנים הקיימים");
        eq(side.side(), 2, "הצד החדש הוא ב");
        Item leaf = right.divisions.get(1).add("עלה");
        Division s1 = Edit.addSide(leaf, "לפי כלום");
        eq(leaf.divisions.size(), 1, "צד ראשון בפריט בלי בנים");
        eq(s1.basis, "לפי כלום", "שם הצד הראשון");
        Edit.addChild(right, side, "בן בצד ב");
        eq(side.children.size(), 2, "הוספה לצד שנבחר");
        Item clip = Edit.copy(edRoot.divisions.get(0).children.get(0));
        Item pasted = Edit.paste(clip, kid, null);
        eq(pasted.name, "שווה צלעות", "הדבקה");
        eq(pasted.infos.size(), 2, "מידע מועתק");
        check(pasted != clip && pasted.parent() == kid, "מודבק עותק תחת היעד");
        pasted.infos.get(0).text = "שונה";
        check(!edRoot.divisions.get(0).children.get(0).infos.get(0).text.equals("שונה"), "העותק נפרד מהמקור");
        check(Edit.within(pasted, right) && !Edit.within(right, pasted), "בדיקת צאצא");
        check(!Edit.delete(ed, edRoot), "את המפה הראשית לא מוחקים");
        int before = TextLines.countItems(ed);
        check(Edit.delete(ed, kid), "מחיקת ענף");
        eq(TextLines.countItems(ed), before - 2, "נמחקו הבן והמודבק");
        check(Edit.move(sib, -1) && edRoot.divisions.get(1).children.indexOf(sib) == 0, "הזזה למעלה");
        check(!Edit.move(sib, -1), "אין לאן להזיז");
        check(Edit.deleteDivision(side), "מחיקת צד");
        eq(right.divisions.size(), 1, "נשאר צד אחד");
        Project ed2 = roundTrip(OpmlIO.write(ed), "אחרי עריכה");
        eq(TextLines.countItems(ed2), TextLines.countItems(ed), "אחרי עריכה: אותו מספר פריטים");
        check(noOverlap(MapLayout.build(ed2, true, fake, 30, 12)), "אחרי עריכה: אין חפיפות");

        // --- מידע ותגיות ---
        Project ip = OpmlIO.read(file(dir, "משולש.xml"));
        Item iRoot = ip.mainRoot();
        Item iKid = iRoot.divisions.get(0).children.get(1);
        Model.Tag geo = Edit.addTag(ip, iKid, "גאומטריה");
        eq(geo.id, "t1", "תגית קיימת לפי שם");
        Edit.addTag(ip, iKid, "גאומטריה");
        eq(iKid.tagIds.size(), 1, "אותה תגית לא נוספת פעמיים");
        Model.Tag fresh = Edit.addTag(ip, iKid, "  חדשה ");
        eq(fresh.name, "חדשה", "תגית חדשה בלי רווחים");
        eq(Edit.tagUse(ip, geo.id), 2, "שימוש בתגית");
        Edit.renameTag(ip, geo, "הנדסה");
        eq(ip.tag(iRoot.tagIds.get(0)).name, "הנדסה", "שינוי שם חל על כולם");
        Model.Tag kept = Edit.renameTag(ip, fresh, "הנדסה");
        eq(kept, geo, "שינוי לשם קיים מאחד");
        check(ip.tag(fresh.id) == null, "התגית שאוחדה נמחקה");
        eq(iKid.tagIds.size(), 1, "בלי כפילות אחרי איחוד");
        Edit.removeTag(iKid, geo.id);
        eq(Edit.tagUse(ip, geo.id), 1, "הסרה מפריט אחד");
        check(Edit.userInfoNames(ip).contains("הגדרה") && Edit.userInfoNames(ip).get(0).equals("הגדרה"), "שמות מידע מוצעים");
        Info d4 = Edit.addInfo(iRoot, Info.Kind.USER, "הגדרה", "הגדרה רביעית");
        eq(iRoot.infos.indexOf(d4), 3, "הגדרה חדשה אחרי ההגדרות");
        Info ex3 = Edit.addInfo(iRoot, Info.Kind.USER, "דוגמה", "משולש בטבע");
        eq(iRoot.infos.indexOf(ex3), iRoot.infos.size() - 1, "דוגמה בסוף הדוגמאות");
        Info ess = iRoot.infos(Info.Kind.ESSENTIAL).get(0);
        Edit.setKind(iRoot, ess, Info.Kind.INCIDENTAL);
        eq(iRoot.infos(Info.Kind.ESSENTIAL).size(), 2, "עצמית עברה");
        eq(iRoot.infos(Info.Kind.INCIDENTAL).size(), 6, "למקרית");
        check(iRoot.infos(Info.Kind.INCIDENTAL).get(5) == ess, "בסוף המקריות, אותו אובייקט");
        Project ip2 = roundTrip(OpmlIO.write(ip), "אחרי עריכת מידע");
        eq(ip2.mainRoot().infos.size(), iRoot.infos.size(), "מידע נשמר");
        Item noTags = new Item("x");
        eq(Edit.tagUse(ip2, "zz"), 0, "תגית שלא קיימת");

        // --- מספור עברי ---
        int[] n = {1, 2, 9, 10, 11, 15, 16, 19, 20, 22, 100, 123, 400, 500};
        String[] h = {"א", "ב", "ט", "י", "י\"א", "ט\"ו", "ט\"ז", "י\"ט", "כ", "כ\"ב", "ק", "קכ\"ג", "ת", "ת\"ק"};
        for (int k = 0; k < n.length; k++) eq(Hebrew.number(n[k]), h[k], "מספר עברי " + n[k]);

        System.out.println("עברו " + pass + ", נכשלו " + fail);
        if (fail > 0) System.exit(1);
    }
}
