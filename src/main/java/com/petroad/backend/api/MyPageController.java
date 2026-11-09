package com.petroad.backend.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.petroad.backend.application.MyPageService;
import com.petroad.backend.application.MyPageService.*;
import com.petroad.backend.domain.DogSize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/api/me") @RequiredArgsConstructor
public class MyPageController {
 private final MyPageService service;
 @Operation(summary = "마이페이지 기본 정보 조회")
 @GetMapping public Overview overview(@RequestAttribute("userId") Long userId) {return service.overview(userId);}
 @Operation(summary = "내 산책·코스 통계 조회")
 @GetMapping("/stats") public Stats stats(@RequestAttribute("userId") Long userId) {return service.stats(userId);}
 @Operation(summary = "내 산책일지 목록 조회")
 @GetMapping("/walks") public List<WalkDetail> walks(@RequestAttribute("userId") Long userId) {return service.walks(userId);}
 @Operation(summary = "내 산책일지 상세 조회")
 @GetMapping("/walks/{id}") public WalkDetail walk(@RequestAttribute("userId") Long userId,@PathVariable Long id) {return service.walk(userId,id);}
 @Operation(summary = "내 산책일지 삭제", description = "본인의 산책 기록과 연결된 사진 URL 목록을 삭제합니다. 코스와 업로드된 이미지 파일은 삭제하지 않습니다. 다른 사용자 또는 없는 기록은 404입니다.")
 @ApiResponse(responseCode = "204", description = "삭제 완료")
 @ApiResponse(responseCode = "404", description = "본인의 산책 기록을 찾을 수 없음")
 @DeleteMapping("/walks/{id}")
 @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void deleteWalk(@RequestAttribute("userId") Long userId,@PathVariable Long id) {service.deleteWalk(userId,id);}
 @Operation(summary = "내가 만든 코스 목록 조회")
 @GetMapping("/courses") public List<CourseDetail> courses(@RequestAttribute("userId") Long userId) {return service.courses(userId,false);}
 @Operation(summary = "내가 좋아요한 코스 목록 조회")
 @GetMapping("/likes") public List<CourseDetail> likes(@RequestAttribute("userId") Long userId) {return service.courses(userId,true);}
 @Operation(summary = "코스 상세 조회")
 @GetMapping("/courses/{id}") public CourseDetail course(@RequestAttribute("userId") Long userId,@PathVariable Long id) {return service.course(userId,id);}
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 @Schema(name = "MyDogProfileRequest")
 public record DogRequest(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=100) String breed,@NotNull DogSize size,@NotNull @PastOrPresent(message="반려견 생일은 오늘 또는 과거 날짜여야 합니다.") LocalDate birthDate,@Size(max=2048) @Pattern(regexp="https?://.+") String profileImage) {}
 @Operation(summary = "내 반려견 정보 등록·수정")
 @PutMapping("/dog") public DogProfile saveDog(@RequestAttribute("userId") Long userId,@Valid @RequestBody DogRequest r) {
  return service.saveDog(userId,r.name().trim(),r.breed().trim(),r.size(),r.birthDate(),r.profileImage());
 }
}
