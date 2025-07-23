package com.example.springbootkafkamongodb.config;

import oracle.jdbc.pool.OracleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

@Configuration
public class OracleDataSourceConfig {

    @Value("${okafka.oracle.service-name}")
    private String serviceName;

    @Value("${okafka.oracle.tns-admin}")
    private String tnsAdmin;

    @Value("${okafka.oracle.username}")
    private String username;

    @Value("${okafka.oracle.password}")
    private String password;

    @Value("${okafka.oracle.tns-alias}")
    private String tnsAlias;

    @Bean(name = "oracleDataSource")
    public DataSource oracleDataSource() throws SQLException {
        OracleDataSource dataSource = new OracleDataSource();

        // Set TNS_ADMIN for wallet location
        System.setProperty("oracle.net.tns_admin", tnsAdmin);

        // Construct the JDBC URL for Oracle Autonomous Database
        String jdbcUrl = "jdbc:oracle:thin:@" + tnsAlias + "?TNS_ADMIN=" + tnsAdmin;

        dataSource.setURL(jdbcUrl);
        dataSource.setUser(username);
        dataSource.setPassword(password);

        // Additional connection properties for Autonomous Database
        Properties props = new Properties();
        props.setProperty("oracle.jdbc.fanEnabled", "false");
        props.setProperty("oracle.net.ssl_server_dn_match", "true");
        props.setProperty("oracle.net.ssl_version", "1.2");
        dataSource.setConnectionProperties(props);

        return dataSource;
    }
}
