/*

package principal;

import com.fazecast.jSerialComm.*;

public class Principal {

    public static void main(String[] args) {

        SerialPort[] array_ports = SerialPort.getCommPorts();

        for (SerialPort port : array_ports) {
            System.out.println(port.getDescriptivePortName());
        }
    }
}


*/


package principal;

import javax.swing.SwingUtilities;

public class Principal {

    public static void main(String[] args) {
        // Ejecutamos la interfaz en el hilo de eventos de Swing
        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}