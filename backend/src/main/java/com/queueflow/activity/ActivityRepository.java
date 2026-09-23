package com.queueflow.activity;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    /**
     * Chronological history. created_at comes from PostgreSQL's
     * transaction-stable now(), so every Activity recorded by one business
     * operation (e.g. a PATCH changing several fields) shares the exact
     * same created_at. id (a time-ordered uuidv7) is the tie-breaker that
     * makes the order among those rows deterministic.
     */
    List<Activity> findByTicketIdOrderByCreatedAtAscIdAsc(UUID ticketId);
}
