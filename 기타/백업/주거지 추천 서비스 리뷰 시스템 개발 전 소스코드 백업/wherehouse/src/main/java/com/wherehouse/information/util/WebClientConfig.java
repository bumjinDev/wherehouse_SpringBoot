package com.wherehouse.information.util;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient 설정 클래스
 *
 * 목적:
 * - 외부 API(카카오맵) 호출을 위한 WebClient 빈 생성
 * - 타임아웃 설정으로 안정성 확보
 *
 * 타임아웃 설정:
 * - 연결 타임아웃: 3초
 * - 응답 타임아웃: 5초
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)  // 연결 타임아웃 3초
                .responseTimeout(Duration.ofSeconds(5));              // 응답 타임아웃 5초

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}