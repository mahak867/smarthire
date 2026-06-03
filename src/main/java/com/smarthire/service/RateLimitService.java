// ── SmartHire · src/main/java/com/smarthire/service/RateLimitService.java ──
package com.smarthire.service;

import com.smarthire.exception.RateLimitException;
import io.github.bucket4j.*;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class RateLimitService {

    @Value("${smarthire.rate-limit.login-max:5}")
    private int loginMax;

    @Value("${smarthire.rate-limit.login-window-minutes:15}")
    private int loginWindowMinutes;

    @Value("${smarthire.rate-limit.register-max:3}")
    private int registerMax;

    @Value("${smarthire.rate-limit.register-window-hours:1}")
    private int registerWindowHours;

    @Value("${smarthire.rate-limit.global-max:100}")
    private int globalMax;

    @Value("${smarthire.rate-limit.global-window-minutes:1}")
    private int globalWindowMinutes;

    private final RedisClient redisClient;
    private ProxyManager<String> proxyManager;

    public RateLimitService(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @PostConstruct
    void init() {
        StatefulRedisConnection<String, byte[]> connection =
            redisClient.connect(io.lettuce.core.codec.ByteArrayCodec.INSTANCE);
        proxyManager = LettuceBasedProxyManager.builderFor(connection)
            .withExpirationAfterWriteStrategy(
                io.github.bucket4j.distributed.expiration.ExpirationAfterWriteStrategy
                    .basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(2)))
            .build();
    }

    public void checkLoginLimit(String ip) {
        check("rl:login:" + ip,
            BandwidthDefinition.simple(loginMax, Duration.ofMinutes(loginWindowMinutes)),
            "Too many login attempts from your IP. Please wait %d minutes before trying again."
                .formatted(loginWindowMinutes),
            Duration.ofMinutes(loginWindowMinutes).toSeconds());
    }

    public void checkRegisterLimit(String ip) {
        check("rl:register:" + ip,
            BandwidthDefinition.simple(registerMax, Duration.ofHours(registerWindowHours)),
            "Too many registration attempts. Please wait %d hour(s) before trying again."
                .formatted(registerWindowHours),
            Duration.ofHours(registerWindowHours).toSeconds());
    }

    public void checkGlobalLimit(String userId) {
        check("rl:global:" + userId,
            BandwidthDefinition.simple(globalMax, Duration.ofMinutes(globalWindowMinutes)),
            "You have exceeded the API rate limit (%d requests/%d min). Please slow down."
                .formatted(globalMax, globalWindowMinutes),
            Duration.ofMinutes(globalWindowMinutes).toSeconds());
    }

    private void check(String key, BandwidthDefinition bandwidth, String message, long retryAfter) {
        try {
            BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(bandwidth.toBandwidth())
                .build();
            Bucket bucket = proxyManager.builder().build(key, () -> config);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                long retrySeconds = Math.max(retryAfter, probe.getNanosToWaitForRefill() / 1_000_000_000L);
                throw new RateLimitException(message, retrySeconds);
            }
        } catch (RateLimitException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Rate limit check failed for key {}: {} — allowing request", key, e.getMessage());
            // Fail open — don't block users if Redis is down
        }
    }

    private record BandwidthDefinition(long capacity, Duration refillDuration) {
        static BandwidthDefinition simple(long capacity, Duration window) {
            return new BandwidthDefinition(capacity, window);
        }
        Bandwidth toBandwidth() {
            return Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillDuration)
                .build();
        }
    }
}
