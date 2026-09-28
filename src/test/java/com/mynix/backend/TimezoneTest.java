package com.mynix.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The POS runs on Sri Lanka time; V19 moved the old UTC history forward. */
@IntegrationTest
class TimezoneTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void appAndDatabaseUseSriLankaTime() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Colombo");
        assertThat(jdbc.queryForObject("SHOW TIME ZONE", String.class)).isEqualTo("Asia/Colombo");
    }

    @Test
    void newRecordsGetSriLankaTime() {
        // A database default (CURRENT_TIMESTAMP) and Java's clock agree on shop time.
        String name = "tz-" + UUID.randomUUID();
        jdbc.update("INSERT INTO categories (name) VALUES (?)", name);
        LocalDateTime stored = jdbc.queryForObject("SELECT created_at FROM categories WHERE name = ?",
                Timestamp.class, name).toLocalDateTime();
        LocalDateTime colomboNow = LocalDateTime.now(ZoneId.of("Asia/Colombo"));
        assertThat(Duration.between(stored, colomboNow).abs()).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void historyShiftMovesDateTimesButNotDates() throws Exception {
        String name = "tz-history-" + UUID.randomUUID();
        jdbc.update("INSERT INTO categories (name, created_at) VALUES (?, ?)", name,
                Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 20, 0)));
        jdbc.update("INSERT INTO invoice_counters (day, last_value) VALUES (?, 1) ON CONFLICT (day) DO NOTHING",
                LocalDate.of(2001, 1, 1));

        // Re-run V19's block (it ran once on the empty test database at startup).
        String migration = new ClassPathResource("db/migration/V19__shift_history_to_sri_lanka_time.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        String block = migration.substring(migration.indexOf("DO $$"));
        jdbc.execute(block.replace("AND table_name <> 'flyway_schema_history'",
                "AND table_name = 'categories'"));

        assertThat(jdbc.queryForObject("SELECT created_at FROM categories WHERE name = ?", Timestamp.class, name)
                .toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 9, 2, 1, 30));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice_counters WHERE day = ?", Integer.class,
                LocalDate.of(2001, 1, 1))).isEqualTo(1);
    }
}
