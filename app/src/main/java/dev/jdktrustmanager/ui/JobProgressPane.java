package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.install.WorkQueue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

public final class JobProgressPane extends VBox {

    private static final String CANCELLED = "Download cancelled";

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
        Button cancel = new Button("Cancel download");
        showCancel(cancel, false);
        cancel.setOnAction(event -> {
            queue.cancelDownload();
            cancel.setDisable(true);
            status.setText("Cancelling download");
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(Spacing.GAP, status, spacer, cancel);
        header.setAlignment(Pos.CENTER_LEFT);
        setSpacing(6);
        setPadding(new Insets(8, 16, 12, 16));
        getChildren().addAll(header, bar, details);
        queue.subscribe(event -> javafx.application.Platform.runLater(() -> {
            if (event.state() != null) {
                showCancel(cancel, event.kind() == WorkQueue.Kind.INSTALL && event.state() == WorkQueue.State.DOWNLOADING);
            }
            show(status, bar, log, event);
        }));
    }

    /** The button only exists while bytes are being downloaded; after that the install can't be stopped safely. */
    private static void showCancel(Button cancel, boolean downloading) {
        if (downloading && !cancel.isVisible()) {
            cancel.setDisable(false);
        }
        cancel.setVisible(downloading);
        cancel.setManaged(downloading);
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
            case FAILED -> bar.setProgress(0);
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
            case FAILED -> CANCELLED.equals(message) ? "Download cancelled. Nothing was installed." : "Failed: " + message;
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
