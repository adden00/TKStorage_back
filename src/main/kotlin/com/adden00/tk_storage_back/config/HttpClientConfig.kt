package com.adden00.tk_storage_back.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class HttpClientConfig {
    @Bean
    fun appScriptRestClient(): RestClient {
        val httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build()
        // без read timeout плановый импорт может повиснуть навсегда на подвисшем Apps Script
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofSeconds(60))
        }
        return RestClient.builder()
            .requestFactory(requestFactory)
            .build()
    }
}
