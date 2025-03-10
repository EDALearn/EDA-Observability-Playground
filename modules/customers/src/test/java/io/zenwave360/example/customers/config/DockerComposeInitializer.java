package io.zenwave360.example.customers.config;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class DockerComposeInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private Logger log = LoggerFactory.getLogger(getClass());

    /** Use this annotation to activate TestContainers in your test. */
    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    @org.springframework.test.context.ContextConfiguration(initializers = DockerComposeInitializer.class)
    public @interface EnableDockerCompose {

    }

    // The number of services in the docker-compose.yml file
    private static final int NUMBER_OF_SERVICES = 1;

    static String HOST = DockerClientFactory.instance().dockerHostIpAddress();
    static DockerComposeContainer container = new DockerComposeContainer(new File("../docker-compose.yml"))
        .withEnv("HOST", HOST)
        .withExposedService("mongodb", 27017, Wait.forListeningPort());
    static boolean isContainerRunning = false;

    @SneakyThrows
    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        if (isDockerComposeRunningAllServices(NUMBER_OF_SERVICES)) {
            log.info("Docker Compose Containers are running from local docker-compose. Skipping TestContainers...");
        }
        else {
            log.info(
                    "Docker Compose Containers are not running from local docker-compose. Starting from TestContainers...");
            if (isContainerRunning) {
                log.info("Docker Compose Containers are already running from TestContainers. Skipping...");
            }
            else {
                log.info("Starting Docker Compose Containers from TestContainers...");
                container.start();
                isContainerRunning = true;

                int mongodbPort = container.getServicePort("mongodb", 27017);
                log.info("Docker Compose Containers are running from TestContainers. Mongodb: {}",
                        HOST + ":" + mongodbPort);

                log.info("Container Ports Status: Mongodb: {}", isPortOpen(HOST, mongodbPort));

                TestPropertyValues
                    .of(String.format("MONGODB_URI=mongodb://%s:%s/REVIEW?replicaSet=rs0", HOST, mongodbPort))
                    .applyTo(ctx.getEnvironment());
            }
        }
    }

    private boolean isDockerComposeRunningAllServices(int numberOfServices) {
        return Stream.of("docker-compose", "docker-compose.exe").anyMatch(cmd -> {
            try {
                return readProcessOutputStream(cmd, "-f", "../docker-compose.yml", "ps")
                    .size() == (numberOfServices + 1);
            }
            catch (IOException | InterruptedException e) {
                return false;
            }
        });
    }

    private List<String> readProcessOutputStream(String... command) throws IOException, InterruptedException {
        var process = new ProcessBuilder(command).start();
        var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
        var line = "";
        var output = new ArrayList<String>();
        while ((line = reader.readLine()) != null) {
            output.add(line);
        }
        process.waitFor();
        return output;
    }

    boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket(host, port)) {
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

}
