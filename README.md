# kvutzot-vetagiot

קבוצות ותגיות — כלי לאיסוף וארגון ידע בקבוצות ובתגיות.

כלי שמלווה את האדם בדרך מהבלגן לסדר: אוסף כל הבחנה, מאפשר לסווג אותה שוב ושוב לפי כל צורך, ומראה מה מתגבש, וההכרעה בידי האדם.

## מבנה המאגר
- `docs/` — מסמכי התכנון (עותק; המסמכים החיים בפרויקט ב-claude.ai)
- `source/` — עץ המקור של הגרסה האחרונה (בלי מפתח החתימה) — ייווצר עם הגרסה הראשונה
- `desktop/` — הגרסה למחשב (חלונות): המסך ב-Swing, והמוח מ-`source` (אותו קוד)
- `notes/` — סיכום כל גרסה (`desktop-X.md` — הגרסה למחשב)

## הבנייה
באותה שיטה כמו מנהל הקבצים (`aviccc-dev/vfm`): Java פשוט בלי Gradle, אריזה וחתימה בסביבת העבודה של Claude, וקובץ APK שנשלח להתקנה.

### הכנת סביבת הבנייה (10.11.2026)
- `apt-get install aapt zipalign apksigner dalvik-exchange`
- `android.jar` של API 30: `git clone --depth 1 --filter=blob:none --sparse https://github.com/Sable/android-platforms` ← `git sparse-checkout set android-30` ← העתקה ל-`/home/claude/tools/android.jar`
- שם החבילה: `com.aviccc.kvutzot`
- **מפתח החתימה לא נמצא במאגר.** הוא שמור בפרויקט ב-claude.ai (`claude/מפתח-חתימה.md`, עם הוראות שחזור), ועותק אצל המשתמש. כינוי `kv`, טביעת SHA-256 מתחילה ב-`E1:0C:2E:F3`.
