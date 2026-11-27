package com.petroad.backend.application;

import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import com.petroad.backend.storage.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MyPageServiceTest {
 @Mock UserRepository users;
 @Mock DogRepository dogs;
 @Mock WalkLogRepository walks;
 @Mock CourseRepository courses;
 @Mock CourseLikeRepository likes;
 @Mock ImageStorage images;
 @Mock UploadedImageRepository uploadRecords;
 MyPageService service;

 @BeforeEach void setup() {
  service=new MyPageService(users,dogs,walks,courses,likes,new UploadedImageService(images,uploadRecords));
 }

 @Test void aggregatesOnlyAuthenticatedUsersRecordsAndConvertsKilometers() {
  WalkLog first=new WalkLog(null,null,3.2); first.setDetails("산책",120,List.of("http://localhost:8080/uploads/walk_photo/00000000-0000-0000-0000-000000000002.jpg","http://localhost:8080/uploads/walk_photo/00000000-0000-0000-0000-000000000003.jpg"));
  WalkLog second=new WalkLog(null,null,2.1);
  when(walks.findAllByUserIdOrderByWalkedAtDesc(7L)).thenReturn(List.of(first,second));
  when(courses.findAllByUserIdOrderByIdDesc(7L)).thenReturn(List.of(mock(Course.class)));
  when(likes.findAllByUserIdOrderByIdDesc(7L)).thenReturn(List.of(mock(CourseLike.class)));
  var stats=service.stats(7L);
  assertEquals(2,stats.walkCount()); assertEquals(5300,stats.totalDistanceMeters(),0.001);
  assertEquals(2,stats.photoCount()); assertEquals(1,stats.courseCount()); assertEquals(1,stats.likedCourseCount());
  verify(walks).findAllByUserIdOrderByWalkedAtDesc(7L);
 }
 @Test void foreignWalkIsNotExposed() {
  when(walks.findByIdAndUserId(99L,7L)).thenReturn(Optional.empty());
  var error=assertThrows(ResponseStatusException.class,() -> service.walk(7L,99L));
  assertEquals(404,error.getStatusCode().value());
  verify(walks,never()).findById(anyLong());
 }
 @Test void walkDatesUseKstAndLegacyTimeIsUnknown() {
  WalkLog log=mock(WalkLog.class);
  when(log.getId()).thenReturn(99L);
  when(log.getWalkedAt()).thenReturn(LocalDateTime.of(2026,9,30,16,0));
  when(log.getPhotos()).thenReturn(List.of());
  when(log.getDurationSec()).thenReturn(null);
  when(walks.findByIdAndUserId(99L,7L)).thenReturn(Optional.of(log));
  var result=service.walk(7L,99L);
  assertEquals("2026-10-01",result.date().toString()); assertNull(result.durationSec());
  assertEquals("99",result.id());
 }
 @Test void missingDogIsValidEmptyProfile() {
  when(users.findById(7L)).thenReturn(Optional.of(new User("a@example.com","hash")));
  when(dogs.findByUserId(7L)).thenReturn(Optional.empty());
  when(walks.findAllByUserIdOrderByWalkedAtDesc(7L)).thenReturn(List.of());
  when(courses.findAllByUserIdOrderByIdDesc(7L)).thenReturn(List.of());
  when(likes.findAllByUserIdOrderByIdDesc(7L)).thenReturn(List.of());
  var result=service.overview(7L); assertNull(result.dog()); assertEquals(0,result.stats().walkCount());
 }

 @Test void externalWalkPhotosAndCoversAreRejectedBeforePersistence() {
  assertThrows(IllegalArgumentException.class, () -> service.saveWalk(7L,null,"산책",1,1,
          List.of("https://example.com/a.jpg"),null,null,List.of()));
  assertThrows(IllegalArgumentException.class, () -> service.saveWalk(7L,null,"산책",1,1,
          List.of(),"https://example.com/cover.jpg",null,List.of()));
  verifyNoInteractions(users,walks,courses);
 }

 @Test void photosUploadedBySomeoneElseAreRejectedBeforePersistence() {
  String url="http://localhost:8080/uploads/walk_photo/00000000-0000-0000-0000-000000000002.jpg";
  when(images.isOwnUrl(url)).thenReturn(true);
  when(uploadRecords.existsByUserIdAndUrl(7L,url)).thenReturn(false);
  var walkError=assertThrows(IllegalArgumentException.class, () -> service.saveWalk(7L,null,"산책",1,1,
          List.of(url),url,null,List.of()));
  assertEquals("photos는 본인이 업로드한 사진이어야 합니다.",walkError.getMessage());
  var dogError=assertThrows(IllegalArgumentException.class, () -> service.saveDog(7L,"콩이","말티즈",DogSize.SMALL,
          java.time.LocalDate.of(2024,1,1),url));
  assertEquals("profileImage는 본인이 업로드한 사진이어야 합니다.",dogError.getMessage());
  verifyNoInteractions(users,dogs,walks,courses);
 }

 @Test void externalDogImageIsRejectedBeforePersistence() {
  assertThrows(IllegalArgumentException.class, () -> service.saveDog(7L,"콩이","말티즈",DogSize.SMALL,
          java.time.LocalDate.of(2024,1,1),"https://example.com/profile.jpg"));
  verifyNoInteractions(users,dogs);
 }
}
