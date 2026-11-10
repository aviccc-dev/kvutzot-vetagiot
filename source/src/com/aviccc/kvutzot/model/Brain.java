package com.aviccc.kvutzot.model;

import java.util.ArrayList;
import java.util.List;

/**
 * סיעור מוחות (גרסה 0.7): "בלי סדר קבוע". קובץ אחד = חלקים בזה אחר זה, וכל חלק מסוג אחד:
 * כתבן (טקסט חופשי בשורות), גיליון (משבצות), או פנקס (שורות מקובעות, עם מיקום, פס ומחיקה).
 * הסוג נבחר כשיוצרים, ונשאר; אפשר להוסיף חלקים נוספים אחריו.
 */
public final class Brain {
    public String title = "";
    public final List<Section> sections = new ArrayList<>();

    public enum Kind {
        TEXT("text", "כתבן"), GRID("grid", "גיליון"), NOTEBOOK("notebook", "פנקס");
        public final String code, label;
        Kind(String code, String label) { this.code = code; this.label = label; }
        public static Kind of(String c) {
            for (Kind k : values()) if (k.code.equals(c)) return k;
            return null;
        }
    }

    /** שורה בפנקס. struck = עבר עליה פס (המשמעות — של המשתמש). */
    public static final class Line {
        public String text;
        public boolean struck;
        public Line(String text, boolean struck) { this.text = text == null ? "" : text; this.struck = struck; }
    }

    public static final class Section {
        public final Kind kind;
        public String title = "";
        /** כתבן. */
        public String text = "";
        /** גיליון: שורות של משבצות; כל השורות באותו אורך. */
        public final List<List<String>> grid = new ArrayList<>();
        /** פנקס. */
        public final List<Line> lines = new ArrayList<>();

        public Section(Kind kind) { this.kind = kind; }

        public int cols() { return grid.isEmpty() ? 0 : grid.get(0).size(); }

        /** גיליון ריק בגודל נתון. */
        public void initGrid(int rows, int cols) {
            grid.clear();
            for (int r = 0; r < rows; r++) {
                List<String> row = new ArrayList<>();
                for (int c = 0; c < cols; c++) row.add("");
                grid.add(row);
            }
        }

        public void addRow(int at) {
            List<String> row = new ArrayList<>();
            for (int c = 0; c < Math.max(1, cols()); c++) row.add("");
            grid.add(Math.max(0, Math.min(at, grid.size())), row);
        }

        public void addCol(int at) {
            if (grid.isEmpty()) { addRow(0); return; }
            for (List<String> row : grid) row.add(Math.max(0, Math.min(at, row.size())), "");
        }

        public boolean removeRow(int r) {
            if (r < 0 || r >= grid.size() || grid.size() <= 1) return false;
            grid.remove(r);
            return true;
        }

        public boolean removeCol(int c) {
            if (c < 0 || c >= cols() || cols() <= 1) return false;
            for (List<String> row : grid) row.remove(c);
            return true;
        }

        /** הזזת שורה בפנקס למקום אחר. */
        public boolean moveLine(int from, int to) {
            if (from < 0 || from >= lines.size() || to < 0 || to >= lines.size() || from == to) return false;
            Line l = lines.remove(from);
            lines.add(to, l);
            return true;
        }
    }

    /** חלק חדש מהסוג הזה, עם תוכן התחלתי ריק מתאים. */
    public Section addSection(Kind k) {
        Section s = new Section(k);
        if (k == Kind.GRID) s.initGrid(5, 3);
        sections.add(s);
        return s;
    }
}
