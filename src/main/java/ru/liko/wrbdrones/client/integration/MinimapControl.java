package ru.liko.wrbdrones.client.integration;

import java.util.function.Consumer;

/**
 * Прослойка между управлением дроном и сторонними миникартами (JourneyMap), НЕ зависящая
 * от их классов. Если JourneyMap установлен, его плагин ({@code WrbJourneyMapPlugin})
 * регистрирует сюда переключатель; если мода нет — {@link #toggle} остаётся {@code null}
 * и всё превращается в no-op. Так интеграция остаётся мягкой (soft dependency).
 *
 * <p>{@code toggle.accept(visible)} — {@code visible=false}, пока игрок управляет дроном:
 * миникарта поверх FPV-картинки мешает.</p>
 */
public final class MinimapControl {

    private static volatile Consumer<Boolean> toggle;
    private static volatile boolean controlling;

    private MinimapControl() {
    }

    /** Вызывается плагином JourneyMap при инициализации. {@code visible} — показать миникарту. */
    public static void registerToggle(Consumer<Boolean> t) {
        toggle = t;
        if (t != null) {
            t.accept(!controlling);
        }
    }

    /** Драйвится клиентским тиком: {@code true}, пока локальный игрок управляет дроном. */
    public static void setControlling(boolean c) {
        if (c == controlling) {
            return;
        }
        controlling = c;
        Consumer<Boolean> t = toggle;
        if (t != null) {
            t.accept(!c);
        }
    }
}
