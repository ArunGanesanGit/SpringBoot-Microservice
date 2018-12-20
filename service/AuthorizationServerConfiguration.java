package com.arun.security.config;

import com.arun.security.aes.AESAccessTokenConverter;
import com.arun.security.aes.AESCryptoHelper;
import com.arun.security.aes.AESTokenStore;
import com.arun.security.filter.CustomAuthFilter;
import com.arun.security.manager.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.CompositeTokenGranter;
import org.springframework.security.oauth2.provider.TokenGranter;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;

import java.util.ArrayList;
import java.util.List;

/**
 * @author
 * Configure the security of the Authorization Server
 * Enabling Authorization Server will expose the framework endpoints like Token, Authorization, etc.
 */
@EnableAuthorizationServer
@Configuration
public class AuthorizationServerConfiguration extends AuthorizationServerConfigurerAdapter {

    private ConsumerVerifier consumerVerifier;
    private AuthProperties authProperties;
    private SuccessFailureHandler successFailureHandler;
    private ClientDetailsService clientDetailsService;

    @Autowired
    public AuthorizationServerConfiguration(ConsumerVerifier consumerVerifier,
                                            AuthProperties authProperties,
                                            SuccessFailureHandler successFailureHandler,
                                            CustomClientDetailsService clientDetailsService) {
        this.consumerVerifier = consumerVerifier;
        this.authProperties = authProperties;
        this.successFailureHandler = successFailureHandler;
        this.clientDetailsService = clientDetailsService;
    }

    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        clients.withClientDetails(clientDetailsService);
    }

    /**
     * Sets Custom Grant Type and endpoints mapping to support SalesForce.
     *
     * @param endpoints To configure enhanced functionality of the Authorization Server endpoints
     * @throws Exception Any General Exception
     */
    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {

        List<TokenGranter> tokenGranters = new ArrayList<>();
        tokenGranters.add(new CustomTokenGranter(tokenServices(), clientDetailsService, endpoints.getOAuth2RequestFactory()));
        endpoints.tokenStore(tokenStore())
                .accessTokenConverter(accessTokenConverter())
                .tokenGranter(new CompositeTokenGranter(tokenGranters))
                .pathMapping("/oauth/token", "/services/oauth2/token")
                .pathMapping("/oauth/authorize", "/services/oauth2/authorize")
                .pathMapping("/oauth/confirm_access", "/services/oauth2/confirm_access")
                .pathMapping("/oauth/token_key", "/services/oauth2/token_key")
                .pathMapping("/oauth/check_token", "/services/oauth2/check_token");
    }

    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
        security.addTokenEndpointAuthenticationFilter(customAuthFilter());
    }

    @Bean
    @Primary
    public DefaultTokenServices tokenServices() {
        DefaultTokenServices defaultTokenServices = new DefaultTokenServices();
        defaultTokenServices.setTokenStore(tokenStore());
        defaultTokenServices.setSupportRefreshToken(false);
        defaultTokenServices.setTokenEnhancer(accessTokenConverter());
        defaultTokenServices.setAccessTokenValiditySeconds(uthProperties.getExpiryInMins() * 60);
        return defaultTokenServices;
    }

    @Bean
    public CustomAuthFilter customAuthFilter() {
        AuthenticationManager authenticationManager = new CustomAuthenticationManager(clientDetailsService);

        CustomAuthFilter customAuthFilter = new CustomAuthFilter();
        customAuthFilter.setAuthenticationManager(authenticationManager);
        customAuthFilter.setConsumerVerifier(consumerVerifier);
        customAuthFilter.setAuthenticationSuccessHandler(successFailureHandler);
        customAuthFilter.setAuthenticationFailureHandler(successFailureHandler);
        return customAuthFilter;
    }

    // AES token - Configuration
    @Bean
    public TokenStore tokenStore() {
        return new AESTokenStore(accessTokenConverter());
    }

    @Bean
    public AESAccessTokenConverter accessTokenConverter() {
        return new AESAccessTokenConverter(clientDetailsService, cryptoHelper());
    }

    @Bean
    public AESCryptoHelper cryptoHelper() {
        return new AESCryptoHelper(authProperties.getPassword());
    }

}