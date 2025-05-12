package org.example.service.interfaces;

import org.example.domain.entitiesAssociatedWithUser.User;
import org.example.service.ServicesException;

import java.util.List;
import java.util.Optional;

public interface IUserService {
    // Existing methods
    boolean authenticate(String firstName, String lastName, String password) throws ServicesException;
    boolean registerUser(String firstName, String lastName, String password, String ocupation) throws ServicesException;
    Optional<User> getUserByCredentials(String firstName, String lastName, String password) throws ServicesException;

    // New methods for gene search history
    /**
     * Save a gene search for a user.
     *
     * @param userId the user ID
     * @param geneName the gene name that was searched
     * @return true if save was successful
     * @throws ServicesException if service operations fail
     */
    boolean saveGeneSearch(Long userId, String geneName) throws ServicesException;

    /**
     * Get the gene search history for a user.
     *
     * @param userId the user ID
     * @return list of gene names, most recent first
     * @throws ServicesException if service operations fail
     */
    List<String> getGeneSearchHistory(Long userId) throws ServicesException;

    /**
     * Clear the gene search history for a user.
     *
     * @param userId the user ID
     * @return true if clear was successful
     * @throws ServicesException if service operations fail
     */
    boolean clearGeneSearchHistory(Long userId) throws ServicesException;
}