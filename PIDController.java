package principal;

/**
 * Modelo que almacena y valida los parámetros de control PID
 * más el setpoint y la base PWM.
 *
 * Tambien construye la trama TX:
 *   AA 55 SP_HI SP_LO KP KI KD PB_HI PB_LO  (9 bytes)
 */ 
public class PIDController {

    // ── Límites de la trama ──────────────────────────────────────────────────
    public static final int SP_MIN  =   80;   // mm
    public static final int SP_MAX  =  420;   // mm
    public static final int SP_DEF  =  250;

    public static final double KP_MIN =  0.0;
    public static final double KP_MAX = 20.0;
    public static final double KP_DEF =  1.0;

    public static final double KI_MIN =  0.0;
    public static final double KI_MAX = 20.0;
    public static final double KI_DEF =  0.0;

    public static final double KD_MIN =  0.0;
    public static final double KD_MAX = 20.0;
    public static final double KD_DEF =  0.0;

    public static final int PB_MIN  = 1000;   // μs
    public static final int PB_MAX  = 2000;   // μs
    public static final int PB_DEF  = 1440;

    // ── Estado actual ─────────────────────────────────────────────────────────
    private int    setpoint = SP_DEF;
    private double kp       = KP_DEF;
    private double ki       = KI_DEF;
    private double kd       = KD_DEF;
    private int    pwmBase  = PB_DEF;

    // ── Getters / Setters con validación ──────────────────────────────────────

    public int getSetpoint()         { return setpoint; }
    public void setSetpoint(int v)   { setpoint = clamp(v, SP_MIN, SP_MAX); }

    public double getKp()            { return kp; }
    public void setKp(double v)      { kp = clamp(v, KP_MIN, KP_MAX); }

    public double getKi()            { return ki; }
    public void setKi(double v)      { ki = clamp(v, KI_MIN, KI_MAX); }

    public double getKd()            { return kd; }
    public void setKd(double v)      { kd = clamp(v, KD_MIN, KD_MAX); }

    public int getPwmBase()          { return pwmBase; }
    public void setPwmBase(int v)    { pwmBase = clamp(v, PB_MIN, PB_MAX); }

    // ── Construir trama TX ────────────────────────────────────────────────────

    /**
     * Devuelve los 9 bytes listos para escribir al puerto serial.
     *
     * TX: AA 55 SP_HI SP_LO KP KI KD PB_HI PB_LO
     *
     * KP/KI/KD se escalan ×10 y se limitan a 200 (caben en 1 byte sin signo).
     */
    public byte[] buildTxFrame() {
        int sp = setpoint;
        int kpB = Math.min((int)(kp * 10), 200);
        int kiB = Math.min((int)(ki * 10), 200);
        int kdB = Math.min((int)(kd * 10), 200);
        int pb  = pwmBase;

        return new byte[]{
            (byte) 0xAA, (byte) 0x55,
            (byte)(sp >> 8),  (byte)(sp & 0xFF),
            (byte) kpB, (byte) kiB, (byte) kdB,
            (byte)(pb >> 8),  (byte)(pb & 0xFF)
        };
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

   
    public int pwmBaseCycles() { return pwmBase * 50; }

    
    public int pwmBasePercent() { return Math.max(0, Math.min(100, pwmBase - 1000)); }

 

    private static int clamp(int v, int mn, int mx)       { return Math.max(mn, Math.min(mx, v)); }
    private static double clamp(double v, double mn, double mx) { return Math.max(mn, Math.min(mx, v)); }
}