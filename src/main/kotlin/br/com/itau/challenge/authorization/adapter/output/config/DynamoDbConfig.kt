package br.com.itau.challenge.authorization.adapter.output.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import java.time.Duration
import java.net.URI

@Configuration
class DynamoDbConfig {

    @Bean
    fun dynamoDbClient(
        @Value($$"${dynamodb.endpoint}") endpoint: String,
        @Value($$"${dynamodb.region}") region: String,
        @Value($$"${dynamodb.use-static-credentials:false}") useStaticCredentials: Boolean,
        @Value($$"${dynamodb.static-access-key:local}") staticAccessKey: String,
        @Value($$"${dynamodb.static-secret-key:local}") staticSecretKey: String,
        @Value($$"${dynamodb.api-call-timeout:5s}") apiCallTimeout: Duration,
        @Value($$"${dynamodb.api-call-attempt-timeout:2s}") apiCallAttemptTimeout: Duration,
    ): DynamoDbClient {
        val credentialsProvider: AwsCredentialsProvider =
            if (useStaticCredentials) {
                StaticCredentialsProvider.create(AwsBasicCredentials.create(staticAccessKey, staticSecretKey))
            } else {
                DefaultCredentialsProvider.builder().build()
            }
        val builder = DynamoDbClient.builder()
        .region(Region.of(region))
        .credentialsProvider(credentialsProvider)
        .overrideConfiguration(
            ClientOverrideConfiguration.builder()
                .apiCallTimeout(apiCallTimeout)
                .apiCallAttemptTimeout(apiCallAttemptTimeout)
                .build(),
        )
        if (endpoint.isNotBlank()) builder.endpointOverride(URI.create(endpoint))
        return builder.build()
    }
}
