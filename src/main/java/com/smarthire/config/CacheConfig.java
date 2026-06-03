// ── SmartHire · config/CacheConfig.java ──
package com.smarthire.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Cache TTL strategy:
     * - dashboard-stats: 30s   — refreshed frequently, stale-ok for analytics
     * - job-list:        60s   — job listings change infrequently
     * - job-detail:      120s  — individual job pages, evicted on update
     * - hiring-funnel:   30s   — pipeline numbers, short TTL for accuracy
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        var jsonSerializer = new GenericJackson2JsonRedisSerializer();
        var valueSerializer = RedisSerializationContext.SerializationPair
            .fromSerializer(jsonSerializer);

        var defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(valueSerializer)
            .disableCachingNullValues()
            .entryTtl(Duration.ofSeconds(60));

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            "dashboard-stats", defaultConfig.entryTtl(Duration.ofSeconds(30)),
            "job-list",        defaultConfig.entryTtl(Duration.ofSeconds(60)),
            "job-detail",      defaultConfig.entryTtl(Duration.ofSeconds(120)),
            "hiring-funnel",   defaultConfig.entryTtl(Duration.ofSeconds(30)),
            "pipeline",        defaultConfig.entryTtl(Duration.ofSeconds(30))
        );

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .transactionAware()
            .build();
    }
}
