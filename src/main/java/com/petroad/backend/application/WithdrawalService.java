package com.petroad.backend.application;

import com.petroad.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WithdrawalService {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Transactional
    public void withdraw(Long userId, String password) {
        var user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
        if (!encoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 올바르지 않습니다.");
        }
        // 2단계에서 여기에 데이터 삭제를 추가합니다.
    }
}