// מפעיל קטן לחלונות: מחפש Java מותקנת (11 ומעלה), ואם יש — מפעיל איתה את kvutzot.jar.
// כך אין צורך להוריד את התוכנה עם Java בפנים (28 מגה) כשהיא כבר במחשב.
// אם אין: מציע להתקין Java פעם אחת (חינם), או להוריד את הגרסה המלאה עם Java בפנים.
// נכתב ב-C# 5, כדי שיתקמפל ב-csc של ‎.NET Framework 4, שקיים בכל חלונות 10 ו-11.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Windows.Forms;
using Microsoft.Win32;

static class Launcher
{
    const int MinVersion = 11;
    const string Title = "קבוצות ותגיות";
    const string JavaUrl = "https://adoptium.net/temurin/releases/?os=windows&arch=x64&package=jre&version=21";
    const string FullUrl = "https://github.com/aviccc-dev/kvutzot-vetagiot/releases/latest";

    [STAThread]
    static int Main(string[] args)
    {
        string dir = AppDomain.CurrentDomain.BaseDirectory;
        string jar = Path.Combine(dir, "kvutzot.jar");
        string testOut = args.Length >= 2 && args[0] == "--test" ? args[1] : null;

        int version;
        string old;
        // קודם: סביבת ההרצה שקובץ ההתקנה הוריד ליד התוכנה (כשלא הייתה Java במחשב); אחר כך — Java של המחשב
        string javaw = FindJava(dir, out version, out old);

        if (testOut != null)
        {
            File.WriteAllText(testOut, javaw == null ? "none" + (old == null ? "" : " old=" + old)
                : "java=" + javaw + " version=" + version, new UTF8Encoding(false));
            return javaw == null ? 1 : 0;
        }

        if (!File.Exists(jar))
        {
            Show("הקובץ kvutzot.jar לא נמצא ליד התוכנה.\n\nכדאי להתקין את התוכנה מחדש מקובץ ההתקנה.", MessageBoxIcon.Error);
            return 2;
        }

        if (javaw == null)
        {
            string msg = (old == null
                    ? "לא נמצאה במחשב Java."
                    : "נמצאה במחשב Java ישנה (גרסה " + old + ").")
                + " התוכנה צריכה Java בגרסה " + MinVersion + " ומעלה.\n\n"
                + "הכי פשוט: להתקין את התוכנה מחדש מקובץ ההתקנה — הוא יוריד את Java בעצמו.\n"
                + "אפשר גם להתקין Java בנפרד (חינם, Temurin), ואז להפעיל את התוכנה שוב.";
            int r = Ask(msg, new string[] { "לדף ההורדה של התוכנה", "להתקנת Java", "ביטול" });
            if (r == 0) Open(FullUrl);
            else if (r == 1) Open(JavaUrl);
            return 3;
        }

        StringBuilder a = new StringBuilder("-Dfile.encoding=UTF-8 -jar \"" + jar + "\"");
        foreach (string x in args) a.Append(" \"").Append(x.Replace("\"", "")).Append("\"");
        try
        {
            ProcessStartInfo psi = new ProcessStartInfo(javaw, a.ToString());
            psi.UseShellExecute = false;
            psi.WorkingDirectory = dir;
            Process.Start(psi);
        }
        catch (Exception e)
        {
            Show("לא ניתן להפעיל את Java:\n" + javaw + "\n\n" + e.Message, MessageBoxIcon.Error);
            return 4;
        }
        return 0;
    }

    static void Show(string text, MessageBoxIcon icon)
    {
        MessageBox.Show(text, Title, MessageBoxButtons.OK, icon, MessageBoxDefaultButton.Button1,
            MessageBoxOptions.RtlReading | MessageBoxOptions.RightAlign);
    }

