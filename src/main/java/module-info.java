module com.laurosantos.clippr {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires atlantafx.base;

    requires java.sql;
    requires java.naming;
    requires java.net.http;

    requires org.slf4j;
    requires jakarta.persistence;
    requires org.hibernate.orm.core;
    requires org.postgresql.jdbc;
    requires com.zaxxer.hikari;
    requires com.fasterxml.jackson.databind;

    opens com.laurosantos.clippr to javafx.fxml, javafx.graphics;
    opens com.laurosantos.clippr.ui to javafx.fxml;
    opens com.laurosantos.clippr.persistence.entities to org.hibernate.orm.core, jakarta.persistence;
    opens com.laurosantos.clippr.domain to com.fasterxml.jackson.databind;

    exports com.laurosantos.clippr;
    exports com.laurosantos.clippr.domain;
    exports com.laurosantos.clippr.downloader;
    exports com.laurosantos.clippr.events;
    exports com.laurosantos.clippr.persistence;
    exports com.laurosantos.clippr.persistence.entities;
    exports com.laurosantos.clippr.ui;
}
