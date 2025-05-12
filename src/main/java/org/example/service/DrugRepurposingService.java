package org.example.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.domain.Disease;
import org.example.domain.Drug;
import org.example.domain.DrugRepurposingResult;
import org.example.repository.RepositoryException;
import org.example.repository.interfaces.IDrugRepurposingRepository;
import org.example.repository.interfaces.IGeneDiseaseDrugCompoundRepository;
import org.example.service.interfaces.IDrugRepurposingService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Implementation of the drug repurposing service.
 */
public class DrugRepurposingService implements IDrugRepurposingService {
    private static final Logger log = LogManager.getLogger(DrugRepurposingService.class);

    private final IGeneDiseaseDrugCompoundRepository geneDiseaseDrugCompoundRepository;
    private final IDrugRepurposingRepository drugRepurposingRepository;

    // Cache for drug information to reduce database hits
    private final Map<String, DrugRepurposingResult> drugCache = new HashMap<>();

    public DrugRepurposingService(IGeneDiseaseDrugCompoundRepository geneDiseaseDrugCompoundRepository,
                                  IDrugRepurposingRepository drugRepurposingRepository) {
        this.geneDiseaseDrugCompoundRepository = geneDiseaseDrugCompoundRepository;
        this.drugRepurposingRepository = drugRepurposingRepository;
    }

    @Override
    public List<DrugRepurposingResult> getDrugsForDisease(String diseaseId, int maxResults) throws ServicesException {
        try {
            // Get all drug IDs for the disease
            List<String> drugIds = drugRepurposingRepository.getDrugsForDisease(diseaseId);

            if (drugIds.isEmpty()) {
                return findPotentialDrugsForDisease(diseaseId, maxResults);
            }

            // Create a thread pool with a reasonable size
            int numThreads = Math.min(Runtime.getRuntime().availableProcessors(), drugIds.size());
            numThreads = Math.min(numThreads, 8); // Cap at 8 threads to avoid resource exhaustion

            ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            try {
                // Process drugs in parallel
                List<CompletableFuture<DrugRepurposingResult>> futures = drugIds.stream()
                        .map(drugId -> CompletableFuture.supplyAsync(() -> {
                            try {
                                // Get drug data from repository
                                Drug drug = drugRepurposingRepository.getDrugById(drugId);
                                if (drug == null) return null;

                                // Calculate all needed properties
                                double score = drugRepurposingRepository.calculateRepurposingScore(drugId, diseaseId);
                                String mechanismOfAction = drugRepurposingRepository.getMechanismOfAction(drugId);
                                String currentIndication = drugRepurposingRepository.getCurrentIndication(drugId);
                                String evidenceLevel = determineEvidenceLevel(score);

                                // Create and return result object
                                return new DrugRepurposingResult(
                                        drugId, drug.getAliases(), score, mechanismOfAction,
                                        currentIndication, evidenceLevel);
                            } catch (Exception e) {
                                log.warn("Error processing drug {}: {}", drugId, e.getMessage());
                                return null; // Skip failed drugs
                            }
                        }, executor))
                        .toList();

                // Wait for all futures to complete
                CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0])
                );

                // Process results when all complete
                CompletableFuture<List<DrugRepurposingResult>> allResultsFuture =
                        allFutures.thenApply(v -> futures.stream()
                                .map(CompletableFuture::join)
                                .filter(Objects::nonNull)
                                .sorted(Comparator.comparing(DrugRepurposingResult::getSuccessRate).reversed())
                                .limit(maxResults)
                                .collect(Collectors.toList())
                        );

