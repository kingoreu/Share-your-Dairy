package com.share.dairy.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;

public abstract class OverlayChildController
        implements Initializable, MainController.NeedsOverlayHost {

    protected MainController.OverlayHost host;

    @Override
    public void setOverlayHost(MainController.OverlayHost host) {
        this.host = host;
        onHostReady();
    }

    /** host 주입 이후 추가 초기화가 필요하면 자식에서 override */
    protected void onHostReady() {}

    /** 공통 홈(오버레이 닫기) – FXML에서 onAction="#goHome" 으로 사용 */
    @FXML protected void goHome() {
        if (host != null) host.closeOverlay();
    }

    /** 유틸 */
    @FXML protected void closeOverlay() { if (host != null) host.closeOverlay(); }
    protected void openOverlay(String fxmlPath) { if (host != null) host.openOverlay(fxmlPath); }
}
