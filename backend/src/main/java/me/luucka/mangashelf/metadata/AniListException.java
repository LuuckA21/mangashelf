package me.luucka.mangashelf.metadata;

import me.luucka.mangashelf.common.ApiException;
import org.springframework.http.HttpStatus;

/** Safe error code and waiting time for a user-initiated retry. */
public class AniListException extends ApiException {
    private final int retryAfterSeconds;

    public AniListException(String code, int retryAfterSeconds) {
        super("anilist_rate_limited".equals(code)
                ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY, code);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
