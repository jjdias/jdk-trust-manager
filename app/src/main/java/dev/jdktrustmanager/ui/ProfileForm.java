package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.buildtools.BuildTool;
import dev.jdktrustmanager.buildtools.BuildTools;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.JdkRef;
import dev.jdktrustmanager.model.Profile;
import dev.jdktrustmanager.model.Tool;
import dev.jdktrustmanager.profiles.ProfileActivation;
import dev.jdktrustmanager.profiles.ProfileStore;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** The New / Edit / Save-current form: a name, a managed JDK, and an optional Maven, Ant, and Gradle version. */
final class ProfileForm {

    private record JdkChoice(JdkRef ref, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record VersionChoice(String version, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private ProfileForm() {
    }

    /**
     * {@code installedVersions} lists each tool's installed versions; {@code existing} is the other profiles, for the
     * duplicate-name check; {@code originalName} is the name being edited, or null for a new profile.
     */
    static Optional<Profile> show(Node owner, Profile initialOrNull, String originalName, List<Profile> existing,
            List<Installation> managed, Map<Tool, List<String>> installedVersions, List<String> notes) {
        Dialog<Profile> dialog = new Dialog<>();
        dialog.setTitle(originalName == null ? "New profile" : "Edit " + originalName);
        dialog.setHeaderText(null);
        if (owner != null && owner.getScene() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField name = new TextField(initialOrNull == null || initialOrNull.name() == null ? "" : initialOrNull.name());
        name.setPromptText("For example Release 4.2");
        ComboBox<JdkChoice> jdk = new ComboBox<>();
        for (Installation installation : managed) {
            jdk.getItems().add(new JdkChoice(new JdkRef(installation.vendorSlug(), installation.major()),
                    installation.vendorSlug() + " " + installation.major() + " — " + installation.path()));
        }
        if (initialOrNull != null && initialOrNull.jdk() != null) {
            JdkChoice chosen = jdk.getItems().stream().filter(choice -> choice.ref().equals(initialOrNull.jdk())).findFirst().orElse(null);
            if (chosen == null) {
                chosen = new JdkChoice(initialOrNull.jdk(), initialOrNull.jdk().vendorSlug() + " " + initialOrNull.jdk().major() + " (not installed)");
                jdk.getItems().add(0, chosen);
            }
            jdk.setValue(chosen);
        }
        jdk.setMaxWidth(Double.MAX_VALUE);
        Map<Tool, ComboBox<VersionChoice>> tools = new EnumMap<>(Tool.class);
        for (BuildTool tool : BuildTools.ALL) {
            ComboBox<VersionChoice> box = new ComboBox<>();
            VersionChoice none = new VersionChoice(null, "(none)");
            box.getItems().add(none);
            for (String version : installedVersions.getOrDefault(tool.tool(), List.of())) {
                box.getItems().add(new VersionChoice(version, version));
            }
            String current = initialOrNull == null ? null : initialOrNull.version(tool.tool());
            VersionChoice selected = none;
            if (current != null && !current.isBlank()) {
                selected = box.getItems().stream().filter(choice -> current.equals(choice.version())).findFirst().orElse(null);
                if (selected == null) {
                    selected = new VersionChoice(current, current + " (not installed)");
                    box.getItems().add(1, selected);
                }
            }
            box.setValue(selected);
            box.setMaxWidth(Double.MAX_VALUE);
            tools.put(tool.tool(), box);
        }

        Label warning = new Label();
        warning.setWrapText(true);
        warning.setStyle("-fx-text-fill: #9a5b00;");
        Label error = new Label();
        error.setWrapText(true);
        error.setStyle("-fx-text-fill: #b00020;");
        Runnable update = () -> {
            Profile draft = draft(name, jdk, tools);
            warning.setText(draft.jdk() == null ? "" : String.join("\n", ProfileActivation.javaWarnings(draft)));
        };
        jdk.valueProperty().addListener((obs, old, value) -> update.run());
        tools.values().forEach(box -> box.valueProperty().addListener((obs, old, value) -> update.run()));
        update.run();

        GridPane grid = new GridPane();
        grid.setHgap(Spacing.GAP);
        grid.setVgap(Spacing.GAP);
        grid.addRow(0, new Label("Name"), name);
        grid.addRow(1, new Label("JDK"), jdk);
        int row = 2;
        for (BuildTool tool : BuildTools.ALL) {
            grid.addRow(row++, new Label(tool.displayName()), tools.get(tool.tool()));
        }
        javafx.scene.layout.ColumnConstraints labels = new javafx.scene.layout.ColumnConstraints();
        javafx.scene.layout.ColumnConstraints fields = new javafx.scene.layout.ColumnConstraints(420);
        fields.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, fields);

        List<Node> content = new ArrayList<>();
        if (managed.isEmpty()) {
            content.add(new Label("No managed JDKs yet. Install one from Java → Catalog, or adopt one on Java → Installed, to use it in a profile."));
        }
        for (String note : notes) {
            Label label = new Label(note);
            label.setWrapText(true);
            content.add(label);
        }
        content.add(grid);
        content.add(warning);
        content.add(error);
        VBox box = new VBox(Spacing.GAP, content.toArray(Node[]::new));
        box.setPadding(new Insets(4, 0, 0, 0));
        box.setPrefWidth(560);
        dialog.getDialogPane().setContent(box);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            String problem = ProfileStore.validate(existing, draft(name, jdk, tools), originalName);
            if (problem != null) {
                error.setText(problem);
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == save ? draft(name, jdk, tools) : null);
        return dialog.showAndWait();
    }

    private static Profile draft(TextField name, ComboBox<JdkChoice> jdk, Map<Tool, ComboBox<VersionChoice>> tools) {
        return new Profile(name.getText() == null ? "" : name.getText().trim(),
                jdk.getValue() == null ? null : jdk.getValue().ref(),
                version(tools.get(Tool.MAVEN)), version(tools.get(Tool.ANT)), version(tools.get(Tool.GRADLE)));
    }

    private static String version(ComboBox<VersionChoice> box) {
        return box.getValue() == null ? null : box.getValue().version();
    }
}
