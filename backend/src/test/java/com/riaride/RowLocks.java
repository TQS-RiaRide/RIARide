package com.riaride;

import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

// Tells whether another transaction holds a row lock (SELECT ... FOR UPDATE), without waiting: a
// fresh connection asks for the same row with NOWAIT, which PostgreSQL refuses with
// lock_not_available (55P03) while the lock is held.
public final class RowLocks {

  private static final String LOCK_NOT_AVAILABLE = "55P03";

  private RowLocks() {}

  // lockQuery selects one row by id with FOR UPDATE NOWAIT, for example
  // "select id from hub where id = ? for update nowait". A missing row is a test error, not "free".
  public static boolean isLocked(DataSource dataSource, String lockQuery, UUID id) {
    try (var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement = connection.prepareStatement(lockQuery)) {
        statement.setObject(1, id);
        try (var rows = statement.executeQuery()) {
          if (!rows.next()) {
            throw new IllegalStateException("No committed row " + id + " to lock");
          }
          return false;
        }
      } catch (SQLException refused) {
        if (LOCK_NOT_AVAILABLE.equals(refused.getSQLState())) {
          return true;
        }
        throw refused;
      } finally {
        connection.rollback();
      }
    } catch (SQLException failed) {
      throw new IllegalStateException("Could not check the lock on row " + id, failed);
    }
  }
}
