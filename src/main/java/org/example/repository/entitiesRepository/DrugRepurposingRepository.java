package org.example.repository.entitiesRepository;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.domain.Disease;
import org.example.domain.Drug;
import org.example.repository.JdbcUtils;
import org.example.repository.RepositoryException;
import org.example.repository.interfaces.IDrugRepurposingRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;

import org.example.repository.interfaces.Repository;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.fingerprint.*;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.similarity.Tanimoto;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

public class DrugRepurposingRepository extends BaseRepository implements IDrugRepurposingRepository {
    private static final Logger log = LogManager.getLogger(DrugRepurposingRepository.class);
    private final Map<String, String> smilesCache = new ConcurrentHashMap<>();

    public DrugRepurposingRepository(JdbcUtils jdbcUtils) {
        super(jdbcUtils);
        log.info("Initializing DrugRepurposingRepository...");
    }

    // Overload for using with an existing connection
    @Override
    public List<String> getDrugsForDisease(String diseaseId) throws RepositoryException {
        return withConnection(conn -> {
            String sql = "SELECT drug_id FROM DiseaseDrugs WHERE disease_id = ?";
            List<String> drugIds = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, diseaseId);  // Set parameter first

                try (ResultSet rs = ps.executeQuery()) {  // Then execute query
                    while (rs.next()) {
                        drugIds.add(rs.getString("drug_id"));
                    }
                }

                return drugIds;
            }
        });
    }

    /**
     * Get drugs for a specific disease using a provided database connection.
     *
     * @param diseaseId the disease ID
     * @param conn the database connection to use
     * @return list of drug IDs associated with the disease
     * @throws SQLException if database error occurs
     */
    public List<String> getDrugsForDisease(String diseaseId, Connection conn) throws SQLException {
        String sql = "SELECT drug_id FROM DiseaseDrugs WHERE disease_id = ?";
        List<String> drugIds = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, diseaseId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    drugIds.add(rs.getString("drug_id"));
                }
            }
        }

        return drugIds;
    }

    @Override
    public Drug getDrugById(String drugId) throws RepositoryException {
        return withConnection(conn -> {
            // First check if the drug exists
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM Drugs WHERE id = ?")) {
                ps.setString(1, drugId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                }
            }

            // Get drug aliases as the name
            String alias = drugId; // Default to ID if no alias

            try (PreparedStatement psAlias = conn.prepareStatement("SELECT alias FROM DrugAliases WHERE drug_id = ? LIMIT 1")) {
                psAlias.setString(1, drugId);

                try (ResultSet rsAlias = psAlias.executeQuery()) {
                    if (rsAlias.next()) {
                        alias = rsAlias.getString("alias");
                    }
                }
            }

            // Create and return drug object
            return new Drug(drugId, alias);
        });
    }

    @Override
    public Disease getDiseaseById(String diseaseId) throws RepositoryException {
        return withConnection(conn -> {
            // First check if the disease exists
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM Diseases WHERE id = ?")) {
                ps.setString(1, diseaseId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                }
            }

            // Get disease alias as the name
            String alias = diseaseId; // Default to ID if no alias

            try (PreparedStatement psAlias = conn.prepareStatement("SELECT alias FROM DiseaseAliases WHERE disease_id = ? LIMIT 1")) {
                psAlias.setString(1, diseaseId);

                try (ResultSet rsAlias = psAlias.executeQuery()) {
                    if (rsAlias.next()) {
                        alias = rsAlias.getString("alias");
                    }
                }
            }

            // Create and return disease object
            return new Disease(diseaseId, alias);
        });
    }

    @Override
    public double calculateRepurposingScore(String drugId, String diseaseId) throws RepositoryException {
        try {
            // Create an ExecutorService with a fixed thread pool
            int numThreads = Math.max(4, Runtime.getRuntime().availableProcessors());
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            try {
                // Create futures for each calculation that can be parallelized
                Future<List<String>> diseaseGenesFuture = executor.submit(() -> getGenesForDisease(diseaseId));
                Future<List<String>> drugTargetGenesFuture = executor.submit(() -> getDrugTargetGenes(drugId));
                Future<List<String>> diseasePathwaysFuture = executor.submit(() -> getPathwaysForDisease(diseaseId));
                Future<List<String>> drugPathwaysFuture = executor.submit(() -> getDrugPathways(drugId));

                // Start similarity score calculations in parallel
                Future<Double> similarDrugScoreFuture = executor.submit(() -> calculateSimilarDrugScore(drugId, diseaseId));
                Future<Double> chemicalSimilarityScoreFuture = executor.submit(() -> calculateChemicalSimilarityScore(drugId, diseaseId));

                // Get the results from futures
                List<String> diseaseGenes = diseaseGenesFuture.get();
                List<String> drugTargetGenes = drugTargetGenesFuture.get();
                List<String> diseasePathways = diseasePathwaysFuture.get();
                List<String> drugPathways = drugPathwaysFuture.get();

                // Process gene overlap
                Set<String> commonGenes = Collections.synchronizedSet(new HashSet<>(diseaseGenes));
                commonGenes.retainAll(drugTargetGenes);
                int geneOverlapCount = commonGenes.size();

                // Process pathway overlap
                Set<String> commonPathways = Collections.synchronizedSet(new HashSet<>(diseasePathways));
                commonPathways.retainAll(drugPathways);
                int pathwayOverlapCount = commonPathways.size();

                // Get the similarity scores
                double similarDrugScore = similarDrugScoreFuture.get();
                double chemicalSimilarityScore = chemicalSimilarityScoreFuture.get();

                // Calculate normalized scores
                double geneScore = diseaseGenes.isEmpty() ? 0 : (double) geneOverlapCount / diseaseGenes.size();
                double pathwayScore = diseasePathways.isEmpty() ? 0 : (double) pathwayOverlapCount / diseasePathways.size();

                // Weights for different factors (can be adjusted)
                double geneWeight = 0.4;
                double pathwayWeight = 0.25;
                double similarDrugWeight = 0.2;
                double chemicalWeight = 0.15;

                // Calculate final score
                double finalScore = (geneScore * geneWeight) +
                        (pathwayScore * pathwayWeight) +
                        (similarDrugScore * similarDrugWeight) +
                        (chemicalSimilarityScore * chemicalWeight);

                // Ensure score is between 0 and 1
                return Math.max(0.0, Math.min(1.0, finalScore));
            } finally {
                // Shutdown the executor to prevent thread leaks
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                    throw new RepositoryException("Thread execution interrupted", e);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error in parallel execution while calculating repurposing score for drug {} and disease {}",
                    drugId, diseaseId, e);
            throw new RepositoryException("Error in multithreaded calculation", e);
        } catch (Exception e) {
            log.error("Error calculating repurposing score for drug {} and disease {}", drugId, diseaseId, e);
            throw new RepositoryException(e);
        }
    }

    // Helper method to get genes targeted by a drug
    private List<String> getDrugTargetGenes(String drugId) throws RepositoryException {
        return withConnection(conn -> {
            // This would query a drug-gene relationship table
            String sql = "SELECT DISTINCT gene_id FROM DrugGenes WHERE drug_id = ?";
            List<String> genes = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(sql);) {
                ps.setString(1, drugId);

                try (ResultSet rs = ps.executeQuery()) {
                    // Process results
                    while (rs.next()) {
                        genes.add(rs.getString("gene_id"));
                    }
                }
            }

            return genes;
        });
    }

    // Helper method to get pathways affected by a drug
    private List<String> getDrugPathways(String drugId) throws RepositoryException {
        return withConnection(conn -> {
            // This would query a drug-pathway relationship table
            String sql = "SELECT DISTINCT pathway_id FROM DrugPathways WHERE drug_id = ?";
            List<String> pathways = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, drugId);

                try (ResultSet rs = ps.executeQuery()) {
                    // Process results
                    while (rs.next()) {
                        pathways.add(rs.getString("pathway_id"));
                    }
                }
            }

            return pathways;
        });
    }

    // Calculate how similar this drug is to other drugs known to work for similar diseases
    private double calculateSimilarDrugScore(String drugId, String diseaseId) throws RepositoryException {
        // First get all disease genes
        List<String> diseaseGenes = getGenesForDisease(diseaseId);

        return withConnection(conn -> {
            Set<String> similarDiseases = new HashSet<>();

            // Find diseases that share genes with our target disease
            String sql = "SELECT disease_id FROM GeneDiseases WHERE gene_id = ? AND disease_id != ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (String gene : diseaseGenes) {
                    // For each gene, find diseases that share this gene
                    ps.setString(1, gene);
                    ps.setString(2, diseaseId);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            similarDiseases.add(rs.getString("disease_id"));
                        }
                    }
                }
            }

            // If we found no similar diseases, return 0
            if (similarDiseases.isEmpty()) {
                return 0.0;
            }

            // For calculating similarity
            int matchCount = 0;
            int totalComparisons = 0;

            // For each similar disease, get its drugs
            for (String similarDisease : similarDiseases) {
                // Get drugs for the disease
                List<String> diseaseDrugs = new ArrayList<>();

                String drugSql = "SELECT drug_id FROM DiseaseDrugs WHERE disease_id = ?";
                try (PreparedStatement drugPs = conn.prepareStatement(drugSql)) {
                    drugPs.setString(1, similarDisease);

                    try (ResultSet drugRs = drugPs.executeQuery()) {
                        while (drugRs.next()) {
                            diseaseDrugs.add(drugRs.getString("drug_id"));
                        }
                    }
                }

                // For each drug, compare targets
                for (String otherDrug : diseaseDrugs) {
                    // Get drug targets for both drugs
                    List<String> drugTargets = new ArrayList<>();
                    List<String> otherDrugTargets = new ArrayList<>();

                    // Get targets for both drugs
                    String targetSql = "SELECT DISTINCT gene_id FROM DrugGenes WHERE drug_id = ?";

                    // First drug targets
                    try (PreparedStatement targetPs = conn.prepareStatement(targetSql)) {
                        targetPs.setString(1, drugId);

                        try (ResultSet targetRs = targetPs.executeQuery()) {
                            while (targetRs.next()) {
                                drugTargets.add(targetRs.getString("gene_id"));
                            }
                        }
                    }

                    // Second drug targets
                    try (PreparedStatement otherTargetPs = conn.prepareStatement(targetSql)) {
                        otherTargetPs.setString(1, otherDrug);

                        try (ResultSet otherTargetRs = otherTargetPs.executeQuery()) {
                            while (otherTargetRs.next()) {
                                otherDrugTargets.add(otherTargetRs.getString("gene_id"));
                            }
                        }
                    }

                    // Calculate Jaccard similarity
                    Set<String> union = new HashSet<>(drugTargets);
                    union.addAll(otherDrugTargets);

                    if (!union.isEmpty()) {
                        Set<String> intersection = new HashSet<>(drugTargets);
                        intersection.retainAll(otherDrugTargets);

                        // Add to running totals
                        matchCount += intersection.size();
                        totalComparisons++;
                    }
                }
            }

            // Calculate average
            return totalComparisons > 0 ? (double) matchCount / totalComparisons : 0.0;
        });
    }

    // Calculate chemical similarity score
    private double calculateChemicalSimilarityScore(String drugId, String diseaseId) throws RepositoryException {
        // This would ideally use chemical structure similarity algorithms
        // For a basic implementation, we can use common compound bases

        return withConnection(conn -> {
            // 1. Get approved drugs for this disease
            List<String> approvedDrugs = getDrugsForDisease(diseaseId);

            if (approvedDrugs.isEmpty()) {
                // For diseases with no approved drugs, look at chemically similar drugs
                // approved for diseases with similar genetic profiles
                return calculateSimilarityToRelatedDiseaseDrugs(drugId, diseaseId);
            }

            // 2. Compare drug chemical composition with approved drugs
            int similarityCount = 0;
            int totalComparisons = 0;

            // Get chemical compounds in our drug
            List<String> drugCompounds = getDrugCompounds(drugId, conn);

            for (String approvedDrug : approvedDrugs) {
                List<String> approvedDrugCompounds = getDrugCompounds(approvedDrug, conn);

                // Count overlap in compounds
                Set<String> union = new HashSet<>(drugCompounds);
                union.addAll(approvedDrugCompounds);

                if (!union.isEmpty()) {
                    Set<String> intersection = new HashSet<>(drugCompounds);
                    intersection.retainAll(approvedDrugCompounds);

                    similarityCount += intersection.size();
                    totalComparisons++;
                }
            }

            // Calculate average similarity
            return totalComparisons > 0 ? (double) similarityCount / (totalComparisons * 5) : 0.0;
        });
    }

    // Overload for using with an existing connection
    private List<String> getDrugCompounds(String drugId, Connection conn) throws SQLException {
        Set<String> compounds = new HashSet<>();

        // Try direct query if DrugCompounds table exists
        try {
            String sql = "SELECT compound_id FROM DrugCompounds WHERE drug_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                ps.setString(1, drugId);

                while (rs.next()) {
                    compounds.add(rs.getString("compound_id"));
                }

                // If we got results, we can skip alternative approach
                if (!compounds.isEmpty()) {
                    return new ArrayList<>(compounds);
                }
            }
        } catch (SQLException e) {
            // Table might not exist, try alternative approach
            log.debug("DrugCompounds table not found, using alternative approach", e);
        }

        // If no results from direct query, try to parse compound information from drug aliases
        // Get drug aliases
        List<String> aliases = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement("SELECT alias FROM DrugAliases WHERE drug_id = ?")) {
            ps.setString(1, drugId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    aliases.add(rs.getString("alias"));
                }
            }
        }

        // Check aliases against known compounds
        if (!aliases.isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT compound_id FROM CompoundAliases WHERE alias LIKE ?")) {
                for (String alias : aliases) {
                    // Extract potential compound names from the alias
                    String[] words = alias.split("[\\s,\\-\\(\\)]");

                    for (String word : words) {
                        if (word.length() < 3) continue; // Skip short words

                        ps.setString(1, "%" + word + "%");

                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                compounds.add(rs.getString("compound_id"));
                            }
                        }
                    }
                }
            }
        }

        return new ArrayList<>(compounds);
    }

    // Calculate chemical similarity to drugs approved for related diseases
    private double calculateSimilarityToRelatedDiseaseDrugs(String drugId, String diseaseId) throws RepositoryException {
        return withConnection(conn -> {
            // Find diseases with similar genetic profiles
            List<String> diseaseGenes = getGenesForDisease(diseaseId);
            Set<String> relatedDiseases = new HashSet<>();

            // Query for related diseases using the provided connection
            String diseaseSql = "SELECT disease_id FROM GeneDiseases WHERE gene_id = ? AND disease_id != ?";
            for (String gene : diseaseGenes) {
                try (PreparedStatement ps = conn.prepareStatement(diseaseSql)) {
                    ps.setString(1, gene);
                    ps.setString(2, diseaseId);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            relatedDiseases.add(rs.getString("disease_id"));
                        }
                    }
                }
            }

            // Get approved drugs for related diseases
            Set<String> relatedDrugs = new HashSet<>();
            for (String relatedDisease : relatedDiseases) {
                relatedDrugs.addAll(getDrugsForDisease(relatedDisease, conn));
            }

            if (relatedDrugs.isEmpty()) {
                return 0.0;
            }

            // Compare our drug with related disease drugs
            int totalSimilarity = 0;
            int comparisonCount = 0;

            // Use the provided connection for compound retrieval
            List<String> drugCompounds = getDrugCompounds(drugId, conn);

            for (String relatedDrug : relatedDrugs) {
                List<String> relatedDrugCompounds = getDrugCompounds(relatedDrug, conn);

                Set<String> union = new HashSet<>(drugCompounds);
                union.addAll(relatedDrugCompounds);

                if (!union.isEmpty()) {
                    Set<String> intersection = new HashSet<>(drugCompounds);
                    intersection.retainAll(relatedDrugCompounds);

                    totalSimilarity += intersection.size();
                    comparisonCount++;
                }
            }

            return comparisonCount > 0 ? (double) totalSimilarity / (comparisonCount * 5) : 0.0;
        });
    }

    @Override
    public String getMechanismOfAction(String drugId) throws RepositoryException {
        // This would retrieve mechanism of action from a database
        // For demonstration, we'll use drug information available in the database
        try {
            // Query DrugAliases to get more information
            List<String> aliases = getDrugAliases(drugId);

            // Check if there are any aliases that might contain mechanism information
            for (String alias : aliases) {
                if (alias.contains("inhibitor") ||
                        alias.contains("agonist") ||
                        alias.contains("antagonist") ||
                        alias.contains("blocker")) {
                    return "Mechanism inferred from aliases: " + alias;
                }
            }

            // If no specific mechanism found, provide a placeholder
            return "Detailed mechanism of action information not available in database. " +
                    "May involve target binding or enzymatic modulation.";

        } catch (Exception e) {
            log.error("Error retrieving mechanism of action for drug: {}", drugId, e);
            throw new RepositoryException(e);
        }
    }

    private List<String> getDrugAliases(String drugId) throws RepositoryException {
        return withConnection(conn -> {
            List<String> aliases = new ArrayList<>();
            String sql = "SELECT alias FROM DrugAliases WHERE drug_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, drugId);

                try (ResultSet rs = ps.executeQuery()) {
                    // Process results
                    while (rs.next()) {
                        aliases.add(rs.getString("alias"));
                    }
                }
            }

            return aliases;
        });
    }

    @Override
    public String getCurrentIndication(String drugId) throws RepositoryException {
        return withConnection(conn -> {
            // Get diseases where this drug is currently approved
            List<String> currentDiseases = new ArrayList<>();
            String sql = "SELECT disease_id FROM DiseaseDrugs WHERE drug_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, drugId);

                try (ResultSet rs = ps.executeQuery()) {
                    // Process results
                    while (rs.next()) {
                        String diseaseId = rs.getString("disease_id");
                        Disease disease = getDiseaseById(diseaseId);
                        if (disease != null) {
                            currentDiseases.add(disease.getName());
                        }
                    }
                }
            }

            if (currentDiseases.isEmpty()) {
                return "No current indications found in database";
            } else {
                return "Currently approved for: " + String.join(", ", currentDiseases);
            }
        });
    }

    @Override
    public String getEvidenceDetails(String drugId) throws RepositoryException {
        // This would retrieve evidence details from literature or clinical trials
        // For demonstration, we'll return placeholder text
        return "Evidence based on preclinical studies and pathway analysis. " +
                "Target interactions suggest potential efficacy based on " +
                "molecular mechanisms similar to approved therapies.";
    }

    @Override
    public String getAdverseEffects(String drugId) throws RepositoryException {
        // This would retrieve adverse effects from a database
        // For demonstration, we'll return placeholder text
        return "Potential adverse effects may include those typical for this " +
                "drug class. Refer to existing drug literature for specific " +
                "safety profiles. Additional testing required.";
    }

    @Override
    public String getDosageInformation(String drugId) throws RepositoryException {
        // This would retrieve dosage information from a database
        // For demonstration, we'll return placeholder text
        return "Dosage would need to be determined through clinical trials " +
                "for new indications. Reference existing approved uses for " +
                "initial dosing guidelines.";
    }

    @Override
    public List<String> getGenesForDisease(String diseaseId) throws RepositoryException {
        return withConnection(conn -> {
            List<String> genes = new ArrayList<>();
            String sql = "SELECT gene_id FROM GeneDiseases WHERE disease_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, diseaseId);

                try (ResultSet rs = ps.executeQuery()) {
                    // Process results
                    while (rs.next()) {
                        genes.add(rs.getString("gene_id"));
                    }
                }
            }

            return genes;
        });
    }

    @Override
    public List<String> getPathwaysForDisease(String diseaseId) throws RepositoryException {
        // First get genes associated with the disease
        List<String> diseaseGenes = getGenesForDisease(diseaseId);

        return withConnection(conn -> {
            Set<String> pathways = new HashSet<>();
            String sql = "SELECT pathway_id FROM GenePathways WHERE gene_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                // For each gene, find associated pathways
                for (String geneId : diseaseGenes) {
                    ps.setString(1, geneId);

                    try (ResultSet rs = ps.executeQuery()) {
                        // Process results
                        while (rs.next()) {
                            pathways.add(rs.getString("pathway_id"));
                        }
                    }

                    // PreparedStatement is reused for each gene, no need to close it between iterations
                    ps.clearParameters();
                }
            }

            return new ArrayList<>(pathways);
        });
    }

    public List<String> getPathwaysForDisease(String diseaseId, Connection providedConn) throws RepositoryException {
        // First get genes associated with the disease
        List<String> diseaseGenes = getGenesForDisease(diseaseId);
        Set<String> pathways = new HashSet<>();

        boolean createdConnection = false;
        Connection conn = providedConn;

        try {
            // If no connection was provided, create a new one
            if (conn == null) {
                conn = jdbcUtils.getConnection();
                createdConnection = true;
            }

            // For each gene, find associated pathways
            String sql = "SELECT pathway_id FROM GenePathways WHERE gene_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (String geneId : diseaseGenes) {
                    ps.setString(1, geneId);

                    try (ResultSet rs = ps.executeQuery()) {
                        // Process results
                        while (rs.next()) {
                            pathways.add(rs.getString("pathway_id"));
                        }
                    }

                    // Clear parameters before reusing the PreparedStatement
                    ps.clearParameters();
                }
            }

            return new ArrayList<>(pathways);
        } catch (SQLException e) {
            log.error("Error fetching pathways for disease: {}", diseaseId, e);
            throw new RepositoryException(e);
        } finally {
            // Only release the connection if we created it
            if (createdConnection && conn != null) {
                jdbcUtils.releaseConnection(conn);
            }
        }
    }

    @Override
    public List<String> getDrugsThatTargetGene(String geneId) throws RepositoryException {
        return withConnection(conn -> {
            List<String> drugs = new ArrayList<>();
            String sql = "SELECT drug_id FROM DrugGenes WHERE gene_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, geneId);

                try (ResultSet rs = ps.executeQuery()) {
                    // Process results
                    while (rs.next()) {
                        drugs.add(rs.getString("drug_id"));
                    }
                }
            }

            return drugs;
        });
    }

    @Override
    public List<String> getDrugsThatAffectPathway(String pathwayId) throws RepositoryException {
        // This is a more complex query that might involve multiple tables
        // Get genes in the pathway, then drugs that target those genes
        List<String> pathwayGenes = getGenesInPathway(pathwayId);
        Set<String> drugs = new HashSet<>();

        for (String geneId : pathwayGenes) {
            drugs.addAll(getDrugsThatTargetGene(geneId));
        }

        return new ArrayList<>(drugs);
    }

    private List<String> getGenesInPathway(String pathwayId) throws RepositoryException {
        return withConnection(conn -> {
            List<String> genes = new ArrayList<>();
            String sql = "SELECT gene_id FROM GenePathways WHERE pathway_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, pathwayId);

                try (ResultSet rs = ps.executeQuery()) {
                    // Process results
                    while (rs.next()) {
                        genes.add(rs.getString("gene_id"));
                    }
                }
            }

            return genes;
        });
    }

    @Override
    public boolean isDrugApprovedForDisease(String drugId, String diseaseId) throws RepositoryException {
        return withConnection(conn -> {
            String sql = "SELECT 1 FROM DiseaseDrugs WHERE drug_id = ? AND disease_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, drugId);
                ps.setString(2, diseaseId);

                try (ResultSet rs = ps.executeQuery()) {
                    // Process result
                    return rs.next(); // True if there's a match
                }
            }
        });
    }

    @Override
    public List<Disease> getAllDiseases() throws RepositoryException {
        return withConnection(conn -> {
            List<Disease> diseases = new ArrayList<>();
            String sql = "SELECT id FROM Diseases";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    // Process results
                    while (rs.next()) {
                        String diseaseId = rs.getString("disease_id");
                        Disease disease = getDiseaseById(diseaseId);
                        if (disease != null) {
                            diseases.add(disease);
                        }
                    }
                }
            }

            return diseases;
        });
    }

    @Override
    public List<String> getAllDrugIds() throws RepositoryException {
        return withConnection(conn -> {
            List<String> drugIds = new ArrayList<>();
            String sql = "SELECT id FROM Drugs";

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                // Process results
                while (rs.next()) {
                    drugIds.add(rs.getString("id"));
                }
            }

            return drugIds;
        });
    }

    @Override
    public List<String> getGeneTargetsForDrug(String drugId) throws RepositoryException {
        return withConnection(conn -> {
            Set<String> geneTargets = new HashSet<>();

            // First try direct drug-gene relationships (if table exists)
            try {
                try (PreparedStatement ps = conn.prepareStatement("SELECT gene_id FROM DrugGenes WHERE drug_id = ?")) {
                    ps.setString(1, drugId);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            geneTargets.add(rs.getString("gene_id"));
                        }
                    }
                }
            } catch (SQLException e) {
                // Table might not exist, try alternative approach
                log.debug("DrugGenes table not found, trying alternative approach", e);
            }

            // If no direct mappings found, try inferring through pathways
            if (geneTargets.isEmpty()) {
                // Get pathways affected by the drug using the existing connection
                List<String> pathways = getPathwaysForDrug(drugId, conn);

                // For each pathway, get associated genes using the same connection
                for (String pathwayId : pathways) {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT gene_id FROM GenePathways WHERE pathway_id = ?")) {
                        ps.setString(1, pathwayId);

                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                geneTargets.add(rs.getString("gene_id"));
                            }
                        }
                    }
                }
            }

            // If still no results, try through disease associations
            if (geneTargets.isEmpty()) {
                // Get diseases treated by this drug using the same connection
                List<String> treatedDiseases = new ArrayList<>();

                try (PreparedStatement ps = conn.prepareStatement("SELECT disease_id FROM DiseaseDrugs WHERE drug_id = ?")) {
                    ps.setString(1, drugId);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            treatedDiseases.add(rs.getString("disease_id"));
                        }
                    }
                }

                // For each disease, get associated genes using the same connection
                for (String diseaseId : treatedDiseases) {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT gene_id FROM GeneDiseases WHERE disease_id = ?")) {
                        ps.setString(1, diseaseId);

                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                geneTargets.add(rs.getString("gene_id"));
                            }
                        }
                    }
                }
            }

            // Return list from set (already ensures uniqueness)
            return new ArrayList<>(geneTargets);
        });
    }

    @Override
    public List<String> getPathwaysForDrug(String drugId) throws RepositoryException {
        return withConnection(conn -> {
            Set<String> pathways = new HashSet<>();

            // Try direct drug-pathway relationships (if table exists)
            try {
                try (PreparedStatement ps = conn.prepareStatement("SELECT pathway_id FROM DrugPathways WHERE drug_id = ?")) {
                    ps.setString(1, drugId);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            pathways.add(rs.getString("pathway_id"));
                        }
                    }
                }
            } catch (SQLException e) {
                // Table might not exist, try alternative approach
                log.debug("DrugPathways table not found, trying alternative approach", e);
            }

            // If no direct mappings found, try inferring through gene targets
            if (pathways.isEmpty()) {
                // Get genes targeted by the drug
                Set<String> targetGenes = new HashSet<>();

                // Try from DrugGenes table if it exists
                try {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT gene_id FROM DrugGenes WHERE drug_id = ?")) {
                        ps.setString(1, drugId);

                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                targetGenes.add(rs.getString("gene_id"));
                            }
                        }
                    }
                } catch (SQLException e) {
                    // DrugGenes table might not exist
                    log.debug("DrugGenes table not found", e);
                }

                // If no target genes found, try through disease associations
                if (targetGenes.isEmpty()) {
                    // Get diseases treated by this drug
                    List<String> treatedDiseases = new ArrayList<>();

                    try (PreparedStatement ps = conn.prepareStatement("SELECT disease_id FROM DiseaseDrugs WHERE drug_id = ?")) {
                        ps.setString(1, drugId);

                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                treatedDiseases.add(rs.getString("disease_id"));
                            }
                        }
                    }

                    // For each disease, get associated genes
                    for (String diseaseId : treatedDiseases) {
                        try (PreparedStatement ps = conn.prepareStatement("SELECT gene_id FROM GeneDiseases WHERE disease_id = ?")) {
                            ps.setString(1, diseaseId);

                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    targetGenes.add(rs.getString("gene_id"));
                                }
                            }
                        }
                    }
                }

                // For each gene, get associated pathways
                for (String geneId : targetGenes) {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT pathway_id FROM GenePathways WHERE gene_id = ?")) {
                        ps.setString(1, geneId);

                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                pathways.add(rs.getString("pathway_id"));
                            }
                        }
                    }
                }
            }

            // Return list from set (already ensures uniqueness)
            return new ArrayList<>(pathways);
        });
    }

    /**
     * Gets pathways for a drug using a provided connection
     * @param drugId the drug ID to find pathways for
     * @param conn the database connection to use
     * @return list of pathway IDs associated with the drug
     * @throws RepositoryException if database error occurs
     */
    public List<String> getPathwaysForDrug(String drugId, Connection conn) throws RepositoryException {
        Set<String> pathways = new HashSet<>();

        try {
            // Try direct drug-pathway relationships (if table exists)
            try {
                try (PreparedStatement ps = conn.prepareStatement("SELECT pathway_id FROM DrugPathways WHERE drug_id = ?")) {
                    ps.setString(1, drugId);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            pathways.add(rs.getString("pathway_id"));
                        }
                    }
                }
            } catch (SQLException e) {
                // Table might not exist, try alternative approach
                log.debug("DrugPathways table not found, trying alternative approach", e);
            }

            // If no direct mappings found, try inferring through gene targets
            if (pathways.isEmpty()) {
                // Get genes targeted by the drug
                Set<String> targetGenes = new HashSet<>();

                // Try from DrugGenes table if it exists
                try {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT gene_id FROM DrugGenes WHERE drug_id = ?")) {
                        ps.setString(1, drugId);

                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                targetGenes.add(rs.getString("gene_id"));
                            }
                        }
                    }
                } catch (SQLException e) {
                    // DrugGenes table might not exist
                    log.debug("DrugGenes table not found", e);
                }

                // If no target genes found, try through disease associations
                if (targetGenes.isEmpty()) {
                    // Get diseases treated by this drug
                    List<String> treatedDiseases = new ArrayList<>();

                    try (PreparedStatement ps = conn.prepareStatement("SELECT disease_id FROM DiseaseDrugs WHERE drug_id = ?")) {
                        ps.setString(1, drugId);

                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                treatedDiseases.add(rs.getString("disease_id"));
                            }
                        }
                    }

                    // For each disease, get associated genes
                    for (String diseaseId : treatedDiseases) {
                        try (PreparedStatement ps = conn.prepareStatement("SELECT gene_id FROM GeneDiseases WHERE disease_id = ?")) {
                            ps.setString(1, diseaseId);

                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    targetGenes.add(rs.getString("gene_id"));
                                }
                            }
                        }
                    }
                }

                // For each gene, get associated pathways
                for (String geneId : targetGenes) {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT pathway_id FROM GenePathways WHERE gene_id = ?")) {
                        ps.setString(1, geneId);

                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                pathways.add(rs.getString("pathway_id"));
                            }
                        }
                    }
                }
            }

            // Return list from set (already ensures uniqueness)
            return new ArrayList<>(pathways);

        } catch (SQLException e) {
            log.error("Error getting pathways for drug: {}", drugId, e);
            throw new RepositoryException("Error getting pathways for drug", e);
        }
        // Note: The connection is NOT closed here as it was provided by the caller
    }

    @Override
    public List<String> getSimilarDiseases(String diseaseId) throws RepositoryException {
        // We'll consider diseases similar if they:
        // 1. Share a significant number of genes
        // 2. Share a significant number of pathways
        // 3. Are treated by the same drugs

        return withConnection(conn -> {
            Map<String, Integer> diseaseSimilarityScores = new HashMap<>();

            // 1. Find diseases that share genes
            List<String> diseaseGenes = getGenesForDisease(diseaseId);

            if (!diseaseGenes.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT disease_id FROM GeneDiseases WHERE gene_id = ? AND disease_id != ?")) {
                    for (String geneId : diseaseGenes) {
                        ps.setString(1, geneId);
                        ps.setString(2, diseaseId);

                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String otherDiseaseId = rs.getString("disease_id");
                                diseaseSimilarityScores.merge(otherDiseaseId, 3, Integer::sum); // 3 points for gene overlap
                            }
                        }

                        // Clear parameters for reuse
                        ps.clearParameters();
                    }
                }
            }

            // 2. Find diseases that share pathways
            List<String> diseasePathways = getPathwaysForDisease(diseaseId, conn);

            if (!diseasePathways.isEmpty()) {
                // For each pathway, find other diseases
                for (String pathwayId : diseasePathways) {
                    // Get all diseases associated with this pathway
                    List<String> pathwayDiseases = getDiseasesForPathway(pathwayId, conn);

                    for (String otherDiseaseId : pathwayDiseases) {
                        if (!otherDiseaseId.equals(diseaseId)) {
                            diseaseSimilarityScores.merge(otherDiseaseId, 2, Integer::sum); // 2 points for pathway overlap
                        }
                    }
                }
            }

            // 3. Find diseases treated by the same drugs
            List<String> diseaseDrugs = getDrugsForDisease(diseaseId, conn);

            if (!diseaseDrugs.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT disease_id FROM DiseaseDrugs WHERE drug_id = ? AND disease_id != ?")) {
                    for (String drugId : diseaseDrugs) {
                        ps.setString(1, drugId);
                        ps.setString(2, diseaseId);

                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String otherDiseaseId = rs.getString("disease_id");
                                diseaseSimilarityScores.merge(otherDiseaseId, 5, Integer::sum); // 5 points for shared treatment
                            }
                        }

                        // Clear parameters for reuse
                        ps.clearParameters();
                    }
                }
            }

            // Sort by similarity score (descending) and return top diseases
            return diseaseSimilarityScores.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .filter(entry -> entry.getValue() >= 5) // Minimum similarity threshold
                    .limit(10) // Top 10 similar diseases
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        });
    }

    public List<String> getDiseasesForPathway(String pathwayId, Connection conn) throws RepositoryException {
        Set<String> diseases = new HashSet<>();
        List<String> pathwayGenes = new ArrayList<>();

        // Step 1: Get genes in the pathway
        String getGenesSql = "SELECT gene_id FROM GenePathways WHERE pathway_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(getGenesSql)) {
            ps.setString(1, pathwayId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pathwayGenes.add(rs.getString("gene_id"));
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Error querying genes for pathway: " + pathwayId, e);
        }

        // Step 2: For each gene, get associated diseases
        String getDiseasesSql = "SELECT disease_id FROM GeneDiseases WHERE gene_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(getDiseasesSql)) {
            for (String geneId : pathwayGenes) {
                ps.setString(1, geneId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        diseases.add(rs.getString("disease_id"));
                    }
                }
                ps.clearParameters();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Error querying diseases for pathway genes", e);
        }

        return new ArrayList<>(diseases);
    }


    // Helper method to get diseases for a pathway
    public List<String> getDiseasesForPathway(String pathwayId) throws RepositoryException {
        return withConnection(conn -> {
            Set<String> diseases = new HashSet<>();

            // Step 1: Get genes in the pathway
            List<String> pathwayGenes = new ArrayList<>();
            String getGenesSql = "SELECT gene_id FROM GenePathways WHERE pathway_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(getGenesSql)) {
                ps.setString(1, pathwayId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        pathwayGenes.add(rs.getString("gene_id"));
                    }
                }
            }

            // Step 2: For each gene, get associated diseases
            String getDiseasesSql = "SELECT disease_id FROM GeneDiseases WHERE gene_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(getDiseasesSql)) {
                for (String geneId : pathwayGenes) {
                    ps.setString(1, geneId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            diseases.add(rs.getString("disease_id"));
                        }
                    }
                    ps.clearParameters();
                }
            }

            return new ArrayList<>(diseases);
        });
    }


    private String getDrugSmiles(String drugId) throws SQLException {
        final String NO_SMILES_FOUND = "NO_SMILES_AVAILABLE";

        // Check the cache
        String cached = smilesCache.get(drugId);
        if (cached != null) {
            log.debug("Cache hit for drug SMILES: {}", drugId);
            return NO_SMILES_FOUND.equals(cached) ? null : cached;
        }

        log.debug("Cache miss for drug SMILES: {}, querying database", drugId);

        return withConnection(conn -> {
            final String sql = "SELECT smiles FROM DrugStructures WHERE drug_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, drugId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String smiles = rs.getString("smiles");
                        log.debug("Found SMILES for drug {}: {}", drugId, smiles);

                        if (isOrganometallic(smiles)) {
                            log.info("Found potential organometallic compound with SMILES: {}", smiles);
                        }

                        smilesCache.put(drugId, smiles);
                        return smiles;
                    }
                }

                log.debug("No SMILES found for drug: {}", drugId);
                smilesCache.put(drugId, NO_SMILES_FOUND);
                return null;

            } catch (SQLException e) {
                log.error("Database error when retrieving SMILES for drug {}: {}", drugId, e.getMessage(), e);
                throw e;
            }
        });
    }

    private boolean isOrganometallic(String smiles) {
        return smiles != null && (
                smiles.contains("[Pt]") ||
                        smiles.contains("[Fe]") ||
                        smiles.contains("[Ru]") ||
                        smiles.contains("[Os]") ||
                        smiles.contains("->") ||
                        smiles.contains("<-")
        );
    }

    private String getCompoundSmiles(String compoundId) throws SQLException {
        // Check cache first
        if (smilesCache.containsKey(compoundId)) {
            return smilesCache.get(compoundId);
        }

        String sql = "SELECT smiles FROM CompoundStructures WHERE compound_id = ?";

        return withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, compoundId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String smiles = rs.getString("smiles");
                        smilesCache.put(compoundId, smiles);
                        return smiles;
                    } else {
                        smilesCache.put(compoundId, null);
                        return null;
                    }
                }
            }
        });
    }


    @Override
    public double calculateMolecularSimilarity(String drugId1, String drugId2) throws RepositoryException {
        try {
            // Get SMILES strings for both drugs from DrugStructures table
            String smiles1 = getDrugSmiles(drugId1);
            String smiles2 = getDrugSmiles(drugId2);

            if (smiles1 == null || smiles2 == null) {
                log.warn("Missing SMILES data for drug comparison: {} and {}", drugId1, drugId2);
                // Fall back to previous approach if SMILES data is missing
                return calculateSimilarityByTargetsAndPathways(drugId1, drugId2);
            }

            // Calculate multiple fingerprint-based similarities using CDK
            double maccsCoefficient = calculateMACCSSimilarity(smiles1, smiles2);
            double pubchemCoefficient = calculatePubchemSimilarity(smiles1, smiles2);
            double extendedCoefficient = calculateExtendedSimilarity(smiles1, smiles2);

            // Use weighted average of different fingerprint methods for more robust similarity
            double structuralSimilarity = (maccsCoefficient * 0.4) +
                    (pubchemCoefficient * 0.3) +
                    (extendedCoefficient * 0.3);

            // Compare functional properties (target genes and pathways)
            double functionalSimilarity = calculateFunctionalSimilarity(drugId1, drugId2);

            // Combine structural and functional similarity (70% structural, 30% functional)
            return (structuralSimilarity * 0.7) + (functionalSimilarity * 0.3);
        } catch (Exception e) {
            log.error("Error calculating molecular similarity between drugs: {} and {}", drugId1, drugId2, e);
            // Fix: Don't cast non-SQLException exceptions to SQLException
            if (e instanceof SQLException) {
                throw new RepositoryException("Error calculating molecular similarity", (SQLException) e);
            } else {
                throw new RepositoryException("Error calculating molecular similarity: " + e.getMessage(), e);
            }
        }
    }

    // Calculate similarity using MACCS fingerprints (166 structural keys)
    private double calculateMACCSSimilarity(String smiles1, String smiles2) throws Exception {
        try {
            // Check if either SMILES contains organometallic features
            if (containsOrganometallicFeatures(smiles1) || containsOrganometallicFeatures(smiles2)) {
                log.info("Using alternative similarity method for organometallic compounds");
                return calculateAlternativeSimilarity(smiles1, smiles2);
            }

            // Parse SMILES strings to CDK molecules
            SmilesParser parser = new SmilesParser(DefaultChemObjectBuilder.getInstance());
            IAtomContainer mol1 = parser.parseSmiles(smiles1);
            IAtomContainer mol2 = parser.parseSmiles(smiles2);

            // Preprocessing molecules (add implicit hydrogens, perceive atom types, etc.)
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(mol1);
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(mol2);

            // Generate MACCS fingerprints
            MACCSFingerprinter fingerprinter = new MACCSFingerprinter();
            IBitFingerprint fp1 = fingerprinter.getBitFingerprint(mol1);
            IBitFingerprint fp2 = fingerprinter.getBitFingerprint(mol2);

            // Calculate Tanimoto coefficient
            return Tanimoto.calculate(fp1, fp2);
        } catch (Exception e) {
            log.warn("Error calculating MACCS similarity, returning 0: {}", e.getMessage());
            return 0.0;
        }
    }

    // Helper method to check for organometallic features
    private boolean containsOrganometallicFeatures(String smiles) {
        if (smiles == null) return false;

        // Check for transition metals or coordinate bonds
        return smiles.contains("[Pt]") ||
                smiles.contains("[Fe]") ||
                smiles.contains("[Ru]") ||
                smiles.contains("[Os]") ||
                smiles.contains("[Pd]") ||
                smiles.contains("[Rh]") ||
                smiles.contains("[Ir]") ||
                smiles.contains("->") ||
                smiles.contains("<-");
    }

    // Alternative similarity calculation for organometallic compounds
    private double calculateAlternativeSimilarity(String smiles1, String smiles2) {
        // Fall back to property-based similarity for organometallic compounds
        // This approach doesn't rely on fingerprints that may fail with coordinate bonds

        try {
            // Compare basic molecular properties that don't require fingerprinting
            return calculatePropertyBasedSimilarity(smiles1, smiles2);
        } catch (Exception e) {
            log.warn("Error calculating alternative similarity, returning 0: {}", e.getMessage());
            return 0.0;
        }
    }

    // Calculate similarity based on molecular properties
    private double calculatePropertyBasedSimilarity(String smiles1, String smiles2) {
        try {
            // Use a more relaxed SmilesParser for organometallic compounds
            SmilesParser parser = new SmilesParser(DefaultChemObjectBuilder.getInstance());
            parser.kekulise(false); // Turn off kekulization for problematic structures

            IAtomContainer mol1 = null;
            IAtomContainer mol2 = null;

            try {
                mol1 = parser.parseSmiles(smiles1);
            } catch (Exception e) {
                log.warn("Could not parse first SMILES with relaxed parser: {}", e.getMessage());
                return 0.0;
            }

            try {
                mol2 = parser.parseSmiles(smiles2);
            } catch (Exception e) {
                log.warn("Could not parse second SMILES with relaxed parser: {}", e.getMessage());
                return 0.0;
            }

            // Compare basic properties that don't require full structure interpretation
            int atomCountDiff = Math.abs(mol1.getAtomCount() - mol2.getAtomCount());
            int bondCountDiff = Math.abs(mol1.getBondCount() - mol2.getBondCount());

            // Calculate a simple similarity score based on atom and bond count differences
            double maxAtomCount = Math.max(mol1.getAtomCount(), mol2.getAtomCount());
            double maxBondCount = Math.max(mol1.getBondCount(), mol2.getBondCount());

            double atomSimilarity = maxAtomCount > 0 ? 1.0 - (atomCountDiff / maxAtomCount) : 0.0;
            double bondSimilarity = maxBondCount > 0 ? 1.0 - (bondCountDiff / maxBondCount) : 0.0;

            // Simple weighted average
            return (atomSimilarity * 0.5) + (bondSimilarity * 0.5);
        } catch (Exception e) {
            log.warn("Error calculating property-based similarity, returning 0: {}", e.getMessage());
            return 0.0;
        }
    }

    // Calculate similarity using PubChem fingerprints (881 bits)
    private double calculatePubchemSimilarity(String smiles1, String smiles2) throws Exception {
        try {
            // Parse SMILES strings to CDK molecules
            SmilesParser parser = new SmilesParser(DefaultChemObjectBuilder.getInstance());
            IAtomContainer mol1 = parser.parseSmiles(smiles1);
            IAtomContainer mol2 = parser.parseSmiles(smiles2);

            // Preprocessing molecules
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(mol1);
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(mol2);

            // Generate PubChem fingerprints
            PubchemFingerprinter fingerprinter = new PubchemFingerprinter(DefaultChemObjectBuilder.getInstance());
            IBitFingerprint fp1 = fingerprinter.getBitFingerprint(mol1);
            IBitFingerprint fp2 = fingerprinter.getBitFingerprint(mol2);

            // Calculate Tanimoto coefficient
            return Tanimoto.calculate(fp1, fp2);
        } catch (Exception e) {
            log.warn("Error calculating PubChem similarity, returning 0: {}", e.getMessage());
            return 0.0;
        }
    }

    // Calculate similarity using Extended fingerprints (1024 bits)
    private double calculateExtendedSimilarity(String smiles1, String smiles2) throws Exception {
        try {
            // Parse SMILES strings to CDK molecules
            SmilesParser parser = new SmilesParser(DefaultChemObjectBuilder.getInstance());
            IAtomContainer mol1 = parser.parseSmiles(smiles1);
            IAtomContainer mol2 = parser.parseSmiles(smiles2);

            // Preprocessing molecules
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(mol1);
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(mol2);

            // Generate Extended fingerprints
            ExtendedFingerprinter fingerprinter = new ExtendedFingerprinter();
            IBitFingerprint fp1 = fingerprinter.getBitFingerprint(mol1);
            IBitFingerprint fp2 = fingerprinter.getBitFingerprint(mol2);

            // Calculate Tanimoto coefficient
            return Tanimoto.calculate(fp1, fp2);
        } catch (Exception e) {
            log.warn("Error calculating Extended similarity, returning 0: {}", e.getMessage());
            return 0.0;
        }
    }

    // Calculate compound similarity using CDK for multiple compounds associated with drugs
    private double calculateCompoundSimilarityWithCDK(List<String> compounds1, List<String> compounds2) throws RepositoryException, SQLException {
        if (compounds1.isEmpty() || compounds2.isEmpty()) {
            return 0.0;
        }

        double totalSimilarity = 0.0;
        int comparisonCount = 0;

        for (String compoundId1 : compounds1) {
            String smiles1 = getCompoundSmiles(compoundId1);
            if (smiles1 == null) continue;

            double maxSimilarity = 0.0;

            for (String compoundId2 : compounds2) {
                String smiles2 = getCompoundSmiles(compoundId2);
                if (smiles2 == null) continue;

                try {
                    // Calculate average similarity using multiple fingerprint methods for robustness
                    double maccs = calculateMACCSSimilarity(smiles1, smiles2);
                    double pubchem = calculatePubchemSimilarity(smiles1, smiles2);
                    double extended = calculateExtendedSimilarity(smiles1, smiles2);

                    double avgSimilarity = (maccs + pubchem + extended) / 3.0;
                    maxSimilarity = Math.max(maxSimilarity, avgSimilarity);
                } catch (Exception e) {
                    log.warn("Error calculating similarity between compounds {} and {}: {}",
                            compoundId1, compoundId2, e.getMessage());
                }
            }

            totalSimilarity += maxSimilarity;
            comparisonCount++;
        }

        return comparisonCount > 0 ? totalSimilarity / comparisonCount : 0.0;
    }

    // Calculate functional similarity based on targets and pathways
    private double calculateFunctionalSimilarity(String drugId1, String drugId2) throws RepositoryException {
        // Compare target genes of the drugs
        List<String> drug1Genes = getGeneTargetsForDrug(drugId1);
        List<String> drug2Genes = getGeneTargetsForDrug(drugId2);

        double geneSimilarity = calculateJaccardSimilarity(drug1Genes, drug2Genes);

        // Compare pathways affected by the drugs
        List<String> drug1Pathways = getPathwaysForDrug(drugId1);
        List<String> drug2Pathways = getPathwaysForDrug(drugId2);

        double pathwaySimilarity = calculateJaccardSimilarity(drug1Pathways, drug2Pathways);

        // Weight gene similarity higher than pathway similarity
        return (geneSimilarity * 0.7) + (pathwaySimilarity * 0.3);
    }

    // Fallback method if SMILES data is missing
    private double calculateSimilarityByTargetsAndPathways(String drugId1, String drugId2) throws RepositoryException {
        Connection conn = null;
        try {
            // Establish a connection to be used throughout the method
            conn = connect();

            // Compare compounds in the drugs
            List<String> drug1Compounds = getDrugCompounds(drugId1, conn);
            List<String> drug2Compounds = getDrugCompounds(drugId2, conn);

            double compoundSimilarity = 0.0;

            // Try to calculate similarity using SMILES data if available
            try {
                compoundSimilarity = calculateCompoundSimilarityWithCDK(drug1Compounds, drug2Compounds);
            } catch (Exception e) {
                // Fall back to basic Jaccard similarity if CDK calculation fails
                compoundSimilarity = calculateJaccardSimilarity(drug1Compounds, drug2Compounds);
            }

            // Calculate functional similarity
            double functionalSimilarity = calculateFunctionalSimilarity(drugId1, drugId2);

            // Weight structural similarity higher
            return (compoundSimilarity * 0.6) + (functionalSimilarity * 0.4);

        } catch (SQLException e) {
            log.error("Error calculating similarity by targets and pathways", e);
            throw new RepositoryException(e);
        } finally {
            // Return connection to the pool
            if (conn != null) {
                jdbcUtils.releaseConnection(conn);
            }
        }
    }

    // Helper method to calculate Jaccard similarity between two lists
    private double calculateJaccardSimilarity(List<String> list1, List<String> list2) {
        if (list1.isEmpty() && list2.isEmpty()) {
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

    // Helper method to get compounds in a drug

    /**
     * Find candidate drugs based on structural similarity to drugs that treat similar diseases.
     *
     * @param diseaseId the KEGG disease ID
     * @return a list of drug IDs selected based on structural similarity
     * @throws RepositoryException if database error occurs
     */
    @Override
    public List<String> findCandidatesByStructuralSimilarity(String diseaseId) throws RepositoryException {
        Set<String> candidateDrugs = new HashSet<>();

        try {
            log.info("Finding drug candidates through structural similarity for disease: {}", diseaseId);

            // 1. Find similar diseases
            List<String> similarDiseases = getSimilarDiseases(diseaseId);
            if (similarDiseases.isEmpty()) {
                log.info("No similar diseases found for disease: {}", diseaseId);
                return new ArrayList<>();
            }

            // 2. Get drugs approved for similar diseases
            Set<String> similarDiseaseDrugs = new HashSet<>();
            for (String simDisease : similarDiseases) {
                similarDiseaseDrugs.addAll(getDrugsForDisease(simDisease));
            }

            if (similarDiseaseDrugs.isEmpty()) {
                log.info("No drugs found for similar diseases to: {}", diseaseId);
                return new ArrayList<>();
            }

            // 3. Get all drugs to compare with
            List<String> allDrugIds = getAllDrugIds();

            // 4. For each approved drug, find structurally similar drugs
            for (String approvedDrug : similarDiseaseDrugs) {
                // Get SMILES for this drug
                String smiles = null;
                try {
                    smiles = getDrugSmiles(approvedDrug);
                } catch (SQLException e) {
                    log.warn("Could not get SMILES for drug: {}", approvedDrug, e);
                    continue;
                }

                if (smiles == null) {
                    log.info("No SMILES information available for drug: {}", approvedDrug);
                    continue;
                }

                // Find similar drugs by SMILES comparison
                Map<String, Double> similarityScores = new HashMap<>();

                similarityScores = allDrugIds.parallelStream()
                        .filter(candidateDrugId -> !candidateDrugId.equals(approvedDrug) && !similarDiseaseDrugs.contains(candidateDrugId))
                        .collect(Collectors.toMap(
                                candidateDrugId -> candidateDrugId,
                                candidateDrugId -> {
                                    try {
                                        return calculateMolecularSimilarity(approvedDrug, candidateDrugId);
                                    } catch (Exception e) {
                                        return 0.0; // Default value if calculation fails
                                    }
                                },
                                (v1, v2) -> v1, // Merge function (not really needed here)
                                ConcurrentHashMap::new // Use thread-safe map
                        ))
                        .entrySet().stream()
                        .filter(entry -> entry.getValue() >= 0.5)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                // Add top similar drugs (up to 5 per approved drug)
                similarityScores.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .limit(5)
                        .forEach(entry -> candidateDrugs.add(entry.getKey()));
            }

            log.info("Found {} drug candidates through structural similarity", candidateDrugs.size());
            return new ArrayList<>(candidateDrugs);

        } catch (Exception e) {
            log.error("Error finding candidate drugs by structural similarity", e);
            throw new RepositoryException("Error finding candidate drugs by structural similarity", (SQLException) e);
        }
    }

    /**
     * Get pathways associated with a specific gene.
     *
     * @param geneId the gene ID
     * @return list of pathway IDs
     * @throws RepositoryException if database error occurs
     */
    @Override
    public List<String> getPathwaysForGene(String geneId) throws RepositoryException {
        return withConnection(conn -> {
            List<String> pathways = new ArrayList<>();
            String sql = "SELECT pathway_id FROM GenePathways WHERE gene_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, geneId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        pathways.add(rs.getString("pathway_id"));
                    }
                }
            } catch (SQLException e) {
                log.error("Error fetching pathways for gene: {}", geneId, e);
                throw new RepositoryException("Error fetching pathways for gene", e);
            }
            return pathways;
        });
    }

    /**
     * Find drugs that directly target a specific gene.
     *
     * @param geneId the gene ID
     * @return list of drug IDs that target the gene
     * @throws RepositoryException if database error occurs
     */
    public List<String> findDrugsThatTargetGene(String geneId) throws RepositoryException {
        return withConnection(conn -> {
            Set<String> drugs = new HashSet<>();

            try {
                // Direct drug-gene relationships
                String sql = "SELECT drug_id FROM DrugGenes WHERE gene_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, geneId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            drugs.add(rs.getString("drug_id"));
                        }
                    }
                } catch (SQLException e) {
                    log.debug("DrugGenes table not found or error querying it.", e);
                }

                if (drugs.isEmpty()) {
                    List<String> diseases = new ArrayList<>();
                    sql = "SELECT disease_id FROM GeneDiseases WHERE gene_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, geneId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                diseases.add(rs.getString("disease_id"));
                            }
                        }
                    }

                    for (String diseaseId : diseases) {
                        sql = "SELECT drug_id FROM DiseaseDrugs WHERE disease_id = ?";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setString(1, diseaseId);
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    drugs.add(rs.getString("drug_id"));
                                }
                            }
                        }
                    }
                }

                try {
                    sql = "SELECT source_name FROM Interaction WHERE target_name = ? AND target_type = 'gene' AND source_type = 'drug'";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, geneId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                drugs.add(rs.getString("source_name"));
                            }
                        }
                    }

                    sql = "SELECT target_name FROM Interaction WHERE source_name = ? AND source_type = 'gene' AND target_type = 'drug'";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, geneId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                drugs.add(rs.getString("target_name"));
                            }
                        }
                    }
                } catch (SQLException e) {
                    log.debug("Interaction table not found or error querying it.", e);
                }
            } catch (SQLException e) {
                log.error("Error finding drugs that target gene: {}", geneId, e);
                throw new RepositoryException("Error finding drugs that target gene", e);
            }

            return new ArrayList<>(drugs);
        });
    }

    /**
     * Find drugs through interaction network analysis up to a specific depth.
     *
     * @param geneId the starting gene ID
     * @param maxDepth maximum network traversal depth
     * @return list of drug IDs found through network analysis
     * @throws RepositoryException if database error occurs
     */
    @Override
    public List<String> findDrugsThroughInteractionNetwork(String geneId, int maxDepth) throws RepositoryException {
        Set<String> foundDrugs = new HashSet<>();
        Set<String> visitedNodes = new HashSet<>();
        Map<String, String> nodeTypes = new HashMap<>(); // Store node types (gene, drug, disease, compound)

        // Initialize with starting gene
        Set<String> currentLayer = new HashSet<>();
        currentLayer.add(geneId);
        nodeTypes.put(geneId, "gene");
        visitedNodes.add(geneId);

        // BFS traversal up to maxDepth
        for (int depth = 0; depth < maxDepth && !currentLayer.isEmpty(); depth++) {
            Set<String> nextLayer = new HashSet<>();

            for (String currentNode : currentLayer) {
                String nodeType = nodeTypes.get(currentNode);

                // If current node is a drug, add to results
                if ("drug".equals(nodeType)) {
                    foundDrugs.add(currentNode);
                    continue; // Don't expand further from drugs
                }

                // Find all nodes that interact with current node
                Set<String> neighbors = findInteractingNodes(currentNode, nodeType);

                for (String neighborInfo : neighbors) {
                    // Parse neighbor information (ID and type)
                    String[] parts = neighborInfo.split("\\|");
                    if (parts.length != 2) continue;

                    String neighborId = parts[0];
                    String neighborType = parts[1];

                    // Skip if already visited
                    if (visitedNodes.contains(neighborId)) {
                        continue;
                    }

                    // Add to next layer
                    nextLayer.add(neighborId);
                    nodeTypes.put(neighborId, neighborType);
                    visitedNodes.add(neighborId);

                    // If neighbor is a drug, add to results
                    if ("drug".equals(neighborType)) {
                        foundDrugs.add(neighborId);
                    }
                }
            }

            // Move to next layer
            currentLayer = nextLayer;
        }

        log.info("Found {} drugs through interaction network from gene: {}", foundDrugs.size(), geneId);
        return new ArrayList<>(foundDrugs);
    }

    /**
     * Find nodes that interact with a given node in the interaction network.
     *
     * @param nodeId the ID of the node
     * @param nodeType the type of the node (gene, drug, disease, compound)
     * @return set of interacting node IDs with their types (format: "id|type")
     * @throws RepositoryException if database error occurs
     */
    private Set<String> findInteractingNodes(String nodeId, String nodeType) throws RepositoryException {
        return withConnection(conn -> {
            Set<String> interactingNodes = new HashSet<>();
            try {
                String sql = "SELECT target_name, target_type FROM Interaction WHERE source_name = ? AND source_type = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, nodeId);
                    ps.setString(2, nodeType);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            interactingNodes.add(rs.getString("target_name") + "|" + rs.getString("target_type"));
                        }
                    }
                }

                sql = "SELECT source_name, source_type FROM Interaction WHERE target_name = ? AND target_type = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, nodeId);
                    ps.setString(2, nodeType);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            interactingNodes.add(rs.getString("source_name") + "|" + rs.getString("source_type"));
                        }
                    }
                }
            } catch (SQLException e) {
                log.error("Error finding interacting nodes for: {} ({})", nodeId, nodeType, e);
                throw new RepositoryException("Error finding interacting nodes", e);
            }
            return interactingNodes;
        });
    }

    /**
     * Find drugs associated with a specific pathway.
     *
     * @param pathwayId the pathway ID
     * @return list of drug IDs associated with the pathway
     * @throws RepositoryException if database error occurs
     */
    @Override
    public List<String> findDrugsInPathway(String pathwayId) throws RepositoryException {
        return withConnection(conn -> {
            Set<String> pathwayDrugs = new HashSet<>();

            try {
                String sql = "SELECT drug_id FROM DrugPathways WHERE pathway_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, pathwayId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            pathwayDrugs.add(rs.getString("drug_id"));
                        }
                    }
                } catch (SQLException e) {
                    log.debug("DrugPathways table not found or error querying it.", e);
                }

                if (pathwayDrugs.isEmpty()) {
                    List<String> genes = new ArrayList<>();
                    sql = "SELECT gene_id FROM GenePathways WHERE pathway_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, pathwayId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                genes.add(rs.getString("gene_id"));
                            }
                        }
                    }

                    for (String geneId : genes) {
                        sql = "SELECT drug_id FROM DrugGenes WHERE gene_id = ?";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setString(1, geneId);
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    pathwayDrugs.add(rs.getString("drug_id"));
                                }
                            }
                        } catch (SQLException e) {
                            log.debug("DrugGenes table not found when looking for drugs targeting gene: {}", geneId, e);
                        }
                    }
                }

                try {
                    sql = "SELECT source_name FROM Interaction WHERE target_name = ? AND source_type = 'drug'";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, pathwayId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                pathwayDrugs.add(rs.getString("source_name"));
                            }
                        }
                    }

                    sql = "SELECT target_name FROM Interaction WHERE source_name = ? AND target_type = 'drug'";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, pathwayId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                pathwayDrugs.add(rs.getString("target_name"));
                            }
                        }
                    }
                } catch (SQLException e) {
                    log.debug("Interaction table not found or error querying it.", e);
                }
            } catch (SQLException e) {
                log.error("Error finding drugs in pathway: {}", pathwayId, e);
                throw new RepositoryException("Error finding drugs in pathway", e);
            }

            return new ArrayList<>(pathwayDrugs);
        });
    }

}