module com.share.diary.dairy {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;

    opens com.share.diary.dairy to javafx.fxml;
    exports com.share.diary.dairy;
}