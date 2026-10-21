package com.petroad.backend.config;

import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;

/** Explicit opt-in fixture for a local development database only. */
@Configuration
@Profile("local-mock")
public class LocalCourseMockConfig {
    @Bean
    CommandLineRunner courseMockData(UserRepository users, DogRepository dogs, CourseRepository courses,
                                    PasswordEncoder passwords, TransactionTemplate transactions) {
        return args -> transactions.executeWithoutResult(status -> {
            seed(users, dogs, courses, passwords, "river", "성북천 따라 걷기", Difficulty.EASY, DogSize.SMALL,
                    75, 1390, "포메라니안", 3, new String[]{"물가", "평지"}, new double[][]{
                            {37.5886,127.0068}, {37.5887,127.0100}, {37.5890,127.0135},
                            {37.5893,127.0165}, {37.5870,127.0185}, {37.5852,127.0196}});
            seed(users, dogs, courses, passwords, "park", "낙산공원 성곽길", Difficulty.NORMAL, DogSize.SMALL,
                    42, 760, "말티즈", 2, new String[]{"공원", "야경"}, new double[][]{
                            {37.5855,127.0058}, {37.5838,127.0070}, {37.5820,127.0074},
                            {37.5806,127.0076}, {37.5790,127.0070}});
            seed(users, dogs, courses, passwords, "sky", "북악 스카이웨이", Difficulty.HARD, DogSize.LARGE,
                    210, 1520, "보더콜리", 6, new String[]{"야경"}, new double[][]{
                            {37.6020,126.9810}, {37.6000,126.9760}, {37.5985,126.9700}, {37.5970,126.9650}});
        });
    }

    private void seed(UserRepository users, DogRepository dogs, CourseRepository courses, PasswordEncoder passwords,
                      String key, String name, Difficulty difficulty, DogSize size, int likes, double meters,
                      String breed, int age, String[] tags, double[][] points) {
        String email = "mock-" + key + "@petroad.local";
        // Re-starting the local profile must not duplicate fixtures or overwrite user edits.
        if (users.findByEmail(email).isPresent()) return;
        User user = users.save(new User(email, passwords.encode("Petroad123!")));
        user.updateRegion("서울특별시 성북구 삼선2동");
        dogs.save(new Dog(user, "목업 강아지", breed, size,
                LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(age), null));
        Course course = new Course(user, name, null, meters / 1000, difficulty, size, points[0][0], points[0][1]);
        course.changeLikeCount(likes);
        for (int i = 0; i < points.length; i++) course.addPoint(points[i][0], points[i][1], i);
        for (String tag : tags) course.addTag(tag);
        courses.save(course);
    }
}
