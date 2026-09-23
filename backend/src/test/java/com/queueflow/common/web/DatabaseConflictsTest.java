package com.queueflow.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for the SQLSTATE-based classification, using plain JDBC
 * exceptions. The real PostgreSQL/Hibernate/Spring chains are covered by
 * DatabaseConflictsPostgresTest.
 */
class DatabaseConflictsTest {

    private static SQLException sqlState(String state) {
        return new SQLException("database message that must never matter", state);
    }

    @Test
    void uniqueViolationSqlStateIsRecognized() {
        assertThat(DatabaseConflicts.isUniqueViolation(sqlState("23505"))).isTrue();
    }

    @Test
    void uniqueViolationIsRecognizedThroughWrappingLayers() {
        // Same shape as Spring -> Hibernate -> JDBC driver.
        Throwable wrapped = new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("hibernate layer", sqlState("23505")));

        assertThat(DatabaseConflicts.isUniqueViolation(wrapped)).isTrue();
    }

    @ParameterizedTest(name = "SQLSTATE {0}")
    @ValueSource(strings = {
            "23503", // foreign_key_violation
            "23514", // check_violation
            "23502", // not_null_violation
            "23P01", // exclusion_violation
            "40001"})// serialization_failure - not an integrity violation at all
    void otherSqlStatesAreNotUniqueConflicts(String state) {
        Throwable wrapped = new DataIntegrityViolationException("integrity failure",
                new RuntimeException("hibernate layer", sqlState(state)));

        assertThat(DatabaseConflicts.isUniqueViolation(wrapped)).isFalse();
    }

    @Test
    void integrityViolationWithoutAnySqlExceptionIsNotAUniqueConflict() {
        // Message text alone never counts, even if it looks like a duplicate.
        assertThat(DatabaseConflicts.isUniqueViolation(
                new DataIntegrityViolationException("duplicate key value violates unique constraint"))).isFalse();
    }

    @Test
    void nullAndSelfReferencingCauseChainsAreHandledSafely() {
        assertThat(DatabaseConflicts.isUniqueViolation(null)).isFalse();

        RuntimeException cyclic = new RuntimeException("outer") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertThat(DatabaseConflicts.isUniqueViolation(cyclic)).isFalse();
    }
}
