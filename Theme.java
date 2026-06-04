package principal;
import java.awt.Color;
import java.awt.Font;

/**
 * Paleta de colores y fuentes del tema oscuro.
 * Equivalente a las constantes globales del script Python.
 */
public class Theme {

    // ── Colores ──────────────────────────────────────────────────────────────
    public static final Color BG         = hex("#0E0F15");
    public static final Color SURFACE    = hex("#1A1D27");
    public static final Color CARD       = hex("#1F2235");
    public static final Color BORDER     = hex("#2E3248");
    public static final Color ACCENT     = hex("#4F8EF7");
    public static final Color ACCENT2    = hex("#7C5CFC");
    public static final Color TEXT       = hex("#E8EAF6");
    public static final Color TEXT_MUTED = hex("#7A7F9A");
    public static final Color SUCCESS    = hex("#1D9E75");
    public static final Color WARNING    = hex("#EF9F27");
    public static final Color DANGER     = hex("#E24B4A");

    // ── Fuentes ───────────────────────────────────────────────────────────────
    public static final Font FONT_TITLE  = new Font("Courier New", Font.BOLD,  22);
    public static final Font FONT_SUB    = new Font("Courier New", Font.PLAIN, 12);
    public static final Font FONT_BIG    = new Font("Courier New", Font.BOLD,  24);
    public static final Font FONT_MED    = new Font("Courier New", Font.BOLD,  16);
    public static final Font FONT_NORMAL = new Font("Courier New", Font.PLAIN, 12);
    public static final Font FONT_SMALL  = new Font("Courier New", Font.PLAIN, 11);
    public static final Font FONT_TINY   = new Font("Courier New", Font.PLAIN, 10);
    public static final Font FONT_LABEL  = new Font("Courier New", Font.BOLD,  13);
    public static final Font FONT_SECTION= new Font("Courier New", Font.BOLD,  10);

    /** Convierte un string "#RRGGBB" en Color. */
    public static Color hex(String h) {
        return Color.decode(h);
    }

    private Theme() {}   
}