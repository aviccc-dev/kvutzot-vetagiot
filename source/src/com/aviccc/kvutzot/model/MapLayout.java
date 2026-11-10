package com.aviccc.kvutzot.model;

import com.aviccc.kvutzot.model.Model.Division;
import com.aviccc.kvutzot.model.Model.Item;
import com.aviccc.kvutzot.model.Model.Project;

import java.util.ArrayList;
import java.util.List;

/**
 * פריסת המפה הגרפית: איפה כל תיבה יושבת, בלי חפיפות. בלי אנדרואיד, כדי שאפשר לבדוק.
 *
 * שני כיוונים: מלמעלה למטה, ומימין לשמאל. בשניהם הבן הראשון בצד ימין/למעלה (סדר קריאה בעברית).
 * כל עומק הוא שורה (או עמודה) אחת, בגובה (או ברוחב) התיבה הגדולה בה, כדי שהמפה תהיה מסודרת.
 * חלוקה עם שם, או כשיש לאב כמה חלוקות (צדדים), מוצגת כתווית בין האב לבניו.
 */
public final class MapLayout {

    public interface Measurer {
        /** מחזיר {רוחב, גובה} של התיבה לפי הטקסט. */
        float[] measure(Node n);
    }

    public static final class Node {
        /** הפריט; בתווית חלוקה — הבעלים של החלוקה. */
        public final Item item;
        /** בתווית חלוקה: החלוקה; בפריט: null. */
        public final Division division;
        public final String text;
        /** סימון זמני קטן: עומק ואות צד. */
        public final String mark;
        /** עומק הפריט (בלי לספור תוויות); בתווית — עומק הבנים שלה. */
        public final int depth;
        /** שורה/עמודה בפריסה (כולל תוויות). */
        int rank;
        /** אינדקס הגוון של הענף: 0 = אפור (שורש ותלושים), 1… = ענפים ראשיים. */
        public final int hue;
        public final boolean loose;
        /** בפריט מכווץ: כמה צאצאים מוסתרים. */
        public final int hidden;
        public final List<Node> children = new ArrayList<>();
        public Node parent;
        /** מיקום וגודל סופיים, בפיקסלים. */
        public float x, y, w, h;
        float b, bSize, dSize, span, kidsSpan;

        Node(Item item, Division division, String text, String mark, int depth, int hue, boolean loose, int hidden) {
            this.item = item; this.division = division; this.text = text; this.mark = mark;
            this.depth = depth; this.hue = hue; this.loose = loose; this.hidden = hidden;
        }

        public boolean isDivision() { return division != null; }
    }

    public final List<Node> nodes = new ArrayList<>();
    public final List<Node> roots = new ArrayList<>();
    public float width, height;
    public final boolean vertical;
    /** מיקום תחילת כל שורה/עמודה, לציור הקווים. */
    public float[] rankStart = new float[0];
    public float[] rankSize = new float[0];

    public final float gapDepth, gapBreadth;
    /**
     * פריסה צפופה (0.10.2), "כמו במפת חשיבה רגילה": הבנים סמוכים לאביהם עם מעט ריווח,
     * והקווים לבניהם נמתחים בעיקול לפי הצורך. בלי צפופה — כל ענף ממורכז מעל כל תחום תת־העץ שלו.
     */
    public final boolean compact;

    private MapLayout(boolean vertical, float gapDepth, float gapBreadth, boolean compact) {
        this.vertical = vertical; this.gapDepth = gapDepth; this.gapBreadth = gapBreadth; this.compact = compact;
    }

    /**
     * vertical=true: מלמעלה למטה. false: מימין לשמאל.
     * gapDepth: מרווח בין שורות/עמודות; gapBreadth: מרווח בין אחים.
     */
    public static MapLayout build(Project p, boolean vertical, Measurer m, float gapDepth, float gapBreadth) {
        return build(p, vertical, m, gapDepth, gapBreadth, false);
    }

    public static MapLayout build(Project p, boolean vertical, Measurer m, float gapDepth, float gapBreadth, boolean compact) {
        MapLayout L = new MapLayout(vertical, gapDepth, gapBreadth, compact);
        for (Item r : p.roots) if (!r.loose) L.roots.add(L.tree(r, null, 0, 0, "0", false));
        for (Item r : p.roots) if (r.loose) L.roots.add(L.tree(r, null, 0, 0, "תלוש", true));
        L.measureAll(m);
        L.place();
        return L;
    }

