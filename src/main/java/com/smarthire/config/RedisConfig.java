// ── SmartHire · src/main/java/com/smarthire/config/RedisConfig.java ──
package com.smarthire.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.resource.DefaultClientResources;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import java.time.Duration;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.timeout:5000ms}")
    private Duration timeout;

    /** Lettuce connection pool used by Spring Data Redis (rate limiting, session cache). */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        GenericObjectPoolConfig<Object> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setMaxWait(Duration.ofMillis(2000));
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);

        LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
            .poolConfig(poolConfig)
            .commandTimeout(timeout)
            .clientOptions(ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build())
            .build();

        var connInfo = org.springframework.data.redis.connection.RedisStandaloneConfiguration
            .builder()
            .hostName(host)
            .port(port)
            .build();

        if (password != null && !password.isBlank()) {
            connInfo.setPassword(password);
        }

        return new LettuceConnectionFactory(connInfo, clientConfig);
    }

    /** StringRedisTemplate for lightweight key-value ops (views counter, email tokens). */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * Lettuce RedisClient used by Bucket4j distributed rate limiter.
     * Separate from the Spring Data connection pool so rate limiting traffic
     * does not starve normal application queries.
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient bucket4jRedisClient() {
        RedisURI.Builder uriBuilder = RedisURI.builder()
            .withHost(host)
            .withPort(port)
            .withTimeout(timeout);
        if (password != null && !password.isBlank()) {
            uriBuilder.withPassword(password.toCharArray());
        }
        RedisClient client = RedisClient.create(DefaultClientResources.create(), uriBuilder.build());
        client.setDefaultTimeout(timeout);
        return client;
    }
}
