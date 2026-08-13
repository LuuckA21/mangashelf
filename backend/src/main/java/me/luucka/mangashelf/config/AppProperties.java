package me.luucka.mangashelf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings bound from {@code app.*}.
 *
 * @param registrationEnabled whether new accounts may be created; turning
 *                            this off after the intended users have signed
 *                            up is the simplest way to keep a self-hosted
 *                            instance closed
 * @param coversDir           filesystem path where downloaded covers are cached
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(boolean registrationEnabled, String coversDir) {
}
