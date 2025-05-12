package org.example.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.example.domain.entitiesAssociatedWithUser.User;
import org.example.service.AllServices;
import org.example.service.ServicesException;

import java.util.Optional;
import java.util.function.Consumer;

public class LoginDialogController {
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private PasswordField passwordField;
    @FXML private Text errorMessage;
    @FXML private Button cancelButton;
    @FXML private Button loginButton;

    private AllServices services;
    private Consumer<Optional<User>> onLoginSuccess;

    public void setServices(AllServices services) {
        this.services = services;
    }

    public void setOnLoginSuccess(Consumer<Optional<User>> onLoginSuccess) {
        this.onLoginSuccess = onLoginSuccess;
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String password = passwordField.getText();

        // Input validation
        if (firstName.isEmpty() || lastName.isEmpty() || password.isEmpty()) {
            showError("All fields are required.");
            return;
        }

        try {
            if (services.getUserService().authenticate(firstName, lastName, password)) {
                Optional<User> user = services.getUserService().getUserByCredentials(firstName, lastName, password);
                if (onLoginSuccess != null) {
                    onLoginSuccess.accept(user);
                }
                closeDialog();
            } else {
                showError("Invalid credentials. Please try again.");
            }
        } catch (ServicesException e) {
            showError("Authentication error: " + e.getMessage());
        }
    }

    @FXML
    public void handleCancel(ActionEvent event) {
        closeDialog();
    }

    private void showError(String message) {
        errorMessage.setText(message);
        errorMessage.setVisible(true);
        errorMessage.setManaged(true);
    }

    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}