package com.petroad.backend.application;

import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
 @InjectMocks MyPageService service;

 @Test void aggregatesOnlyAuthenticatedUsersRecordsAndConvertsKilometers() {
  WalkLog first=new WalkLog(null,null,3.2); first.setDetails("산책",120,List.of("https://example.com/a.jpg","https://example.com/b.jpg"));
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
  when(users.findById(7L)).thenReturn(Optional.of(new User("a@example.com","hash",null)));
  when(dogs.findByUserId(7L)).thenReturn(Optional.empty());
  when(walks.findAllByUserIdOrderByWalkedAtDesc(7L)).thenReturn(List.of());
  when(courses.findAllByUserIdOrderByIdDesc(7L)).thenReturn(List.of());
  when(likes.findAllByUserIdOrderByIdDesc(7L)).thenReturn(List.of());
  var result=service.overview(7L); assertNull(result.dog()); assertEquals(0,result.stats().walkCount());
 }
}
