package com.challenge.order_processing_api.repository;

import com.challenge.order_processing_api.model.entity.OutboxEvent;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE published_at IS NULL
              AND (claim_expires_at IS NULL OR claim_expires_at <= :now)
            ORDER BY created_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<OutboxEvent> findNextPendingForUpdate(@Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE OutboxEvent event
            SET event.publishedAt = :publishedAt,
                event.claimedBy = null,
                event.claimExpiresAt = null
            WHERE event.id = :id
              AND event.claimedBy = :claimToken
              AND event.publishedAt IS NULL
            """)
    int markPublished(
            @Param("id") UUID id,
            @Param("claimToken") UUID claimToken,
            @Param("publishedAt") Instant publishedAt);

    @Modifying
    @Query("""
            UPDATE OutboxEvent event
            SET event.claimedBy = null,
                event.claimExpiresAt = null
            WHERE event.id = :id
              AND event.claimedBy = :claimToken
              AND event.publishedAt IS NULL
            """)
    int releaseClaim(@Param("id") UUID id, @Param("claimToken") UUID claimToken);
}
