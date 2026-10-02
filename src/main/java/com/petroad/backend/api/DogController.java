package com.petroad.backend.api;

import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dog")
@RequiredArgsConstructor
public class DogController {
    private final DogRepository dogs;
    private final UserRepository users;

    public record DogRequest(@NotBlank String name, String breed, DogSize size, java.time.LocalDate birthDate,
                             String profileImage) {
    }

    public record DogResponse(Long id, String name, String breed, DogSize size, java.time.LocalDate birthDate,
                              String profileImage) {
    }

    @Operation(summary = "내 반려견 정보 조회")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public DogResponse get(@RequestAttribute("userId") Long userId) {
        return dogs.findByUserId(userId).map(this::response).orElseThrow(() -> new IllegalArgumentException("반려견 프로필이 없습니다."));
    }

    @Operation(summary = "반려견 정보 등록/수정")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping
    public DogResponse upsert(@RequestAttribute("userId") Long userId, @Valid @RequestBody DogRequest r) {
        User u = users.findById(userId).orElseThrow();
        Dog d = dogs.findByUserId(userId).orElseGet(() -> new Dog(u, r.name(), r.breed(), r.size(), r.birthDate(), r.profileImage()));
        d.update(r.name(), r.breed(), r.size(), r.birthDate(), r.profileImage());
        return response(dogs.save(d));
    }

    private DogResponse response(Dog d) {
        return new DogResponse(d.getId(), d.getName(), d.getBreed(), d.getSize(), d.getBirthDate(), d.getProfileImage());
    }
}