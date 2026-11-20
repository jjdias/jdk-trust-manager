package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.install.WorkQueue;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

public final class JobProgressPane extends VBox {

    public JobProgressPane(WorkQueue queue) {
        Label status = new Label("Idle");
        status.setStyle("-fx-font-size: 13px;");
        ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setMinHeight(18);
        bar.setPrefHeight(18);
        TextArea log = new TextArea();
        log.setEditable(false);
        log.setFont(Font.font("Consolas", 12));
        log.setPrefRowCount(8);
        TitledPane details = new TitledPane("Install log", log);
        details.setExpanded(false);
        VBox.setVgrow(details, Priority.NEVER);
        setSpacing(6);
        setPadding(new Insets(8, 16, 12, 16));
        getChildren().addAll(status, bar, details);
        queue.subscribe(event -> javafx.application.Platform.runLater(() -> show(status, bar, log, event)));
    }

    private static void show(Label status, ProgressBar bar, TextArea log, WorkQueue.Event event) {
        if (!Double.isNaN(event.fraction())) {
            status.setText("Downloading " + event.message());
            bar.setProgress(event.fraction() * 0.8);
            return;
        }
        String line = event.message() == null ? "" : event.message();
        if (event.state() == null) {
            append(log, line);
            return;
        }
        status.setText(label(event.state(), line));
        append(log, event.state().name().toLowerCase() + " " + line);
        switch (event.state()) {
            case QUEUED -> bar.setProgress(0);
            case DOWNLOADING -> bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            case VERIFYING -> bar.setProgress(0.85);
            case UNPACKING -> bar.setProgress(0.92);
            case UPDATING_TRUSTSTORE -> bar.setProgress(0.96);
            case DONE -> bar.setProgress(1);
            case FAILED -> status.setText("Failed: " + line);
            default -> {
            }
        }
    }

    private static String label(WorkQueue.State state, String message) {
        return switch (state) {
            case QUEUED -> "Queued";
            case DOWNLOADING -> "Downloading";
            case VERIFYING -> "Verifying checksum";
            case UNPACKING -> "Installing";
            case UPDATING_TRUSTSTORE -> "Updating truststore";
            case DONE -> "Finished";
            case FAILED -> "Failed: " + message;
        };
    }

    private static void append(TextArea log, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        log.appendText(line + "\n");
        log.setScrollTop(Double.MAX_VALUE);
    }
}
