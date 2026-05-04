package digital.zil.hl.additional.service;

import digital.zil.hl.additional.client.CrudApiClient;
import digital.zil.hl.additional.client.CrudResponseMapper;
import digital.zil.hl.additional.client.dto.CrudUserResponse;
import digital.zil.hl.additional.model.User;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * In-memory кеш пользователей (LAB10): снижает число S2S-запросов к CRUD при повторных обращениях к одним и тем же {@code userId}.
 * <p>
 * Хранилище — {@link HashMap}; доступ синхронизирован одним замком. Периодически в лог пишется размер кеша и счётчики попаданий/промахов.
 */
@Service
public class UserCacheService {

    private static final Logger log = LoggerFactory.getLogger(UserCacheService.class);

    private final CrudApiClient crudApiClient;
    private final CrudResponseMapper crudResponseMapper;

    private final HashMap<Long, User> cache = new HashMap<>();
    private final Object mutex = new Object();

    private long hits;
    private long misses;

    private final boolean statsLogEnabled;

    public UserCacheService(
            final CrudApiClient crudApiClient,
            final CrudResponseMapper crudResponseMapper,
            @Value("${user-cache.stats-log-enabled:true}") final boolean statsLogEnabled
    ) {
        this.crudApiClient = Objects.requireNonNull(crudApiClient, "crudApiClient не может быть null");
        this.crudResponseMapper = Objects.requireNonNull(crudResponseMapper, "crudResponseMapper не может быть null");
        this.statsLogEnabled = statsLogEnabled;
    }

    /**
     * Возвращает пользователя из кеша или загружает по {@code GET /api/users/{id}} и кладёт в кеш.
     */
    public User getUserById(final long userId) {
        synchronized (mutex) {
            final User cached = cache.get(userId);
            if (cached != null) {
                hits++;
                return cached;
            }
            final CrudUserResponse body = crudApiClient.getUserByIdBody(userId);
            if (body == null) {
                throw new IllegalStateException("CRUD /api/users/{id} вернул пустое тело (null) для userId=" + userId);
            }
            misses++;
            final User loaded = crudResponseMapper.toUser(body);
            cache.put(userId, loaded);
            return loaded;
        }
    }

    /**
     * Обновляет кеш из полного списка пользователей (после {@code GET /api/users}).
     */
    public void warmAll(final List<User> users) {
        Objects.requireNonNull(users, "users не может быть null");
        synchronized (mutex) {
            for (User u : users) {
                cache.put(u.id(), u);
            }
        }
    }

    public int size() {
        synchronized (mutex) {
            return cache.size();
        }
    }

    @Scheduled(fixedDelayString = "${user-cache.stats-log-ms:60000}")
    public void logCacheStatistics() {
        if (!statsLogEnabled || !log.isInfoEnabled()) {
            return;
        }
        final int sz;
        final long h;
        final long m;
        synchronized (mutex) {
            sz = cache.size();
            h = hits;
            m = misses;
        }
        log.info("[user-cache] size={} hits={} misses={}", sz, h, m);
    }
}
