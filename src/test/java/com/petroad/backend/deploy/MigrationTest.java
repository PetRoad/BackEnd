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
                .containsExactly("1", "2", "3", "4", "5", "6", "7");
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
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "100");

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
        // V7이 기존 사진의 주인을 기록하는지 확인하려고, V6까지 올린 뒤 사진이 있는 데이터를 넣는다.
        Flyway beforeUploadRecords = Flyway.configure().dataSource(dataSource).schemas("legacy_fixture")
                .defaultSchema("legacy_fixture").locations("classpath:db/migration").target("6").load();
        assertThat(beforeUploadRecords.migrate().migrationsExecuted).isEqualTo(5);
        insertImagesUsedBeforeUploadRecords();
        assertThat(legacy.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT user_id || ' ' || public_id || ' ' || url FROM legacy_fixture.uploaded_images",
                String.class)).containsExactlyInAnyOrder(
                "100 petroad/dog_profile/" + uuid(1) + " " + cloudinaryUrl("dog_profile", 1),
                "100 course_cover/" + uuid(2) + ".png " + localUrl("course_cover", 2),
                "100 petroad/walk_photo/" + uuid(3) + " " + cloudinaryUrl("walk_photo", 3),
                "101 walk_photo/" + uuid(4) + ".png " + localUrl("walk_photo", 4));
        assertThat(legacy.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM legacy_fixture.users", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT distance FROM legacy_fixture.walk_log WHERE id = 100", Double.class))
                .isEqualTo(1.5);
        assertThat(jdbc.queryForObject("SELECT title FROM legacy_fixture.walk_log WHERE id = 100", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT duration_sec FROM legacy_fixture.walk_log WHERE id = 100", Integer.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM legacy_fixture.walk_log_photos WHERE walk_log_id = 100",
                Integer.class)).isZero();
    }

    private void insertImagesUsedBeforeUploadRecords() {
        jdbc.update("INSERT INTO legacy_fixture.users(id,email,password) VALUES (101,'second@example.com','fixture')");
        jdbc.update("INSERT INTO legacy_fixture.dog(user_id,profile_image) VALUES (100,?)", cloudinaryUrl("dog_profile", 1));
        // 업로드 API가 만들지 않은 주소는 기록하지 않는다.
        jdbc.update("INSERT INTO legacy_fixture.dog(user_id,profile_image) VALUES (101,'https://example.com/photo.jpg')");
        jdbc.update("INSERT INTO legacy_fixture.course(user_id,cover_image_url,distance,start_lat,start_lng,like_count) "
                + "VALUES (100,?,1,0,0,0)", localUrl("course_cover", 2));
        // 두 사용자가 같은 사진을 쓰면 사용자 번호가 작은 쪽만 기록한다. 같은 사진이 대표 사진이어도 한 번만 기록한다.
        jdbc.update("INSERT INTO legacy_fixture.walk_log(id,user_id,distance,cover) VALUES (101,101,1,?)",
                cloudinaryUrl("walk_photo", 3));
        jdbc.update("INSERT INTO legacy_fixture.walk_log(id,user_id,distance) VALUES (102,100,1)");
        String photo = "INSERT INTO legacy_fixture.walk_log_photos(walk_log_id,sequence,photo_url) VALUES (?,?,?)";
        jdbc.update(photo, 101, 0, cloudinaryUrl("walk_photo", 3));
        jdbc.update(photo, 101, 1, localUrl("walk_photo", 4));
        jdbc.update(photo, 102, 0, cloudinaryUrl("walk_photo", 3));
    }

    private static String uuid(int number) {
        return "00000000-0000-0000-0000-%012d".formatted(number);
    }

    private static String cloudinaryUrl(String folder, int number) {
        return "https://res.cloudinary.com/test-cloud/image/upload/v1/petroad/" + folder + "/" + uuid(number) + ".jpg";
    }

    private static String localUrl(String folder, int number) {
        return "http://localhost:8080/uploads/" + folder + "/" + uuid(number) + ".png";
    }
}
