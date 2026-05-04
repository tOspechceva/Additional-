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
 * Два принципа работы с кешем:
 * <ul>
 *     <li><b>Ленивая подгрузка (cache-aside)</b> — {@link #getUserById(long)}: при промахе один запрос {@code GET /api/users/{id}}.</li>
 *     <li><b>Прогрев (cache warming)</b> — {@link #preheatFromBulkUserSnapshot(List)}: после одного {@code GET /api/users}
 *         кеш заполняется из <em>уже прочитанного</em> списка, без дополнительных походов по id.</li>
 * </ul>
 * Хранилище — {@link HashMap}; доступ синхронизирован одним замком. Периодически в лог пишется размер кеша и счётчики попаданий/промахов.
 */
@Service
public class UserCacheService {

    private static final Logger log = LoggerFactory.getLogger(UserCacheService.class);

    private final CrudApiClient crudApiClient;
    private final CrudResponseMapper crudResponseMapper;

    // Основное хранилище кеша: userId -> User
    private final HashMap<Long, User> cache = new HashMap<>();
    
    // Объект-монитор для синхронизации доступа к кешу и статистике
    private final Object mutex = new Object();

    // Счётчики для метрик: количество успешных обращений к кешу
    private long hits;
    // Счётчики для метрик: количество обращений, потребовавших загрузки из CRUD
    private long misses;

    // Флаг включения логирования статистики (управляется через application.properties)
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
     * Реализует паттерн Cache-Aside (Lazy Loading).
     */
    public User getUserById(final long userId) {
        synchronized (mutex) {
            // 1. Пробуем найти пользователя в кеше
            final User cached = cache.get(userId);
            if (cached != null) {
                hits++; // Увеличиваем счётчик попаданий
                return cached;
            }
            
            // 2. Cache miss: загружаем данные из внешнего CRUD-сервиса
            final CrudUserResponse body = crudApiClient.getUserByIdBody(userId);
            if (body == null) {
                throw new IllegalStateException("CRUD /api/users/{id} вернул пустое тело (null) для userId=" + userId);
            }
            
            misses++; // Увеличиваем счётчик промахов
            // 3. Маппим ответ API в доменную модель и сохраняем в кеш
            final User loaded = crudResponseMapper.toUser(body);
            cache.put(userId, loaded);
            return loaded;
        }
    }

    /**
     * Прогрев кеша (второй принцип LAB10): после массового ответа CRUD {@code GET /api/users} заменяем содержимое кеша
     * снимком этого списка. Дополнительных S2S-вызовов по одному {@code userId} нет — данные уже в памяти приложения.
     * <p>
     * Смысл {@code cache.clear()}: кеш по пользователям согласуется с актуальным списком (удалённые из БД id не остаются «вечно» в map).
     */
    public void preheatFromBulkUserSnapshot(final List<User> usersFromListEndpoint) {
        Objects.requireNonNull(usersFromListEndpoint, "usersFromListEndpoint не может быть null");
        synchronized (mutex) {
            cache.clear();
            for (User u : usersFromListEndpoint) {
                cache.put(u.id(), u);
            }
        }
    }

    /**
     * Возвращает текущее количество записей в кеше (потокобезопасно).
     */
    public int size() {
        synchronized (mutex) {
            return cache.size();
        }
    }

    /**
     * Периодическая задача: логирование статистики кеша.
     * Частота выполнения настраивается через {@code user-cache.stats-log-ms} (по умолчанию 60000 мс).
     */
    @Scheduled(fixedDelayString = "${user-cache.stats-log-ms:60000}")
    public void logCacheStatistics() {
        // Быстрая проверка флага и уровня логирования без захвата блокировки
        if (!statsLogEnabled || !log.isInfoEnabled()) {
            return;
        }
        
        // Короткая синхронизация только для атомарного чтения счётчиков
        final int sz;
        final long h;
        final long m;
        synchronized (mutex) {
            sz = cache.size();
            h = hits;
            m = misses;
        }
        
        // Логируем метрики: размер кеша, попадания, промахи
        log.info("[user-cache] size={} hits={} misses={}", sz, h, m);
    }
}