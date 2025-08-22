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

    // 공용 네비
    @FXML protected final void goHome() { close(); }
    @FXML protected final void closeOverlay() { close(); }

    // 헬퍼
    protected final void open(String fxmlPath) { if (host != null) host.openOverlay(fxmlPath); }
    protected final void close()               { if (host != null) host.closeOverlay(); }
    protected final MainController.OverlayHost host() { return host; }
}