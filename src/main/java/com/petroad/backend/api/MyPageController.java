package com.petroad.backend.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.petroad.backend.application.MyPageService;
import com.petroad.backend.application.MyPageService.*;
import com.petroad.backend.domain.DogSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/api/me") @RequiredArgsConstructor
public class MyPageController {
 private final MyPageService service;
 @GetMapping public Overview overview(@RequestAttribute("userId") Long userId) {return service.overview(userId);}
 @GetMapping("/stats") public Stats stats(@RequestAttribute("userId") Long userId) {return service.stats(userId);}
 @GetMapping("/walks") public List<WalkDetail> walks(@RequestAttribute("userId") Long userId) {return service.walks(userId);}
 @GetMapping("/walks/{id}") public WalkDetail walk(@RequestAttribute("userId") Long userId,@PathVariable Long id) {return service.walk(userId,id);}
 @GetMapping("/courses") public List<CourseDetail> courses(@RequestAttribute("userId") Long userId) {return service.courses(userId,false);}
 @GetMapping("/likes") public List<CourseDetail> likes(@RequestAttribute("userId") Long userId) {return service.courses(userId,true);}
 @GetMapping("/courses/{id}") public CourseDetail course(@RequestAttribute("userId") Long userId,@PathVariable Long id) {return service.course(userId,id);}
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 public record DogRequest(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=100) String breed,@NotNull DogSize size,@NotNull @PastOrPresent LocalDate birthDate,@Size(max=2048) @Pattern(regexp="https?://.+") String profileImage) {}
 @PutMapping("/dog") public DogProfile saveDog(@RequestAttribute("userId") Long userId,@Valid @RequestBody DogRequest r) {
  return service.saveDog(userId,r.name().trim(),r.breed().trim(),r.size(),r.birthDate(),r.profileImage());
 }
}
