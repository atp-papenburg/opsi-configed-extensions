package de.atppbg.opsi.configed.task;

import de.atppbg.opsi.configed.dialogs.ConfirmationDialog;
import de.atppbg.opsi.configed.Main;
import de.uib.configed.Globals;

import javax.swing.*;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.InternationalFormatter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.atppbg.opsi.configed.Main.exceptionHandler;
import static de.atppbg.opsi.configed.Main.resourceBundle;
import static java.lang.Math.toIntExact;

public class TaskPlannerFrame extends Frame {
    private static final List<String> TIMESTAMP_PARTS = List.of("dd", "MM", "yyyy", "", "HH", "mm");
    private static final TaskType[] AVAILABLE_TASKS = TaskType.values();
    private static final int MINIMUM_TIME_DELTA_MIN = 30;
    private static final int MAXIMUM_CLIENT_COUNT = 30;

    private TextField taskName;
    private Choice taskType;
    private final Map<String, JFormattedTextField> timestampPartFields = new HashMap<>();
    private final String[] clients;

    public TaskPlannerFrame() {
        super(resourceBundle.getString("taskPlanner.title"));

        this.clients = Main.configedMain.getSelectedClients();
        assert this.clients.length > 0;

        this.setVisible(false);
        this.setupWindow();
        this.setVisible(true);
    }

