package com._team._team.airecording.feignclient;

import feign.codec.Encoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import feign.form.spring.SpringFormEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectFactory;


public class FeignFormConfig {
    @Bean
    public Encoder feignFormEncoder(ObjectFactory<HttpMessageConverters> converters) {
        return new SpringFormEncoder(new SpringEncoder(converters));
    }
}
