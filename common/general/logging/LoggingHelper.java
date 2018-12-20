package com.arun.general.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.arun.general.helper.Util;
import com.arun.general.messaging.channel.LoggingOutputChannel;
import com.arun.model.history.logs.InfoLog;
import com.arun.model.history.logs.ReqResponseLog;
import com.arun.model.history.logs.ReqResponseType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

import static com.arun.general.logging.LoggingUtil.ATTRIBUTE_USER;

@Slf4j
public class LoggingHelper {

	private static final List<MediaType> VISIBLE_TYPES = Arrays.asList(
			MediaType.valueOf("text/*"),
			MediaType.APPLICATION_FORM_URLENCODED,
			MediaType.APPLICATION_JSON,
			MediaType.APPLICATION_XML,
			MediaType.valueOf("application/*+json"),
			MediaType.valueOf("application/*+xml"),
			MediaType.MULTIPART_FORM_DATA);

	private static final String API_VERSION_PATTERN = "/services/api/[vV][1-9][0-9]{0,3}/(.*)";

	private String serviceName;
	private TraceIdProvider tracer;
	private ApiNames apiNames;
	private LoggingOutputChannel loggingOutputChannel;
	private ObjectWriter reqRespObjectWriter;

	public LoggingHelper (String serviceName, TraceIdProvider tracer, ApiNames apiNames) {
		this.serviceName = serviceName;
		this.tracer = tracer;
		this.apiNames = apiNames;
		this.reqRespObjectWriter = new ObjectMapper().writerWithDefaultPrettyPrinter();
	}

	public LoggingHelper (String serviceName, TraceIdProvider tracer, ApiNames apiNames,
			LoggingOutputChannel loggingOutputChannel) {
		this(serviceName, tracer, apiNames);
		this.loggingOutputChannel = loggingOutputChannel;
	}

	public void debug(String message) {
		log.debug(message);
		sendLog(message, LoggingHandler.Level.DEBUG);
	}

	public void info(String message) {
		log.info(message);
		sendLog(message, LoggingHandler.Level.INFO);
	}

	public void warn(String message) {
		log.warn(message);
		sendLog(message, LoggingHandler.Level.WARN);
	}

	public void warn(String message, Throwable throwable) {
		log.warn(message, throwable);
		String stackTrace = message + StringUtils.substring(ExceptionUtils.getStackTrace(throwable), 0, 1200);
		sendLog(stackTrace, LoggingHandler.Level.WARN);
	}

	public void error(String message) {
		log.error(message);
		sendLog(message, LoggingHandler.Level.ERROR);
	}

	public void error(String message, Throwable throwable) {
		log.error(message, throwable);
		String stackTrace = message + StringUtils.substring(ExceptionUtils.getStackTrace(throwable), 0, 1200);
		sendLog(stackTrace, LoggingHandler.Level.ERROR);
	}

	private void sendLog(String message, LoggingHandler.Level level) {
		try {
			if (this.loggingOutputChannel != null) {
				InfoLog infoLog = InfoLog.builder()
						.serviceName(this.serviceName)
						.logDatetime(ZonedDateTime.now(ZoneOffset.UTC))
						.type(level.name())
						.traceId(this.tracer.getTraceId())
						.message(message)
						.build();
				this.loggingOutputChannel.recordInfoLog().send(MessageBuilder.withPayload(infoLog).build());
				log.info("==============Information Log sent===========================");
			}
		} catch (Exception e) {
			log.error("Information Logging Error", e);
		}
	}

	public void sendReqRespLog(ReqResponseLog reqResponseLog) {
		if (reqResponseLog != null) {

			if (this.loggingOutputChannel != null) {
				this.loggingOutputChannel.recordRequestResponse().send(MessageBuilder.withPayload(reqResponseLog).build());
				log.info("==============Request Response Log sent===========================");
			} else {
				log.info(reqResponseLog.toString());
			}

		}
	}

	public ReqResponseLog getRequest(HttpServletRequest request) {
		String requestUrl = request.getRequestURI();
		String queryString = request.getQueryString();
		Map<String, String> queryParams;

		if (StringUtils.isNotBlank(queryString)) {
			queryParams = new HashMap<>();

			String[] params = queryString.split("&");
			if (params.length > 0) {
				Util.asStream(Arrays.asList(params)).forEach(param -> {
					String[] keyValue = param.split("=");
					if (keyValue.length > 1) {
						queryParams.put(keyValue[0], keyValue[1]);
					} else if (keyValue.length == 1) {
						queryParams.put(keyValue[0], "");
					}
				});
			}
		} else {
			queryParams = null;
		}

		String user = request.getRemoteUser();
		user = user == null ? (String) request.getAttribute(ATTRIBUTE_USER) : null;

		Map<String, String> headersMap = getRequestHeader(request);
		String body = "";

		if (request instanceof ContentCachingRequestWrapper) {
			byte[] content = ((ContentCachingRequestWrapper) request).getContentAsByteArray();
			if (content.length > 0) {
				body = getContent(content, request.getContentType(), request.getCharacterEncoding(), "Request Body");
			}
		}

		ReqResponseLog rrLog = new ReqResponseLog();
		rrLog.setUrl(requestUrl);
		rrLog.setMethod(request.getMethod());
		rrLog.setUser(user);
		rrLog.setQueryParams(convertMapToString(queryParams));
		rrLog.setRequestHeader(convertMapToString(headersMap));
		rrLog.setRequestBody(body);
		rrLog.setType(ReqResponseType.INBOUND);
		rrLog.setApiName(getApiName(requestUrl, request.getMethod()));
        rrLog.setApiVersion(getApiVersion(requestUrl));
		rrLog.setAccessToken(extractToken(headersMap));

		String vin = extractVin(requestUrl, queryParams, headersMap, user, body);
		rrLog.setVin(StringUtils.substring(vin, 0, 29));
		return rrLog;
	}

