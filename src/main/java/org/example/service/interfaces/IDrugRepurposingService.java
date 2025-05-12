package org.example.service.interfaces;

import org.example.domain.Disease;
import org.example.domain.DrugRepurposingResult;
import org.example.service.ServicesException;

import java.util.List;
import java.util.Map;

/**
 * Service interface for drug repurposing operations.
 */
public interface IDrugRepurposingService {

    /**
     * Get optimized list of drugs for a disease with parallel processing.
     *
     * @param diseaseId the disease ID
     * @param maxResults the maximum number of results to return
     * @return list of drug repurposing results
     * @throws ServicesException if service operations fail
     */
    List<DrugRepurposingResult> getDrugsForDisease(String diseaseId, int maxResults) throws ServicesException;

    /**
     * Retrieves drug repurposing options for a given disease.
     *
     * @param diseaseId the KEGG ID of the disease
     * @return a list of drug repurposing candidates with success rates
     * @throws ServicesException if service operations fail
     */
    List<DrugRepurposingResult> getDrugRepurposingForDisease(String diseaseId) throws ServicesException;

    /**
     * Retrieves detailed information about a specific drug.
     *
     * @param drugId the KEGG ID of the drug
     * @return detailed drug information
     * @throws ServicesException if service operations fail
     */
    DrugRepurposingResult getDrugDetailedInformation(String drugId) throws ServicesException;

    /**
     * Finds potential drug candidates for diseases with no current treatments.
     *
     * @param diseaseId the KEGG ID of the disease with no current drugs
     * @param maxResults maximum number of results to return
     * @return a list of potential drug candidates sorted by repurposing score
     * @throws ServicesException if service operations fail
     */
    List<DrugRepurposingResult> findPotentialDrugsForDisease(String diseaseId, int maxResults) throws ServicesException;

    /**
     * Identifies diseases that have no associated drugs in the database.
     *
     * @return a list of diseases with no current drugs
     * @throws ServicesException if service operations fail
     */
    List<Disease> findDiseasesWithNoDrugs() throws ServicesException;

    /**
     * Retrieves top drug candidates across all diseases with no treatments.
     *
     * @param maxResultsPerDisease maximum number of drug results per disease
     * @param maxDiseases maximum number of diseases to include
     * @return a map of diseases to potential drug candidates
     * @throws ServicesException if service operations fail
     */
    Map<Disease, List<DrugRepurposingResult>> findTopDrugCandidatesForOrphanDiseases(
            int maxResultsPerDisease, int maxDiseases) throws ServicesException;

    /**
     * Get drug repurposing results with pagination
     * @param diseaseId the disease ID
     * @param offset starting position
     * @param limit maximum number of results
     * @return list of drug repurposing results
     * @throws ServicesException if an error occurs
     */
    List<DrugRepurposingResult> getDrugRepurposingForDiseasePaginated(String diseaseId, int offset, int limit) throws ServicesException;
}