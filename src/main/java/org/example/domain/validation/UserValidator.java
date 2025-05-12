package org.example.domain.validation;

import org.example.domain.entitiesAssociatedWithUser.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a User entity’s fields.
 */
public class UserValidator {

    /**
     * Validate all User fields; if any rule fails, throws ValidationException
     */
    public static void validate(User user) throws ValidationException {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        List<String> errors = new ArrayList<>();

        // firstName: required, 1–50 chars, letters and spaces only
        String fn = user.getFirstName();
        if (fn == null || fn.isBlank()) {
            errors.add("First name is required");
        } else if (fn.length() > 50) {
            errors.add("First name must be at most 50 characters");
        } else if (!fn.matches("[A-Za-z ]+")) {
            errors.add("First name can only contain letters and spaces");
        }

        // lastName: required, 1–50 chars, letters and spaces only
        String ln = user.getLastName();
        if (ln == null || ln.isBlank()) {
            errors.add("Last name is required");
        } else if (ln.length() > 50) {
            errors.add("Last name must be at most 50 characters");
        } else if (!ln.matches("[A-Za-z ]+")) {
            errors.add("Last name can only contain letters and spaces");
        }

        // password: required, 8–100 chars, must include at least one digit and one letter
        String pw = user.getPassword();
        if (pw == null || pw.isBlank()) {
            errors.add("Password is required");
        } else {
            if (pw.length() < 8) {
                errors.add("Password must be at least 8 characters");
            }
            if (pw.length() > 100) {
                errors.add("Password must be at most 100 characters");
            }
            if (!pw.matches(".*[A-Za-z].*")) {
                errors.add("Password must contain at least one letter");
            }
            if (!pw.matches(".*\\d.*")) {
                errors.add("Password must contain at least one digit");
            }
        }

        // occupation: optional, but if present must be <= 100 chars
        String occ = user.getOccupation();
        if (occ != null && occ.length() > 100) {
            errors.add("Occupation must be at most 100 characters");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
