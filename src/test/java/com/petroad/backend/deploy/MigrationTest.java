package com.petroad.backend.deploy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import java.sql.Connection;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "PETROAD_DB_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "jwt.secret=migration-test-only-key-do-not-deploy-0123456789",
        "spring.datasource.username=petroad_test", "spring.datasource.password=test-only-password"
})
class MigrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PETROAD_DB_TEST_URL"));
    }
    @Autowired private Flyway flyway;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test void freshSchemaValidatesAndMigrationsPreserveDataOrRollbackAtomically() throws Exception {
        assertThat(flyway.info().applied()).extracting(info -> info.getVersion().getVersion())
                .containsExactly("1", "2");
        jdbc.update("INSERT INTO users(email,password,region) VALUES (?,?,?)", "migration-fixture@example.com", "test-fixture-hash", "서울");
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        Flyway next = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:migration-success").load();
        assertThat(next.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM migration_probe", Integer.class)).isEqualTo(1);

        Flyway invalid = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:migration-success", "classpath:migration-failure").load();
        assertThatThrownBy(invalid::migrate).isInstanceOf(FlywayException.class);
        assertThat(jdbc.queryForObject("SELECT to_regclass('migration_rollback_probe')::text", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
        assertThat(next.info().applied()).extracting(info -> info.getVersion().getVersion())
                .containsExactly("1", "2", "3");

        jdbc.execute("CREATE SCHEMA legacy_fixture");
        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema("legacy_fixture");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__initial_schema.sql"))
                    .populate(connection);
        }
        jdbc.execute("INSERT INTO legacy_fixture.users(id,email,password,region) VALUES (100,'legacy@example.com','fixture','서울')");
        jdbc.execute("INSERT INTO legacy_fixture.walk_log(id,user_id,distance) VALUES (100,100,1.5)");
        Flyway legacy = Flyway.configure().dataSource(dataSource).schemas("legacy_fixture")
                .defaultSchema("legacy_fixture").baselineVersion("1").locations("classpath:db/migration").load();
        assertThatThrownBy(legacy::migrate).isInstanceOf(FlywayException.class);
        legacy.baseline();
        assertThat(legacy.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(legacy.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM legacy_fixture.users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT distance FROM legacy_fixture.walk_log WHERE id = 100", Double.class))
                .isEqualTo(1.5);
        assertThat(jdbc.queryForObject("SELECT title FROM legacy_fixture.walk_log WHERE id = 100", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT duration_sec FROM legacy_fixture.walk_log WHERE id = 100", Integer.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM legacy_fixture.walk_log_photos", Integer.class)).isZero();
    }
}
