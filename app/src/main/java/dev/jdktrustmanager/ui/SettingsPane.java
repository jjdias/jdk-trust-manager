package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.model.EnvironmentScope;
import dev.jdktrustmanager.model.Settings;
import dev.jdktrustmanager.persist.JsonStores;
import java.nio.file.Path;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

public final class SettingsPane extends HBox {

    public SettingsPane(Settings initial, Path file, JsonStores stores, Consumer<Settings> onChange) {
        TextField root = new TextField(initial.rootDirectory());
        TextField age = new TextField(Long.toString(initial.catalogMaxAgeHours()));
        root.setPrefColumnCount(28);
        CheckBox hideBelow8 = box("Hide majors below 8", initial.hideMajorsBelow8());
        CheckBox hideEol = box("Hide upstream end of life", initial.hideUpstreamEndOfLife());
        CheckBox early = box("Show early access", initial.showEarlyAccess());
        CheckBox graal = box("Show GraalVM", initial.showGraalvm());
        CheckBox javafx = box("Show JavaFX", initial.showJavafxBuilds());
        CheckBox pause = box("Pause automatic trust sync", initial.pauseAutomaticTrustSync());
        Button save = new Button("Save");
        Label message = new Label();
        message.setWrapText(true);
        message.setMaxWidth(320);
        save.setOnAction(event -> {
            long hours;
            try {
                hours = SettingsForm.parseHours(age.getText());
            } catch (IllegalArgumentException ex) {
                message.setText(ex.getMessage());
                return;
            }
            Settings next = new Settings(
                    root.getText(),
                    initial.environmentScope() == null ? EnvironmentScope.USER : initial.environmentScope(),
                    hours,
                    hideBelow8.isSelected(),
                    hideEol.isSelected(),
                    early.isSelected(),
                    graal.isSelected(),
                    javafx.isSelected(),
                    pause.isSelected());
            try {
                stores.writeSettings(file, next);
                onChange.accept(next);
                message.setText("Saved.");
            } catch (Exception ex) {
                message.setText("Could not save settings: " + ex.getMessage());
            }
        });
        VBox form = new VBox(8,
                new Label("Install root"),
                root,
                new Label("Catalog cache, hours"),
                age,
                hideBelow8,
                hideEol,
                early,
                graal,
                javafx,
                pause,
                save,
                message);
        form.setPadding(new Insets(12));
        form.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #bdbdbd; -fx-border-width: 1;");
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.getContent().add(form);
        Button options = new Button("Options");
        options.setOnAction(event -> {
            if (popup.isShowing()) {
                popup.hide();
                return;
            }
            var bounds = options.localToScreen(options.getBoundsInLocal());
            popup.show(options, bounds.getMinX(), bounds.getMaxY());
        });
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(4, 8, 4, 8));
        getChildren().add(options);
    }

    private static CheckBox box(String label, boolean selected) {
        CheckBox box = new CheckBox(label);
        box.setSelected(selected);
        return box;
    }
}
