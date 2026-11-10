package com.aviccc.kvutzot.model;

/** מספור עברי לסימון הצדדים: א, ב … י, י"א, ט"ו, ט"ז … ק, קכ"ג. */
public final class Hebrew {
    private Hebrew() {}

    private static final String ONES = "אבגדהוזחט";
    private static final String TENS = "יכלמנסעפצ";
    private static final String HUNDREDS = "קרשת";

    /** n גדול מאפס. יותר מאות אחת: גרשיים לפני האות האחרונה. */
    public static String number(int n) {
        if (n <= 0) throw new IllegalArgumentException("מספר חייב להיות חיובי: " + n);
        StringBuilder b = new StringBuilder();
        int h = n / 100;
        while (h >= 4) { b.append('ת'); h -= 4; }
        if (h > 0) b.append(HUNDREDS.charAt(h - 1));
        int r = n % 100;
        if (r == 15) b.append("טו");
        else if (r == 16) b.append("טז");
        else {
            if (r >= 10) b.append(TENS.charAt(r / 10 - 1));
            if (r % 10 > 0) b.append(ONES.charAt(r % 10 - 1));
        }
        if (b.length() > 1) b.insert(b.length() - 1, '"');
        return b.toString();
    }
}
