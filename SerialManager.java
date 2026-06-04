package principal;

import com.fazecast.jSerialComm.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestiona la conexión UART con jSerialComm.
 *
 * ─ TX: AA 55 SP_HI SP_LO KP KI KD PB_HI PB_LO  (9 bytes)
 * ─ RX: AA 55 DIST_HI DIST_LO PWM_HI PWM_LO PB_HI PB_LO  (8 bytes)
 *
 * La lectura corre en su propio hilo demonio.
 * Los datos recibidos se notifican mediante el callback RxListener.
 */
public class SerialManager {

    // ── Callback para datos recibidos ─────────────────────────────────────────
    public interface RxListener {
        /**
         * Llamado en el hilo de lectura (NO en el EDT).
         * Usar SwingUtilities.invokeLater() en la implementación para tocar UI.
         *
         * @param distMm   distancia medida por el sensor (mm)
         * @param pwmUs    PWM actual aplicado al servo   (μs)
         * @param pwmBaseUs PWM base reportado por la FPGA (μs)
         */
        void onDataReceived(int distMm, int pwmUs, int pwmBaseUs);
    }

    // ── Estado ────────────────────────────────────────────────────────────────
    private SerialPort  port     = null;
    private Thread      rxThread = null;
    private volatile boolean running = false;
    private RxListener  listener = null;

    // ── Puertos disponibles ───────────────────────────────────────────────────

    /** Lista de nombres de puertos detectados (ej: "COM3", "/dev/ttyUSB0"). */
    public static List<String> availablePorts() {
        List<String> names = new ArrayList<>();
        for (SerialPort p : SerialPort.getCommPorts()) {
            names.add(p.getSystemPortName());
        }
        return names;
    }

    // ── Conexión / desconexión ────────────────────────────────────────────────

    /**
     * Abre el puerto especificado a 9600 baud e inicia el hilo de lectura.
     *
     * @param portName nombre del puerto ("COM3", "/dev/ttyUSB0", etc.)
     * @param listener callback para tramas recibidas
     * @throws Exception si el puerto no se puede abrir
     */
    public void connect(String portName, RxListener listener) throws Exception {
        if (isConnected()) disconnect();

        this.listener = listener;
        port = SerialPort.getCommPort(portName);
        port.setBaudRate(9600);
        port.setNumDataBits(8);
        port.setNumStopBits(SerialPort.ONE_STOP_BIT);
        port.setParity(SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0);

        if (!port.openPort()) {
            port = null;
            throw new Exception("No se pudo abrir el puerto: " + portName);
        }

        running  = true;
        rxThread = new Thread(this::readLoop, "SerialRxThread");
        rxThread.setDaemon(true);
        rxThread.start();
    }

    /** Cierra el puerto y detiene el hilo de lectura. */
    public void disconnect() {
        running = false;
        if (rxThread != null) {
            try { rxThread.join(500); } catch (InterruptedException ignored) {}
            rxThread = null;
        }
        if (port != null && port.isOpen()) {
            port.closePort();
        }
        port     = null;
        listener = null;
    }

    /** Devuelve true si hay un puerto abierto. */
    public boolean isConnected() {
        return port != null && port.isOpen();
    }

    /** Nombre del puerto actualmente abierto, o null. */
    public String connectedPortName() {
        return (port != null) ? port.getSystemPortName() : null;
    }

    // ── Escritura TX ──────────────────────────────────────────────────────────

    /**
     * Envía una trama de 9 bytes al microcontrolador.
     *
     * @param frame  bytes producidos por {@link PIDController#buildTxFrame()}
     * @throws Exception si el puerto está cerrado o la escritura falla
     */
    public void sendFrame(byte[] frame) throws Exception {
        if (!isConnected()) throw new Exception("Puerto no conectado");
        int written = port.writeBytes(frame, frame.length);
        if (written < 0) throw new Exception("Error al escribir en el puerto");
    }

    // ── Hilo de lectura RX ───────────────────────────────────────────────────

    /**
     * Máquina de estados:
     *   0 → espera 0xAA
     *   1 → espera 0x55
     *   2 → acumula 6 bytes de datos
     */
    private void readLoop() {
        int   state = 0;
        int[] buf   = new int[6];
        int   idx   = 0;
        byte[] tmp  = new byte[1];

        while (running) {
            try {
                if (port == null || !port.isOpen()) break;

                int n = port.readBytes(tmp, 1);
                if (n <= 0) continue;

                int b = tmp[0] & 0xFF;   // byte sin signo

                switch (state) {
                    case 0:
                        if (b == 0xAA) state = 1;
                        break;
                    case 1:
                        state = (b == 0x55) ? 2 : 0;
                        if (state == 2) idx = 0;
                        break;
                    case 2:
                        buf[idx++] = b;
                        if (idx == 6) {
                            // DIST_HI DIST_LO PWM_HI PWM_LO PB_HI PB_LO
                            int dist = buf[0] * 256 + buf[1];
                            int pwm  = buf[2] * 256 + buf[3];
                            int pb   = buf[4] * 256 + buf[5];
                            if (listener != null) {
                                listener.onDataReceived(dist, pwm, pb);
                            }
                            state = 0;
                        }
                        break;
                }
            } catch (Exception e) {
                // Puerto cerrado o error de lectura → salir del hilo
                break;
            }
        }
    }
}