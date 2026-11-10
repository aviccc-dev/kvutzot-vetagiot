package com.aviccc.kvutzot.model;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** ציור ניסיון של המפה למחשב (לא חלק מהאפליקציה): java Preview <קובץ> <יעד.png> <v|h> */
public class Preview {
    static final float S = 2f;               // "צפיפות" כמו בטלפון
    static final float MAXW = 150 * S, PAD = 8 * S;
    static Font font, small, label;
    static BufferedImage scratch = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);

    static List<String> wrap(String t, Font f) {
        FontMetrics fm = scratch.createGraphics().getFontMetrics(f);
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : t.split(" ")) {
            String cand = line.length() == 0 ? w : line + " " + w;
            if (fm.stringWidth(cand) > MAXW && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(w); }
            else line = new StringBuilder(cand);
        }
        out.add(line.toString());
        return out;
    }

    public static void main(String[] a) throws Exception {
        font = new Font("DejaVu Sans", Font.PLAIN, Math.round(15 * S));
        small = new Font("DejaVu Sans", Font.PLAIN, Math.round(10 * S));
        label = new Font("DejaVu Sans", Font.ITALIC, Math.round(12 * S));
        Model.Project p = OpmlIO.read(new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8));
        boolean vert = a[2].equals("v");
        boolean cmp = a.length > 3 && a[3].equals("c");
        if (a.length > 4) Edit.showToLevel(p, Integer.parseInt(a[4]));
        MapLayout L = MapLayout.build(p, vert, new MapLayout.Measurer() {
            public float[] measure(MapLayout.Node n) {
                Font f = n.isDivision() ? label : font;
                FontMetrics fm = scratch.createGraphics().getFontMetrics(f);
                List<String> lines = wrap(n.text + (n.hidden > 0 ? "  ▸ " + n.hidden : ""), f);
                float w = 0;
                for (String l : lines) w = Math.max(w, fm.stringWidth(l));
                float markW = n.mark.isEmpty() ? 0 : scratch.createGraphics().getFontMetrics(small).stringWidth(n.mark) + 4 * S;
                return new float[]{w + 2 * PAD + markW, lines.size() * fm.getHeight() + 2 * PAD * (n.isDivision() ? .5f : 1)};
            }
        }, 26 * S, 10 * S, cmp);
        float M = 16 * S;
        BufferedImage img = new BufferedImage((int) (L.width + 2 * M), (int) (L.height + 2 * M), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.translate(M, M);
        g.setColor(new Color(0xB4B2A9));
        g.setStroke(new BasicStroke(1.4f * S));
        for (MapLayout.Node n : L.nodes)
            for (MapLayout.Node c : n.children) {
                float[] e = L.edge(n, c);
                for (int i = 0; i + 3 < e.length; i += 2)
                    g.drawLine(Math.round(e[i]), Math.round(e[i + 1]), Math.round(e[i + 2]), Math.round(e[i + 3]));
            }
        FontRenderContext frc = g.getFontRenderContext();
        for (MapLayout.Node n : L.nodes) {
            int depth = n.isDivision() ? n.depth - 1 : n.depth;
            RoundRectangle2D r = new RoundRectangle2D.Float(n.x, n.y, n.w, n.h, 10 * S, 10 * S);
            if (n.isDivision()) {
                g.setColor(Color.WHITE);
                g.fill(r);
                g.setColor(new Color(Palette.border(n.hue, depth)));
                g.setStroke(new BasicStroke(1.2f * S, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, new float[]{5 * S, 4 * S}, 0));
                g.draw(r);
            } else {
                g.setColor(new Color(Palette.fill(n.hue, depth)));
                g.fill(r);
                g.setColor(new Color(Palette.border(n.hue, depth)));
                g.setStroke(n.loose ? new BasicStroke(1.2f * S, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, new float[]{5 * S, 4 * S}, 0)
                        : new BasicStroke(Palette.borderWidth(depth, S)));
                g.draw(r);
            }
            Font f = n.isDivision() ? label : font;
            Color tc = n.isDivision() ? new Color(Palette.border(n.hue, depth)) : new Color(Palette.text(n.hue, depth));
            List<String> lines = wrap(n.text + (n.hidden > 0 ? "  ▸ " + n.hidden : ""), f);
            FontMetrics fm = g.getFontMetrics(f);
            float y = n.y + (n.isDivision() ? PAD * .5f : PAD) + fm.getAscent();
            for (String l : lines) {
                TextLayout tl = new TextLayout(l, f, frc);
                g.setColor(tc);
                tl.draw(g, n.x + (n.w - (float) tl.getAdvance()) / 2, y);
                y += fm.getHeight();
            }
            if (!n.mark.isEmpty()) {
                TextLayout tl = new TextLayout(n.mark, small, frc);
                g.setColor(n.isDivision() ? tc : new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), 170));
                tl.draw(g, n.x + n.w - (float) tl.getAdvance() - 4 * S, n.y + 3 * S + tl.getAscent());
            }
        }
        javax.imageio.ImageIO.write(img, "png", new File(a[1]));
        System.out.println(img.getWidth() + "x" + img.getHeight());
    }
}
