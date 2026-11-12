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

        // DB 외래 키가 받은 좋아요를 삭제하고 다른 사용자의 산책일지를 보존합니다.
        courses.deleteAll(courses.findAllByUserIdOrderByIdDesc(userId));

        // 4. 반려견, 5. 사용자 삭제
        dogs.findByUserId(userId).ifPresent(dogs::delete);
        users.delete(user);
    }
}
