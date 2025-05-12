package org.example.repository.interfaces;

import org.example.domain.*;
import org.example.repository.RepositoryException;
import org.example.repository.entitiesRepository.TypesOfEntities;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for gene, disease, drug, and compound operations.
 * Includes methods for drug repurposing application.
 */
public interface IGeneDiseaseDrugCompoundRepository {
    // Original methods
    boolean existGene(Gene potentialGene) throws RepositoryException;
    boolean existDrug(Drug potentialDrug) throws RepositoryException;
    boolean existDisease(Disease potentialDisease) throws RepositoryException;
    boolean existCompound(Compound potentialCompound) throws RepositoryException;

    HashMap<String, String> retrieveInfoAboutGene(Gene potentialGene) throws RepositoryException;
    HashMap<String, String> retrieveInfoAboutDisease(Disease potentialDisease) throws RepositoryException;
    HashMap<String, String> retrieveInfoAboutCompound(Compound potentialCompound) throws RepositoryException;
    HashMap<String, String> retrieveInfoAboutDrug(Drug potentialDrug) throws RepositoryException;

    List<String> getGeneNameSuggestion(Gene potentialGene) throws RepositoryException;
    List<String> getCompoundNameSuggestion(Compound potentialCompound) throws RepositoryException;
    List<String> getDrugNameSuggestion(Drug potentialDrug) throws RepositoryException;

    // New methods for drug repurposing application

    /**
     * Get all diseases associated with a gene by gene ID.
     *
     * @param geneId the canonical gene ID
     * @return list of disease IDs
     * @throws RepositoryException if repository operations fail
     */
    List<String> getDiseasesForGene(String geneId) throws RepositoryException;

    /**
     * Get a disease name from a disease ID.
     *
     * @param diseaseId the disease ID
     * @return the disease name
     * @throws RepositoryException if repository operations fail
     */
    String getDiseaseNameFromId(String diseaseId) throws RepositoryException;

    /**
     * Get all drugs associated with a disease by disease ID.
     *
     * @param diseaseId the disease ID
     * @return list of drug IDs
     * @throws RepositoryException if repository operations fail
     */
    List<String> getDrugsForDisease(String diseaseId) throws RepositoryException;

    /**
     * Get information about a drug by ID.
     *
     * @param drugId the drug ID
     * @return Drug object
     * @throws RepositoryException if repository operations fail
     */
    Optional<Drug> getDrugById(String drugId) throws RepositoryException;

    /**
     * Get details about a gene from its ID.
     *
     * @param geneId the gene ID
     * @return list of gene details
     * @throws RepositoryException if repository operations fail
     */
    List<String> getGeneDetails(String geneId) throws RepositoryException;

    /**
     * Get pathways associated with a gene.
     *
     * @param geneId the gene ID
     * @return list of pathway IDs
     * @throws RepositoryException if repository operations fail
     */
    List<String> getPathwaysForGene(String geneId) throws RepositoryException;

    /**
     * Get all genes associated with a disease.
     *
     * @param diseaseId the disease ID
     * @return list of gene IDs
     * @throws RepositoryException if repository operations fail
     */
    List<String> getGenesForDisease(String diseaseId) throws RepositoryException;

    /**
     * Check if a drug is approved for a disease.
     *
     * @param drugId the drug ID
     * @param diseaseId the disease ID
     * @return true if approved, false otherwise
     * @throws RepositoryException if repository operations fail
     */
    boolean isApprovedForDisease(String drugId, String diseaseId) throws RepositoryException;
}