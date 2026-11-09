package com.petroad.backend.api;

import com.petroad.backend.application.WithdrawalService;
import com.petroad.backend.security.MaxPasswordBytes;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "회원 탈퇴", description = "비밀번호를 확인한 뒤 계정과 내 데이터를 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "탈퇴 완료")
    @ApiResponse(responseCode = "400", description = "비밀번호 불일치 또는 입력값 오류")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@RequestAttribute("userId") Long userId,
                         @Valid @RequestBody WithdrawRequest request) {
        service.withdraw(userId, request.password());
    }
}