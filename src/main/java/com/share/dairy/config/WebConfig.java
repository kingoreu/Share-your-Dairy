package com.share.dairy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

import java.nio.file.Path;

/** /media/** → (app.media.root-dir) 정적 파일 서빙 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.media.root-dir:./generated-images}")
    private String mediaRootDir;

    @Value("${app.media.url-prefix:/media/}")
    private String mediaUrlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Path.of(mediaRootDir).toAbsolutePath() + "/";
        registry.addResourceHandler(mediaUrlPrefix + "**")
                .addResourceLocations(location);
        System.out.println("[WebConfig] media mapped: " + mediaUrlPrefix + " → " + location);
    }
}
