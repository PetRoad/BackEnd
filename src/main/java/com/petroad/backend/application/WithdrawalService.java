package com.petroad.backend.application;

import com.petroad.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WithdrawalService {
    private final UserRepository users;
    private final DogRepository dogs;
    private final CourseRepository courses;
    private final CourseLikeRepository likes;
    private final WalkLogRepository walkLogs;
    private final PasswordEncoder encoder;

    @Transactional
    public void withdraw(Long userId, String password) {
        var user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
        if (!encoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 올바르지 않습니다.");
        }

        // 1. 내가 누른 좋아요 삭제 (남의 코스는 좋아요 수를 1 줄임)
        var myLikes = likes.findAllByUserIdOrderByIdDesc(userId);
        for (var like : myLikes) {
            var course = like.getCourse();
            if (!course.getUser().getId().equals(userId)) {
                course.changeLikeCount(-1);
            }
        }
        likes.deleteAll(myLikes);

        // 2. 내 산책일지 삭제 (사진 목록 포함)
        walkLogs.deleteAll(walkLogs.findAllByUserIdOrderByWalkedAtDesc(userId));

        // 3. 내가 만든 코스 삭제
        for (var course : courses.findAllByUserIdOrderByIdDesc(userId)) {
            likes.deleteAllByCourseId(course.getId());   // 남이 누른 좋아요
            walkLogs.clearCourse(course.getId());        // 남의 산책일지에서 이 코스 연결 끊기
            courses.delete(course);                      // 경로·태그는 같이 삭제됨
        }

        // 4. 반려견, 5. 사용자 삭제
        dogs.findByUserId(userId).ifPresent(dogs::delete);
        users.delete(user);
    }
}