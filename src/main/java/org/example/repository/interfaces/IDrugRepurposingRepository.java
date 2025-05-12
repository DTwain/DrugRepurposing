package org.example.repository.interfaces;

import org.example.domain.Disease;
import org.example.domain.Drug;
import org.example.repository.RepositoryException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Repository interface for drug repurposing operations.
 */
public interface IDrugRepurposingRepository {

    /**
     * Get all drugs associated with a disease.
     * @param diseaseId the KEGG disease ID
     * @return list of drug IDs
     * @throws RepositoryException if database error occurs
     */
    List<String> getDrugsForDisease(String diseaseId) throws RepositoryException;

    // Overload for using with an existing connection

    /**
     * Get information about a drug by ID.
     * @param drugId the KEGG drug ID
     * @return drug object or null if not found
     * @throws RepositoryException if database error occurs
     */
    Drug getDrugById(String drugId) throws RepositoryException;

    /**
     * Get information about a disease by ID.
     * @param diseaseId the KEGG disease ID
     * @return disease object or null if not found
     * @throws RepositoryException if database error occurs
     */
    Disease getDiseaseById(String diseaseId) throws RepositoryException;

    /**
     * Calculate repurposing score for a drug-disease pair.
     * @param drugId the KEGG drug ID
     * @param diseaseId the KEGG disease ID
     * @return score between 0.0 and 1.0
     * @throws RepositoryException if calculation fails
     */
    double calculateRepurposingScore(String drugId, String diseaseId) throws RepositoryException;

    /**
     * Get mechanism of action for a drug.
     * @param drugId the KEGG drug ID
     * @return mechanism description
     * @throws RepositoryException if database error occurs
     */
    String getMechanismOfAction(String drugId) throws RepositoryException;

    /**
     * Get current indications for a drug.
     * @param drugId the KEGG drug ID
     * @return current indications
     * @throws RepositoryException if database error occurs
     */
    String getCurrentIndication(String drugId) throws RepositoryException;

    /**
     * Get detailed evidence for drug repurposing.
     * @param drugId the KEGG drug ID
     * @return evidence details
     * @throws RepositoryException if database error occurs
     */
    String getEvidenceDetails(String drugId) throws RepositoryException;

    /**
     * Get adverse effects for a drug.
     * @param drugId the KEGG drug ID
     * @return adverse effects description
     * @throws RepositoryException if database error occurs
     */
    String getAdverseEffects(String drugId) throws RepositoryException;

    /**
     * Get dosage information for a drug.
     * @param drugId the KEGG drug ID
     * @return dosage information
     * @throws RepositoryException if database error occurs
     */
    String getDosageInformation(String drugId) throws RepositoryException;

    /**
     * Get genes associated with a disease.
     * @param diseaseId the KEGG disease ID
     * @return list of gene IDs
     * @throws RepositoryException if database error occurs
     */
    List<String> getGenesForDisease(String diseaseId) throws RepositoryException;

    /**
     * Get pathways associated with a disease.
     * @param diseaseId the KEGG disease ID
     * @return list of pathway IDs
     * @throws RepositoryException if database error occurs
     */
    List<String> getPathwaysForDisease(String diseaseId) throws RepositoryException;

    /**
     * Get drugs that target a specific gene.
     * @param geneId the gene ID
     * @return list of drug IDs
     * @throws RepositoryException if database error occurs
     */
    List<String> getDrugsThatTargetGene(String geneId) throws RepositoryException;

    /**
     * Get drugs that affect a specific pathway.
     * @param pathwayId the pathway ID
     * @return list of drug IDs
     * @throws RepositoryException if database error occurs
     */
    List<String> getDrugsThatAffectPathway(String pathwayId) throws RepositoryException;

    /**
     * Check if a drug is already approved for a disease.
     * @param drugId the KEGG drug ID
     * @param diseaseId the KEGG disease ID
     * @return true if drug is approved for disease
     * @throws RepositoryException if database error occurs
     */
    boolean isDrugApprovedForDisease(String drugId, String diseaseId) throws RepositoryException;

    /**
     * Get all diseases in the database.
     * @return list of all diseases
     * @throws RepositoryException if database error occurs
     */
    List<Disease> getAllDiseases() throws RepositoryException;

    /**
     * Get all drug IDs available in the database.
     * @return list of all drug IDs
     * @throws RepositoryException if database error occurs
     */
    List<String> getAllDrugIds() throws RepositoryException;

    /**
     * Get gene targets for a specific drug.
     * @param drugId the KEGG drug ID
     * @return list of gene IDs targeted by the drug
     * @throws RepositoryException if database error occurs
     */
    List<String> getGeneTargetsForDrug(String drugId) throws RepositoryException;

    /**
     * Get pathways affected by a specific drug.
     * @param drugId the KEGG drug ID
     * @return list of pathway IDs affected by the drug
     * @throws RepositoryException if database error occurs
     */
    List<String> getPathwaysForDrug(String drugId) throws RepositoryException;

    /**
     * Find diseases similar to the given disease based on shared genes or pathways.
     * @param diseaseId the KEGG disease ID
     * @return list of similar disease IDs
     * @throws RepositoryException if database error occurs
     */
    List<String> getSimilarDiseases(String diseaseId) throws RepositoryException;

    /**
     * Calculate molecular similarity between two drugs.
     * @param drugId1 first drug KEGG ID
     * @param drugId2 second drug KEGG ID
     * @return similarity score between 0.0 and 1.0
     * @throws RepositoryException if calculation fails
     */
    double calculateMolecularSimilarity(String drugId1, String drugId2) throws RepositoryException;

    /**
     * Find drugs that target a specific gene.
     * @param geneId the gene ID
     * @return list of drug IDs that target the gene
     * @throws RepositoryException if database error occurs
     */
    List<String> findDrugsThatTargetGene(String geneId) throws RepositoryException;

    /**
     * Find drugs by network analysis, traversing the interaction network up to the specified depth.
     * @param geneId the starting gene ID
     * @param maxDepth maximum traversal depth in the interaction network
     * @return list of drug IDs found through network analysis
     * @throws RepositoryException if database error occurs
     */
    List<String> findDrugsThroughInteractionNetwork(String geneId, int maxDepth) throws RepositoryException;

    /**
     * Find drugs that affect a pathway.
     * @param pathwayId the pathway ID
     * @return list of drug IDs that affect the pathway
     * @throws RepositoryException if database error occurs
     */
    List<String> findDrugsInPathway(String pathwayId) throws RepositoryException;

    /**
     * Get pathways associated with a gene.
     * @param geneId the gene ID
     * @return list of pathway IDs
     * @throws RepositoryException if database error occurs
     */
    List<String> getPathwaysForGene(String geneId) throws RepositoryException;

    /**
     * Find candidate drugs based on structural similarity to known drugs.
     * @param diseaseId the disease ID to find candidates for
     * @return list of candidate drug IDs based on structural similarity
     * @throws RepositoryException if database error occurs
     */
    List<String> findCandidatesByStructuralSimilarity(String diseaseId) throws RepositoryException;
}