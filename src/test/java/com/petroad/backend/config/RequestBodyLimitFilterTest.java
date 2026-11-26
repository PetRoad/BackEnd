package com.petroad.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RequestBodyLimitFilterTest {
    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter();

    @Test void unknownOrUnderreportedLengthsCannotBypassTheByteLimit() throws Exception {
        for (int reported : new int[]{-1, 0, 1}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/me/walks") {
                @Override public long getContentLengthLong() { return reported; }
            };
            request.setContent(new byte[RequestBodyLimitFilter.MAX_BYTES + 1]);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(413);
            assertThat(response.getContentAsString()).contains("message", "2MB");
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            verifyNoInteractions(chain);
        }
    }

    @Test void exactLimitIsForwardedWithoutLosingAnyBytes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/me/walks");
        byte[] body = new byte[RequestBodyLimitFilter.MAX_BYTES];
        body[body.length - 1] = 42;
        request.setContent(body);
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (ServletRequest req, ServletResponse res) -> {
            called.set(true);
            req.getInputStream().transferTo(received);
        });
        assertThat(called).isTrue();
        assertThat(received.toByteArray()).isEqualTo(body);
    }

    @Test void onlyTheExactUploadPathIsExemptIncludingUnderAContextPath() throws Exception {
        MockHttpServletRequest upload = new MockHttpServletRequest("POST", "/petroad/api/uploads");
        upload.setContextPath("/petroad");
        upload.setContent(new byte[RequestBodyLimitFilter.MAX_BYTES + 1]);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(upload, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(same(upload), any());

        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/uploads-extra");
        other.setContent(new byte[RequestBodyLimitFilter.MAX_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(other, response, mock(FilterChain.class));
        assertThat(response.getStatus()).isEqualTo(413);
    }
}
