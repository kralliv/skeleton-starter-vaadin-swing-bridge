package de.bewotec.launcher

import com.fasterxml.jackson.annotation.JsonProperty
import de.bewotec.distribution.DistributionIdentifier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class LoginService(
    private val client: HttpClient,
    @Value($$"${bewotec.login.url}")
    private val launcherService: String,
) {

    fun authenticate(
        username: String,
        password: String
    ): Authentication = runBlocking {
        class OAuth2Grant(
            @JsonProperty("access_token")
            val accessToken: String,
        )

        val grant = client.post("$launcherService/oauth2/token") {
            // public secret, compatibility reasons
            header(HttpHeaders.Authorization, "Basic bXlqYWNrLWxvZ2luLXNlcnZlcjpPb2hvb3o5dg==")
            accept(ContentType.Application.Json)

            setBody(
                FormDataContent(
                    Parameters.build {
                        append("grant_type", "password")
                        append("username", username)
                        append("password", password)
                    }
                )
            )
        }.body<OAuth2Grant>()

        Authentication(username, grant.accessToken)
    }

    fun requestLaunchAuthorization(
        authentication: Authentication,
        environment: String,
        agency: Long
    ): LaunchAuthorization = runBlocking {
        val ticketData = client.get("$launcherService/target/ticket") {
            authorization(authentication)
            parameter("environment", environment)
        }.body<TicketData>()

        val tokenData = client.get("$launcherService/target/token") {
            authorization(authentication)
            parameter("user", authentication.username)
            parameter("agency", agency.toString())
            parameter("ticket", ticketData.ticket)
        }.body<TokenData>()

        LaunchAuthorization(
            DistributionIdentifier.fromString(ticketData.distribution),
            ticketData.url,
            tokenData.token,
        )
    }
}

private fun HttpMessageBuilder.authorization(authentication: Authentication) {
    header(HttpHeaders.Authorization, "Bearer ${authentication.accessToken}")
}

class Authentication(
    val username: String,
    val accessToken: String,
)

class TicketData(
    val distribution: String,
    val url: String,
    val ticket: String,
)

class TokenData(
    val token: String,
)

class LaunchAuthorization(
    val distribution: DistributionIdentifier,
    val url: String,
    val token: String,
)
