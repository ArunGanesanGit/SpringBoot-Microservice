package com.arun.general.logging;

import brave.Tracer;
import com.arun.general.config.AbstractLoggingConfig;
import com.arun.general.messaging.channel.LoggingOutputChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * @author
 */
@Configuration
@Profile("cloud")
@EnableConfigurationProperties(ApiNames.class)
@ConditionalOnProperty(name = "arun.logging.mode", havingValue = "db")
@ConditionalOnClass (Tracer.class)
@EnableBinding ({ LoggingOutputChannel.class })
@Slf4j
public class DBLoggingConfig extends AbstractLoggingConfig {

    @Value ("${spring.application.name}")
    private String serviceName;

    private Tracer tracer;
    private ApiNames apiNames;
    private LoggingOutputChannel loggingOutputChannel;

    @Autowired
    public DBLoggingConfig(Tracer tracer, ApiNames apiNames, LoggingOutputChannel loggingOutputChannel) {
        this.tracer = tracer;
        this.apiNames = apiNames;
        this.loggingOutputChannel = loggingOutputChannel;
        log.info("Loading cloud logging config ...");
    }

    @Override
    @Bean
    protected LoggingHelper loggingHelper() {
        return new LoggingHelper(this.serviceName,
                new Sleuth1TraceProvider(this.tracer),
                this.apiNames,
                this.loggingOutputChannel);
    }
}
