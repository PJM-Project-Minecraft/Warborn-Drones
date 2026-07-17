package ru.liko.wrbdrones.client.integration;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;
import ru.liko.wrbdrones.Wrbdrones;

import java.util.function.Consumer;

/**
 * Плагин JourneyMap: прячет миникарту, пока игрок управляет дроном (FPV-картинке она мешает).
 *
 * <p>Класс загружается ТОЛЬКО когда JourneyMap установлен — его обнаруживает сканер
 * {@code @JourneyMapPlugin}. Наш собственный код на него не ссылается (обращается только
 * к JM-независимому {@link MinimapControl}), поэтому без JourneyMap класс не грузится и
 * зависимость остаётся мягкой.</p>
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public final class WrbJourneyMapPlugin implements IClientPlugin {

    @Override
    public void initialize(final IClientAPI clientAPI) {
        // Запоминаем прежнее состояние миникарты и восстанавливаем его на выходе из управления,
        // чтобы не «включить» миникарту тому, кто её выключил вручную.
        MinimapControl.registerToggle(new Consumer<>() {
            private Boolean previous;

            @Override
            public void accept(final Boolean visible) {
                try {
                    if (!visible) {
                        if (previous == null) {
                            previous = clientAPI.minimapEnabled();
                        }
                        clientAPI.toggleMinimap(false);
                    } else {
                        clientAPI.toggleMinimap(previous == null || previous);
                        previous = null;
                    }
                } catch (Throwable ignored) {
                    // JourneyMap другой версии — молча игнорируем, управление дроном важнее.
                }
            }
        });
    }

    @Override
    public String getModId() {
        return Wrbdrones.MODID;
    }
}
