package org.example.repository.entitiesRepository;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.domain.*;
import org.example.repository.JdbcUtils;
import org.example.repository.RepositoryException;
import org.example.repository.interfaces.IGeneDiseaseDrugCompoundRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.*;

/**
 * Repository implementation for gene, disease, drug, and compound operations.
 * Includes extensions for the drug repurposing application.
 */
public class GeneDiseaseDrugCompoundRepository extends BaseRepository implements IGeneDiseaseDrugCompoundRepository {
    private final JdbcUtils jdbcUtils;
    private static final Logger log = LogManager.getLogger(GeneDiseaseDrugCompoundRepository.class);
    private final TableMappingAndQueryMaker queryHelper;

    // Cache for frequently accessed data
    private static final Map<String, List<String>> geneDiseasesCache = new HashMap<>();
    private static final Map<String, String> diseaseNameCache = new HashMap<>();
    private static final Map<String, List<String>> geneDetailsCache = new HashMap<>();
    private static final Map<String, List<String>> pathwayGeneCache = new HashMap<>();

    public GeneDiseaseDrugCompoundRepository(JdbcUtils jdbcUtils) {
        super(jdbcUtils);
        queryHelper = new TableMappingAndQueryMaker(jdbcUtils);
        log.info("Initializing GeneDiseaseDrugCompoundRepository...");
        this.jdbcUtils = jdbcUtils; // Use the provided instance
    }


    private String fetchFromUrl(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    // --- existence checks via alias tables ---

    @Override
    public boolean existGene(Gene potentialGene) throws RepositoryException {
        if (potentialGene.getGeneName() == null) throw new IllegalArgumentException("geneName must not be null");
        return queryHelper.findCanonicalId("GeneAliases", potentialGene.getGeneName()) != null;
    }

    @Override
    public boolean existDrug(Drug potentialDrug) throws RepositoryException {
        if (potentialDrug.getName() == null) throw new IllegalArgumentException("drugName must not be null");
        return queryHelper.findCanonicalId("DrugAliases", potentialDrug.getName()) != null;
    }

    @Override
    public boolean existDisease(Disease potentialDisease) throws RepositoryException {
        if (potentialDisease.getName() == null) throw new IllegalArgumentException("diseaseName must not be null");
        return queryHelper.findCanonicalId("DiseaseAliases", potentialDisease.getName()) != null;
    }

    @Override
    public boolean existCompound(Compound potentialCompound) throws RepositoryException {
        if (potentialCompound.getName() == null) throw new IllegalArgumentException("compoundName must not be null");
        return queryHelper.findCanonicalId("CompoundAliases", potentialCompound.getName()) != null;
    }

    // --- retrieve info via KEGG REST or alias table ---

    @Override
    public HashMap<String, String> retrieveInfoAboutGene(Gene potentialGene) throws RepositoryException {
        String id = queryHelper.findCanonicalId("GeneAliases", potentialGene.getGeneName());
        if (id == null) return new HashMap<>();

        String url = "http://rest.kegg.jp/get/" + id;
        try {
            String keggData = fetchFromUrl(url);
            HashMap<String,String> info = new HashMap<>();
            info.put("id", id);
            info.put("keggEntry", keggData);
            return info;
        } catch (IOException e) {
            log.error("Error fetching gene info from KEGG for id={}", id, e);
            throw new RepositoryException(e);
        }
    }

    @Override
    public HashMap<String, String> retrieveInfoAboutDisease(Disease potentialDisease) throws RepositoryException {
        String id = queryHelper.findCanonicalId("DiseaseAliases", potentialDisease.getName());
        if (id == null) return new HashMap<>();

        String url = "https://rest.kegg.jp/get/disease:" + id;
        try {
            String keggData = fetchFromUrl(url);
            HashMap<String,String> info = new HashMap<>();
            info.put("id", id);
            info.put("keggEntry", keggData);
            return info;
        } catch (IOException e) {
            log.error("Error fetching disease info from KEGG for id={}", id, e);
            throw new RepositoryException(e);
        }
    }

    @Override
    public HashMap<String, String> retrieveInfoAboutDrug(Drug potentialDrug) throws RepositoryException {
        String id = queryHelper.findCanonicalId("DrugAliases", potentialDrug.getName());
        if (id == null) return new HashMap<>();

        String url = "https://rest.kegg.jp/get/dr:" + id;
        try {
            String keggData = fetchFromUrl(url);
            HashMap<String,String> info = new HashMap<>();
            info.put("id", id);
            info.put("keggEntry", keggData);
            return info;
        } catch (IOException e) {
            log.error("Error fetching drug info from KEGG for id={}", id, e);
            throw new RepositoryException(e);
        }
    }

    @Override
    public HashMap<String, String> retrieveInfoAboutCompound(Compound potentialCompound) throws RepositoryException {
        // No public KEGG REST endpoint was specified for compounds here,
        // so we'll just return the canonical ID + alias list.
        String id = queryHelper.findCanonicalId("CompoundAliases", potentialCompound.getName());
        if (id == null) return new HashMap<>();

        // Gather all synonyms
        String sql = "SELECT alias FROM CompoundAliases WHERE compound_id = ?";
        List<String> syns = new ArrayList<>();

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            // Get connection from the pool
            conn = jdbcUtils.getConnection();

            // Prepare and execute the query
            ps = conn.prepareStatement(sql);
            ps.setString(1, id);
            rs = ps.executeQuery();

            // Process results
            while (rs.next()) {
                syns.add(rs.getString("alias"));
            }

            HashMap<String,String> info = new HashMap<>();
            info.put("id", id);
            info.put("aliases", String.join(",", syns));
            return info;
        } catch (SQLException e) {
            log.error("Error retrieving compound aliases for id={}", id, e);
            throw new RepositoryException(e);
        } finally {
            // Close resources
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (SQLException e) {
                log.error("Error closing resources", e);
            }

            // Return connection to the pool
            if (conn != null) {
                jdbcUtils.releaseConnection(conn);
            }
        }
    }

