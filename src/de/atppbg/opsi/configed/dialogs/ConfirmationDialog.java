package de.atppbg.opsi.configed.dialogs;

import de.uib.configed.Globals;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static de.atppbg.opsi.configed.Main.exceptionHandler;
import static de.atppbg.opsi.configed.Main.resourceBundle;

public class ConfirmationDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    public final BlockingQueue<Boolean> resultQueue;

    /**
     * Zeigt ein Bestätigungs-Modal an
     * @param message Nachricht, die angezeigt werden soll
     * @param buttonOKText Text, der für den OK-Button verwendet werden soll.
     *                     Darf <code>null</code> sein.
     * @param buttonCancelText Text, der für den Abbrechen-Button verwendet werden soll.
     *                         Darf <code>null</code> sein.
     */
    public ConfirmationDialog(String message, String buttonOKText, String buttonCancelText) {
        this.setVisible(false);
        this.setupElements(message, buttonOKText, buttonCancelText);

        buttonOK.addActionListener(e -> onOK());
        buttonCancel.addActionListener(e -> onCancel());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        contentPane.registerKeyboardAction(
                e -> onCancel(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        );

        this.resultQueue = new LinkedBlockingQueue<>(1);
        String[] lines = message.split("\n");
        Optional<String> longestLine = Arrays.stream(lines).max((o1, o2) -> o2.length() - o1.length());

        setTitle(resourceBundle.getString("confirmationModal.title"));
        setIconImage(Globals.mainIcon);
        setMinimumSize(new Dimension(Math.max(300, longestLine.orElse("").length() * 6), 50 * (lines.length + 1)));
        setVisible(true);
    }

    private void setupElements(String message, String buttonOKText, String buttonCancelText) {
        contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 1;
        contentPane.add(new JLabel(message), constraints);

        JPanel buttonGroup = new JPanel();
        buttonOK = new JButton(buttonOKText);
        buttonCancel = new JButton(buttonCancelText);
        buttonGroup.add(buttonOK, constraints);
        buttonGroup.add(buttonCancel, constraints);

        constraints.gridy = 2;
        contentPane.add(buttonGroup, constraints);

        this.setContentPane(contentPane);
        this.setModal(true);
        this.getRootPane().setDefaultButton(buttonOK);
    }

    private void onOK() {
        dispose();
        try {
            resultQueue.put(true);
        } catch (InterruptedException e) {
            exceptionHandler.uncaughtException(Thread.currentThread(), e);
        }
    }

    private void onCancel() {
        dispose();
        try {
            resultQueue.put(false);
        } catch (InterruptedException e) {
            exceptionHandler.uncaughtException(Thread.currentThread(), e);
        }
    }
}
