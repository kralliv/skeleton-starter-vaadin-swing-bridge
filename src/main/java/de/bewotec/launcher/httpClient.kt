package de.bewotec.launcher

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson3.jackson
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature

@Configuration
class HttpClientConfiguration {

    @Bean
    fun httpClient(): HttpClient {
        return HttpClient(Apache5) {
            expectSuccess = true

            engine {
                connectTimeout = 10_000
                socketTimeout = 10_000
            }

            install(ContentNegotiation) {
                jackson {
                    findAndAddModules()
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
        }
    }
}