	public void getResponse(HttpServletResponse response, ReqResponseLog reqResponseLog) {
		int status = response.getStatus();
		Map<String, String> headersMap = new HashMap<>();
		response.getHeaderNames().forEach(headerName ->
						response.getHeaders(headerName)
								.forEach(headerValue -> headersMap.put(headerName, headerValue))
										 );
		if (StringUtils.isNotBlank(response.getContentType())) {
            headersMap.put("Content-Type", response.getContentType());
        }

		String body = "";
		if (response instanceof ContentCachingResponseWrapper) {
			byte[] content = ((ContentCachingResponseWrapper) response).getContentAsByteArray();
			if (content.length > 0) {
				body = getContent(content, response.getContentType(), response.getCharacterEncoding(), "Response Body");
			}
		}

		reqResponseLog.setStatus(status);
		reqResponseLog.setResponseHeader(convertMapToString(headersMap));
		reqResponseLog.setResponseBody(body);
		reqResponseLog.setTraceId(this.tracer.getTraceId());
		reqResponseLog.setServiceName(this.serviceName);
	}

	private Map<String, String> getRequestHeader(HttpServletRequest request) {
		Map<String, String> headersMap = new HashMap<>();
		Collections.list(request.getHeaderNames()).forEach(headerName -> Collections.list(request.getHeaders(headerName)).forEach(headerValue -> headersMap.put(headerName, headerValue)));
		return headersMap;
	}

	private String getContent(byte[] content, String contentType, String contentEncoding, String prefix) {
		String contentString;
		MediaType mediaType = MediaType.valueOf(contentType);
		boolean visible = VISIBLE_TYPES.stream().anyMatch(visibleType -> visibleType.includes(mediaType));
		if (visible) {
			try {
				contentString = new String(content, contentEncoding);
			} catch (UnsupportedEncodingException e) {
				log.info("{} [{} bytes content]", prefix, content.length);
				contentString = "Failed to read from request; Only " + content.length + " bytes content available]";
			}
		} else {
			log.info("{} [{} bytes content]", prefix, content.length);
			contentString = "Invisible content type[" + contentType + "]; Only " + content.length + " bytes content available]";
		}
		return contentString;
	}

	private String convertMapToString(Map<String, String> headersMap) {
		String headerMapString = null;

		if (CollectionUtils.isEmpty(headersMap)) {
			return null;
		}

		try {
			headerMapString = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(headersMap);
		} catch (JsonProcessingException e) {
			log.error("Header HashMap to Json String conversion error", e);
		}

		return headerMapString;
	}

	private String extractToken(Map<String, String> requestHeaders) {
		// Extract token from request headers
		if (!CollectionUtils.isEmpty(requestHeaders)) {
			String token = requestHeaders.get("authorization") == null ? requestHeaders.get("Authorization") : requestHeaders.get("authorization");
			if (StringUtils.isNotBlank(token)) {
				String tempToken = StringUtils.substringAfter( token, "bearer ");
				token = StringUtils.isBlank(tempToken) ? StringUtils.substringAfter( token, "Bearer ") : tempToken;
				return token;
			}
		}
		return null;
	}

	private String getCaseInsensitiveVinFromMap(Map<String, ?> map) {
		return (String) (map.get("vin") == null ? map.get("VIN") : map.get("vin"));
	}

	private String getApiName(String url, String method) {
		String apiName = null;

		if (!CollectionUtils.isEmpty(apiNames.getApi())) {
			apiName = apiNames.getApi().entrySet().parallelStream()
					.filter(e -> url.matches(e.getValue()))
					.map(Map.Entry::getKey)
					.filter(Objects::nonNull)
					.filter(name -> StringUtils.startsWithIgnoreCase(name, method))
					.findFirst()
					.orElse(null);
		}

		return apiName;
	}

	private String getApiVersion(String url) {
		String apiVersion = null;
		if (url.matches(API_VERSION_PATTERN)) {
            apiVersion = StringUtils.substringBetween(url,"/services/api/", "/");
            apiVersion = StringUtils.lowerCase(apiVersion);
        }
		return apiVersion;
	}

}
