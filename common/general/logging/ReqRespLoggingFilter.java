package com.arun.general.logging;

import com.arun.model.history.logs.ReqResponseLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Slf4j
public class ReqRespLoggingFilter implements Filter {

	private LoggingHelper loggingHelper;

	@Autowired
	public ReqRespLoggingFilter (LoggingHelper loggingHelper) {
		this.loggingHelper = loggingHelper;
	}

	@Override
	public void doFilter(
			ServletRequest request,
			ServletResponse response,
			FilterChain chain) throws IOException, ServletException {

		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse res = (HttpServletResponse) response;
		doFilterWrapped(wrapRequest(req), wrapResponse(res), chain);

	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {

	}

	@Override
	public void destroy() {

	}

	private void doFilterWrapped(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response,
			FilterChain filterChain) throws ServletException, IOException {

		ZonedDateTime currentTime = ZonedDateTime.now(ZoneOffset.UTC);
		long startTime = System.currentTimeMillis();
		try {
			filterChain.doFilter(request, response);
		} finally {
			long timeTaken = System.currentTimeMillis() - startTime;
			ReqResponseLog reqResponseLog = getRequestResponseInfo(request, response);
			reqResponseLog.setLogDatetime(currentTime);
			reqResponseLog.setResponseTime(timeTaken);

			this.loggingHelper.sendReqRespLog(reqResponseLog);

			response.copyBodyToResponse();
		}
	}

	private ReqResponseLog getRequestResponseInfo(ContentCachingRequestWrapper request,
			ContentCachingResponseWrapper response) {
		ReqResponseLog reqResponseLog = this.loggingHelper.getRequest(request);
		this.loggingHelper.getResponse(response, reqResponseLog);
		return reqResponseLog;
	}

	private ContentCachingRequestWrapper wrapRequest(HttpServletRequest request) {
		if (request instanceof ContentCachingRequestWrapper) {
			return (ContentCachingRequestWrapper) request;
		} else {
			return new ContentCachingRequestWrapper(request);
		}
	}

	private ContentCachingResponseWrapper wrapResponse(HttpServletResponse response) {
		if (response instanceof ContentCachingResponseWrapper) {
			return (ContentCachingResponseWrapper) response;
		} else {
			return new ContentCachingResponseWrapper(response);
		}
	}
}
