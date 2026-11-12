package com.petroad.backend.application;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class MyPageService {
 private final UserRepository users;
 private final DogRepository dogs;
 private final WalkLogRepository walks;
 private final CourseRepository courses;
 private final CourseLikeRepository likes;
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 @Schema(name="MyDogProfile")
 public record DogProfile(String name,@Schema(description="내 반려견의 견종") String breed,DogSize size,LocalDate birthDate,String profileImage) {}
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 public record Stats(@Schema(description="내 산책일지 수") long walkCount,double totalDistanceMeters,long photoCount,
                     @Schema(description="내가 작성·공유한 코스 수. 산책일지 수와 다릅니다.") long courseCount,long likedCourseCount) {}
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 public record Overview(String region,DogProfile dog,Stats stats) {}
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 public record WalkDetail(String id,String title,LocalDate date,double distanceMeters,Integer durationSec,List<String> photos,String courseId,
                          List<WalkRouteCoordinate> route,Integer completion,String cover) {}
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 public record WalkRouteCoordinate(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
                                   @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {}
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 public record Point(double lat,double lng) {}
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 public record CourseDetail(String id,String name,String cover,int likes,double distanceMeters,Difficulty difficulty,List<String> tags,List<Point> points,boolean mine,boolean liked,
                            String creatorDogBreed,Integer creatorDogAge) {}

 public Overview overview(Long userId) {
  User user=users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다."));
  return new Overview(user.getRegion(),dogs.findByUserId(userId).map(this::dog).orElse(null),stats(userId));
 }
 public Stats stats(Long userId) {
  List<WalkLog> list=walks.findAllByUserIdOrderByWalkedAtDesc(userId);
  return new Stats(list.size(),list.stream().mapToDouble(w -> w.getDistance()*1000).sum(),list.stream().mapToLong(w -> w.getPhotos().size()).sum(),courses.findAllByUserIdOrderByIdDesc(userId).size(),likes.findAllByUserIdOrderByIdDesc(userId).size());
 }
 public List<WalkDetail> walks(Long userId) {return walks.findAllByUserIdOrderByWalkedAtDesc(userId).stream().map(this::walk).toList();}
 public WalkDetail walk(Long userId,Long id) {
  return walk(walks.findByIdAndUserId(id,userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"산책 기록을 찾을 수 없습니다.")));
 }
 @Transactional
 public WalkDetail saveWalk(Long userId, Long courseId, String title, double distanceMeters, Integer durationSec,
                            List<String> photos, String cover, Integer completion, List<WalkRouteCoordinate> route) {
  if (!Double.isFinite(distanceMeters)) {
   throw new IllegalArgumentException("distanceMeters는 유한한 거리여야 합니다.");
  }
  if (cover != null && (photos == null || !photos.contains(cover))) {
   throw new IllegalArgumentException("cover는 photos에 포함된 URL이어야 합니다.");
  }
  if (courseId == null && completion != null) {
   throw new IllegalArgumentException("자유 산책의 completion은 null이어야 합니다.");
  }
  List<WalkRoutePoint> points = route == null ? List.of() : route.stream()
          .map(point -> new WalkRoutePoint(point.lat(), point.lng())).toList();
  User user = users.findById(userId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
  Course course = courseId == null ? null : courses.lockById(courseId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다."));
  WalkLog log = new WalkLog(user, course, distanceMeters / 1000);
  log.setDetails(title, durationSec, photos);
  log.setRouteDetails(cover, completion, points);
  return walk(walks.save(log));
 }
 @Transactional
 public void deleteWalk(Long userId,Long id) {
  WalkLog log=walks.findByIdAndUserId(id,userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"산책 기록을 찾을 수 없습니다."));
  walks.delete(log);
 }
 public List<CourseDetail> courses(Long userId,boolean likedOnly) {
  Set<Long> likedIds=new HashSet<>();
  List<Course> liked=likes.findAllByUserIdOrderByIdDesc(userId).stream().map(CourseLike::getCourse).toList();
  liked.forEach(c -> likedIds.add(c.getId()));
  List<Course> list=likedOnly ? liked : courses.findAllByUserIdOrderByIdDesc(userId);
  if (list.isEmpty()) return List.of();
  Set<Long> ownerIds = list.stream().map(c -> c.getUser().getId()).collect(Collectors.toSet());
  Map<Long, Dog> profiles = dogs.findAllByUserIdIn(ownerIds).stream()
          .collect(Collectors.toMap(d -> d.getUser().getId(), Function.identity()));
  LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
  return list.stream().map(c -> course(c,userId,likedIds.contains(c.getId()), profiles.get(c.getUser().getId()), today)).toList();
 }
 public CourseDetail course(Long userId,Long id) {
  Course c=courses.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"코스를 찾을 수 없습니다."));
  Dog profile = dogs.findByUserId(c.getUser().getId()).orElse(null);
  return course(c,userId,likes.existsByUserIdAndCourseId(userId,id), profile, LocalDate.now(ZoneId.of("Asia/Seoul")));
 }
 @Transactional
 public DogProfile saveDog(Long userId,String name,String breed,DogSize size,LocalDate birthDate,String profileImage) {
  User user=users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다."));
  Dog d=dogs.findByUserId(userId).orElseGet(() -> new Dog(user,name,breed,size,birthDate,profileImage));
  d.update(name,breed,size,birthDate,profileImage);return dog(dogs.save(d));
 }
 private DogProfile dog(Dog d) {return new DogProfile(d.getName(),d.getBreed(),d.getSize(),d.getBirthDate(),d.getProfileImage());}
 private WalkDetail walk(WalkLog w) {
  LocalDate date=w.getWalkedAt()==null ? null : w.getWalkedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate();
  List<WalkRouteCoordinate> route = w.getRoute().stream().map(p -> new WalkRouteCoordinate(p.getLat(), p.getLng())).toList();
  return new WalkDetail(w.getId().toString(),w.getTitle()==null ? "산책 기록" : w.getTitle(),date,w.getDistance()*1000,w.getDurationSec(),List.copyOf(w.getPhotos()),w.getCourse()==null ? null : w.getCourse().getId().toString(),
          route, w.getCompletion(), w.getCover());
 }
 private CourseDetail course(Course c,Long userId,boolean liked,Dog profile,LocalDate today) {
  String breed = profile == null ? null : profile.getBreed();
  Integer age = profile == null ? null : CourseRecommendationService.age(profile.getBirthDate(), today);
  return new CourseDetail(c.getId().toString(),c.getName(),c.getCoverImageUrl(),c.getLikeCount(),c.getDistance()*1000,c.getDifficulty(),c.getTags().stream().map(CourseTag::getTag).toList(),c.getPoints().stream().map(p -> new Point(p.getLat(),p.getLng())).toList(),c.getUser().getId().equals(userId),liked,breed,age);
 }
}
