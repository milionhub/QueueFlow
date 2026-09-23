package com.queueflow.common.web;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Recognizes the one kind of database integrity failure the API treats as an
 * expected client conflict: a UNIQUE / PRIMARY KEY violation that a
 * concurrent request won (e.g. two simultaneous creates of the same project
 * key or label name, both passing the service's own duplicate check before
 * either committed).
 *
 * Classification uses the standard SQLSTATE of the JDBC SQLException in the
 * cause chain - 23505 is PostgreSQL's unique_violation - never the
 * (localizable) database message text or constraint names. In QueueFlow the
 * chain is Spring DataIntegrityViolationException -> Hibernate
 * ConstraintViolationException -> PSQLException; every kind of integrity
 * failure (unique, foreign key, not-null, check) arrives with the same outer
 * type, so the SQLSTATE is what separates an expected race from a bug.
 */
final class DatabaseConflicts {

    /** SQLSTATE class 23 "integrity constraint violation", subclass 505 "unique_violation". */
    static final String UNIQUE_VIOLATION = "23505";

    private DatabaseConflicts() {
    }

    /** Whether any SQLException in the cause chain reports a unique violation. */
    static boolean isUniqueViolation(Throwable throwable) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = throwable; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof SQLException sqlException && UNIQUE_VIOLATION.equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
