package org.example.repository.entitiesRepository;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.domain.*;
import org.example.repository.JdbcUtils;
import org.example.repository.RepositoryException;
import org.example.repository.interfaces.IInteractionRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

public class InteractionRepository extends BaseRepository implements IInteractionRepository {
    private final JdbcUtils jdbcUtils;
    private static final Logger log = LogManager.getLogger(InteractionRepository.class);
    private final TableMappingAndQueryMaker queryMaker;
    public InteractionRepository(JdbcUtils jdbcUtils) {
        super(jdbcUtils);
        queryMaker = new TableMappingAndQueryMaker(jdbcUtils);
        log.info("Initializing InteractionRepository...");
        this.jdbcUtils = jdbcUtils; // Use the provided instance
    }


    private String aliasesTableName(TypesOfEntities typesOfEntities) {
        return switch (typesOfEntities) {
            case DRUG -> "DrugAliases";
            case GENE -> "GeneAliases";
            case DISEASE -> "DiseaseAliases";
            case COMPOUND -> "CompoundAliases";
            default -> throw new RepositoryException("Unknown alias table: " + typesOfEntities);
        };
    }


    /**
     * Fetches all pathway interactions where the source gene matches, including sub-interactions.
     */
    @Override
    public List<PathwayInteraction> findPathwayInteractionsBySource(String sourceName, TypesOfEntities typesOfEntities) throws RepositoryException {
        String sql = "SELECT id, source_name, target_name, relation_type, source_type, target_type " +
                "FROM Interaction WHERE source_name = ?";

        String sourceTable = "";
        String sourceID = "";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            // Get connection from the pool
            conn = jdbcUtils.getConnection();

            sourceTable = aliasesTableName(typesOfEntities);
            sourceID = queryMaker.findCanonicalId(sourceTable, sourceName, conn);

            // Prepare and execute the query
            ps = conn.prepareStatement(sql);
            ps.setString(1, sourceID);
            rs = ps.executeQuery();

            List<PathwayInteraction> results = new ArrayList<>();

            // Process results
            while (rs.next()) {
                int id = rs.getInt("id");
                String src = rs.getString("source_name");
                String tgt = rs.getString("target_name");
                String rel = rs.getString("relation_type");
                String srcT = rs.getString("source_type");
                String tgtT = rs.getString("target_type");

                List<SubInteraction> subs = fetchSubInteractions(conn, id);
                results.add(new PathwayInteraction(src, tgt, rel, srcT, tgtT, subs));
            }

            // 2) For each interaction, replace the raw targetName (an alias)
            //    with its canonical name (the first entry returned by your alias lookup).
            for (PathwayInteraction pi : results) {
                String tgtAlias = pi.getTargetName();
                String tgtType = pi.getTargetType();      // e.g. "gene", "drug", etc.

                // Map from tgtType to alias‐table, and factory that builds the right domain object:
                String aliasTable;
                Function<String, ?> factory;
                switch (tgtType.toLowerCase()) {
                    case "gene":
                        aliasTable = "GeneAliases";
                        factory = Gene::new;
                        break;
                    case "drug":
                        aliasTable = "DrugAliases";
                        factory = Drug::new;
                        break;
                    case "disease":
                        aliasTable = "DiseaseAliases";
                        factory = Disease::new;
                        break;
                    case "compound":
                        aliasTable = "CompoundAliases";
                        factory = Compound::new;
                        break;
                    default:
                        // if it's not one of the above, skip replacement
                        continue;
                }

                // perform the lookup
                @SuppressWarnings("unchecked")
                List<Object> canonicalEntities = (List<Object>) findEntityNamesById(
                        tgtAlias, aliasTable, factory
                );
                if (!canonicalEntities.isEmpty()) {
                    Object first = canonicalEntities.get(0);
                    String canonicalName;

                    if (first instanceof Gene g) {
                        canonicalName = g.getGeneName();
                    } else if (first instanceof Drug d) {
                        canonicalName = d.getName();
                    } else if (first instanceof Disease di) {
                        canonicalName = di.getName();
                    } else if (first instanceof Compound c) {
                        canonicalName = c.getName();
                    } else {
                        canonicalName = tgtAlias;
                    }

                    pi.setTargetName(canonicalName);
                }
            }

            return results;
        } catch (SQLException e) {
            log.error("Error fetching aliases for entity_id={} in {}", sourceID, sourceTable, e);
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


    /**
     * Helper: retrieves all SubInteraction entries linked to a given interaction ID.
     */
    private List<SubInteraction> fetchSubInteractions(Connection conn, int interactionId) throws SQLException {
        String sql = "SELECT id, interaction_id, name, value FROM Subtype WHERE interaction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, interactionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<SubInteraction> list = new ArrayList<>();
                while (rs.next()) {
                    String subRel = rs.getString("name");
                    String sym    = rs.getString("value");
                    list.add(new SubInteraction(subRel, sym));
                }
                return list;
            }
        }
    }

    /**
     * Generic lookup of aliases by canonical ID.
     * @param <T>          the target domain type (Gene, Drug, Disease, Compound)
     * @param entityID     the canonical id (e.g. \"hsa:128338\")
     * @param tableName    one of \"GeneAliases\",\"DrugAliases\",\"DiseaseAliases\",\"CompoundAliases\"
     * @param aliasFactory maps each alias‐string to a T instance (e.g. Gene::new)
     */
    private <T> List<T> findEntityNamesById(
            String entityID,
            String tableName,
            Function<String, T> aliasFactory
    ) throws RepositoryException {
        // 1) whitelist the table
        if (!List.of("GeneAliases", "DrugAliases", "DiseaseAliases", "CompoundAliases")
                .contains(tableName)) {
            throw new RepositoryException("Invalid alias table: " + tableName);
        }

        // 2) pick the correct ID column
        String idColumn = switch (tableName) {
            case "GeneAliases" -> "gene_id";
            case "DrugAliases" -> "drug_id";
            case "DiseaseAliases" -> "disease_id";
            case "CompoundAliases" -> "compound_id";
            default -> throw new AssertionError();  // we've whitelisted already
        };

        String sql = "SELECT alias FROM " + tableName + " WHERE " + idColumn + " = ?";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            // Get connection from the pool
            conn = jdbcUtils.getConnection();

            // Prepare and execute the query
            ps = conn.prepareStatement(sql);
            ps.setString(1, entityID);
            rs = ps.executeQuery();

            // Process results
            List<T> results = new ArrayList<>();
            while (rs.next()) {
                String alias = rs.getString("alias");
                results.add(aliasFactory.apply(alias));
            }

            return results;
        } catch (SQLException e) {
            log.error("Error fetching aliases for id={} in {}", entityID, tableName, e);
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
    public List<Gene> findGenesById(String geneId) throws RepositoryException {
        return findEntityNamesById(geneId, "GeneAliases", Gene::new);
    }

    @Override
    public List<Drug> findDrugsById(String drugId) throws RepositoryException {
        return findEntityNamesById(drugId, "DrugAliases", Drug::new);
    }

    @Override
    public List<Disease> findDiseasesById(String diseaseId) throws RepositoryException {
        return findEntityNamesById(diseaseId, "DiseaseAliases", Disease::new);
    }

    @Override
    public List<Compound> findCompoundsById(String compoundId) throws RepositoryException {
        return findEntityNamesById(compoundId, "CompoundAliases", Compound::new);
    }

}
