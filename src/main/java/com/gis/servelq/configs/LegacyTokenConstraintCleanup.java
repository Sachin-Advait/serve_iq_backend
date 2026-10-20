package com.gis.servelq.configs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Drops the old one-counter-per-priority uniqueness on tokens if it is still
 * there: the uk_ constraint from V1, and uq_token_branch_priority_day_seq, a
 * unique index on (branch_id, priority, token_seq, created_at::date) that some
 * databases have although no migration creates it.
 *
 * V32 replaces it with a per-prefix one, but a database that was not migrated
 * by Flyway (ddl-auto=update keeps old constraints) still has it. There the
 * first B ticket of the day (B001) clashes with A001 on (branch, priority, seq,
 * date), and every attempt fails with "Could not allocate a token number".
 * The per-prefix constraint already guards against duplicates, so dropping the
 * old one loses nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyTokenConstraintCleanup implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        drop("ALTER TABLE tokens DROP CONSTRAINT IF EXISTS uk_tokens_branch_priority_seq_date");
        drop("ALTER TABLE tokens DROP CONSTRAINT IF EXISTS uq_token_branch_priority_day_seq");
        drop("DROP INDEX IF EXISTS uq_token_branch_priority_day_seq");
    }

    private void drop(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("Legacy token uniqueness cleanup failed ({}): {}", sql, e.getMessage());
        }
    }
}
