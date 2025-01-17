package me.botsko.prism.database.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import me.botsko.prism.ApiHandler;
import me.botsko.prism.Prism;
import me.botsko.prism.database.SelectQuery;
import me.botsko.prism.database.sql.SqlPrismDataSource;
import me.botsko.prism.database.sql.SqlSelectQueryBuilder;
import org.bukkit.configuration.ConfigurationSection;

import javax.annotation.Nonnull;
import java.io.File;
import java.sql.*;
import java.util.HashMap;

/**
 * Created for use for the Add5tar MC Minecraft server
 * Created by Narimm on 5/04/2019.
 */
public class MySqlPrismDataSource extends SqlPrismDataSource {

    private static final File propFile = new File(Prism.getInstance().getDataFolder(),
            "hikari.properties");
    private static final HikariConfig dbConfig;
    private static final HashMap<String, String> dbInfo = new HashMap<>();

    static {
        if (propFile.exists()) {
            Prism.log("Configuring Hikari from " + propFile.getName());
            Prism.debug("This file will not save the jdbcURL, username or password - these are loaded"
                    + " by default from the standard prism configuration file.  If you set these "
                    + "explicitly in the properties file the settings in the standard config will be"
                    + "ignored.");
            dbConfig = new HikariConfig(propFile.getPath());
        } else {
            dbConfig = new HikariConfig();
        }
    }

    private final Boolean nonStandardSql;

    /**
     * Create a dataSource.
     *
     * @param section Config
     */
    public MySqlPrismDataSource(ConfigurationSection section) {
        super(section);
        nonStandardSql = this.section.getBoolean("useNonStandardSql", false);
        detectNonStandardSql();
        name = "mysql";
    }

    /**
     * The adds the new requirements to an old configuration file.
     *
     * @param section a {@link ConfigurationSection}
     */
    public static void updateDefaultConfig(ConfigurationSection section) {
        section.addDefault("hostname", "127.0.0.1");
        section.addDefault("username", "prism");
        section.addDefault("password", "prism");
        section.addDefault("databaseName", "prism");
        section.addDefault("prefix", "prism_");
        section.addDefault("port", "3306");
        section.addDefault("useNonStandardSql", true);
        setupDefaultProperties(section);
    }

    private static void setupDefaultProperties(@Nonnull ConfigurationSection section) {
        int maxPool = section.getInt("database.max-pool-connections", 10);
        int minIdle = section.getInt("database.min-idle-connections", 2);
        if (maxPool > 0 && minIdle > 0 && !propFile.exists()) {
            dbConfig.addDataSourceProperty("maximumPoolSize", maxPool);
            dbConfig.addDataSourceProperty("minimumIdle", minIdle);
            dbConfig.setMaximumPoolSize(maxPool);
            dbConfig.setMinimumIdle(minIdle);
        }
        if (!propFile.exists()) {
            HikariHelper.createPropertiesFile(propFile, dbConfig, true);
        }
    }

    @Override
    public MySqlPrismDataSource createDataSource() {
        if (dbConfig.getJdbcUrl() == null) {
            final String dns = "jdbc:mysql://" + this.section.getString("hostname") + ":"
                    + this.section.getString("port") + "/" + this.section.getString("databaseName")
                    + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false";
            dbConfig.setJdbcUrl(dns);
            dbConfig.setUsername(this.section.getString("username"));
            dbConfig.setPassword(this.section.getString("password"));
        }
        dbConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dbConfig.addHealthCheckProperty("connectivityCheckTimeoutMs", "1000");
        dbConfig.addHealthCheckProperty("expected99thPercentileMs", "10");
        if (Prism.getInstance().monitoring) {
            dbConfig.setMetricRegistry(ApiHandler.monitor.getRegistry());
            dbConfig.setHealthCheckRegistry(ApiHandler.monitor.getHealthRegistry());
            Prism.log("Hikari is configured with Metric Reporting.");
        } else {
            Prism.log("No metric recorder found to hook into Hikari.");
        }
        try {
            database = new HikariDataSource(dbConfig);
            createSettingsQuery();
            return this;
        } catch (HikariPool.PoolInitializationException e) {
            Prism.warn("Hikari Pool did not Initialize: " + e.getMessage());
            database = null;
        }
        return this;
    }

    @Override
    public void setFile() {
        //not required here.
    }

    @Override
    public SelectQuery createSelectQuery() {
        if (nonStandardSql) {
            return new MySqlSelectQueryBuilder(this);
        } else {
            return new SqlSelectQueryBuilder(this);
        }
    }

    private void detectNonStandardSql() {
        try (
                Connection conn = getConnection();
                PreparedStatement st = (conn != null) ? conn.prepareStatement("SHOW VARIABLES") : null;
                PreparedStatement st1 = (conn != null) ? conn.prepareStatement("SELECT ANY_VALUE(1)") : null;
                ResultSet rs = (st != null) ? st.executeQuery() : null;
                ResultSet rs1 = (st1 != null) ? st1.executeQuery() : null

        ) {
            if (rs == null || rs1 == null) {
                throw new SQLNonTransientConnectionException("Database did not configure correctly.");
            }
            while (rs.next()) {
                dbInfo.put(rs.getString(1).toLowerCase(), rs.getString(2));
            }
            rs1.next();
            String version = dbInfo.get("version");
            String versionComment = dbInfo.get("version_comment");
            Prism.log("Prism detected your database is version:" + version + " / " + versionComment);
            Prism.log("You have set nonStandardSql to " + nonStandardSql);
            Prism.log("You are able to use non standard SQL");
            if (!nonStandardSql) {
                Prism.log("Prism will use standard sql queries");
            }
        } catch (SQLNonTransientConnectionException e) {
            Prism.warn(e.getMessage());
        } catch (SQLException e) {
            Prism.log("You are not able to use non standard Sql");
            if (nonStandardSql) {
                Prism.log("This sounds like a configuration error.  If you have database access"
                        + "errors please set nonStandardSql to false");
            }
        }
    }
}
