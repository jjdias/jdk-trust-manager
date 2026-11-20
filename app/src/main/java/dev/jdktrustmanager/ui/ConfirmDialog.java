package dev.jdktrustmanager.ui;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

/** Shows a {@link Confirmations.Prompt} and reports whether the user chose to go ahead. Cancel is the default. */
final class ConfirmDialog {

    private ConfirmDialog() {
    }

    static boolean confirm(Node owner, Confirmations.Prompt prompt) {
        ButtonType proceed = new ButtonType(prompt.action(), ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, prompt.message(), proceed, ButtonType.CANCEL);
        alert.setTitle(prompt.title());
        alert.setHeaderText(null);
        if (owner != null && owner.getScene() != null) {
            alert.initOwner(owner.getScene().getWindow());
        }
        ((Button) alert.getDialogPane().lookupButton(proceed)).setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
        return alert.showAndWait().filter(proceed::equals).isPresent();
    }
}
