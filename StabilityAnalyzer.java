package principal;

import java.awt.Color;

/**
 * Calcula la puntuación de estabilidad estimada dados Kp, Ki, Kd.
 * Equivalente a stability_score / stability_label / stability_hint en Python.
 */
public class StabilityAnalyzer {

    public static final int SCORE_GOOD     = 70;
    public static final int SCORE_UNSTABLE = 40;

    /** Resultado inmutable que agrupa score + label + color + hint. */
    public static class Result {
        public final int    score;
        public final String label;
        public final Color  color;
        public final String hint;

        Result(int score, String label, Color color, String hint) {
            this.score = score;
            this.label = label;
            this.color = color;
            this.hint  = hint;
        }
    }

    /**
     * Evalúa los parámetros PID y devuelve un Result completo.
     *
     * @param kp  Ganancia proporcional  (0–20)
     * @param ki  Ganancia integral      (0–20)
     * @param kd  Ganancia derivativa    (0–20)
     */
    public static Result analyze(double kp, double ki, double kd) {
        int score = 100;

        if (kp > 80) score -= 25;
        if (ki > 8)  score -= 20;
        if (ki == 0) score -= 10;

        double ratio = kd / Math.max(kp, 1.0);
        if (ratio < 0.2 || ratio > 0.8) score -= 20;
        if (kd > 50) score -= 15;

        score = Math.max(5, Math.min(100, score));

        String label;
        Color  color;
        if (score >= SCORE_GOOD) {
            label = "Buena";
            color = Theme.SUCCESS;
        } else if (score >= SCORE_UNSTABLE) {
            label = "Inestable";
            color = Theme.WARNING;
        } else {
            label = "Peligroso";
            color = Theme.DANGER;
        }

        String hint;
        if (score >= SCORE_GOOD) {
            hint = "Los valores actuales ofrecen buena respuesta.";
        } else if (kp > 80) {
            hint = "Kp alto puede causar oscilaciones.";
        } else if (ki > 8) {
            hint = "Ki alto puede causar windup.";
        } else {
            hint = "Ajusta Kd para amortiguar la respuesta.";
        }

        return new Result(score, label, color, hint);
    }

    private StabilityAnalyzer() {}
}