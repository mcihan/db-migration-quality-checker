package com.dbmigrationqualitychecker.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigProperties {

    @Value("${datasource.db2.jdbc-url}")
    private String db2JdbcUrl;

    @Value("${datasource.db2.username}")
    private String db2username;
    @Value("${datasource.db2.password}")
    private String db2password;

    @Value("${datasource.mysql.jdbc-url}")
    private String mysqlJdbcUrl;

    @Value("${datasource.mysql.username}")
    private String mysqlusername;

    @Value("${datasource.mysql.password}")
    private String mysqlpassword;


    @PostConstruct
    void init() {
        System.out.println("**************************************");
        System.out.println("**************************************");
        System.out.println("db2JdbcUrl = " + db2JdbcUrl);
        System.out.println("db2username = " + db2username);
        System.out.println("mysqlJdbcUrl = " + mysqlJdbcUrl);
        System.out.println("mysqlusername = " + mysqlusername);
        System.out.println("**************************************");
        System.out.println("**************************************");
    }
}
