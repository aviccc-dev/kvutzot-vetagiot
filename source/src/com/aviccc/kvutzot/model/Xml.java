package com.aviccc.kvutzot.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** קורא וכותב XML פשוט, בלי ספריות, כדי שאותו קוד ירוץ באנדרואיד ובבדיקות. */
public final class Xml {

    /** צומת: שם, תכונות לפי הסדר, ילדים, וטקסט. */
    public static final class Node {
        public final String name;
        public final LinkedHashMap<String, String> attrs = new LinkedHashMap<>();
        public final List<Node> children = new ArrayList<>();
        public final StringBuilder text = new StringBuilder();

        public Node(String name) { this.name = name; }

        public String attr(String key) { return attrs.get(key); }

        public Node child(String n) {
            for (Node c : children) if (c.name.equals(n)) return c;
            return null;
        }

        public List<Node> all(String n) {
            List<Node> r = new ArrayList<>();
            for (Node c : children) if (c.name.equals(n)) r.add(c);
            return r;
        }

        /** עותק עמוק, לשמירת צמתים שלא מוכרים לתוכנה. */
        public Node copy() {
            Node n = new Node(name);
            n.attrs.putAll(attrs);
            n.text.append(text);
            for (Node c : children) n.children.add(c.copy());
            return n;
        }
    }

    public static final class ParseException extends Exception {
        public ParseException(String msg, int pos) { super(msg + " (תו " + pos + ")"); }
    }

    private final String s;
    private int i;

    private Xml(String s) { this.s = s; }

    /** קריאת מסמך שלם; מחזיר את צומת השורש. */
    public static Node parse(String text) throws ParseException {
        if (text.length() > 0 && text.charAt(0) == '﻿') text = text.substring(1);
        Xml x = new Xml(text);
        x.skipMisc();
        if (x.i >= x.s.length() || x.s.charAt(x.i) != '<') throw new ParseException("לא נמצא תג פתיחה", x.i);
        Node root = x.element();
        x.skipMisc();
        if (x.i < x.s.length()) throw new ParseException("יש תוכן אחרי סוף המסמך", x.i);
        return root;
    }

    private void skipMisc() throws ParseException {
        while (true) {
            skipWs();
            if (s.startsWith("<?", i)) { i = endOf("?>", i); }
            else if (s.startsWith("<!--", i)) { i = endOf("-->", i); }
            else if (s.startsWith("<!DOCTYPE", i)) { i = endOf(">", i); }
            else return;
        }
    }

    private int endOf(String tok, int from) throws ParseException {
        int e = s.indexOf(tok, from);
        if (e < 0) throw new ParseException("חסר " + tok, from);
        return e + tok.length();
    }

