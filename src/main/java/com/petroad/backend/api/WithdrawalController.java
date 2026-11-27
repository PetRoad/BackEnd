package com.petroad.backend.api;

import com.petroad.backend.application.WithdrawalService;
import com.petroad.backend.security.MaxPasswordBytes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class WithdrawalController {
    private final WithdrawalService service;

    public record WithdrawRequest(@NotBlank @MaxPasswordBytes String password) {}

    @Operation(summary = "회원 탈퇴", description = "비밀번호를 확인한 뒤 계정과 내 데이터를 삭제합니다. 업로드한 사진 파일도 저장하지 않은 것까지 저장소에서 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "탈퇴 완료")
    @ApiResponse(responseCode = "400", description = "비밀번호 불일치 또는 입력값 오류")
    @ApiResponse(responseCode = "429", description = "15분 안에 비밀번호를 5번 틀림. Retry-After 초 이후 다시 시도하세요.",
            headers = @Header(name = "Retry-After", description = "재시도까지 대기할 초",
                    schema = @Schema(type = "integer", minimum = "1")),
            content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ApiError.class)))
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@RequestAttribute("userId") Long userId,
                         @Valid @RequestBody WithdrawRequest request) {
        service.withdraw(userId, request.password());
    }
}