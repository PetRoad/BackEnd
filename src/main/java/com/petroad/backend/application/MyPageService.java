package com.petroad.backend.application;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class MyPageService {
 private final UserRepository users;
 private final DogRepository dogs;
 private final WalkLogRepository walks;
 private final CourseRepository courses;
 private final CourseLikeRepository likes;
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 public record DogProfile(String name,String breed,DogSize size,LocalDate birthDate,String profileImage) {}
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 public record Stats(long walkCount,double totalDistanceMeters,long photoCount,long courseCount,long likedCourseCount) {}
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 public record Overview(String region,DogProfile dog,Stats stats) {}
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 public record WalkDetail(String id,String title,LocalDate date,double distanceMeters,Integer durationSec,List<String> photos,String courseId) {}
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 public record Point(double lat,double lng) {}
 @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
 public record CourseDetail(String id,String name,String cover,int likes,double distanceMeters,Difficulty difficulty,List<String> tags,List<Point> points,boolean mine,boolean liked) {}

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
 public List<CourseDetail> courses(Long userId,boolean likedOnly) {
  Set<Long> likedIds=new HashSet<>();
  List<Course> liked=likes.findAllByUserIdOrderByIdDesc(userId).stream().map(CourseLike::getCourse).toList();
  liked.forEach(c -> likedIds.add(c.getId()));
  List<Course> list=likedOnly ? liked : courses.findAllByUserIdOrderByIdDesc(userId);
  return list.stream().map(c -> course(c,userId,likedIds.contains(c.getId()))).toList();
 }
 public CourseDetail course(Long userId,Long id) {
  Course c=courses.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"코스를 찾을 수 없습니다."));
  return course(c,userId,likes.existsByUserIdAndCourseId(userId,id));
 }
 @Transactional
 public DogProfile saveDog(Long userId,String name,String breed,DogSize size,LocalDate birthDate,String profileImage) {
  User user=users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다."));
  Dog d=dogs.findByUserId(userId).orElseGet(() -> new Dog(user,name,breed,size,birthDate,profileImage));
  d.update(name,breed,size,birthDate,profileImage);return dog(dogs.save(d));
 }
 private DogProfile dog(Dog d) {return new DogProfile(d.getName(),d.getBreed(),d.getSize(),d.getBirthDate(),d.getProfileImage());}
 private WalkDetail walk(WalkLog w) {
  LocalDate date=w.getWalkedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate();
  return new WalkDetail(w.getId().toString(),w.getTitle()==null ? "산책 기록" : w.getTitle(),date,w.getDistance()*1000,w.getDurationSec(),List.copyOf(w.getPhotos()),w.getCourse()==null ? null : w.getCourse().getId().toString());
 }
 private CourseDetail course(Course c,Long userId,boolean liked) {
  return new CourseDetail(c.getId().toString(),c.getName(),c.getCoverImageUrl(),c.getLikeCount(),c.getDistance()*1000,c.getDifficulty(),c.getTags().stream().map(CourseTag::getTag).toList(),c.getPoints().stream().map(p -> new Point(p.getLat(),p.getLng())).toList(),c.getUser().getId().equals(userId),liked);
 }
}
