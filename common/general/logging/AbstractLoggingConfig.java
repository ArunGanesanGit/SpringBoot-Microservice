package com.arun.general.config;

import com.arun.general.helper.Util;
import com.arun.general.logging.LoggingHelper;
import com.arun.general.logging.ReqRespLoggingFilter;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author
 */
public abstract class AbstractLoggingConfig {

    @Value("${spring.application.name}")
    protected String serviceName;

    @Value("${logging.reqRespFilter.includeUrls:#{null}}")
    private Optional<String> urlsOptional;

    @ConditionalOnProperty(value = "arun.logging.req-resp", havingValue = "true")
    @Bean(name = "reqRespLoggingFilter")
    public FilterRegistrationBean reqRespLoggingFilterRegBean() {

        Collection<String> includeUrls = null;
        if (urlsOptional.isPresent() && StringUtils.isNotBlank(urlsOptional.get())) {
            includeUrls = CollectionUtils.arrayToList(urlsOptional.get().split(","));
            includeUrls = Util.asStream(includeUrls)
                    .filter(Objects:: nonNull)
                    .map(String :: trim)
                    .collect(Collectors.toList());
            includeUrls.removeIf(String::isEmpty);
        }

        final FilterRegistrationBean registrationBean = new FilterRegistrationBean();
        registrationBean.setFilter(new ReqRespLoggingFilter(loggingHelper()));
        registrationBean.setOrder(-10000);

        if (!CollectionUtils.isEmpty(includeUrls)) {
            registrationBean.setUrlPatterns(includeUrls);
        }

        return registrationBean;
    }

    abstract protected LoggingHelper loggingHelper();

}
