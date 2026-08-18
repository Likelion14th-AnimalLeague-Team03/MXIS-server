package com.mxis.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** /images/** 요청을 컨테이너에 마운트된 로컬 폴더로 서빙한다 (테스트/데모용 임시 이미지 호스팅). */
@Configuration
public class StaticImageConfig implements WebMvcConfigurer {

    @Value("${mxis.static-images.dir:/data/images}")
    private String imagesDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + imagesDir + "/");
    }
}