                // Get final results (blocking call)
                return allResultsFuture.get(60, TimeUnit.SECONDS); // Add timeout

            } finally {
                // Properly shut down the executor
                shutdownExecutor(executor);
            }
        } catch (Exception e) {
            throw new ServicesException("Error retrieving drugs for disease: " + diseaseId, e);
        }
    }

    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                List<Runnable> droppedTasks = executor.shutdownNow();
                log.warn("Executor did not terminate in time. {} tasks will not be executed.", droppedTasks.size());
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get drug repurposing options for a disease.
     *
     * @param diseaseId the disease ID to find drug repurposing options for
     * @throws ServicesException if an error occurs during processing
     */
    public List<DrugRepurposingResult> getDrugRepurposingForDisease(String diseaseId) throws ServicesException {
        // Default to returning up to 50 results
        return getDrugsForDisease(diseaseId, 50);
    }


    @Override
    public DrugRepurposingResult getDrugDetailedInformation(String drugId) throws ServicesException {
        // Check cache first
        if (drugCache.containsKey(drugId)) {
            DrugRepurposingResult cached = drugCache.get(drugId);

            // If we already have full details, return from cache
            if (cached.getEvidenceDetails() != null) {
                return cached;
            }

            // Otherwise, we'll enhance with full details below
        }

        try {
            DrugRepurposingResult basicInfo;

            // Get basic info from cache or create new
            if (drugCache.containsKey(drugId)) {
                basicInfo = drugCache.get(drugId);
            } else {
                Drug drug = drugRepurposingRepository.getDrugById(drugId);
                if (drug == null) {
                    throw new ServicesException("Drug not found: " + drugId);
                }

                double defaultScore = 0.5; // Default if not in context of disease
                basicInfo = new DrugRepurposingResult(
                        drugId,
                        drug.getName(),
                        defaultScore
                );
            }

            // Fetch additional information
            String evidenceDetails = drugRepurposingRepository.getEvidenceDetails(drugId);
            String adverseEffects = drugRepurposingRepository.getAdverseEffects(drugId);
            String dosageInfo = drugRepurposingRepository.getDosageInformation(drugId);

            // Create full result
            DrugRepurposingResult fullInfo = new DrugRepurposingResult(
                    basicInfo.getDrugId(),
                    basicInfo.getDrugName(),
                    basicInfo.getSuccessRate(),
                    basicInfo.getMechanismOfAction(),
                    basicInfo.getCurrentIndication(),
                    basicInfo.getEvidenceLevel(),
                    evidenceDetails,
                    adverseEffects,
                    dosageInfo
            );

            // Update cache
            drugCache.put(drugId, fullInfo);

            return fullInfo;
        } catch (RepositoryException e) {
            log.error("Error retrieving detailed drug information for drug: {}", drugId, e);
            throw new ServicesException("Error retrieving detailed drug information", e);
        }
    }

    @Override
    public List<DrugRepurposingResult> findPotentialDrugsForDisease(String diseaseId, int maxResults) throws ServicesException {
        try {
            log.info("Finding potential drugs for disease with no current treatments: {}", diseaseId);

            // Verify the disease exists
            Disease disease = drugRepurposingRepository.getDiseaseById(diseaseId);
            if (disease == null) {
                throw new ServicesException("Disease not found: " + diseaseId);
            }

            // Verify it has no current drugs
            List<String> existingDrugs = drugRepurposingRepository.getDrugsForDisease(diseaseId);
            if (!existingDrugs.isEmpty()) {
                log.info("Disease {} already has {} drugs. Using existing drugs.", diseaseId, existingDrugs.size());
                return getDrugRepurposingForDisease(diseaseId);
            }

            // Instead of evaluating all drugs, find candidates through network analysis
            Set<String> candidateDrugIds = findCandidateDrugs(diseaseId);
            log.info("Found {} potential drug candidates through network analysis for disease {}",
                    candidateDrugIds.size(), diseaseId);

            // If we have too few candidates, add some from structural similarity
            if (candidateDrugIds.size() < maxResults * 3) {
                List<String> structuralCandidatesList = drugRepurposingRepository.findCandidatesByStructuralSimilarity(diseaseId);
                Set<String> structuralCandidates = new HashSet<>(structuralCandidatesList);
                candidateDrugIds.addAll(structuralCandidates);
                log.info("Added {} additional candidates through structural similarity",
                        structuralCandidates.size());
            }

            // Set up a thread pool for parallel processing
            int numThreads = Math.min(4, candidateDrugIds.size());
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            try {
                // Process drugs in parallel using CompletableFuture
                List<CompletableFuture<DrugRepurposingResult>> futures = new ArrayList<>();

                for (String drugId : candidateDrugIds) {
                    CompletableFuture<DrugRepurposingResult> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            // Basic drug information
                            Drug drug = drugRepurposingRepository.getDrugById(drugId);
                            if (drug == null) return null;

                            // Calculate score
                            double score = calculateRepurposingScoreComprehensive(drugId, diseaseId);
                            log.info("Score for drug {}: {}", drugId, score);

                            // Create result object
                            String mechanismOfAction = ""; // Could call drugRepurposingRepository.getMechanismOfAction(drugId)
                            String currentIndication = ""; // Could call drugRepurposingRepository.getCurrentIndication(drugId)
                            String evidenceLevel = determineEvidenceLevel(score);

                            return new DrugRepurposingResult(
                                    drugId,
                                    drug.getAliases(),
                                    score,
                                    mechanismOfAction,
                                    currentIndication,
                                    evidenceLevel
                            );
                        } catch (Exception e) {
                            log.warn("Error processing drug {} for repurposing: {}", drugId, e.getMessage());
                            return null; // Return null for failed processing
                        }
                    }, executor);

                    futures.add(future);
                }

                // Wait for all futures to complete and collect results
                CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0])
                );

                // Create a combined future that will complete when all individual futures complete
                CompletableFuture<List<DrugRepurposingResult>> allResultsFuture = allFutures.thenApply(v ->
                        futures.stream()
                                .map(CompletableFuture::join)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList())
                );

                // Get the results (blocking call)
                List<DrugRepurposingResult> results = allResultsFuture.get();

                // Sort by score (highest first) and limit results
                List<DrugRepurposingResult> topResults = results.stream()
                        .sorted(Comparator.comparing(DrugRepurposingResult::getSuccessRate).reversed())
                        .limit(maxResults)
                        .collect(Collectors.toList());

                log.info("Found {} potential drugs for repurposing for disease {}", topResults.size(), diseaseId);
                return topResults;
            } finally {
                // Properly shut down the executor
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        } catch (RepositoryException e) {
            log.error("Error finding potential drugs for disease: {}", diseaseId, e);
            throw new ServicesException("Error finding potential drugs for disease", e);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error in parallel processing of drug candidates: {}", e.getMessage(), e);
            throw new ServicesException("Error in parallel processing of drug candidates", e);
        }
    }

    /**
     * Find candidate drugs through network analysis using the Interaction table.
     * This uses a multi-level approach to identify drugs based on:
     * 1. Drugs that target genes associated with the disease
     * 2. Drugs that affect pathways involved in the disease
     * 3. Drugs that are structurally similar to those for similar diseases
     */
    private Set<String> findCandidateDrugs(String diseaseId) throws RepositoryException {
        Set<String> candidateDrugs = new HashSet<>();

        /*for (String geneId : diseaseGenes) {
            // Find drugs that directly target this gene
            candidateDrugs.addAll(drugRepurposingRepository.findDrugsThatTargetGene(geneId));

            // Find drugs that interact with this gene through the interaction network
            candidateDrugs.addAll(drugRepurposingRepository.findDrugsThroughInteractionNetwork(geneId, 1)); // Depth of 2
        }*/

        List<String> similarDiseases = drugRepurposingRepository.getSimilarDiseases(diseaseId);
        for (String similarDisease : similarDiseases) {
            candidateDrugs.addAll(drugRepurposingRepository.getDrugsForDisease(similarDisease));
        }

        return candidateDrugs;
    }

    @Override
    public List<Disease> findDiseasesWithNoDrugs() throws ServicesException {
        try {
            log.info("Identifying diseases with no associated drugs");

            // Get all diseases
            List<Disease> allDiseases = drugRepurposingRepository.getAllDiseases();
            List<Disease> diseasesWithNoDrugs = new ArrayList<>();

            // For each disease, check if it has associated drugs
            for (Disease disease : allDiseases) {
                List<String> drugs = drugRepurposingRepository.getDrugsForDisease(disease.getKeggId());
                if (drugs.isEmpty()) {
                    diseasesWithNoDrugs.add(disease);
                }
            }

            log.info("Found {} diseases with no associated drugs", diseasesWithNoDrugs.size());
            return diseasesWithNoDrugs;

        } catch (RepositoryException e) {
            log.error("Error finding diseases with no drugs", e);
            throw new ServicesException("Error finding diseases with no drugs", e);
        }
    }

    @Override
    public Map<Disease, List<DrugRepurposingResult>> findTopDrugCandidatesForOrphanDiseases(
            int maxResultsPerDisease, int maxDiseases) throws ServicesException {

        try {
            log.info("Finding top drug candidates for orphan diseases");

            // Get diseases with no drugs
            List<Disease> orphanDiseases = findDiseasesWithNoDrugs();

            // Limit the number of diseases if needed
            if (orphanDiseases.size() > maxDiseases) {
                log.info("Limiting analysis to {} out of {} orphan diseases", maxDiseases, orphanDiseases.size());
                orphanDiseases = orphanDiseases.subList(0, maxDiseases);
            }

            // For each disease, find potential drugs
            Map<Disease, List<DrugRepurposingResult>> results = new HashMap<>();

            for (Disease disease : orphanDiseases) {
                try {
                    List<DrugRepurposingResult> potentialDrugs =
                            findPotentialDrugsForDisease(disease.getKeggId(), maxResultsPerDisease);

                    if (!potentialDrugs.isEmpty()) {
                        results.put(disease, potentialDrugs);
                    }
                } catch (Exception e) {
                    log.warn("Error finding drugs for disease {}: {}", disease.getKeggId(), e.getMessage());
                    // Continue with next disease
                }
            }

            log.info("Found potential drug candidates for {} out of {} orphan diseases",
                    results.size(), orphanDiseases.size());

            return results;

        } catch (Exception e) {
            log.error("Error finding top drug candidates for orphan diseases", e);
            throw new ServicesException("Error finding top drug candidates for orphan diseases", e);
        }
    }

    // Helper method for comprehensive drug repurposing score calculation
    private double calculateRepurposingScoreComprehensive(String drugId, String diseaseId) throws RepositoryException {
        try {
            // Base score from repository
            double baseScore = drugRepurposingRepository.calculateRepurposingScore(drugId, diseaseId);

            // Enhanced scoring based on additional factors:

            // 1. Gene target overlap between disease and drug
            List<String> diseaseGenes = drugRepurposingRepository.getGenesForDisease(diseaseId);
            List<String> drugTargetGenes = drugRepurposingRepository.getGeneTargetsForDrug(drugId);

            double geneOverlapScore = calculateOverlapScore(diseaseGenes, drugTargetGenes);

            // 2. Pathway overlap
            List<String> diseasePathways = drugRepurposingRepository.getPathwaysForDisease(diseaseId);
            List<String> drugPathways = drugRepurposingRepository.getPathwaysForDrug(drugId);

            double pathwayOverlapScore = calculateOverlapScore(diseasePathways, drugPathways);

            // 3. Molecular similarity to known treatments for similar diseases
            double molecularSimilarityScore = calculateMolecularSimilarityScore(drugId, diseaseId);

            // Calculate weighted final score
            double finalScore = (baseScore * 0.3) +
                    (geneOverlapScore * 0.3) +
                    (pathwayOverlapScore * 0.2) +
                    (molecularSimilarityScore * 0.2);

            // Ensure score is between 0 and 1
            return Math.max(0.0, Math.min(1.0, finalScore));

        } catch (Exception e) {
            log.error("Error calculating comprehensive repurposing score", e);
            // Fall back to base score from repository
            return drugRepurposingRepository.calculateRepurposingScore(drugId, diseaseId);
        }
    }

    // Calculate overlap score between two lists
    private double calculateOverlapScore(List<String> list1, List<String> list2) {
        if (list1.isEmpty() || list2.isEmpty()) {
            return 0.0;
        }

        Set<String> set1 = new HashSet<>(list1);
        Set<String> set2 = new HashSet<>(list2);

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    // Calculate molecular similarity score
    private double calculateMolecularSimilarityScore(String drugId, String diseaseId) throws RepositoryException {
        // Find similar diseases
        List<String> similarDiseases = drugRepurposingRepository.getSimilarDiseases(diseaseId);

        if (similarDiseases.isEmpty()) {
            return 0.0;
        }

        // Get drugs for similar diseases
        Set<String> relatedDrugs = new HashSet<>();
        for (String similarDisease : similarDiseases) {
            relatedDrugs.addAll(drugRepurposingRepository.getDrugsForDisease(similarDisease));
        }

        if (relatedDrugs.isEmpty()) {
            return 0.0;
        }

        // Calculate molecular similarity
        double totalSimilarity = 0.0;
        int count = 0;

        for (String relatedDrug : relatedDrugs) {
            double similarity = drugRepurposingRepository.calculateMolecularSimilarity(drugId, relatedDrug);
            totalSimilarity += similarity;
            count++;
        }

        return count > 0 ? totalSimilarity / count : 0.0;
    }


    // Helper method to determine evidence level based on score
    private String determineEvidenceLevel(double score) {
        if (score >= 0.8) {
            return "Strong clinical evidence";
        } else if (score >= 0.6) {
            return "Moderate clinical evidence";
        } else if (score >= 0.4) {
            return "Strong preclinical evidence";
        } else if (score >= 0.2) {
            return "Moderate preclinical evidence";
        } else {
            return "Theoretical only";
        }
    }

    // Fetch additional drug data from KEGG if needed
    private String fetchFromKEGG(String drugId) {
        try {
            String url = "https://rest.kegg.jp/get/dr:" + drugId;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
            }

            return response.toString();
        } catch (IOException e) {
            log.error("Error fetching drug data from KEGG for drug: {}", drugId, e);
            return null;
        }
    }

    /**
     * Get drug repurposing options for a disease with pagination.
     *
     * @param diseaseId the disease ID to find drug repurposing options for
     * @param offset the starting index
     * @param limit the maximum number of results to return
     * @throws ServicesException if an error occurs during processing
     */
    @Override
    public List<DrugRepurposingResult> getDrugRepurposingForDiseasePaginated(String diseaseId, int offset, int limit) throws ServicesException {
        try {
            // Get all drugs using the optimized method
            List<DrugRepurposingResult> allDrugs = getDrugsForDisease(diseaseId, Integer.MAX_VALUE);

            // Apply pagination
            int endIndex = Math.min(offset + limit, allDrugs.size());
            if (offset >= allDrugs.size()) {
                return Collections.emptyList();
            }

            return allDrugs.subList(offset, endIndex);
        } catch (Exception e) {
            throw new ServicesException("Error retrieving paginated drug repurposing options", e);
        }
    }
}