package com.petroad.backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {

    @Operation(
            summary = "Hello World 확인",
            description = "개발 환경과 API 연결 상태를 확인하기 위한 공개 테스트 API입니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "정상 응답",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Hello World")
                    )
            )
    })
    @GetMapping(value = "/hello", produces = "text/plain")
    public String hello() {
        return "Hello World";
    }
}