    private Node tree(Item it, Node parent, int depth, int hue, String mark, boolean loose) {
        Node n = new Node(it, null, it.name, mark, depth, hue, loose,
                it.collapsed ? TextLines.descendants(it) : 0);
        attach(parent, n);
        if (it.collapsed) return n;
        boolean sides = it.divisions.size() > 1;
        int branch = 0;
        for (Division d : it.divisions) {
            String letter = sides ? Hebrew.number(d.side()) : "";
            Node holder = n;
            if (sides || !d.basis.isEmpty()) {
                holder = new Node(it, d, d.basis.isEmpty() ? "(חלוקה בלי שם)" : d.basis, letter,
                        depth + 1, hue, loose, 0);
                attach(n, holder);
            }
            String childMark = (depth + 1) + (sides ? " " + letter : "");
            for (Item c : d.children) {
                int h = hue;
                if (depth == 0 && !loose) h = ++branch;
                tree(c, holder, depth + 1, h, childMark, loose);
            }
        }
        return n;
    }

    private void attach(Node parent, Node n) {
        n.parent = parent;
        if (parent != null) parent.children.add(n);
        n.rank = parent == null ? 0 : parent.rank + 1;
        nodes.add(n);
    }

    private void measureAll(Measurer m) {
        int maxRank = 0;
        for (Node n : nodes) maxRank = Math.max(maxRank, n.rank);
        rankSize = new float[maxRank + 1];
        for (Node n : nodes) {
            float[] s = m.measure(n);
            n.w = s[0]; n.h = s[1];
            n.bSize = vertical ? n.w : n.h;
            n.dSize = vertical ? n.h : n.w;
            rankSize[n.rank] = Math.max(rankSize[n.rank], n.dSize);
        }
        rankStart = new float[maxRank + 1];
        float acc = 0;
        for (int r = 0; r <= maxRank; r++) { rankStart[r] = acc; acc += rankSize[r] + gapDepth; }
    }

    private float span(Node n) {
        float kids = 0;
        for (int i = 0; i < n.children.size(); i++) {
            if (i > 0) kids += gapBreadth;
            kids += span(n.children.get(i));
        }
        n.kidsSpan = kids;
        n.span = Math.max(n.bSize, kids);
        return n.span;
    }

    private void placeB(Node n, float b0) {
        if (n.children.isEmpty()) { n.b = b0 + (n.span - n.bSize) / 2; return; }
        float c = b0 + (n.span - n.kidsSpan) / 2;
        for (int i = 0; i < n.children.size(); i++) {
            if (i > 0) c += gapBreadth;
            Node k = n.children.get(i);
            placeB(k, c);
            c += k.span;
        }
        Node first = n.children.get(0), last = n.children.get(n.children.size() - 1);
        float mid = (first.b + first.bSize / 2 + last.b + last.bSize / 2) / 2;
        n.b = mid - n.bSize / 2;
        // לא לחרוג מהתחום של תת־העץ
        n.b = Math.max(b0, Math.min(n.b, b0 + n.span - n.bSize));
    }

    private void place() {
        float b0 = 0;
        for (int i = 0; i < roots.size(); i++) {
            Node r = roots.get(i);
            span(r);
            if (i > 0) b0 += gapBreadth * 3;
            placeB(r, b0);
            b0 += r.span;
        }
        float breadth = b0;
        if (compact && !nodes.isEmpty()) breadth = compactB();
        float depthTotal = rankStart.length == 0 ? 0
                : rankStart[rankStart.length - 1] + rankSize[rankSize.length - 1];
        for (Node n : nodes) {
            float d = rankStart[n.rank];
            if (vertical) {
                // הבן הראשון מימין: מראה של ציר הרוחב
                n.x = breadth - n.b - n.bSize;
                n.y = d;
            } else {
                // השורש מימין, הבנים משמאלו; תיבה צמודה לימין העמודה שלה
                n.x = depthTotal - d - n.w;
                n.y = n.b;
            }
        }
        width = vertical ? breadth : depthTotal;
        height = vertical ? depthTotal : breadth;
    }

