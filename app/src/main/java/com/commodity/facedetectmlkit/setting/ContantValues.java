package com.commodity.facedetectmlkit.setting;


public enum ContantValues {
    RECT_POS_LEFT(250),
    RECT_POS_RIGHT(830),
    RECT_POS_TOP(200),
    RECT_POS_BOTTOM(800),
    MASK_FOUND_LABEL("Mask Detected"),
    MASK_NOTFOUND_LABEL("Mask not Detected"),
    DEFAULT_MESSAGE("Welcome to Xtreme Media"),
    PERMIT_LABEL("Visiting Access Permitted"),
    NOTPERMIT_LABEL("Visiting Access Denied"),
    PREVIEW_MASK_LABEL("mask"),
    PREVIEW_NOMASK_LABEL("no mask"),
    DETECTED_TEMPERATURE("36.2"),
    BEFORE_CAMERA_TRIGGERING_MSG("Please Come In Front Of The Camera"),
    AFTER_CAMERA_TRIGGERING_MSG("Adjust Your Face In The Circle"),
    ACTIVITY_PAUSED("ACTIVITY_PAUSED"),
    ACTIVITY_RESUMED("ACTIVITY_RESUMED");

    private String eventCodeString;
    private int eventCodeInteger;
    ContantValues(String eCode) {
        eventCodeString = eCode;
    }
    ContantValues(int eCode) {
        eventCodeInteger = eCode;
    }

    public String getEventCodeString() {
        return eventCodeString;
    }
    public int getEventCodeInteger() {
        return eventCodeInteger;
    }
}
