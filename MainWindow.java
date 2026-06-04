package principal;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Ventana principal de la aplicación Ball & Beam PID.
 * Equivalente a la clase App(ctk.CTk) del script Python.
 *
 * Organización:
 *   - buildHeader()        → Título y subtítulo
 *   - buildConnectionPanel()→ Selección de puerto + botón conectar
 *   - buildTelemetryPanel() → Distancia, PWM, barras en tiempo real
 *   - buildSetpointPanel()  → Slider setpoint
 *   - buildPidPanel()       → Sliders Kp/Ki/Kd + badge estabilidad
 *   - buildPwmBasePanel()   → Slider PWM base + métricas FPGA
 *   - buildSendButton()     → Botón "Enviar a FPGA"
 */
public class MainWindow extends JFrame {

    // ── Dependencias ──────────────────────────────────────────────────────────
    private final PIDController  pid    = new PIDController();
    private final SerialManager  serial = new SerialManager();

    // ── Widgets de telemetría ─────────────────────────────────────────────────
    private JLabel lblDist, lblSpRt, lblPwm, lblPbRt;
    private JLabel lblPct, lblErr;
    private JPanel pwmBar, errBar, pbMarker;
    private JPanel pwmBarBg, errBarBg;

    // ── Widgets de setpoint ───────────────────────────────────────────────────
    private JSlider sliderSP;
    private JLabel  lblSetpointVal;
    private JLabel  lblSpRtTop;

    // ── Widgets PID ───────────────────────────────────────────────────────────
    private JSlider sliderKp, sliderKi, sliderKd;
    private JLabel  lblKpVal, lblKiVal, lblKdVal;
    private JLabel  badgeLbl, hintLbl;
    private JPanel  stabBar;
    private JPanel  stabBarBg;

    // ── Widgets PWM Base ──────────────────────────────────────────────────────
    private JSlider sliderPB;
    private JLabel  lblPbVal;
    private JLabel  lblPbCycles, lblPbPct;
    private JLabel  lblPbRtCard;

    // ── Conexión ──────────────────────────────────────────────────────────────
    private JComboBox<String> portCombo;
    private JButton           btnConnect;
    private JLabel            connStatus;
    private JLabel            sendStatus;

    // ── Scroll ────────────────────────────────────────────────────────────────
    private JPanel innerPanel;