    // --- suggestions via alias tables ---

    private List<String> suggestFrom(String table, String prefix) throws RepositoryException {
        String sql = "SELECT DISTINCT alias FROM " + table + " WHERE alias LIKE ? LIMIT 10";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            // Get connection from the pool
            conn = jdbcUtils.getConnection();

            // Prepare and execute the query
            ps = conn.prepareStatement(sql);
            ps.setString(1, prefix + "%");
            rs = ps.executeQuery();

            // Process results
            List<String> list = new ArrayList<>();
            while (rs.next()) {
                list.add(rs.getString("alias"));
            }

            return list;
        } catch (SQLException e) {
            log.error("Error fetching suggestions from {} for prefix={}", table, prefix, e);
            throw new RepositoryException(e);
        } finally {
            // Close resources
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (SQLException e) {
                log.error("Error closing resources", e);
            }

            // Return connection to the pool
            if (conn != null) {
                jdbcUtils.releaseConnection(conn);
            }
        }
    }

    @Override
    public List<String> getGeneNameSuggestion(Gene potentialGene) throws RepositoryException {
        return suggestFrom("GeneAliases", potentialGene.getGeneName());
    }

    @Override
    public List<String> getCompoundNameSuggestion(Compound potentialCompound) throws RepositoryException {
        return suggestFrom("CompoundAliases", potentialCompound.getName());
    }

    @Override
    public List<String> getDrugNameSuggestion(Drug potentialDrug) throws RepositoryException {
        return suggestFrom("DrugAliases", potentialDrug.getName());
    }

    // === New methods for Drug Repurposing application ===

    /**
     * Get all diseases associated with a gene by gene ID.
     *
     * @param geneId the canonical gene ID
     * @return list of disease IDs
     * @throws RepositoryException if repository operations fail
     */
    @Override
    public List<String> getDiseasesForGene(String geneId) throws RepositoryException {
        // Check cache first
        if (geneDiseasesCache.containsKey(geneId)) {
            return geneDiseasesCache.get(geneId);
        }

        List<String> diseaseIds = new ArrayList<>();

        // Query from GeneDiseases table
        String sql = "SELECT disease_id FROM GeneDiseases WHERE gene_id = ?";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            // Get connection from the pool
            conn = jdbcUtils.getConnection();

            // Prepare and execute the query
            ps = conn.prepareStatement(sql);
            ps.setString(1, geneId);
            rs = ps.executeQuery();

            // Process results
            while (rs.next()) {
                String diseaseId = rs.getString("disease_id");
                diseaseIds.add(diseaseId);
            }

            // Store in cache
            geneDiseasesCache.put(geneId, diseaseIds);

            log.debug("Found {} diseases for gene {}", diseaseIds.size(), geneId);
            return diseaseIds;
        } catch (SQLException e) {
            log.error("Error retrieving diseases for gene: {}", geneId, e);
            throw new RepositoryException("Database error retrieving diseases for gene: " + geneId, e);
        } finally {
            // Close resources
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (SQLException e) {
                log.error("Error closing resources", e);
            }

            // Return connection to the pool
            if (conn != null) {
                jdbcUtils.releaseConnection(conn);
            }
        }
    }

    /**
     * Get a disease name from a disease ID.
     *
     * @param diseaseId the disease ID
     * @return the disease name
     * @throws RepositoryException if repository operations fail
     */
    @Override
    public String getDiseaseNameFromId(String diseaseId) throws RepositoryException {
        // Check cache first
        if (diseaseNameCache.containsKey(diseaseId)) {
            return diseaseNameCache.get(diseaseId);
        }

        // Query from DiseaseAliases table
        String sql = "SELECT alias FROM DiseaseAliases WHERE disease_id = ? LIMIT 1";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            // Get connection from the pool
            conn = jdbcUtils.getConnection();

            // Prepare and execute the query
            ps = conn.prepareStatement(sql);
            ps.setString(1, diseaseId);
            rs = ps.executeQuery();

            // Process results
            if (rs.next()) {
                String diseaseName = rs.getString("alias");
                // Store in cache
                diseaseNameCache.put(diseaseId, diseaseName);
                return diseaseName;
            }

            return "";
        } catch (SQLException e) {
            log.error("Error retrieving disease name for ID: {}", diseaseId, e);
            throw new RepositoryException("Database error retrieving disease name: " + diseaseId, e);
        } finally {
            // Close resources
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (SQLException e) {
                log.error("Error closing resources", e);
            }

            // Return connection to the pool
            if (conn != null) {
                jdbcUtils.releaseConnection(conn);
            }
        }
    }

    /**
     * Get all drugs associated with a disease by disease ID.
     *
     * @param diseaseId the disease ID
     * @return list of drug IDs
     * @throws RepositoryException if repository operations fail
     */
    @Override
    public List<String> getDrugsForDisease(String diseaseId) throws RepositoryException {
        List<String> drugIds = new ArrayList<>();

        // Query from DiseaseDrugs table
        String sql = "SELECT drug_id FROM DiseaseDrugs WHERE disease_id = ?";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            // Get connection from the pool
            conn = jdbcUtils.getConnection();

            // Prepare and execute the query
            ps = conn.prepareStatement(sql);
            ps.setString(1, diseaseId);
            rs = ps.executeQuery();

            // Process results
            while (rs.next()) {
                String drugId = rs.getString("drug_id");
                drugIds.add(drugId);
            }

            return drugIds;
        } catch (SQLException e) {
            log.error("Error retrieving drugs for disease: {}", diseaseId, e);
            throw new RepositoryException("Database error retrieving drugs for disease: " + diseaseId, e);
        } finally {
            // Close resources
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (SQLException e) {
                log.error("Error closing resources", e);
            }

            // Return connection to the pool
            if (conn != null) {
                jdbcUtils.releaseConnection(conn);
            }
        }
    }

    /**
     * Get information about a drug by ID.
     *
     * @param drugId the drug ID
     * @return Drug object
     * @throws RepositoryException if repository operations fail
     */
    @Override
    public Optional<Drug> getDrugById(String drugId) throws RepositoryException {
        // Query from DrugAliases table
        String sql = "SELECT alias FROM DrugAliases WHERE drug_id = ? LIMIT 1";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            // Get connection from the pool
            conn = jdbcUtils.getConnection();

            // Prepare and execute the query
            ps = conn.prepareStatement(sql);
            ps.setString(1, drugId);
            rs = ps.executeQuery();

            // Process results
            if (rs.next()) {
                String drugName = rs.getString("alias");
                return Optional.of(new Drug(drugId, drugName));
            }

            return Optional.empty();
        } catch (SQLException e) {
            log.error("Error retrieving drug information for ID: {}", drugId, e);
            throw new RepositoryException("Database error retrieving drug information: " + drugId, e);
        } finally {
            // Close resources
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (SQLException e) {
                log.error("Error closing resources", e);
            }

            // Return connection to the pool
            if (conn != null) {
                jdbcUtils.releaseConnection(conn);
            }
        }
    }

    /**
     * Get details about a gene from its ID.
     *
     * @param geneId the gene ID
     * @return list of gene details
     * @throws RepositoryException if repository operations fail
     */
    @Override
    public List<String> getGeneDetails(String geneId) throws RepositoryException {
        // Check cache first
        if (geneDetailsCache.containsKey(geneId)) {
            return geneDetailsCache.get(geneId);
        }

        List<String> geneDetails = new ArrayList<>();

        // Query from GeneAliases table
        String sql = "SELECT alias FROM GeneAliases WHERE gene_id = ?";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            // Get connection from the pool
            conn = jdbcUtils.getConnection();

            // Prepare and execute the query
            ps = conn.prepareStatement(sql);
            ps.setString(1, geneId);
            rs = ps.executeQuery();

            // Process results
            while (rs.next()) {
                String alias = rs.getString("alias");
                geneDetails.add(alias);
            }

            // Store in cache
            geneDetailsCache.put(geneId, geneDetails);
            return geneDetails;
        } catch (SQLException e) {
            log.error("Error retrieving gene details for ID: {}", geneId, e);
            throw new RepositoryException("Database error retrieving gene details: " + geneId, e);
        } finally {
            // Close resources
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (SQLException e) {
                log.error("Error closing resources", e);
            }

            // Return connection to the pool
            if (conn != null) {
                jdbcUtils.releaseConnection(conn);
            }
        }
    }

    /**
     * Get pathways associated with a gene.
     *
     * @param geneId the gene ID
     * @return list of pathway IDs
     * @throws RepositoryException if repository operations fail
     */
    @Override
    public List<String> getPathwaysForGene(String geneId) throws RepositoryException {
        // Check cache first
        if (pathwayGeneCache.containsKey(geneId)) {
            return pathwayGeneCache.get(geneId);
        }

        List<String> pathwayIds = new ArrayList<>();

        // Query from GenePathways table
        String sql = "SELECT pathway_id FROM GenePathways WHERE gene_id = ?";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            // Get connection from the pool
            conn = jdbcUtils.getConnection();

            // Prepare and execute the query
            ps = conn.prepareStatement(sql);
            ps.setString(1, geneId);
            rs = ps.executeQuery();

            // Process results
            while (rs.next()) {
                String pathwayId = rs.getString("pathway_id");
                pathwayIds.add(pathwayId);
            }

            // Store in cache
            pathwayGeneCache.put(geneId, pathwayIds);
            return pathwayIds;
        } catch (SQLException e) {
            log.error("Error retrieving pathways for gene: {}", geneId, e);
            throw new RepositoryException("Database error retrieving pathways for gene: " + geneId, e);
        } finally {
            // Close resources
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (SQLException e) {
                log.error("Error closing resources", e);
            }

            // Return connection to the pool
            if (conn != null) {
                jdbcUtils.releaseConnection(conn);
            }
        }
    }


    /**
     * Get all genes associated with a disease.
     *
     * @param diseaseId the disease ID
     * @return list of gene IDs
     * @throws RepositoryException if repository operations fail
     */
    @Override
    public List<String> getGenesForDisease(String diseaseId) throws RepositoryException {
        List<String> geneIds = new ArrayList<>();

        // Query from GeneDiseases table
        String sql = "SELECT gene_id FROM GeneDiseases WHERE disease_id = ?";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            // Get connection from the pool
            conn = jdbcUtils.getConnection();

            // Prepare and execute the query
            ps = conn.prepareStatement(sql);
            ps.setString(1, diseaseId);
            rs = ps.executeQuery();

            // Process results
            while (rs.next()) {
                String geneId = rs.getString("gene_id");
                geneIds.add(geneId);
            }

            return geneIds;
        } catch (SQLException e) {
            log.error("Error retrieving genes for disease: {}", diseaseId, e);
            throw new RepositoryException("Database error retrieving genes for disease: " + diseaseId, e);
        } finally {
            // Close resources
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (SQLException e) {
                log.error("Error closing resources", e);
            }

            // Return connection to the pool
            if (conn != null) {
                jdbcUtils.releaseConnection(conn);
            }
        }
    }

    /**
     * Check if a drug is approved for a disease.
     *
     * @param drugId the drug ID
     * @param diseaseId the disease ID
     * @return true if approved, false otherwise
     * @throws RepositoryException if repository operations fail
     */
    @Override
    public boolean isApprovedForDisease(String drugId, String diseaseId) throws RepositoryException {
        // Query from DiseaseDrugs table
        String sql = "SELECT 1 FROM DiseaseDrugs WHERE disease_id = ? AND drug_id = ? LIMIT 1";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            // Get connection from the pool
            conn = jdbcUtils.getConnection();

            // Prepare and execute the query
            ps = conn.prepareStatement(sql);
            ps.setString(1, diseaseId);
            ps.setString(2, drugId);
            rs = ps.executeQuery();

            // Process results
            return rs.next();
        } catch (SQLException e) {
            log.error("Error checking drug approval status: {}", e.getMessage());
            throw new RepositoryException("Database error checking drug approval status", e);
        } finally {
            // Close resources
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (SQLException e) {
                log.error("Error closing resources", e);
            }

            // Return connection to the pool
            if (conn != null) {
                jdbcUtils.releaseConnection(conn);
            }
        }
    }
}