module com.share.dairy {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    // (BootstrapFX를 쓴다면) requires org.kordamp.bootstrapfx.core;

    // FXML이 컨트롤러/커스텀컨트롤 필드에 접근할 수 있게 'opens'
    opens com.share.dairy.controller to javafx.fxml;
    opens com.share.dairy.ui         to javafx.fxml;

    // 런처(javafx.graphics)가 Application/커스텀타입을 볼 수 있게 'exports'
    // CommonUI(런처)나 SidebarItem 타입을 외부 모듈에서 참조 가능하게 함
    exports com.share.dairy.ui;
    exports com.share.dairy.app;
    exports com.share.dairy.controller;
    exports com.share.dairy.model;
}
