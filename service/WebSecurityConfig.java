package com.arun.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;

/**
 * @author
 * To configure access control for all the web requests.
 */
@Configuration
@EnableGlobalMethodSecurity (prePostEnabled = true)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

	@Bean
	public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
		StrictHttpFirewall firewall = new StrictHttpFirewall();
		firewall.setAllowUrlEncodedSlash(true);
		firewall.setAllowSemicolon(true);
		firewall.setAllowUrlEncodedPercent(true);
		firewall.setAllowBackSlash(true);
		firewall.setAllowUrlEncodedPeriod(true);
		return firewall;
	}

	@Override
	public void configure(WebSecurity web) throws Exception {
		web.httpFirewall(allowUrlEncodedSlashHttpFirewall()).ignoring().antMatchers("/v2/api-docs",
				"/swagger-resources/**", "/configuration/security",
				"/swagger-ui.html", "/webjars/**",
				"/cloudfoundryapplication/**", "/");
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		// @formatter:off

		http
			.authorizeRequests()
			.antMatchers("/service/**").authenticated() // Could limit roles by using .hasAnyRole("USER", "ADMIN")
			.anyRequest().permitAll()
		.and()
			.csrf().disable();

        // @formatter:on
	}
}
