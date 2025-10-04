package com.fourthwardai.ghostai.model

data class DeviceSettings(
    val device: FormFactor,
    val quality: Float,
    val fps: Float,
) {
    val isTablet = if (device == FormFactor.Tablet) 1.0F else 0.0F
}
