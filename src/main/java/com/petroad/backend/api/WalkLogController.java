package com.petroad.backend.api;
<<<<<<< HEAD
import com.petroad.backend.domain.*; import com.petroad.backend.repository.*; import jakarta.validation.Valid; import jakarta.validation.constraints.PositiveOrZero; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*; import java.time.LocalDateTime; import java.util.*;
@RestController @RequestMapping("/api/walk-logs") @RequiredArgsConstructor public class WalkLogController { private final WalkLogRepository logs; private final UserRepository users; private final CourseRepository courses;
 public record Create(Long courseId,@PositiveOrZero double distance, @jakarta.validation.constraints.Size(max=100) String title, @PositiveOrZero Integer durationSec, @jakarta.validation.constraints.Size(max=100) List<@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=2048) @jakarta.validation.constraints.Pattern(regexp="https?://.+") String> photos){} public record Response(Long id,Long courseId,double distance,LocalDateTime walkedAt){}
 @PostMapping public Response create(@RequestAttribute("userId") Long userId,@Valid @RequestBody Create r){Course c=r.courseId()==null?null:courses.findById(r.courseId()).orElseThrow(); WalkLog log=new WalkLog(users.findById(userId).orElseThrow(),c,r.distance()); log.setDetails(r.title(),r.durationSec(),r.photos()); return to(logs.save(log));}
 @GetMapping public List<Response> list(@RequestAttribute("userId") Long userId){return logs.findAllByUserIdOrderByWalkedAtDesc(userId).stream().map(this::to).toList();} private Response to(WalkLog w){return new Response(w.getId(),w.getCourse()==null?null:w.getCourse().getId(),w.getDistance(),w.getWalkedAt());}
}
=======

import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/walk-logs")
@RequiredArgsConstructor
public class WalkLogController {
    private final WalkLogRepository logs;
    private final UserRepository users;
    private final CourseRepository courses;

    public record Create(Long courseId, @PositiveOrZero double distance) {
    }

    public record Response(Long id, Long courseId, double distance, LocalDateTime walkedAt) {
    }

    @Operation(summary = "산책 기록 등록")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public Response create(@RequestAttribute("userId") Long userId, @Valid @RequestBody Create r) {
        Course c = r.courseId() == null ? null : courses.findById(r.courseId()).orElseThrow();
        return to(logs.save(new WalkLog(users.findById(userId).orElseThrow(), c, r.distance())));
    }

    @Operation(summary = "내 산책 기록 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public List<Response> list(@RequestAttribute("userId") Long userId) {
        return logs.findAllByUserIdOrderByWalkedAtDesc(userId).stream().map(this::to).toList();
    }

    private Response to(WalkLog w) {
        return new Response(w.getId(), w.getCourse() == null ? null : w.getCourse().getId(), w.getDistance(), w.getWalkedAt());
    }
}
>>>>>>> origin/develop
