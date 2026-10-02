package com.petroad.backend.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.petroad.backend.application.MyPageService;
import com.petroad.backend.domain.DogSize;
import com.petroad.backend.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDate;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MyPageControllerTest {
 MyPageService service=mock(MyPageService.class);
 MockMvc mvc;
 @BeforeEach void setup() {
  var mapper=new ObjectMapper().findAndRegisterModules().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
  JwtService jwt=mock(JwtService.class); when(jwt.parseUserId("valid")).thenReturn(7L);
  mvc=MockMvcBuilders.standaloneSetup(new MyPageController(service))
   .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
   .addInterceptors(new JwtInterceptor(jwt)).build();
 }
 @Test void overviewKeepsCamelCaseDespiteLegacyGlobalSetting() throws Exception {
  when(service.overview(7L)).thenReturn(new MyPageService.Overview("서울",null,new MyPageService.Stats(3,6800,6,0,0)));
  mvc.perform(get("/api/me").header("Authorization","Bearer valid").param("userId","88"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.stats.totalDistanceMeters").value(6800))
   .andExpect(jsonPath("$.stats.walkCount").value(3));
  verify(service).overview(7L); verify(service,never()).overview(88L);
 }
 @Test void dogRequestAcceptsCamelCase() throws Exception {
  mvc.perform(put("/api/me/dog").header("Authorization","Bearer valid").contentType(MediaType.APPLICATION_JSON)
   .content("{\"name\":\"콩이\",\"breed\":\"말티즈\",\"size\":\"SMALL\",\"birthDate\":\"2024-01-01\"}"))
   .andExpect(status().isOk());
  verify(service).saveDog(7L,"콩이","말티즈",DogSize.SMALL,LocalDate.of(2024,1,1),null);
 }
 @Test void invalidDogCannotBeSaved() throws Exception {
  mvc.perform(put("/api/me/dog").header("Authorization","Bearer valid").contentType(MediaType.APPLICATION_JSON)
   .content("{\"name\":\"\",\"breed\":\"말티즈\",\"size\":\"SMALL\",\"birthDate\":\"2999-01-01\"}"))
   .andExpect(status().isBadRequest());
  verifyNoInteractions(service);
 }
}
