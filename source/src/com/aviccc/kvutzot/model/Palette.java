package com.aviccc.kvutzot.model;

/**
 * צבעי המפה (זמני, 6.11.2026): 14 גוונים, גוון לכל ענף ראשי; בהירות לפי עומק מכהה לבהיר;
 * אחרי הרמה האחרונה — שחור, ושוב מההתחלה; מסגרת שמתדקקת עם העומק; טקסט בגוון כהה של אותו צבע,
 * וברמות כהות מאוד — לבן. האפור שמור לשורש ולתלושים.
 */
public final class Palette {
    private Palette() {}

    /** גוונים (מעלות), לפי הסדר: אדום, כתום, ענבר, זית, ירקרק, ירוק, טורקיז, תכלת, כחול, אינדיגו, סגול, סגלגל, ורוד. */
    private static final float[] HUES = {0, 25, 42, 58, 85, 125, 170, 195, 215, 235, 265, 290, 330};
    private static final float[] SATS = {.62f, .70f, .72f, .45f, .50f, .45f, .55f, .60f, .60f, .45f, .45f, .45f, .55f};
    /** סדר הקצאה לענפים: מרוחקים זה מזה קודם. */
    private static final int[] ORDER = {8, 1, 5, 10, 0, 6, 2, 12, 9, 4, 7, 11, 3};

    /** מספר רמות הבהירות לפני השחור. */
    public static final int LEVELS = 6;
    private static final float[] LIGHT = {.36f, .50f, .62f, .72f, .81f, .89f};

    /** מספר הגוונים לענפים (בלי האפור). */
    public static int branchHues() { return HUES.length; }

    /** רמת הבהירות לעומק: 0…LEVELS-1, או LEVELS = שחור. השורש (עומק 0) כהה. */
    public static int level(int depth) { return depth % (LEVELS + 1); }

    private static int hsl(float h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float hp = (h % 360) / 60f;
        float x = c * (1 - Math.abs(hp % 2 - 1));
        float r = 0, g = 0, b = 0;
        if (hp < 1) { r = c; g = x; } else if (hp < 2) { r = x; g = c; } else if (hp < 3) { g = c; b = x; }
        else if (hp < 4) { g = x; b = c; } else if (hp < 5) { r = x; b = c; } else { r = c; b = x; }
        float m = l - c / 2;
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    /** hue 0 = אפור; 1… = ענפים לפי סדר ההקצאה, ובסוף חוזרים מההתחלה. */
    private static float[] hs(int hue) {
        if (hue <= 0) return new float[]{0, 0};
        int k = ORDER[(hue - 1) % ORDER.length];
        return new float[]{HUES[k], SATS[k]};
    }

    public static int fill(int hue, int depth) {
        int lv = level(depth);
        if (lv == LEVELS) return 0xFF161616;
        float[] a = hs(hue);
        float l = LIGHT[lv];
        if (hue <= 0) l = Math.max(.30f, l - .06f);
        return hsl(a[0], a[1], l);
    }

    public static int border(int hue, int depth) {
        float[] a = hs(hue);
        int lv = level(depth);
        return hsl(a[0], hue <= 0 ? 0 : Math.min(.8f, a[1] + .1f), lv == LEVELS ? .45f : .26f);
    }

    public static int text(int hue, int depth) {
        int lv = level(depth);
        if (lv == LEVELS || LIGHT[Math.min(lv, LEVELS - 1)] < .55f) return 0xFFFFFFFF;
        float[] a = hs(hue);
        return hsl(a[0], a[1], .18f);
    }

    /** עובי המסגרת בפיקסלים לפי עומק: מ-3 עד 0.5. */
    public static float borderWidth(int depth, float density) {
        float w = Math.max(.5f, 3f - depth * .55f);
        return w * density;
    }
}
