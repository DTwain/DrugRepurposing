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

public class SignUpDialogController {
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private TextField occupationField;
    @FXML private Text errorMessage;
    @FXML private Button cancelButton;
    @FXML private Button signUpButton;

    private AllServices services;
    private Consumer<Optional<User>> onSignUpSuccess;

    public void setServices(AllServices services) {
        this.services = services;
    }

    public void setOnSignUpSuccess(Consumer<Optional<User>> onSignUpSuccess) {
        this.onSignUpSuccess = onSignUpSuccess;
    }

    @FXML
    public void handleSignUp(ActionEvent event) {
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        String occupation = occupationField.getText().trim();

        // Input validation
        if (firstName.isEmpty() || lastName.isEmpty() || password.isEmpty() || occupation.isEmpty()) {
            showError("All fields are required.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }

        try {
            boolean registered = services.getUserService().registerUser(firstName, lastName, password, occupation);

            if (registered) {
                // Registration succeeded, now authenticate
                if (services.getUserService().authenticate(firstName, lastName, password)) {
                    Optional<User> user = services.getUserService().getUserByCredentials(firstName, lastName, password);
                    if (onSignUpSuccess != null) {
                        onSignUpSuccess.accept(user);
                    }
                    closeDialog();
                } else {
                    showError("Registration successful but auto-login failed. Please try logging in manually.");
                }
            } else {
                showError("User already exists. Please try with different credentials.");
            }
        } catch (ServicesException e) {
            showError("Registration error: " + e.getMessage());
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