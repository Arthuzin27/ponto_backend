package com.relatar.sistema.ponto.common.health;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

import com.relatar.sistema.ponto.config.AppProperties;

/**
 * Monitora a sincronizacao do relogio do servidor, que e a fonte oficial do horario das marcacoes.
 * Exposto em {@code /actuator/health} como {@code clockSync}:
 * <ul>
 *   <li>UP: diferenca para o NTP dentro do limite configurado;</li>
 *   <li>DOWN: diferenca acima do limite (relogio dessincronizado);</li>
 *   <li>UNKNOWN: servidor NTP inacessivel (nao derruba a aplicacao, apenas sinaliza).</li>
 * </ul>
 * O resultado fica em cache para nao consultar o servidor NTP a cada verificacao de saude.
 */
@Component
@ConditionalOnProperty(prefix = "app.clock.ntp", name = "enabled", havingValue = "true")
public class ClockSyncHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(ClockSyncHealthIndicator.class);

    private final SntpClient sntpClient;
    private final Clock clock;
    private final AppProperties.Ntp settings;

    private volatile CachedHealth cached;

    @Autowired
    public ClockSyncHealthIndicator(Clock clock, AppProperties properties) {
        this(new SntpClient(clock), clock, properties);
    }

    ClockSyncHealthIndicator(SntpClient sntpClient, Clock clock, AppProperties properties) {
        this.sntpClient = sntpClient;
        this.clock = clock;
        this.settings = properties.clock().ntp();
    }

    @Override
    public Health health() {
        Instant now = clock.instant();
        CachedHealth current = cached;
        if (current != null && current.checkedAt().plus(settings.cacheTtl()).isAfter(now)) {
            return current.health();
        }
        Health health = check(now);
        cached = new CachedHealth(health, now);
        return health;
    }

    private Health check(Instant now) {
        try {
            Duration offset = sntpClient.queryOffset(settings.server(), settings.timeout());
            Health.Builder builder = offset.abs().compareTo(settings.maxOffset()) <= 0 ? Health.up() : Health.down();
            Health health = builder
                    .withDetail("server", settings.server())
                    .withDetail("offsetMillis", offset.toMillis())
                    .withDetail("maxOffsetMillis", settings.maxOffset().toMillis())
                    .withDetail("checkedAt", now.toString())
                    .build();
            if (!Status.UP.equals(health.getStatus())) {
                log.warn("Relogio do servidor fora de sincronia: offset={}ms limite={}ms servidor={}",
                        offset.toMillis(), settings.maxOffset().toMillis(), settings.server());
            }
            return health;
        } catch (Exception ex) {
            log.warn("Nao foi possivel verificar a sincronizacao do relogio com {}: {}", settings.server(),
                    ex.getMessage());
            return Health.unknown()
                    .withDetail("server", settings.server())
                    .withDetail("error", "Servidor NTP inacessível ou resposta inválida")
                    .withDetail("checkedAt", now.toString())
                    .build();
        }
    }

    private record CachedHealth(Health health, Instant checkedAt) {
    }
}
