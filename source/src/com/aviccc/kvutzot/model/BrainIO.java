package com.aviccc.kvutzot.model;

import java.util.ArrayList;
import java.util.List;

/**
 * קובץ סיעור מוחות: שורת פתיחה ואחריה XML (כמו קובץ המפה, כדי ש-Dropbox יעביר אותו שלם).
 *
 * <kvBrainstorm version="1"><title/>
 *   <section kind="text" title="">טקסט</section>
 *   <section kind="grid"><row><cell>…</cell></row></section>
 *   <section kind="notebook"><line struck="yes">…</line></section>
 * </kvBrainstorm>
 */
public final class BrainIO {
    private BrainIO() {}

    public static final String HEADER = "קבוצות ותגיות · סיעור מוחות";

    /** איזה סוג קובץ זה: "brain" לסיעור מוחות, אחרת "map". */
    public static boolean isBrain(String text) {
        String s = OpmlIO.stripHeader(text);
        int i = 0;
        while (true) {
            i = s.indexOf('<', i);
            if (i < 0) return false;
            if (s.startsWith("<?", i) || s.startsWith("<!", i)) { i++; continue; }
            return s.startsWith("<kvBrainstorm", i);
        }
    }

    public static Brain read(String text) throws Xml.ParseException {
        Xml.Node root = Xml.parse(OpmlIO.stripHeader(text));
        if (!root.name.equals("kvBrainstorm")) throw new Xml.ParseException("זה לא קובץ סיעור מוחות", 0);
        Brain b = new Brain();
        Xml.Node t = root.child("title");
        if (t != null) b.title = t.text.toString().trim();
        for (Xml.Node sn : root.all("section")) {
            Brain.Kind k = Brain.Kind.of(sn.attr("kind") == null ? "" : sn.attr("kind"));
            if (k == null) continue;
            Brain.Section s = new Brain.Section(k);
            s.title = sn.attr("title") == null ? "" : sn.attr("title");
            switch (k) {
                case TEXT:
                    s.text = sn.text.toString();
                    break;
                case GRID: {
                    int cols = 0;
                    for (Xml.Node rn : sn.all("row")) cols = Math.max(cols, rn.all("cell").size());
                    for (Xml.Node rn : sn.all("row")) {
                        List<String> row = new ArrayList<>();
                        for (Xml.Node cn : rn.all("cell")) row.add(cn.text.toString());
                        while (row.size() < Math.max(1, cols)) row.add("");
                        s.grid.add(row);
                    }
                    if (s.grid.isEmpty()) s.initGrid(1, 1);
                    break;
                }
                case NOTEBOOK:
                    for (Xml.Node ln : sn.all("line"))
                        s.lines.add(new Brain.Line(ln.text.toString(), OpmlIO.YES.equals(ln.attr("struck"))));
                    break;
            }
            b.sections.add(s);
        }
        return b;
    }

    public static String write(Brain b) {
        Xml.Node root = new Xml.Node("kvBrainstorm");
        root.attrs.put("version", "1");
        Xml.Node t = new Xml.Node("title");
        t.text.append(b.title);
        root.children.add(t);
        for (Brain.Section s : b.sections) {
            Xml.Node sn = new Xml.Node("section");
            sn.attrs.put("kind", s.kind.code);
            if (!s.title.isEmpty()) sn.attrs.put("title", s.title);
            switch (s.kind) {
                case TEXT: sn.text.append(s.text); break;
                case GRID:
                    for (List<String> row : s.grid) {
                        Xml.Node rn = new Xml.Node("row");
                        for (String c : row) {
                            Xml.Node cn = new Xml.Node("cell");
                            cn.text.append(c);
                            rn.children.add(cn);
                        }
                        sn.children.add(rn);
                    }
                    break;
                case NOTEBOOK:
                    for (Brain.Line l : s.lines) {
                        Xml.Node ln = new Xml.Node("line");
                        if (l.struck) ln.attrs.put("struck", OpmlIO.YES);
                        ln.text.append(l.text);
                        sn.children.add(ln);
                    }
                    break;
            }
            root.children.add(sn);
        }
        StringBuilder out = new StringBuilder(HEADER + "\n<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        Xml.write(root, out, 0);
        return out.toString();
    }
}
