package com.springsentry.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class BharatCodeClientConfig {

	@Bean
	RestClient bharatCodeRestClient(
			@Value("${bharatcode.base-url}") String baseUrl,
			@Value("${bharatcode.connect-timeout:8s}") Duration connectTimeout,
			@Value("${bharatcode.read-timeout:35s}") Duration readTimeout) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}
}