    /// <summary>שאלה עם כפתורים בעברית (תיבת ההודעה של חלונות כותבת את הכפתורים בשפת המערכת).</summary>
    static int Ask(string text, string[] buttons)
    {
        Form f = new Form();
        f.Text = Title;
        f.RightToLeft = RightToLeft.Yes;
        f.RightToLeftLayout = true;
        f.FormBorderStyle = FormBorderStyle.FixedDialog;
        f.MaximizeBox = false;
        f.MinimizeBox = false;
        f.StartPosition = FormStartPosition.CenterScreen;
        f.AutoSize = true;
        f.AutoSizeMode = AutoSizeMode.GrowAndShrink;
        f.Font = new System.Drawing.Font("Segoe UI", 10f);
        try { f.Icon = System.Drawing.Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch (Exception) { }
        TableLayoutPanel t = new TableLayoutPanel();
        t.AutoSize = true;
        t.Padding = new Padding(16);
        t.ColumnCount = 1;
        Label l = new Label();
        l.Text = text;
        l.AutoSize = true;
        l.MaximumSize = new System.Drawing.Size(460, 0);
        l.Margin = new Padding(0, 0, 0, 14);
        t.Controls.Add(l);
        FlowLayoutPanel row = new FlowLayoutPanel();
        row.AutoSize = true;
        row.FlowDirection = FlowDirection.LeftToRight;
        int[] chosen = { -1 };
        for (int i = 0; i < buttons.Length; i++)
        {
            Button b = new Button();
            b.Text = buttons[i];
            b.AutoSize = true;
            b.Padding = new Padding(8, 2, 8, 2);
            int k = i;
            b.Click += delegate { chosen[0] = k; f.Close(); };
            row.Controls.Add(b);
            if (i == 0) f.AcceptButton = b;
            if (i == buttons.Length - 1) f.CancelButton = b;
        }
        t.Controls.Add(row);
        f.Controls.Add(t);
        f.ShowDialog();
        return chosen[0];
    }

    static void Open(string url)
    {
        try { Process.Start(url); } catch (Exception) { }
    }

    // ---------------- חיפוש Java ----------------

    /// <summary>הגרסה הגבוהה ביותר שנמצאה (11 ומעלה). old — גרסה ישנה שנמצאה, אם לא נמצאה מתאימה.</summary>
    static string FindJava(string dir, out int bestVersion, out string old)
    {
        string own = Path.Combine(dir, "runtime");
        bestVersion = 0;
        old = null;
        if (File.Exists(Path.Combine(own, @"bin\javaw.exe")))
        {
            int ov = VersionOf(own);
            if (ov >= MinVersion) { bestVersion = ov; return Path.Combine(own, @"bin\javaw.exe"); }
        }
        List<string> homes = new List<string>();
        Add(homes, Environment.GetEnvironmentVariable("JAVA_HOME"));
        string path = Environment.GetEnvironmentVariable("PATH") ?? "";
        foreach (string p in path.Split(';'))
        {
            string d = p.Trim().Trim('"');
            if (d.Length == 0) continue;
            try { if (File.Exists(Path.Combine(d, "javaw.exe"))) Add(homes, Path.GetDirectoryName(d.TrimEnd('\\'))); } catch (Exception) { }
        }
        foreach (RegistryHive hive in new RegistryHive[] { RegistryHive.LocalMachine, RegistryHive.CurrentUser })
            foreach (RegistryView view in new RegistryView[] { RegistryView.Registry64, RegistryView.Registry32 })
            {
                try
                {
                    using (RegistryKey root = RegistryKey.OpenBaseKey(hive, view))
                    {
                        foreach (string k in new string[] { @"SOFTWARE\JavaSoft\JDK", @"SOFTWARE\JavaSoft\JRE",
                                @"SOFTWARE\JavaSoft\Java Runtime Environment", @"SOFTWARE\JavaSoft\Java Development Kit" })
                            Values(root, k, null, "JavaHome", homes);
                        foreach (string k in new string[] { @"SOFTWARE\Eclipse Adoptium\JDK", @"SOFTWARE\Eclipse Adoptium\JRE",
                                @"SOFTWARE\Eclipse Foundation\JDK", @"SOFTWARE\Microsoft\JDK", @"SOFTWARE\AdoptOpenJDK\JDK", @"SOFTWARE\AdoptOpenJDK\JRE" })
                            Values(root, k, @"hotspot\MSI", "Path", homes);
                        Values(root, @"SOFTWARE\Azul Systems\Zulu", null, "InstallationPath", homes);
                    }
                }
                catch (Exception) { }
            }
        foreach (string pf in new string[] { Environment.GetEnvironmentVariable("ProgramW6432"), Environment.GetEnvironmentVariable("ProgramFiles"),
                Environment.GetEnvironmentVariable("ProgramFiles(x86)") })
        {
            if (string.IsNullOrEmpty(pf)) continue;
            foreach (string vendor in new string[] { "Java", "Eclipse Adoptium", "Eclipse Foundation", "Microsoft", "Zulu", "Amazon Corretto", "BellSoft", "AdoptOpenJDK", "Semeru" })
            {
                string vd = Path.Combine(pf, vendor);
                try { if (Directory.Exists(vd)) foreach (string d in Directory.GetDirectories(vd)) Add(homes, d); } catch (Exception) { }
            }
        }

        string best = null;
        foreach (string h in homes)
        {
            string javaw = Path.Combine(h, @"bin\javaw.exe");
            if (!File.Exists(javaw)) continue;
            int v = VersionOf(h);
            if (v >= MinVersion && v > bestVersion) { best = javaw; bestVersion = v; }
            else if (v > 0 && v < MinVersion && old == null) old = v.ToString();
        }
        return best;
    }

    static void Add(List<string> l, string home)
    {
        if (string.IsNullOrEmpty(home)) return;
        string h = home.Trim().Trim('"').TrimEnd('\\');
        foreach (string x in l) if (string.Equals(x, h, StringComparison.OrdinalIgnoreCase)) return;
        l.Add(h);
    }

    /// <summary>כל תתי־המפתחות (גרסאות) של מפתח, והערך שבהם.</summary>
    static void Values(RegistryKey root, string key, string sub, string value, List<string> homes)
    {
        using (RegistryKey k = root.OpenSubKey(key))
        {
            if (k == null) return;
            foreach (string ver in k.GetSubKeyNames())
            {
                using (RegistryKey v = k.OpenSubKey(sub == null ? ver : ver + "\\" + sub))
                {
                    if (v == null) continue;
                    object o = v.GetValue(value);
                    if (o != null) Add(homes, o.ToString());
                }
            }
        }
    }

    /// <summary>הגרסה הראשית: מקובץ release שבתיקיית Java, ואם אין — מ-java -version.</summary>
    static int VersionOf(string home)
    {
        try
        {
            string rel = Path.Combine(home, "release");
            if (File.Exists(rel))
                foreach (string line in File.ReadAllLines(rel))
                    if (line.StartsWith("JAVA_VERSION=")) return Major(line.Substring(13).Trim('"'));
        }
        catch (Exception) { }
        try
        {
            ProcessStartInfo psi = new ProcessStartInfo(Path.Combine(home, @"bin\java.exe"), "-version");
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            psi.RedirectStandardError = true;
            using (Process p = Process.Start(psi))
            {
                string err = p.StandardError.ReadToEnd();
                p.WaitForExit(5000);
                int q = err.IndexOf('"');
                if (q >= 0)
                {
                    int e = err.IndexOf('"', q + 1);
                    if (e > q) return Major(err.Substring(q + 1, e - q - 1));
                }
            }
        }
        catch (Exception) { }
        return 0;
    }

    /// <summary>"21.0.1" ← 21; "1.8.0_391" ← 8; "17" ← 17.</summary>
    static int Major(string v)
    {
        string[] parts = v.Split('.', '_', '-', '+');
        int a;
        if (parts.Length == 0 || !int.TryParse(parts[0], out a)) return 0;
        if (a == 1 && parts.Length > 1) { int b; if (int.TryParse(parts[1], out b)) return b; }
        return a;
    }
}
