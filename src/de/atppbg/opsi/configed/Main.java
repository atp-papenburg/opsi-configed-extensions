package de.atppbg.opsi.configed;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import de.atppbg.opsi.configed.task.TaskListFrame;
import de.atppbg.opsi.configed.task.TaskPlannerFrame;
import de.uib.configed.ConfigedMain;
import de.uib.configed.UncaughtConfigedExceptionHandlerLocalized;
import de.uib.configed.Configed;
import de.uib.messages.Messages;
import de.uib.opsicommand.sshcommand.SSHConnect;
import de.uib.opsicommand.sshcommand.SSHConnectionInfo;
import org.apache.commons.cli.CommandLine;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.ResourceBundle;

public class Main {
    public static ConfigedMain configedMain;
    public static UncaughtConfigedExceptionHandlerLocalized exceptionHandler;
    public static final ResourceBundle resourceBundle = ResourceBundle.getBundle("de.atppbg.opsi.configed.i18n");

    public static void main(String[] args) {
        Messages.setLocale("de_DE");
        Messages.getLocaleNames();

        CommandLine.Builder cmdLineBuilder = new CommandLine.Builder();
        for(String arg : args)
            cmdLineBuilder.addArg(arg);
        Configed.main(cmdLineBuilder.build());

        exceptionHandler = new UncaughtConfigedExceptionHandlerLocalized();

        try {
            Field appletField = Configed.class.getDeclaredField("configedMain");
            appletField.setAccessible(true);
            configedMain = (ConfigedMain) appletField.get(Configed.class);
        } catch (Exception ex) {
            System.out.printf((resourceBundle.getString("main.configedMainFailure")) + "%n", ex.getMessage());
            System.exit(1);
        }

        Thread.setDefaultUncaughtExceptionHandler(exceptionHandler);

        JMenu menu = new JMenu(resourceBundle.getString("main.menu.extensions"));

        JMenuItem taskPlanButton = new JMenuItem();
        taskPlanButton.setText(resourceBundle.getString("main.menu.planNewTask"));
        taskPlanButton.addActionListener(actionEvent -> new TaskPlannerFrame());
        taskPlanButton.setEnabled(false);

        menu.add(taskPlanButton);
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                taskPlanButton.setEnabled(configedMain.getSelectedClients().length > 0);
            }
            @Override
            public void menuDeselected(MenuEvent e) {

            }
            @Override
            public void menuCanceled(MenuEvent e) {

            }
        });

        JMenuItem taskListButton = new JMenuItem();
        taskListButton.setText(resourceBundle.getString("main.menu.showTaskList"));
        taskListButton.addActionListener(actionEvent -> new TaskListFrame());

        menu.add(taskListButton);

        try {
            int tries = 0;
            while(ConfigedMain.getMainFrame() == null && (tries++ < 600))
                Thread.sleep(100);

            if(ConfigedMain.getMainFrame() == null)
                throw new Exception(resourceBundle.getString("main.mainframeTimeout"));
        } catch (Exception ex) {
            System.out.printf((resourceBundle.getString("main.mainframeFailure")) + "%n", ex.getMessage());
            System.exit(1);
        }

        ConfigedMain.getMainFrame().getJMenuBar().add(menu);
    }

    public static String executeSSHCommand(String command) throws NoSuchFieldException, IllegalAccessException, JSchException, IOException {
        return executeSSHCommand(command, false);
    }

    public static String executeSSHCommand(String command, boolean asRoot) throws NoSuchFieldException, IllegalAccessException, JSchException, IOException {
        SSHConnect connection = new SSHConnect(configedMain);
        connection.connect();

        Field sessionField = connection.getClass().getDeclaredField("session");
        sessionField.setAccessible(true);
        Session session = (Session) sessionField.get(connection);
        Channel channel = session.openChannel("exec");
        ChannelExec execChannel = (ChannelExec) channel;

        if(asRoot)
            command = "sudo -S -p '' bash -c $'" + command.replace("'", "\\'") + "'";
        execChannel.setCommand(command);

        InputStream inputStream = channel.getInputStream();
        OutputStream outputStream = channel.getOutputStream();
        execChannel.setErrStream(System.err);

        channel.connect();

        if(asRoot) {
            SSHConnectionInfo connectionInfo = SSHConnectionInfo.getInstance();
            outputStream.write((connectionInfo.getPassw() + "\n").getBytes());
            outputStream.flush();
        }

        byte[] dataBuffer = new byte[1024];
        StringBuilder data = new StringBuilder();
        while(!channel.isClosed()) {
            while(inputStream.available() > 0) {
                int i = inputStream.read(dataBuffer, 0, 1024);
                if(i < 0) break;
                data.append(new String(Arrays.copyOfRange(dataBuffer, 0, i)));
            }
        }

        channel.disconnect();
        session.disconnect();

        return data.toString();
    }


    public static void showPopup(Component owner, String message) {
        showPopup(owner, message, 5000);
    }

    public static void showPopup(Component owner, String message, long delay) {
        Label label = new Label(message);
        label.setAlignment(Label.CENTER);

        Popup popup = PopupFactory
                .getSharedInstance()
                .getPopup(owner, label, owner.getX() + owner.getWidth() / 2, owner.getY() + owner.getHeight() / 2);
        popup.show();

        Thread waitThread = new Thread(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) { }
            popup.hide();
        });

        waitThread.start();
    }
}