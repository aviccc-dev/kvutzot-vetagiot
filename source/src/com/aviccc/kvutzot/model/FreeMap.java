package com.aviccc.kvutzot.model;

import java.util.ArrayList;
import java.util.List;

/**
 * המפה הגמישה של טאב "סיעור מוחות" (0.8): "מה שיותר גמיש עדיף".
 * פריטים בכל מקום על המסך, בלי אב חובה, וקווים בין כל שני פריטים.
 * מיקום ביחידות של המסך בצפיפות 1 (המסך מכפיל בצפיפות).
 */
public final class FreeMap {
    public static final class Node {
        public final String id;
        public String text;
        public float x, y;
        Node(String id, String text, float x, float y) { this.id = id; this.text = text; this.x = x; this.y = y; }
    }

    public static final class Link {
        public final String a, b;
        Link(String a, String b) { this.a = a; this.b = b; }
        public boolean touches(String id) { return a.equals(id) || b.equals(id); }
        public boolean same(String p, String q) { return (a.equals(p) && b.equals(q)) || (a.equals(q) && b.equals(p)); }
    }

    public final List<Node> nodes = new ArrayList<>();
    public final List<Link> links = new ArrayList<>();

    public boolean isEmpty() { return nodes.isEmpty(); }

    public Node node(String id) {
        for (Node n : nodes) if (n.id.equals(id)) return n;
        return null;
    }

    private String newId() {
        int k = nodes.size() + 1;
        while (node("n" + k) != null) k++;
        return "n" + k;
    }

    public Node add(String text, float x, float y) {
        Node n = new Node(newId(), text == null ? "" : text, x, y);
        nodes.add(n);
        return n;
    }

    /** לשימוש בקריאה מקובץ. */
    public Node addWithId(String id, String text, float x, float y) {
        if (id == null || id.isEmpty() || node(id) != null) id = newId();
        Node n = new Node(id, text == null ? "" : text, x, y);
        nodes.add(n);
        return n;
    }

    /** מחיקת פריט, וכל הקווים שלו. */
    public void remove(Node n) {
        nodes.remove(n);
        for (int i = links.size() - 1; i >= 0; i--) if (links.get(i).touches(n.id)) links.remove(i);
    }

    public boolean linked(Node p, Node q) {
        for (Link l : links) if (l.same(p.id, q.id)) return true;
        return false;
    }

    /** קו בין שני פריטים (אם עוד אין). מחזיר אם נוסף. */
    public boolean link(Node p, Node q) {
        if (p == null || q == null || p == q || linked(p, q)) return false;
        links.add(new Link(p.id, q.id));
        return true;
    }

    public boolean unlink(Node p, Node q) {
        for (int i = 0; i < links.size(); i++) if (links.get(i).same(p.id, q.id)) { links.remove(i); return true; }
        return false;
    }

    /** הפריטים שמחוברים לפריט. */
    public List<Node> neighbors(Node n) {
        List<Node> r = new ArrayList<>();
        for (Link l : links) if (l.touches(n.id)) {
            Node o = node(l.a.equals(n.id) ? l.b : l.a);
            if (o != null) r.add(o);
        }
        return r;
    }
}