    private void setupWindow() {
        this.setSize(550, 200);
        this.setBackground(Globals.BACKGROUND_COLOR_7);
        this.setIconImage(Globals.mainIcon);

        Frame thisFrame = this;
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                thisFrame.dispose();
            }
        });

        this.setLayout(new GridBagLayout());
        this.setupElements();
    }

    @SuppressWarnings("UnusedAssignment")
    private void setupElements() {
        int line = 0;
        this.setupTaskNameElements(line++);
        this.setupClientCountElements(line++);
        this.setupDateTimeElements(line++);
        this.setupTaskComponents(line++);

        this.setupButtonElements(line++);
    }

    private void setupTaskNameElements(int line) {
        String taskName = String.format(
                resourceBundle.getString("taskPlanner.taskNameTemplate"),
                DateTimeFormatter.ofPattern("dd.MM.yyy HH:mm").format(LocalDateTime.now())
        );

        Label taskNameLabel = new Label(resourceBundle.getString("taskPlanner.taskName"));

        TextField taskNameField = new TextField();
        taskNameField.setText(taskName);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = line;

        this.add(taskNameLabel, constraints);
        this.add(taskNameField, constraints);
        this.taskName = taskNameField;
    }

    private void setupClientCountElements(int line) {
        int clientCount = clients.length;

        Label clientCountLabel = new Label(resourceBundle.getString("taskPlanner.clientCount"));

        TextField clientCountField = new TextField();
        clientCountField.setText(String.valueOf(clientCount));
        clientCountField.setEditable(false);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = line;

        this.add(clientCountLabel, constraints);
        this.add(clientCountField, constraints);
    }

    private void setupDateTimeElements(int line) {
        Label dateTimeLabel = new Label(resourceBundle.getString("taskPlanner.dateTime"));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = line;

        this.add(dateTimeLabel, constraints);

        JPanel panel = new JPanel();
        panel.setBackground(Globals.BACKGROUND_COLOR_7);

        LocalDateTime time = LocalDateTime.now()
                .plusDays(1)
                .withHour(3)
                .withMinute(0);

        for (String timestampPart : TIMESTAMP_PARTS) {
            if(timestampPart.equals("")) {
                this.add(panel, constraints);
                constraints.insets = new Insets(0, 8, 0, 0);
                panel = new JPanel();
                panel.setBackground(Globals.BACKGROUND_COLOR_7);
            } else {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(timestampPart);

                JSpinner spinner = new JSpinner();
                JFormattedTextField spinnerField = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
                spinnerField.setColumns(timestampPart.length());

                try {
                    ((DefaultFormatterFactory)spinnerField.getFormatterFactory())
                            .setDefaultFormatter(new InternationalFormatter(new DecimalFormat("0".repeat(timestampPart.length()))));
                } catch (Exception ignored) {}

                spinnerField.setValue(Long.parseLong(formatter.format(time)));
                panel.add(spinner, constraints);
                timestampPartFields.put(timestampPart, spinnerField);
            }
        }

        this.add(panel, constraints);
    }

    private void setupTaskComponents(int line) {
        Label taskNameLabel = new Label(resourceBundle.getString("taskPlanner.action"));

        Choice taskNameChoice = new Choice();
        for(TaskType task : AVAILABLE_TASKS) {
            taskNameChoice.add(task.name());
        }

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = line;

        this.add(taskNameLabel, constraints);
        this.add(taskNameChoice, constraints);
        taskType = taskNameChoice;
    }

    private void setupButtonElements(int line) {
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridBagLayout());
        buttonPanel.setBackground(Globals.BACKGROUND_COLOR_7);

        Button createButton = new Button(resourceBundle.getString("taskPlanner.create"));
        createButton.addActionListener(this::handleCreate);

        Button cancelButton = new Button(resourceBundle.getString("taskPlanner.cancel"));
        cancelButton.addActionListener(e -> this.dispose());

        GridBagConstraints buttonPadding = new GridBagConstraints();
        buttonPadding.insets = new Insets(0, 8, 0, 8);

        buttonPanel.add(createButton, buttonPadding);
        buttonPanel.add(cancelButton, buttonPadding);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = line;
        constraints.insets = new Insets(16, 0, 0, 0);

        this.add(new JPanel(), constraints);
        this.add(buttonPanel, constraints);
    }

    private void handleCreate(ActionEvent ignoredEvent) {
        LocalDateTime time = LocalDateTime.of(
                toIntExact((long) timestampPartFields.get("yyyy").getValue()),
                toIntExact((long) timestampPartFields.get("MM").getValue()),
                toIntExact((long) timestampPartFields.get("dd").getValue()),
                toIntExact((long) timestampPartFields.get("HH").getValue()),
                toIntExact((long) timestampPartFields.get("mm").getValue())
        );

        if(time.isBefore(LocalDateTime.now())) {
            Main.showPopup(this, resourceBundle.getString("taskPlanner.taskTimeNotInFuture"));
            return;
        }

        if(time.isBefore(LocalDateTime.now().plusMinutes(MINIMUM_TIME_DELTA_MIN))) {
            ConfirmationDialog dialog = new ConfirmationDialog(
                    resourceBundle.getString("confirmationModal.deltaTime").formatted(MINIMUM_TIME_DELTA_MIN),
                    resourceBundle.getString("confirmationModal.buttonYes"),
                    resourceBundle.getString("confirmationModal.buttonNo")
            );

            try {
                if(!dialog.resultQueue.take())
                    return;
            } catch (InterruptedException e) {
                exceptionHandler.uncaughtException(Thread.currentThread(), e);
                return;
            }
        }

        if(clients.length > MAXIMUM_CLIENT_COUNT) {
            ConfirmationDialog dialog = new ConfirmationDialog(
                    resourceBundle.getString("confirmationModal.maxClients").formatted(clients.length),
                    resourceBundle.getString("confirmationModal.buttonYes"),
                    resourceBundle.getString("confirmationModal.buttonNo")
            );

            try {
                if(!dialog.resultQueue.take())
                    return;
            } catch (InterruptedException e) {
                exceptionHandler.uncaughtException(Thread.currentThread(), e);
                return;
            }
        }

        TaskType selectedTaskType = TaskType.valueOf(taskType.getSelectedItem());

        StringBuilder command = new StringBuilder("opsi-admin -d method ");
        switch(selectedTaskType) {
            case on_demand -> command.append("hostControlSafe_fireEvent on_demand");
            case start -> command.append("hostControlSafe_start");
            case reboot -> command.append("hostControlSafe_reboot");
            case shutdown -> command.append("hostControlSafe_shutdown");

            default -> {
                Main.exceptionHandler.uncaughtException(Thread.currentThread(), new Exception(resourceBundle.getString("taskPlanner.invalidAction")));
                return;
            }
        }

        command.append(" '[");
        for(String client : clients)
            command.append("\"").append(client).append("\",");

        command.deleteCharAt(command.length() - 1);
        command.append("]' # ");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm dd.MM.yyyy");
        String formattedRunTime = formatter.format(time);

        command.append(formattedRunTime);
        command.append(" # ");
        command.append(taskName.getText());

        try {
            Main.executeSSHCommand("echo \"%s\" | at \"%s\"\n".formatted(command.toString().replace("\"", "\\\""), formattedRunTime));
            Main.showPopup(this, resourceBundle.getString("taskPlanner.taskCreated"));
        } catch (Exception ex) {
            Main.exceptionHandler.uncaughtException(
                    Thread.currentThread(),
                    new Exception(resourceBundle.getString("taskPlanner.taskCreationFailure").formatted(ex.getMessage()))
            );
        }
    }
}
