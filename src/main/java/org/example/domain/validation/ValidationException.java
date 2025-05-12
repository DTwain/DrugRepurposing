package org.example.domain.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Thrown when one or more validation rules fail.
 */
public class ValidationException extends Exception {
    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
