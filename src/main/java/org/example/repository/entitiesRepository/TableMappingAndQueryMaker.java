package org.example.repository.entitiesRepository;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.repository.JdbcUtils;
import org.example.repository.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

public class TableMappingAndQueryMaker extends BaseRepository{
    private static final Logger log = LogManager.getLogger(TableMappingAndQueryMaker.class);

    public TableMappingAndQueryMaker(JdbcUtils jdbcUtils) {
        super(jdbcUtils);
        log.info("Initializing TableMappingAndQueryMaker...");
    }

    // Modified method that accepts a connection parameter
    String findCanonicalId(String table, String alias, Connection conn) throws RepositoryException {
        String sql = getSql(table);
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = conn.prepareStatement(sql);
            ps.setString(1, alias);
            rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getString("id");
            } else {
                return null;
            }
        } catch (SQLException e) {
            log.error("Error finding canonical id in {} for alias={}", table, alias, e);
            throw new RepositoryException(e);
        } finally {
            // Close ResultSet and PreparedStatement but NOT the connection
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (SQLException e) {
                log.error("Error closing resources", e);
            }
            // Connection is NOT closed here as it was provided by the caller
        }
    }

    // Keep the original method for backward compatibility
    String findCanonicalId(String table, String alias) throws RepositoryException {
        String sql = getSql(table);
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = jdbcUtils.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, alias);
            rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getString("id");
            } else {
                return null;
            }
        } catch (SQLException e) {
            log.error("Error finding canonical id in {} for alias={}", table, alias, e);
            throw new RepositoryException(e);
        } finally {
            // Close ResultSet and PreparedStatement
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (SQLException e) {
                log.error("Error closing resources", e);
            }
            // Release connection back to the pool instead of closing it
            if (conn != null) {
                jdbcUtils.releaseConnection(conn);
            }
        }
    }

    private static String getSql(String table) {
        String idColumn = switch (table) {
            case "GeneAliases" -> "gene_id";
            case "DrugAliases" -> "drug_id";
            case "DiseaseAliases" -> "disease_id";
            case "CompoundAliases" -> "compound_id";
            default -> throw new RepositoryException("Unknown alias table: " + table);
        };

        return "SELECT " + idColumn + " AS id FROM " + table + " WHERE alias = ? LIMIT 1";
    }
}
