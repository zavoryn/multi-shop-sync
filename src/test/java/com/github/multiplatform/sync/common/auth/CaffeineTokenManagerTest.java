package com.github.multiplatform.sync.common.auth;

import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.exception.ChannelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CaffeineTokenManagerTest {

    private AtomicInteger fetchCount;
    private CountingRefreshStrategy strategy;

    @BeforeEach
    void setUp() {
        fetchCount = new AtomicInteger(0);
        strategy = new CountingRefreshStrategy(fetchCount, ChannelEnum.DOUYIN,
                () -> TokenInfo.ofTtl("tok-" + fetchCount.get(), Duration.ofMinutes(30)));
    }

    @Test
    void cacheHitDoesNotRefetch() {
        CaffeineTokenManager m = new CaffeineTokenManager(Collections.singletonList(strategy));
        String t1 = m.getToken(ChannelEnum.DOUYIN);
        String t2 = m.getToken(ChannelEnum.DOUYIN);
        String t3 = m.getToken(ChannelEnum.DOUYIN);
        assertEquals(t1, t2);
        assertEquals(t2, t3);
        assertEquals(1, fetchCount.get(), "no additional fetch on cache hit");
    }

    @Test
    void forceRefreshSkipsCache() {
        CaffeineTokenManager m = new CaffeineTokenManager(Collections.singletonList(strategy));
        m.getToken(ChannelEnum.DOUYIN);
        m.refresh(ChannelEnum.DOUYIN);
        m.refresh(ChannelEnum.DOUYIN);
        assertEquals(3, fetchCount.get());
    }

    @Test
    void localChannelReturnsEmptyNoFetch() {
        CaffeineTokenManager m = new CaffeineTokenManager(Collections.singletonList(strategy));
        assertEquals("", m.getToken(ChannelEnum.LOCAL));
        assertEquals("", m.refresh(ChannelEnum.LOCAL));
        assertEquals(0, fetchCount.get());
    }

    @Test
    void expiredTokenTriggersRefresh() {
        CountingRefreshStrategy expiring = new CountingRefreshStrategy(fetchCount, ChannelEnum.DOUYIN,
                () -> new TokenInfo("expired-" + fetchCount.get(), Instant.now().minusSeconds(1)));
        CaffeineTokenManager m = new CaffeineTokenManager(Collections.singletonList(expiring));
        m.getToken(ChannelEnum.DOUYIN);
        m.getToken(ChannelEnum.DOUYIN);
        assertEquals(2, fetchCount.get(), "expired token should refresh every call");
    }

    @Test
    void unregisteredChannelThrows() {
        CaffeineTokenManager m = new CaffeineTokenManager(Collections.singletonList(strategy));
        assertThrows(ChannelException.class, () -> m.getToken(ChannelEnum.XIAOHONGSHU));
    }

    @Test
    void hundredThreadsTriggerOnlyOneFetch() throws Exception {
        // Slow fetch to make threads race
        CountingRefreshStrategy slow = new CountingRefreshStrategy(fetchCount, ChannelEnum.DOUYIN, () -> {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return TokenInfo.ofTtl("tok", Duration.ofMinutes(30));
        });
        CaffeineTokenManager m = new CaffeineTokenManager(Collections.singletonList(slow));

        int n = 100;
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        // 必须 >= n，否则 task 排队等不到 worker，主线程在 ready.await(n) 永远等不到 → 死锁
        ExecutorService pool = Executors.newCachedThreadPool();

        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    fire.await();
                    m.getToken(ChannelEnum.DOUYIN);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        fire.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "concurrent tasks must finish within 10s");
        pool.shutdownNow();

        assertEquals(1, fetchCount.get(), "single-flight: 100 concurrent getToken should fetch only once");
    }

    // ============ helper ============
    static class CountingRefreshStrategy implements RefreshStrategy {
        private final AtomicInteger counter;
        private final ChannelEnum channel;
        private final java.util.function.Supplier<TokenInfo> supplier;

        CountingRefreshStrategy(AtomicInteger counter, ChannelEnum channel,
                                java.util.function.Supplier<TokenInfo> supplier) {
            this.counter = counter;
            this.channel = channel;
            this.supplier = supplier;
        }

        @Override public ChannelEnum getChannel() { return channel; }
        @Override public TokenInfo fetch() {
            counter.incrementAndGet();
            return supplier.get();
        }
    }
}
