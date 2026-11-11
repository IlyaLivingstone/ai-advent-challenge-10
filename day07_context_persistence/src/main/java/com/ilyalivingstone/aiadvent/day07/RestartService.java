package com.ilyalivingstone.aiadvent.day07;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class RestartService {

    private final ConfigurableApplicationContext context;
    private final AtomicBoolean restarting = new AtomicBoolean(false);

    public RestartService(ConfigurableApplicationContext context) {
        this.context = context;
    }

    public void restart() {
        if (!restarting.compareAndSet(false, true)) {
            throw new IllegalStateException("Перезапуск уже выполняется");
        }
        Path workingDir = Path.of(System.getProperty("user.dir"));
        Path jar = findJar(workingDir);
        if (jar == null) {
            restarting.set(false);
            throw new IllegalStateException("Не найден JAR. Запустите сервис через java -jar");
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String command = "sleep 2; exec " + quote(java) + " -jar " + quote(jar.toString())
                + " >> restart.log 2>&1";
        try {
            new ProcessBuilder("/bin/sh", "-c", command)
                    .directory(workingDir.toFile())
                    .start();
        } catch (Exception error) {
            restarting.set(false);
            throw new IllegalStateException("Не удалось запустить новый процесс: " + error.getMessage(), error);
        }
        Thread shutdown = new Thread(() -> {
            try {
                Thread.sleep(700);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            context.close();
        }, "agent-restart");
        shutdown.setDaemon(false);
        shutdown.start();
    }

    private Path findJar(Path workingDir) {
        Path target = workingDir.resolve("target");
        if (!Files.isDirectory(target)) return null;
        try (var stream = Files.list(target)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().endsWith(".original"))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                    .orElse(null);
        } catch (Exception error) {
            return null;
        }
    }

    private String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
