package com.petroad.backend.application;

import com.petroad.backend.api.CourseController;
import com.petroad.backend.application.MyPageService.WalkRouteCoordinate;
import com.petroad.backend.domain.Difficulty;
import com.petroad.backend.domain.DogSize;
import com.petroad.backend.domain.User;
import com.petroad.backend.repository.UserRepository;
import com.petroad.backend.storage.ImageStorage;
import com.petroad.backend.storage.ImageStorage.StoredImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 테스트에 @Transactional을 붙이지 않는다. 파일 삭제는 실제로 커밋된 뒤에만 일어난다.
@EnabledIfEnvironmentVariable(named = "PETROAD_DB_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "jwt.secret=image-cleanup-test-only-key-do-not-deploy-0123456789",
        "spring.datasource.username=petroad_test", "spring.datasource.password=test-only-password",
        "spring.flyway.schemas=image_cleanup_test", "spring.flyway.default-schema=image_cleanup_test",
        "spring.jpa.properties.hibernate.default_schema=image_cleanup_test",
        "spring.datasource.hikari.schema=image_cleanup_test"
})
class ImageCleanupTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PETROAD_DB_TEST_URL"));
    }

    private static final String PASSWORD = "Password123!";

    @Autowired MyPageService myPage;
    @Autowired CourseController courseController;
    @Autowired WithdrawalService withdrawal;
    @Autowired UploadedImageService uploadedImages;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockBean ImageStorage storage;

    @BeforeEach void ownUrls() {
        when(storage.isOwnUrl(any())).thenReturn(true);
    }

    private long newUser() {
        return users.save(new User(UUID.randomUUID() + "@example.com", encoder.encode(PASSWORD))).getId();
    }

    private StoredImage upload(long userId) {
        String publicId = "walk_photo/" + UUID.randomUUID() + ".jpg";
        var image = new StoredImage(publicId, "http://localhost:8080/uploads/" + publicId);
        uploadedImages.record(userId, image);
        return image;
    }

    private long walk(long userId, StoredImage cover, StoredImage... photos) {
        List<String> urls = Arrays.stream(photos).map(StoredImage::url).toList();
        return Long.parseLong(myPage.saveWalk(userId, null, "산책", 100, 60, urls, cover.url(), null, null).id());
    }

    private long course(long userId, StoredImage cover) {
        courseController.create(userId, new CourseController.CourseCreateRequest("코스", cover.url(), 1.0,
                Difficulty.EASY, DogSize.SMALL, 0.0, 0.0, null,
                List.of(new WalkRouteCoordinate(0.0, 0.0), new WalkRouteCoordinate(0.002, 0.0))));
        return jdbc.queryForObject("select max(id) from course where user_id = ?", Long.class, userId);
    }

    private void dogPhoto(long userId, StoredImage image) {
        myPage.saveDog(userId, "콩이", "말티즈", DogSize.SMALL, LocalDate.of(2024, 1, 1),
                image == null ? null : image.url());
    }

    private boolean recorded(StoredImage image) {
        return jdbc.queryForObject("select count(*) from uploaded_images where url = ?", Integer.class, image.url()) == 1;
    }

    private int walks(long userId) {
        return jdbc.queryForObject("select count(*) from walk_log where user_id = ?", Integer.class, userId);
    }

    private void assertDeleted(StoredImage image) {
        verify(storage).delete(image.publicId());
        assertThat(recorded(image)).isFalse();
    }

    private void assertKept(StoredImage image) {
        verify(storage, never()).delete(image.publicId());
        assertThat(recorded(image)).isTrue();
    }

    @Test void deletingAWalkKeepsThePhotoThatACourseStillUses() {
        long userId = newUser();
        StoredImage shared = upload(userId), walkOnly = upload(userId), unsaved = upload(userId);
        long walkId = walk(userId, shared, shared, walkOnly);
        long courseId = course(userId, shared);

        myPage.deleteWalk(userId, walkId);

        assertDeleted(walkOnly);
        assertKept(shared);
        assertKept(unsaved);

        courseController.delete(userId, courseId);

        assertDeleted(shared);
        assertKept(unsaved);
    }

    @Test void deletingACourseKeepsTheCoverThatAWalkStillUses() {
        long userId = newUser();
        StoredImage photo = upload(userId);
        walk(userId, photo, photo);
        long courseId = course(userId, photo);

        courseController.delete(userId, courseId);

        assertKept(photo);
    }

    @Test void changingTheDogPhotoDeletesOnlyThePreviousOne() {
        long userId = newUser();
        StoredImage first = upload(userId), second = upload(userId);
        dogPhoto(userId, first);
        dogPhoto(userId, first);
        assertKept(first);

        dogPhoto(userId, second);
        assertDeleted(first);
        assertKept(second);

        dogPhoto(userId, null);
        assertDeleted(second);
    }

    @Test void nothingIsDeletedWhenTheDatabaseRollsBack() {
        long userId = newUser();
        StoredImage photo = upload(userId);
        long walkId = walk(userId, photo, photo);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            myPage.deleteWalk(userId, walkId);
            status.setRollbackOnly();
        });

        assertKept(photo);
        assertThat(walks(userId)).isEqualTo(1);
    }

    @Test void aStorageFailureDoesNotUndoTheDeleteOrStopOtherPhotos() {
        long userId = newUser();
        StoredImage failing = upload(userId), other = upload(userId);
        long walkId = walk(userId, failing, failing, other);
        doThrow(new IllegalStateException("저장소 오류")).when(storage).delete(failing.publicId());

        myPage.deleteWalk(userId, walkId);

        assertThat(walks(userId)).isZero();
        assertDeleted(other);
        // 지우지 못한 사진은 기록을 남겨 탈퇴할 때 다시 시도한다.
        verify(storage).delete(failing.publicId());
        assertThat(recorded(failing)).isTrue();
    }

    @Test void withdrawalDeletesEveryUploadIncludingUnsavedOnes() {
        long userId = newUser(), otherId = newUser();
        StoredImage walkPhoto = upload(userId), cover = upload(userId), profile = upload(userId), unsaved = upload(userId);
        StoredImage othersPhoto = upload(otherId);
        walk(userId, walkPhoto, walkPhoto);
        course(userId, cover);
        dogPhoto(userId, profile);

        withdrawal.withdraw(userId, PASSWORD);

        for (StoredImage image : List.of(walkPhoto, cover, profile, unsaved)) assertDeleted(image);
        assertKept(othersPhoto);
        assertThat(users.existsById(userId)).isFalse();
    }

    @Test void withdrawalKeepsTheFileThatAnotherUsersRecordStillUses() {
        long userId = newUser(), otherId = newUser();
        StoredImage shared = upload(userId);
        // 소유자 검사가 생기기 전에는 다른 사용자가 같은 주소를 저장할 수 있었다.
        jdbc.update("insert into dog(user_id,profile_image) values (?,?)", otherId, shared.url());

        withdrawal.withdraw(userId, PASSWORD);

        verify(storage, never()).delete(shared.publicId());
        assertThat(users.existsById(userId)).isFalse();
    }

    @Test void theWithdrawalLockMakesNewUploadRecordsWait() throws Exception {
        long userId = newUser();
        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<StoredImage> pending = new TransactionTemplate(transactions).execute(status -> {
                users.lockForDeletion(userId);
                Future<StoredImage> uploading = pool.submit(() -> upload(userId));
                assertThatThrownBy(() -> uploading.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
                return uploading;
            });
            assertThat(recorded(pending.get(10, TimeUnit.SECONDS))).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }
}