    private void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private String name() throws ParseException {
        int st = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '>' || c == '/' || c == '=') break;
            i++;
        }
        if (i == st) throw new ParseException("חסר שם", i);
        return s.substring(st, i);
    }

    private Node element() throws ParseException {
        i++; // '<'
        Node n = new Node(name());
        while (true) {
            skipWs();
            if (i >= s.length()) throw new ParseException("תג לא נסגר", i);
            char c = s.charAt(i);
            if (c == '/') {
                if (!s.startsWith("/>", i)) throw new ParseException("צפוי />", i);
                i += 2;
                return n;
            }
            if (c == '>') { i++; break; }
            String k = name();
            skipWs();
            if (i >= s.length() || s.charAt(i) != '=') throw new ParseException("צפוי = אחרי " + k, i);
            i++;
            skipWs();
            if (i >= s.length()) throw new ParseException("חסר ערך", i);
            char q = s.charAt(i);
            if (q != '"' && q != '\'') throw new ParseException("ערך בלי מרכאות", i);
            int e = s.indexOf(q, i + 1);
            if (e < 0) throw new ParseException("ערך לא נסגר", i);
            n.attrs.put(k, decode(s.substring(i + 1, e), i));
            i = e + 1;
        }
        // תוכן
        StringBuilder raw = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw new ParseException("חסר סגירה ל-" + n.name, i);
            if (s.startsWith("</", i)) {
                i += 2;
                String end = name();
                if (!end.equals(n.name)) throw new ParseException("סגירה " + end + " במקום " + n.name, i);
                skipWs();
                if (i >= s.length() || s.charAt(i) != '>') throw new ParseException("צפוי >", i);
                i++;
                n.text.append(raw);
                return n;
            }
            if (s.startsWith("<!--", i)) { i = endOf("-->", i); continue; }
            if (s.startsWith("<![CDATA[", i)) {
                int e = s.indexOf("]]>", i);
                if (e < 0) throw new ParseException("CDATA לא נסגר", i);
                raw.append(s, i + 9, e);
                i = e + 3;
                continue;
            }
            if (s.startsWith("<?", i)) { i = endOf("?>", i); continue; }
            if (s.charAt(i) == '<') { n.children.add(element()); continue; }
            int e = s.indexOf('<', i);
            if (e < 0) e = s.length();
            raw.append(decode(s.substring(i, e), i));
            i = e;
        }
    }

    private static String decode(String t, int pos) throws ParseException {
        if (t.indexOf('&') < 0) return t;
        StringBuilder b = new StringBuilder();
        int k = 0;
        while (k < t.length()) {
            char c = t.charAt(k);
            if (c != '&') { b.append(c); k++; continue; }
            int e = t.indexOf(';', k);
            if (e < 0) throw new ParseException("ישות לא נסגרה", pos + k);
            String ent = t.substring(k + 1, e);
            switch (ent) {
                case "lt": b.append('<'); break;
                case "gt": b.append('>'); break;
                case "amp": b.append('&'); break;
                case "quot": b.append('"'); break;
                case "apos": b.append('\''); break;
                default:
                    try {
                        int cp = ent.startsWith("#x") || ent.startsWith("#X")
                                ? Integer.parseInt(ent.substring(2), 16)
                                : ent.startsWith("#") ? Integer.parseInt(ent.substring(1)) : -1;
                        if (cp < 0) throw new ParseException("ישות לא מוכרת &" + ent + ";", pos + k);
                        b.appendCodePoint(cp);
                    } catch (NumberFormatException ex) {
                        throw new ParseException("ישות לא תקינה &" + ent + ";", pos + k);
                    }
            }
            k = e + 1;
        }
        return b.toString();
    }

    // ---- כתיבה ----

    public static String escape(String t, boolean attr) {
        StringBuilder b = new StringBuilder(t.length() + 8);
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            switch (c) {
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '&': b.append("&amp;"); break;
                case '"': if (attr) b.append("&quot;"); else b.append(c); break;
                case '\n': if (attr) b.append("&#10;"); else b.append(c); break;
                case '\r': b.append("&#13;"); break;
                case '\t': if (attr) b.append("&#9;"); else b.append(c); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    /** כתיבת צומת עם הזחה; צומת עם טקסט נכתב בשורה אחת. */
    public static void write(Node n, StringBuilder out, int depth) {
        indent(out, depth);
        out.append('<').append(n.name);
        for (Map.Entry<String, String> e : n.attrs.entrySet())
            out.append(' ').append(e.getKey()).append("=\"").append(escape(e.getValue(), true)).append('"');
        String t = n.text.toString();
        boolean hasText = t.trim().length() > 0;
        if (n.children.isEmpty() && !hasText) { out.append("/>\n"); return; }
        out.append('>');
        if (hasText) out.append(escape(t, false));
        if (!n.children.isEmpty()) {
            out.append('\n');
            for (Node c : n.children) write(c, out, depth + 1);
            indent(out, depth);
        }
        out.append("</").append(n.name).append(">\n");
    }

    private static void indent(StringBuilder out, int depth) {
        for (int k = 0; k < depth; k++) out.append("  ");
    }
}