    // =========================================================================
    public MainWindow() {
        super("LEVITATOR — Panel PID");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(620, 980);
        setMinimumSize(new Dimension(540, 800));
        setBackground(Theme.BG);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { onClose(); }
        });

        buildScrollWrapper();
        buildUI();
        setLocationRelativeTo(null);
        updateStaticUI();
    }

    // ── Scroll wrapper ────────────────────────────────────────────────────────

    private void buildScrollWrapper() {
        innerPanel = new JPanel();
        innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.Y_AXIS));
        innerPanel.setBackground(Theme.BG);

        JScrollPane scroll = new JScrollPane(innerPanel);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(null);
        scroll.setBackground(Theme.BG);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getVerticalScrollBar().setBackground(Theme.SURFACE);

        getContentPane().setBackground(Theme.BG);
        getContentPane().add(scroll, BorderLayout.CENTER);
    }

    // ── Construcción UI ───────────────────────────────────────────────────────

    private void buildUI() {
        buildHeader();
        buildConnectionPanel();
        buildTelemetryPanel();
        buildSetpointPanel();
        buildPidPanel();
        buildPwmBasePanel();
        buildSendButton();
        innerPanel.add(Box.createVerticalStrut(20));
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void buildHeader() {
        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setBackground(Theme.SURFACE);
        hdr.setBorder(new EmptyBorder(18, 24, 16, 24));
        hdr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);

        JLabel title = new JLabel("LEVITATOR");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.ACCENT);

        JLabel sub = new JLabel("Panel de control PID  •  UART 9600");
        sub.setFont(Theme.FONT_SUB);
        sub.setForeground(Theme.TEXT_MUTED);

        col.add(title);
        col.add(Box.createVerticalStrut(4));
        col.add(sub);
        hdr.add(col, BorderLayout.WEST);

        innerPanel.add(hdr);
        innerPanel.add(Box.createVerticalStrut(4));
    }

    // ── Sección: Conexión ─────────────────────────────────────────────────────

    private void buildConnectionPanel() {
        sectionLabel("Conexión serial");
        JPanel card = card();

        // Fila puerto
        JPanel row = transparentRow();
        JLabel lbl = muted("Puerto COM", 100);
        row.add(lbl);

        portCombo = new JComboBox<>();
        portCombo.setFont(Theme.FONT_NORMAL);
        portCombo.setBackground(Theme.SURFACE);
        portCombo.setForeground(Theme.TEXT);
        refreshPorts();

        row.add(portCombo);
        row.add(Box.createHorizontalStrut(6));

        JButton btnRefresh = smallButton("↺");
        btnRefresh.addActionListener(e -> refreshPorts());
        row.add(btnRefresh);

        card.add(wrap(row, 12, 0, 8, 0));

        // Botón conectar
        btnConnect = new JButton("Conectar");
        styleButton(btnConnect, Theme.SUCCESS);
        btnConnect.addActionListener(e -> toggleConnect());
        card.add(wrapFill(btnConnect, 12, 0, 8, 8));

        // Estado
        connStatus = new JLabel("● Desconectado");
        connStatus.setFont(Theme.FONT_SMALL);
        connStatus.setForeground(Theme.DANGER);
        card.add(wrap(connStatus, 12, 0, 10, 0));

        innerPanel.add(card);
    }

    // ── Sección: Telemetría ───────────────────────────────────────────────────

    private void buildTelemetryPanel() {
        sectionLabel("Telemetría en tiempo real");
        JPanel card = card();

        // Fila superior: Distancia + Setpoint
        JPanel rowTop = new JPanel(new GridLayout(1, 2, 8, 0));
        rowTop.setOpaque(false);
        rowTop.setBorder(new EmptyBorder(12, 12, 6, 12));

        lblDist  = bigValueLabel("--- mm", Theme.ACCENT);
        lblSpRt  = bigValueLabel("250 mm", Theme.ACCENT2);
        rowTop.add(metricBox("Distancia medida", lblDist));
        rowTop.add(metricBox("Setpoint",         lblSpRt));
        card.add(rowTop);

        // Fila inferior: PWM actual + PWM base
        JPanel rowPwm = new JPanel(new GridLayout(1, 2, 8, 0));
        rowPwm.setOpaque(false);
        rowPwm.setBorder(new EmptyBorder(0, 12, 6, 12));

        lblPwm   = bigValueLabel("--- \u03bcs", Theme.WARNING);
        lblPbRt  = bigValueLabel("--- \u03bcs", Theme.SUCCESS);
        rowPwm.add(metricBox("PWM actual",     lblPwm));
        rowPwm.add(metricBox("PWM base (FPGA)", lblPbRt));
        card.add(rowPwm);

        // Barra throttle
        card.add(mutedLabelLeft("Throttle PWM  (1000\u03bcs \u2014 2000\u03bcs)", 12));
        pwmBarBg = barBackground();
        pwmBar   = barFill(Theme.SUCCESS);
        pbMarker = new JPanel();
        pbMarker.setBackground(Theme.ACCENT2);
        pwmBarBg.add(pwmBar);
        pwmBarBg.add(pbMarker);
        card.add(wrapFill(pwmBarBg, 12, 2, 2, 0));

        lblPct = new JLabel("-- %");
        lblPct.setFont(Theme.FONT_SMALL);
        lblPct.setForeground(Theme.TEXT_MUTED);
        card.add(wrap(lblPct, 12, 0, 4, 0));

        // Barra error
        card.add(mutedLabelLeft("Error  (dist \u2212 setpoint)", 12));
        errBarBg = barBackground();
        errBarBg.setLayout(null);
        errBar   = new JPanel();
        errBar.setBackground(Theme.ACCENT);
        errBarBg.add(errBar);
        card.add(wrapFill(errBarBg, 12, 2, 4, 0));

        lblErr = new JLabel("error: 0 mm");
        lblErr.setFont(Theme.FONT_SMALL);
        lblErr.setForeground(Theme.TEXT_MUTED);
        card.add(wrap(lblErr, 12, 0, 12, 0));

        innerPanel.add(card);
    }

    // ── Sección: Setpoint ─────────────────────────────────────────────────────

    private void buildSetpointPanel() {
        sectionLabel("Setpoint de distancia");
        JPanel card = card();

        sliderSP  = styledSlider(PIDController.SP_MIN, PIDController.SP_MAX, PIDController.SP_DEF, 1);
        lblSpRtTop = new JLabel();
        lblSpRtTop.setFont(Theme.FONT_NORMAL);
        lblSpRtTop.setForeground(Theme.TEXT);

        sliderSP.addChangeListener(e -> {
            pid.setSetpoint(sliderSP.getValue());
            updateStaticUI();
        });

        card.add(sliderRow("Altura objetivo", sliderSP, lblSpRtTop, "mm"));

        JPanel mf = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        mf.setOpaque(false);
        mf.setBorder(new EmptyBorder(4, 12, 12, 0));

        lblSetpointVal = new JLabel("250 mm");
        lblSetpointVal.setFont(Theme.FONT_MED);
        lblSetpointVal.setForeground(Theme.TEXT);

        mf.add(smallMetric("Setpoint",    lblSetpointVal));
        mf.add(smallMetric("Rango sensor", label("80\u2013420 mm")));
        card.add(mf);

        innerPanel.add(card);
    }

    // ── Sección: PID ──────────────────────────────────────────────────────────

    private void buildPidPanel() {
        sectionLabel("Parámetros PID");
        JPanel card = card();

        sliderKp = styledSlider(0, 200, (int)(PIDController.KP_DEF * 10), 1);
        sliderKi = styledSlider(0, 200, (int)(PIDController.KI_DEF * 10), 1);
        sliderKd = styledSlider(0, 200, (int)(PIDController.KD_DEF * 10), 1);

        lblKpVal = new JLabel("1.0"); lblKpVal.setFont(Theme.FONT_NORMAL); lblKpVal.setForeground(Theme.TEXT);
        lblKiVal = new JLabel("0.0"); lblKiVal.setFont(Theme.FONT_NORMAL); lblKiVal.setForeground(Theme.TEXT);
        lblKdVal = new JLabel("0.0"); lblKdVal.setFont(Theme.FONT_NORMAL); lblKdVal.setForeground(Theme.TEXT);

        sliderKp.addChangeListener(e -> { pid.setKp(sliderKp.getValue() / 10.0); updateStaticUI(); });
        sliderKi.addChangeListener(e -> { pid.setKi(sliderKi.getValue() / 10.0); updateStaticUI(); });
        sliderKd.addChangeListener(e -> { pid.setKd(sliderKd.getValue() / 10.0); updateStaticUI(); });

        card.add(sliderRow("Kp  proporcional", sliderKp, lblKpVal, ""));
        card.add(sliderRow("Ki  integral",     sliderKi, lblKiVal, ""));
        card.add(sliderRow("Kd  derivativo",   sliderKd, lblKdVal, ""));

        // Separador
        JSeparator sep = new JSeparator();
        sep.setForeground(Theme.BORDER);
        sep.setBackground(Theme.BORDER);
        card.add(wrapFill(sep, 12, 8, 8, 0));

        // Badge estabilidad
        JPanel stabRow = new JPanel(new BorderLayout());
        stabRow.setOpaque(false);
        stabRow.setBorder(new EmptyBorder(0, 12, 4, 12));

        JLabel stabTitle = new JLabel("Estabilidad estimada");
        stabTitle.setFont(Theme.FONT_NORMAL);
        stabTitle.setForeground(Theme.TEXT_MUTED);
        stabRow.add(stabTitle, BorderLayout.WEST);

        badgeLbl = new JLabel("Buena", SwingConstants.CENTER);
        badgeLbl.setFont(new Font("Courier New", Font.BOLD, 11));
        badgeLbl.setForeground(Color.WHITE);
        badgeLbl.setBackground(Theme.SUCCESS);
        badgeLbl.setOpaque(true);
        badgeLbl.setBorder(new EmptyBorder(3, 10, 3, 10));
        badgeLbl.setPreferredSize(new Dimension(90, 22));
        stabRow.add(badgeLbl, BorderLayout.EAST);
        card.add(stabRow);

        // Barra estabilidad
        stabBarBg = barBackground();
        stabBar   = barFill(Theme.SUCCESS);
        stabBarBg.add(stabBar);
        card.add(wrapFill(stabBarBg, 12, 2, 4, 0));

        // Hint
        hintLbl = new JLabel("<html>Los valores actuales ofrecen buena respuesta.</html>");
        hintLbl.setFont(Theme.FONT_SMALL);
        hintLbl.setForeground(Theme.TEXT_MUTED);
        card.add(wrap(hintLbl, 12, 0, 12, 0));

        innerPanel.add(card);
    }

    // ── Sección: PWM Base ─────────────────────────────────────────────────────

    private void buildPwmBasePanel() {
        sectionLabel("PWM Base (punto de operación)");
        JPanel card = card();

        sliderPB = styledSlider(PIDController.PB_MIN, PIDController.PB_MAX, PIDController.PB_DEF, 1);
        lblPbVal = new JLabel(String.valueOf(PIDController.PB_DEF));
        lblPbVal.setFont(Theme.FONT_NORMAL);
        lblPbVal.setForeground(Theme.TEXT);

        sliderPB.addChangeListener(e -> { pid.setPwmBase(sliderPB.getValue()); updateStaticUI(); });

        card.add(sliderRow("PWM base", sliderPB, lblPbVal, "\u03bcs"));

        JPanel mf3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        mf3.setOpaque(false);
        mf3.setBorder(new EmptyBorder(4, 12, 6, 0));

        lblPbCycles = new JLabel("72000");
        lblPbCycles.setFont(Theme.FONT_MED);
        lblPbCycles.setForeground(Theme.TEXT);

        lblPbPct = new JLabel("44 %");
        lblPbPct.setFont(Theme.FONT_MED);
        lblPbPct.setForeground(Theme.TEXT);

        mf3.add(smallMetric("Ciclos FPGA",    lblPbCycles));
        mf3.add(smallMetric("Throttle base",  lblPbPct));
        card.add(mf3);

        JLabel warn = new JLabel("<html>\u26a0  Ajusta hasta que la pelota flote sin PID (Kp=Ki=Kd=0).</html>");
        warn.setFont(Theme.FONT_SMALL);
        warn.setForeground(Theme.WARNING);
        card.add(wrap(warn, 12, 0, 12, 0));

        innerPanel.add(card);
    }

    // ── Botón enviar ──────────────────────────────────────────────────────────

    private void buildSendButton() {
        JButton btn = new JButton("\u25b6  Enviar a FPGA");
        btn.setFont(Theme.FONT_LABEL);
        btn.setForeground(Color.WHITE);
        btn.setBackground(Theme.ACCENT2);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setPreferredSize(new Dimension(Integer.MAX_VALUE, 44));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(Theme.hex("#6340E0")); }
            @Override public void mouseExited (MouseEvent e) { btn.setBackground(Theme.ACCENT2); }
        });
        btn.addActionListener(e -> sendParams());

        JPanel bp = new JPanel(new BorderLayout());
        bp.setOpaque(false);
        bp.setBorder(new EmptyBorder(10, 20, 4, 20));
        bp.add(btn);
        innerPanel.add(bp);

        sendStatus = new JLabel("", SwingConstants.CENTER);
        sendStatus.setFont(Theme.FONT_SMALL);
        sendStatus.setForeground(Theme.SUCCESS);
        innerPanel.add(sendStatus);

        JLabel footer = new JLabel("Ball & Beam PID  •  DE10-Lite  •  9600 baud", SwingConstants.CENTER);
        footer.setFont(Theme.FONT_TINY);
        footer.setForeground(Theme.TEXT_MUTED);
        innerPanel.add(footer);
    }

    // ── Lógica de conexión ────────────────────────────────────────────────────

    private void toggleConnect() {
        if (serial.isConnected()) {
            serial.disconnect();
            btnConnect.setText("Conectar");
            btnConnect.setBackground(Theme.SUCCESS);
            connStatus.setText("● Desconectado");
            connStatus.setForeground(Theme.DANGER);
        } else {
            String port = (String) portCombo.getSelectedItem();
            if (port == null || port.isBlank()) return;
            try {
                serial.connect(port, (dist, pwm, pb) ->
                    SwingUtilities.invokeLater(() -> updateRealtime(dist, pwm, pb))
                );
                btnConnect.setText("Desconectar");
                btnConnect.setBackground(Theme.DANGER);
                connStatus.setText("● Conectado  →  " + port);
                connStatus.setForeground(Theme.SUCCESS);

                // Enviar parámetros al conectar (con pequeño delay)
                Timer t = new Timer(500, e -> sendParams());
                t.setRepeats(false);
                t.start();

            } catch (Exception ex) {
                connStatus.setText("Error: " + ex.getMessage());
                connStatus.setForeground(Theme.WARNING);
            }
        }
    }

    private void refreshPorts() {
        List<String> ports = SerialManager.availablePorts();
        portCombo.removeAllItems();
        if (ports.isEmpty()) {
            portCombo.addItem("(ninguno)");
        } else {
            for (String p : ports) portCombo.addItem(p);
        }
    }

    // ── Envío de parámetros ───────────────────────────────────────────────────

    private void sendParams() {
        if (!serial.isConnected()) {
            showSendStatus("\u26a0 No conectado", Theme.WARNING, 2000);
            return;
        }
        try {
            serial.sendFrame(pid.buildTxFrame());
            String msg = String.format("✓  SP=%dmm  Kp=%.1f  Ki=%.1f  Kd=%.1f  Base=%d\u03bcs",
                pid.getSetpoint(), pid.getKp(), pid.getKi(), pid.getKd(), pid.getPwmBase());
            showSendStatus(msg, Theme.SUCCESS, 3000);
        } catch (Exception ex) {
            showSendStatus("Error: " + ex.getMessage(), Theme.DANGER, 3000);
        }
    }

    private void showSendStatus(String text, Color color, int ms) {
        sendStatus.setText(text);
        sendStatus.setForeground(color);
        Timer t = new Timer(ms, e -> sendStatus.setText(""));
        t.setRepeats(false);
        t.start();
    }

    // ── Actualización UI estática (sliders cambian) ───────────────────────────

    private void updateStaticUI() {
        // Setpoint
        int sp = pid.getSetpoint();
        if (lblSetpointVal != null) lblSetpointVal.setText(sp + " mm");
        if (lblSpRt        != null) lblSpRt.setText(sp + " mm");
        if (lblSpRtTop     != null) lblSpRtTop.setText(String.valueOf(sp));
        if (sliderSP       != null && sliderSP.getValue() != sp) sliderSP.setValue(sp);

        // Slider labels
        if (lblKpVal != null) lblKpVal.setText(String.format("%.1f", pid.getKp()));
        if (lblKiVal != null) lblKiVal.setText(String.format("%.1f", pid.getKi()));
        if (lblKdVal != null) lblKdVal.setText(String.format("%.1f", pid.getKd()));
        if (lblPbVal != null) lblPbVal.setText(String.valueOf(pid.getPwmBase()));

        // PWM base métricas
        if (lblPbCycles != null) lblPbCycles.setText(String.valueOf(pid.pwmBaseCycles()));
        if (lblPbPct    != null) lblPbPct.setText(pid.pwmBasePercent() + " %");

        // Marcador PWM base en barra throttle
        updatePbMarker(pid.getPwmBase());

        // Estabilidad
        StabilityAnalyzer.Result r = StabilityAnalyzer.analyze(pid.getKp(), pid.getKi(), pid.getKd());
        if (badgeLbl  != null) { badgeLbl.setText(r.label); badgeLbl.setBackground(r.color); }
        if (stabBar   != null) { stabBar.setBackground(r.color); setBarFill(stabBarBg, stabBar, r.score / 100.0); }
        if (hintLbl   != null) hintLbl.setText("<html>" + r.hint + "</html>");

        innerPanel.revalidate();
        innerPanel.repaint();
    }

    // ── Actualización UI en tiempo real (datos RX) ────────────────────────────

    private void updateRealtime(int dist, int pwmUs, int pbUs) {
        // Distancia
        if (lblDist != null) lblDist.setText(dist + " mm");

        // Error
        int err = dist - pid.getSetpoint();
        if (lblErr != null) lblErr.setText(String.format("error: %+d mm", err));

        // Barra de error (centrada)
        if (errBarBg != null && errBar != null) {
            double rel = Math.min(Math.abs(err) / 200.0, 0.5);
            Color ec = Math.abs(err) < 20 ? Theme.SUCCESS : Math.abs(err) < 60 ? Theme.WARNING : Theme.DANGER;
            errBar.setBackground(ec);
            int w = errBarBg.getWidth();
            int bw = (int)(rel * w);
            int bx = (err >= 0) ? w / 2 : w / 2 - bw;
            errBar.setBounds(bx, 0, bw, errBarBg.getHeight());
            errBarBg.repaint();
        }

        // PWM actual
        if (lblPwm != null) lblPwm.setText(pwmUs + " \u03bcs");
        int pct = Math.max(0, Math.min(100, pwmUs - 1000));
        if (lblPct != null) lblPct.setText("Throttle: " + pct + " %");

        Color pcol = pct < 60 ? Theme.SUCCESS : pct < 85 ? Theme.WARNING : Theme.DANGER;
        double relPwm = Math.max(0.0, Math.min(1.0, (pwmUs - 1000.0) / 1000.0));
        if (pwmBar != null) {
            pwmBar.setBackground(pcol);
            setBarFill(pwmBarBg, pwmBar, relPwm);
        }

        // PWM base
        if (lblPbRt != null) lblPbRt.setText(pbUs + " \u03bcs");
    }

    // ── Cierre ────────────────────────────────────────────────────────────────

    private void onClose() {
        serial.disconnect();
        dispose();
        System.exit(0);
    }

    // =========================================================================
    // ── Helpers de construcción de widgets ───────────────────────────────────
    // =========================================================================

    /** Panel de tarjeta oscura con borde. */
    private JPanel card() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Theme.CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(0, 20, 4, 20),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(0, 0, 0, 0)
            )
        ));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /** Etiqueta de sección (texto en mayúsculas, color muted). */
    private void sectionLabel(String text) {
        JLabel lbl = new JLabel(text.toUpperCase());
        lbl.setFont(Theme.FONT_SECTION);
        lbl.setForeground(Theme.TEXT_MUTED);
        lbl.setBorder(new EmptyBorder(14, 20, 2, 0));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        innerPanel.add(lbl);
    }

    /** Caja con título pequeño y label grande debajo. */
    private JPanel metricBox(String title, JLabel valueLbl) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(Theme.SURFACE);
        box.setBorder(new EmptyBorder(8, 12, 8, 12));

        JLabel t = new JLabel(title);
        t.setFont(Theme.FONT_TINY);
        t.setForeground(Theme.TEXT_MUTED);
        box.add(t);
        box.add(Box.createVerticalStrut(4));
        box.add(valueLbl);
        return box;
    }

    /** Label grande con color, para métricas de telemetría. */
    private JLabel bigValueLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_BIG);
        l.setForeground(color);
        return l;
    }

    /** Fila con label + slider + value label + unidad. */
    private JPanel sliderRow(String labelText, JSlider slider, JLabel valLabel, String unit) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(5, 12, 5, 12));

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(Theme.FONT_NORMAL);
        lbl.setForeground(Theme.TEXT_MUTED);
        lbl.setPreferredSize(new Dimension(160, 20));
        row.add(lbl, BorderLayout.WEST);

        JPanel center = new JPanel(new BorderLayout(4, 0));
        center.setOpaque(false);
        center.add(slider, BorderLayout.CENTER);

        valLabel.setFont(new Font("Courier New", Font.BOLD, 12));
        valLabel.setForeground(Theme.TEXT);
        valLabel.setPreferredSize(new Dimension(55, 20));
        valLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        center.add(valLabel, BorderLayout.EAST);

        if (!unit.isBlank()) {
            JLabel u = new JLabel(unit);
            u.setFont(Theme.FONT_SMALL);
            u.setForeground(Theme.TEXT_MUTED);
            u.setPreferredSize(new Dimension(28, 20));
            row.add(u, BorderLayout.EAST);
        }

        row.add(center, BorderLayout.CENTER);
        return row;
    }

    /** Slider con colores del tema. */
    private JSlider styledSlider(int min, int max, int val, int step) {
        JSlider s = new JSlider(min, max, val);
        s.setBackground(Theme.CARD);
        s.setForeground(Theme.ACCENT);
        s.setPaintTicks(false);
        s.setPaintLabels(false);
        s.setMajorTickSpacing((max - min) / 10);
        UIManager.put("Slider.thumbColor",   Theme.ACCENT);
        UIManager.put("Slider.foreground",   Theme.ACCENT2);
        return s;
    }

    /** Panel de barra de progreso (fondo). */
    private JPanel barBackground() {
        JPanel bg = new JPanel(null);
        bg.setBackground(Theme.SURFACE);
        bg.setPreferredSize(new Dimension(Integer.MAX_VALUE, 12));
        bg.setMaximumSize(new Dimension(Integer.MAX_VALUE, 12));
        return bg;
    }

    /** Relleno de barra de progreso. */
    private JPanel barFill(Color color) {
        JPanel fill = new JPanel();
        fill.setBackground(color);
        fill.setBounds(0, 0, 0, 12);
        return fill;
    }

    /** Actualiza el ancho relativo de una barra. */
    private void setBarFill(JPanel bg, JPanel fill, double rel) {
        if (bg.getWidth() == 0) return;
        int w = (int)(bg.getWidth() * Math.max(0, Math.min(1, rel)));
        fill.setBounds(0, 0, w, bg.getHeight());
        bg.repaint();
    }

    /** Actualiza la posición del marcador del PWM base. */
    private void updatePbMarker(int pbUs) {
        if (pbMarker == null || pwmBarBg == null) return;
        double rel = Math.max(0.0, Math.min(1.0, (pbUs - 1000.0) / 1000.0));
        int x = (int)(pwmBarBg.getWidth() * rel);
        pbMarker.setBounds(x, 0, 3, pwmBarBg.getHeight());
        pwmBarBg.repaint();
    }

    private JPanel transparentRow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        return p;
    }

    private JLabel muted(String text, int w) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_NORMAL);
        l.setForeground(Theme.TEXT_MUTED);
        if (w > 0) l.setPreferredSize(new Dimension(w, 20));
        return l;
    }

    private JLabel mutedLabelLeft(String text, int padLeft) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_TINY);
        l.setForeground(Theme.TEXT_MUTED);
        l.setBorder(new EmptyBorder(0, padLeft, 0, 0));
        return l;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_MED);
        l.setForeground(Theme.TEXT);
        return l;
    }

    private JButton smallButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Courier New", Font.PLAIN, 14));
        b.setForeground(Theme.TEXT);
        b.setBackground(Theme.SURFACE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setPreferredSize(new Dimension(36, 30));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void styleButton(JButton b, Color bg) {
        b.setFont(Theme.FONT_LABEL);
        b.setForeground(Color.WHITE);
        b.setBackground(bg);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setPreferredSize(new Dimension(Integer.MAX_VALUE, 36));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    /** Envuelve un componente con padding. */
    private JPanel wrap(JComponent c, int l, int t, int b, int r) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(t, l, b, r));
        p.add(c, BorderLayout.WEST);
        return p;
    }

    /** Envuelve con fill horizontal. */
    private JPanel wrapFill(JComponent c, int l, int t, int b, int r) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(t, l, b, r));
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    /** Caja de métrica pequeña con label y valor. */
    private JPanel smallMetric(String title, JLabel valLbl) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(Theme.SURFACE);
        box.setBorder(new EmptyBorder(6, 10, 6, 10));

        JLabel t = new JLabel(title);
        t.setFont(Theme.FONT_TINY);
        t.setForeground(Theme.TEXT_MUTED);
        box.add(t);
        box.add(Box.createVerticalStrut(2));
        box.add(valLbl);
        return box;
    }
}