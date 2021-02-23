package me.botsko.prism.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Created for Prism.
 *
 * @author Narimm on 23/02/2021
 * @since
 */
class ConfigHandlerTest {

    @Test
    void loadConfiguration() {
        try {
            Path path = Files.createTempFile(null, ".yml");
            ConfigHandler configHandler = new ConfigHandler();
            configHandler.loadConfiguration(path);
            PrismConfig config = configHandler.getConfig();
            Assertions.assertFalse(config.debug);
            Assertions.assertFalse(config.alertConfig.illegalCommands.enabled);
            config.debug = true;
            Assertions.assertTrue(configHandler.getConfig().debug);
            configHandler.saveConfiguration(path);
            File file = path.toFile();
            Assertions.assertTrue(file.exists());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}