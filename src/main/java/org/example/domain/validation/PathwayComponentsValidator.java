package org.example.domain.validation;

import org.example.domain.Gene;
import org.example.domain.Disease;
import org.example.domain.Drug;
import org.example.domain.Compound;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates pathway component entities: Drug, Gene, Disease, Compound.
 */
public class PathwayComponentsValidator {

    /**
     * Validate a Drug entity.
     * @param drug the Drug to validate
     * @throws ValidationException if any rule fails
     */
    public static void validateDrug(Drug drug) throws ValidationException {
        if (drug == null) {
            throw new IllegalArgumentException("Drug must not be null");
        }
        List<String> errors = new ArrayList<>();

        if (drug.getId() == null || drug.getId().isBlank()) {
            errors.add("Drug ID must not be blank");
        }
        String name = drug.getName();
        if (name == null || name.isBlank()) {
            errors.add("Drug name must not be blank");
        } else if (name.length() > 100) {
            errors.add("Drug name must be at most 100 characters");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /**
     * Validate a Gene entity.
     * @param gene the Gene to validate
     * @throws ValidationException if any rule fails
     */
    public static void validateGene(Gene gene) throws ValidationException {
        if (gene == null) {
            throw new IllegalArgumentException("Gene must not be null");
        }
        List<String> errors = new ArrayList<>();

        String name = gene.getGeneName();
        if (name == null || name.isBlank()) {
            errors.add("Gene name must not be blank");
        } else if (name.length() > 100) {
            errors.add("Gene name must be at most 100 characters");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /**
     * Validate a Disease entity.
     * @param disease the Disease to validate
     * @throws ValidationException if any rule fails
     */
    public static void validateDisease(Disease disease) throws ValidationException {
        if (disease == null) {
            throw new IllegalArgumentException("Disease must not be null");
        }
        List<String> errors = new ArrayList<>();

        String name = disease.getName();
        if (name == null || name.isBlank()) {
            errors.add("Disease name must not be blank");
        } else if (name.length() > 100) {
            errors.add("Disease name must be at most 100 characters");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /**
     * Validate a Compound entity.
     * @param compound the Compound to validate
     * @throws ValidationException if any rule fails
     */
    public static void validateCompound(Compound compound) throws ValidationException {
        if (compound == null) {
            throw new IllegalArgumentException("Compound must not be null");
        }
        List<String> errors = new ArrayList<>();

        String name = compound.getName();
        if (name == null || name.isBlank()) {
            errors.add("Compound name must not be blank");
        } else if (name.length() > 100) {
            errors.add("Compound name must be at most 100 characters");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
