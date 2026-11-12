package com.teamcrocodile.tripwire.config;

import com.teamcrocodile.tripwire.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Everything agents use needs a login. The public parts are the login call,
        // the customer refund form (/refund/submit) and the static pages.
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/agent/**", "/transaction/**", "/account/**")
                .excludePathPatterns("/agent/login");
    }
}
