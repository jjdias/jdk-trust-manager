package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.install.WorkQueue;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;

public final class ActivityPane extends BorderPane {

    public ActivityPane(Path logFile, WorkQueue queue) {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setFont(Font.font("Consolas", 12));
        setCenter(area);
        Thread worker = new Thread(() -> {
            try {
                String text = Files.exists(logFile) ? Files.readString(logFile) : "";
                javafx.application.Platform.runLater(() -> area.setText(text));
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> area.setText(ex.getMessage()));
            }
        }, "jkm-activity");
        worker.setDaemon(true);
        worker.start();
        queue.subscribe(event -> {
            if (event.message() == null || event.message().isBlank() || !Double.isNaN(event.fraction())) {
                return;
            }
            String line = (event.state() == null ? "" : event.state().name().toLowerCase() + " ") + event.message();
            javafx.application.Platform.runLater(() -> area.appendText(line + "\n"));
        });
    }
}
