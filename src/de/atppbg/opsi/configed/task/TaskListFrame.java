package de.atppbg.opsi.configed.task;

import de.atppbg.opsi.configed.Main;
import de.uib.configed.Globals;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.atppbg.opsi.configed.Main.resourceBundle;

public class TaskListFrame extends Frame {
    private ArrayList<Map<String, String>> tasks;
    private final Map<String, String> actionNames;

    public TaskListFrame() {
        super(resourceBundle.getString("taskList.title"));

        actionNames = new HashMap<>();
        actionNames.put("hostControlSafe_fireEvent", "on_demand");
        actionNames.put("hostControlSafe_reboot", "reboot");
        actionNames.put("hostControlSafe_shutdown", "shutdown");

        this.setVisible(false);
        this.setupWindow();
        this.setVisible(true);
    }

    private void setupWindow() {
        this.setSize(Globals.dialogFrameDefaultSize);
        this.setBackground(Globals.BACKGROUND_COLOR_7);
        this.setIconImage(Globals.mainIcon);

        Frame thisFrame = this;
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                thisFrame.dispose();
            }
        });

        this.setupElements();
    }

    private void loadTasks() {
        try {
            Pattern taskMetadataPattern = Pattern.compile("^(\\d+)\\t([A-Z]{3})\\s([A-Z]{3})\\s{1,2}(\\d{1,2})\\s(\\d{2}):(\\d{2}):(\\d{2})\\s(\\d{4})\\s(.)\\s(.+)", Pattern.CASE_INSENSITIVE);
            HashMap<String, String> taskOwners = new HashMap<>();
            String taskMetadata = Main.executeSSHCommand("atq", true);
            for(String taskMetaLine : taskMetadata.split("\n")) {
                Matcher matcher = taskMetadataPattern.matcher(taskMetaLine);
                if(!matcher.matches()) continue;

                String taskId = matcher.group(1);
                String owner = matcher.group(10);
                taskOwners.put(taskId, owner);
            }

            String taskDefinitions = Main.executeSSHCommand("for jobId in $(atq | awk '{ print $1 }'); do echo -e \"$jobId\\t$(at -c $jobId | grep opsi-admin)\"; done", true);

            Pattern taskDefinitionPattern = Pattern.compile("^(\\d+)\\topsi-admin.+method (\\S+)[^']+'([^']+)' # (\\d{2}:\\d{2}) (\\d{2}\\.\\d{2}\\.\\d{4}) # (.+)");
            tasks = new ArrayList<>();
            for(String taskLine : taskDefinitions.split("\n")) {
                Matcher matcher = taskDefinitionPattern.matcher(taskLine);
                if(!matcher.matches()) continue;

                String clientsString = matcher.group(3);

                Map<String, String> taskData = new HashMap<>();
                String id = matcher.group(1);
                taskData.put("id", id);
                taskData.put("action", actionNames.getOrDefault(matcher.group(2), matcher.group(2)));
                taskData.put("clients", clientsString.substring(2, clientsString.length() - 2).replace("\",\"", ", "));
                taskData.put("executionTime", "%s %s".formatted(matcher.group(5), matcher.group(4)));
                taskData.put("name", matcher.group(6));
                if(taskOwners.containsKey(id))
                    taskData.put("owner", taskOwners.get(id));
                else
                    taskData.put("owner", resourceBundle.getString("taskList.unknownOwner"));

                tasks.add(taskData);
            }
        } catch (Exception ex) {
            Main.exceptionHandler.uncaughtException(Thread.currentThread(), ex);
        }
    }

    private Object[][] getRowData() {
        Object[][] rowData = new Object[tasks.size()][5];

        tasks.sort((task1, task2) -> {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            LocalDateTime execTime1 = LocalDateTime.parse(task1.get("executionTime"), formatter);
            LocalDateTime execTime2 = LocalDateTime.parse(task2.get("executionTime"), formatter);

            return execTime1.compareTo(execTime2);
        });

        for(int i = 0; i < tasks.size(); i++) {
            Map<String, String> taskData = tasks.get(i);
            String[] taskDataStringArray = {
                    taskData.get("id"),
                    taskData.get("name"),
                    taskData.get("owner"),
                    taskData.get("executionTime"),
                    taskData.get("action"),
                    "[%d]: %s".formatted(taskData.get("clients").split(", ").length, taskData.get("clients"))
            };
            rowData[i] = taskDataStringArray;
        }

        return rowData;
    }

    void setupElements() {
        this.removeAll();
        this.loadTasks();

        String[] columnNames = {
                resourceBundle.getString("taskList.id"),
                resourceBundle.getString("taskList.taskName"),
                resourceBundle.getString("taskList.owner"),
                resourceBundle.getString("taskList.executionTime"),
                resourceBundle.getString("taskList.action"),
                resourceBundle.getString("taskList.clients")
        };
        AtomicReference<AtomicReferenceArray<Object[]>> rowData = new AtomicReference<>(new AtomicReferenceArray<>(this.getRowData()));

        JTable table = new JTable(new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return rowData.get().length();
            }

            @Override
            public int getColumnCount() {
                return columnNames.length;
            }

            @Override
            public String getColumnName(int column) {
                return columnNames[column];
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                return rowData.get().get(rowIndex)[columnIndex];
            }
        });

        table.setCellSelectionEnabled(false);
        table.setColumnSelectionAllowed(false);
        table.setRowSelectionAllowed(true);

        this.setLayout(new BorderLayout());
        this.add(table.getTableHeader(), BorderLayout.PAGE_START);
        this.add(table, BorderLayout.CENTER);

        Button cancelButton = new Button(resourceBundle.getString("taskList.cancelTasks"));
        cancelButton.setSize(0, 10);
        cancelButton.addActionListener(e -> {
            cancelButton.setEnabled(false);
            for (int index : table.getSelectionModel().getSelectedIndices()) {
                String jobId = (String) rowData.get().get(index)[0];
                try {
                    Main.executeSSHCommand("atrm %s".formatted(jobId), true);
                } catch (Exception ex) {
                    Main.exceptionHandler.uncaughtException(Thread.currentThread(), ex);
                }
            }

            this.loadTasks();
            rowData.set(new AtomicReferenceArray<>(this.getRowData()));
            table.repaint();
            cancelButton.setEnabled(true);
        });
        this.add(cancelButton, BorderLayout.PAGE_END);
    }
}
