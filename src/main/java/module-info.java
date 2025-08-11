module dairy {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.swing;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires java.sql;

    exports com.share.dairy.controller;
    opens com.share.dairy.controller to javafx.fxml;
    exports com.share.dairy.app;
    opens com.share.dairy.app to javafx.fxml;
    opens MainFrame_images;

}