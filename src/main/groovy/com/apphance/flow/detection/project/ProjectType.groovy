package com.apphance.flow.detection.project

enum ProjectType {
    IOS, ANDROID

    String getPrefix() {
        name().toLowerCase()
    }
}
