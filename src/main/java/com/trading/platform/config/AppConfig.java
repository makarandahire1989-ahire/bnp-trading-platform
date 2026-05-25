package com.trading.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Central place for configurable business parameters.
 * Values are bound from {@code application.yml} and can be
 * overridden via environment variables without touching source code.
 */
@Configuration
public class AppConfig {

    /** How long a reservation stays valid before the inventory is released. */
    @Value("${app.reservation.window-minutes:15}")
    private int reservationWindowMinutes;

    public int getReservationWindowMinutes() {
        return reservationWindowMinutes;
    }
}
