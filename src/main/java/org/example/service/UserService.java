package org.example.service;

import org.example.domain.entitiesAssociatedWithUser.User;
import org.example.domain.validation.UserValidator;
import org.example.domain.validation.ValidationException;
import org.example.repository.interfaces.IUserRepository;
import org.example.service.interfaces.IUserService;

import java.util.*;

public class UserService implements IUserService {
    private IUserRepository iUserRepository;
    private static final Map<Long, List<String>> geneSearchHistory = new HashMap<>();

    public UserService(IUserRepository iUserRepository) {
        this.iUserRepository = iUserRepository;
    }
    @Override
    public boolean authenticate(String firstName, String lastName, String password) throws ServicesException {
        try {
            // Find user by username (you might need to implement this method in your repository)
            User potentialUser = new User(firstName, lastName, password);
            UserValidator.validate(potentialUser);
            Optional<User> userOpt = iUserRepository.findUserByCredentials(potentialUser);
            if (userOpt.isEmpty()) {
                return false;
            }

            User user = userOpt.get();
            return user.getPassword().equals(password);
        } catch (ValidationException e) {
            throw new ServicesException("Validarea user ului a picat", e);
        } catch (Exception e) {
            throw new ServicesException("Authentication failed", e);
        }
    }

    @Override
    public boolean registerUser(String firstName, String lastName, String password, String ocupation) throws ServicesException {
        try {
            User potentialUser = new User(firstName, lastName, password, ocupation);

            UserValidator.validate(potentialUser);

            // Check if user already exists
            if (iUserRepository.findUserByCredentials(potentialUser).isPresent()) {
                return false;
            }

            Optional<User> savedUser = iUserRepository.save(potentialUser);
            return savedUser.isEmpty();
        } catch(ValidationException e) {
            throw new ServicesException("Validarea user ului a picat", e);
        } catch (Exception e){
            throw new ServicesException("Registration failed", e);
        }
    }

    @Override
    public Optional<User> getUserByCredentials(String firstName, String lastName, String password) throws ServicesException {
        try {
            User potentialUser = new User(firstName, lastName, password);
            UserValidator.validate(potentialUser);
            return iUserRepository.findUserByCredentials(potentialUser);
        } catch(ValidationException e) {
            throw new ServicesException("Validarea credentialelor user ului pe care il cauti a picat", e);
        } catch (Exception e) {
            throw new ServicesException("Error checking username existence", e);
        }
    }
    /**
     * Save a gene search for a user.
     *
     * @param userId the user ID
     * @param geneName the gene name that was searched
     * @return true if save was successful
     * @throws ServicesException if service operations fail
     */
    public boolean saveGeneSearch(Long userId, String geneName) throws ServicesException {
        try {
            if (userId == null || geneName == null || geneName.isBlank()) {
                return false;
            }

            // Get or create user's search history
            List<String> userHistory = geneSearchHistory.computeIfAbsent(userId, k -> new ArrayList<>());

            // Remove if already exists (to avoid duplicates)
            userHistory.remove(geneName);

            // Add to the beginning of the list (most recent first)
            userHistory.add(0, geneName);

            // Keep only the last 20 searches
            if (userHistory.size() > 20) {
                userHistory = userHistory.subList(0, 20);
                geneSearchHistory.put(userId, userHistory);
            }

            return true;
        } catch (Exception e) {
            throw new ServicesException("Error saving gene search history", e);
        }
    }

    /**
     * Get the gene search history for a user.
     *
     * @param userId the user ID
     * @return list of gene names, most recent first
     * @throws ServicesException if service operations fail
     */
    public List<String> getGeneSearchHistory(Long userId) throws ServicesException {
        try {
            if (userId == null) {
                return List.of();
            }

            // Return the user's search history, or an empty list if none exists
            return geneSearchHistory.getOrDefault(userId, new ArrayList<>());
        } catch (Exception e) {
            throw new ServicesException("Error retrieving gene search history", e);
        }
    }

    /**
     * Clear the gene search history for a user.
     *
     * @param userId the user ID
     * @return true if clear was successful
     * @throws ServicesException if service operations fail
     */
    public boolean clearGeneSearchHistory(Long userId) throws ServicesException {
        try {
            if (userId == null) {
                return false;
            }

            geneSearchHistory.remove(userId);
            return true;
        } catch (Exception e) {
            throw new ServicesException("Error clearing gene search history", e);
        }
    }
}