    /**
     * פריסה צפופה: שורה אחר שורה מלמעלה. כל קבוצת אחים מבקשת לשבת צמודה, ממורכזת מול האב;
     * אם קבוצות סמוכות מתנגשות — הן נדחקות זו מזו כמה שפחות (בלוקים ממוצעים), והסדר נשמר,
     * כך שקווים בין שתי שורות לא נחתכים. מחזיר את הרוחב הכולל.
     */
    private float compactB() {
        int maxRank = rankSize.length - 1;
        List<List<Node>> byRank = new ArrayList<>();
        for (int r = 0; r <= maxRank; r++) byRank.add(new ArrayList<Node>());
        for (Node rt : roots) collect(rt, byRank);
        float groupGap = gapBreadth * 2.5f;
        for (int r = 1; r <= maxRank; r++) {
            List<Node> row = byRank.get(r);
            int n = row.size();
            if (n == 0) continue;
            float[] want = new float[n];
            int i = 0;
            while (i < n) {
                Node par = row.get(i).parent;
                int j = i;
                float total = 0;
                while (j < n && row.get(j).parent == par) { total += (j > i ? gapBreadth : 0) + row.get(j).bSize; j++; }
                float c = par.b + par.bSize / 2 - total / 2;
                for (int k = i; k < j; k++) { want[k] = c; c += row.get(k).bSize + gapBreadth; }
                i = j;
            }
            // בלוקים: כל בלוק — רצף צמוד; מיקומו = ממוצע מה שחבריו מבקשים
            int[] first = new int[n];
            float[] sum = new float[n], width = new float[n], pos = new float[n];
            int[] count = new int[n];
            int top = -1;
            for (int k = 0; k < n; k++) {
                top++;
                first[top] = k; sum[top] = want[k]; count[top] = 1; width[top] = row.get(k).bSize; pos[top] = want[k];
                while (top > 0) {
                    int lastPrev = first[top] - 1;
                    float g = row.get(lastPrev).parent == row.get(first[top]).parent ? gapBreadth : groupGap;
                    if (pos[top - 1] + width[top - 1] + g <= pos[top]) break;
                    float off = width[top - 1] + g;
                    sum[top - 1] += sum[top] - count[top] * off;
                    count[top - 1] += count[top];
                    width[top - 1] = off + width[top];
                    pos[top - 1] = sum[top - 1] / count[top - 1];
                    top--;
                }
            }
            for (int bl = 0; bl <= top; bl++) {
                int end = bl < top ? first[bl + 1] : n;
                float c = pos[bl];
                for (int k = first[bl]; k < end; k++) {
                    if (k > first[bl]) c += row.get(k - 1).parent == row.get(k).parent ? gapBreadth : groupGap;
                    row.get(k).b = c;
                    c += row.get(k).bSize;
                }
            }
        }
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (Node x : nodes) { lo = Math.min(lo, x.b); hi = Math.max(hi, x.b + x.bSize); }
        for (Node x : nodes) x.b -= lo;
        // מרווח בין שורות לפי מתיחת הקווים, כדי שהעיקול לא יהיה שטוח מדי
        float acc = 0;
        for (int r = 0; r <= maxRank; r++) {
            rankStart[r] = acc;
            float stretch = 0;
            if (r + 1 <= maxRank)
                for (Node c : byRank.get(r + 1))
                    stretch = Math.max(stretch, Math.abs((c.b + c.bSize / 2) - (c.parent.b + c.parent.bSize / 2)));
            acc += rankSize[r] + Math.max(gapDepth, Math.min(gapDepth * 5, stretch * 0.12f));
        }
        return hi - lo;
    }

    private static void collect(Node n, List<List<Node>> byRank) {
        byRank.get(n.rank).add(n);
        for (Node k : n.children) collect(k, byRank);
    }

    /** הקו מאב לבן: נקודות (x,y) לציור קו שבור (בצפופה — עיקול). */
    public float[] edge(Node parent, Node child) {
        if (compact) {
            float x1, y1, x2, y2, c1x, c1y, c2x, c2y;
            if (vertical) {
                x1 = parent.x + parent.w / 2; y1 = parent.y + parent.h;
                x2 = child.x + child.w / 2; y2 = child.y;
                float dy = (y2 - y1) * 0.55f;
                c1x = x1; c1y = y1 + dy; c2x = x2; c2y = y2 - dy;
            } else {
                x1 = parent.x; y1 = parent.y + parent.h / 2;
                x2 = child.x + child.w; y2 = child.y + child.h / 2;
                float dx = (x1 - x2) * 0.55f;
                c1x = x1 - dx; c1y = y1; c2x = x2 + dx; c2y = y2;
            }
            int steps = Math.abs(x2 - x1) + Math.abs(y2 - y1) < 40 ? 2 : 14;
            float[] e = new float[(steps + 1) * 2];
            for (int s = 0; s <= steps; s++) {
                float u = s / (float) steps, v = 1 - u;
                e[2 * s] = v * v * v * x1 + 3 * v * v * u * c1x + 3 * v * u * u * c2x + u * u * u * x2;
                e[2 * s + 1] = v * v * v * y1 + 3 * v * v * u * c1y + 3 * v * u * u * c2y + u * u * u * y2;
            }
            return e;
        }
        if (vertical) {
            float x1 = parent.x + parent.w / 2, y1 = parent.y + parent.h;
            float x2 = child.x + child.w / 2, y2 = child.y;
            float ym = rankStart[child.rank] - gapDepth / 2;
            return new float[]{x1, y1, x1, ym, x2, ym, x2, y2};
        } else {
            float x1 = parent.x, y1 = parent.y + parent.h / 2;
            float x2 = child.x + child.w, y2 = child.y + child.h / 2;
            float colRight = width - rankStart[child.rank];
            float xm = colRight + gapDepth / 2;
            return new float[]{x1, y1, xm, y1, xm, y2, x2, y2};
        }
    }
}
