package org.example.repository.entitiesRepository;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.example.repository.JdbcUtils;
import org.example.repository.RepositoryException;
import org.example.repository.interfaces.IDrugRepurposingRepository;
import org.example.repository.interfaces.Repository;

import java.sql.Connection;
import java.sql.SQLException;

public abstract class BaseRepository {
    protected final JdbcUtils jdbcUtils;
    private static final Logger log = LogManager.getLogger(BaseRepository.class);

    protected BaseRepository(JdbcUtils jdbcUtils) {
        this.jdbcUtils = jdbcUtils;
    }

    protected Connection connect() throws SQLException {
        return jdbcUtils.getConnection();
    }

    protected <T> T withConnection(Repository.DatabaseOperation<T> operation) throws RepositoryException {
        Connection conn = null;
        try {
            conn = connect();
            return operation.execute(conn);
        } catch (SQLException e) {
            throw new RepositoryException("Database error: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                jdbcUtils.releaseConnection(conn);
            }
        }
    }
}
