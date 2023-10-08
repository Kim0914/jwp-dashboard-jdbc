package com.techcourse.service;

import com.techcourse.config.DataSourceConfig;
import com.techcourse.dao.UserDao;
import com.techcourse.dao.UserHistoryDao;
import com.techcourse.domain.User;
import com.techcourse.domain.UserHistory;
import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.exception.ConnectionException;
import org.springframework.jdbc.exception.RollbackFailException;

public class UserService {

    private final UserDao userDao;
    private final UserHistoryDao userHistoryDao;

    public UserService(final UserDao userDao, final UserHistoryDao userHistoryDao) {
        this.userDao = userDao;
        this.userHistoryDao = userHistoryDao;
    }

    public User findById(final long id) {
        return userDao.findById(id);
    }

    public void insert(final User user) {
        executeWithTransaction(connection -> {
            userDao.insert(connection, user);
            return null;
        });
    }

    public void changePassword(final long id, final String newPassword, final String createBy) {
        final var user = findById(id);
        executeWithTransaction(connection -> {
            user.changePassword(newPassword);
            userDao.update(connection, user);
            userHistoryDao.log(connection, new UserHistory(user, createBy));
            return null;
        });
    }

    private <T> T executeWithTransaction(TransactionExecutor<T> transactionExecutor) {
        Connection connection = getConnection();
        try {
            connection.setAutoCommit(false);
            T result = transactionExecutor.execute(connection);
            connection.commit();
            return result;
        } catch (SQLException e) {
            throw new ConnectionException(e.getMessage());
        } catch (DataAccessException e) {
            rollback(connection);
            throw e;
        } finally {
            close(connection);
        }
    }

    private Connection getConnection() {
        try {
            return DataSourceConfig.getInstance().getConnection();
        } catch (SQLException e) {
            throw new ConnectionException(e.getMessage());
        }
    }

    private void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            throw new RollbackFailException(e.getMessage());
        }
    }

    private void close(Connection connection) {
        if (connection != null) {
            try {
                connection.setAutoCommit(true);
                connection.close();
            } catch (SQLException e) {
                throw new ConnectionException(e.getMessage());
            }
        }
    }
}
