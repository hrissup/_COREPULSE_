package com.corepulse;

import javafx.application.Platform;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OperatingSystem;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PulseEngine {
    private final SystemInfo si = new SystemInfo();

    private final CentralProcessor processor = si.getHardware().getProcessor();

    private final GlobalMemory memory = si.getHardware().getMemory();

    private final OperatingSystem os = si.getOperatingSystem();

    private long[] prevTicks = processor.getSystemCpuLoadTicks();

    private ScheduledExecutorService scheduler;

    private Consumer<HardwareSnapshot> onPulse;

    public void setOnPulse(Consumer<HardwareSnapshot> callback) {
        this.onPulse = callback;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CorePulse-PollThread");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleAtFixedRate(this::poll, 0, 1, TimeUnit.SECONDS);

        System.out.println("[PulseEngine] Started — polling every 1 second.");
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            System.out.println("[PulseEngine] Stopped.");
        }
    }

    private void poll() {

        double cpuUsage = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100.0;
        prevTicks = processor.getSystemCpuLoadTicks();

        double totalRamGb     = memory.getTotal()     / (1024.0 * 1024.0 * 1024.0);
        double availableRamGb = memory.getAvailable() / (1024.0 * 1024.0 * 1024.0);
        double usedRamGb      = totalRamGb - availableRamGb;

        long uptimeSeconds = os.getSystemUptime();

        HardwareSnapshot snapshot = new HardwareSnapshot(cpuUsage, usedRamGb, totalRamGb, uptimeSeconds);

        if (onPulse != null) {
            Platform.runLater(() -> onPulse.accept(snapshot));
        }
    }

    public record HardwareSnapshot(
            double cpuUsage,
            double usedRamGb,
            double totalRamGb,
            long   uptimeSeconds
    ) {}
}
