package com.aviccc.kvutzot.model;

import java.util.ArrayList;
import java.util.List;

/**
 * סקאלת ההגדלה (10.11.2026): ערכים בצעדים קבועים — הצפיפות נקבעת בהגדרות
 * (חצי, רבע, או כל מספר אחר). בלי אנדרואיד, כדי שאפשר לבדוק.
 */
public final class Zoom {
    private Zoom() {}

    public static final float MIN = 0.05f, MAX = 4f;
    private static final float EPS = 0.001f;

    /** צעד תקין: בין 0.05 ל-2. */
    public static float clampStep(float step) {
        if (Float.isNaN(step) || step <= 0) return 0.5f;
        return Math.max(0.05f, Math.min(2f, step));
    }

    /** כל הערכים בסקאלה, מהקטן לגדול: כפולות של הצעד בתוך התחום. */
    public static List<Float> values(float step) {
        step = clampStep(step);
        List<Float> v = new ArrayList<>();
        for (int k = 1; k * step <= MAX + EPS; k++) {
            float z = round(k * step);
            if (z >= MIN - EPS) v.add(z);
        }
        if (v.isEmpty()) v.add(1f);
        return v;
    }

    /** הערך הבא בסקאלה מעל (dir>0) או מתחת (dir<0) להגדלה הנוכחית. */
    public static float next(float cur, float step, int dir) {
        List<Float> v = values(step);
        if (dir > 0) {
            for (float z : v) if (z > cur + EPS) return z;
            return v.get(v.size() - 1);
        }
        for (int i = v.size() - 1; i >= 0; i--) if (v.get(i) < cur - EPS) return v.get(i);
        return v.get(0);
    }

    private static float round(float z) { return Math.round(z * 1000f) / 1000f; }

    /** תצוגה: בלי אפסים מיותרים (1, 1.5, 0.25). */
    public static String format(float z) {
        float r = Math.round(z * 100f) / 100f;
        if (Math.abs(r - Math.round(r)) < EPS) return String.valueOf(Math.round(r));
        String s = String.valueOf(r);
        while (s.contains(".") && s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
