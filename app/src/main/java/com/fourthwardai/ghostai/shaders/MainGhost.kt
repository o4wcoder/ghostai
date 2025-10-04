package com.fourthwardai.ghostai.shaders

import org.intellij.lang.annotations.Language

object MainGhost {
    @Language("AGSL")
    val main = """
half4 main(vec2 fragCoord) {
    // === Common coords =======================================================
    half2 f        = half2(fragCoord);
    half2 resH     = half2(iResolution);
    half  invMin   = half(1.0 / min(iResolution.x, iResolution.y));
    half2 centered = (f - resH * half(0.5)) * invMin;
    half  pxAA     = half(1.5) * invMin;

    // === Floating / tail =====================================================
    // Use full-float bounded clocks to avoid FP16 drift over time
    const float TAU = 6.28318530718;

    float tFloatF = mod(float(iTime) * 0.7, TAU * 8.0);
    half  floatOffset = half(0.03 * sin(tFloatF));

    half2 ghostUV = centered;
    ghostUV.y += floatOffset;
    half2 faceUV = ghostUV;

    float tTailF = mod(float(iTime) * 2.0, TAU * 8.0);
    float waveF  = 0.05 * sin(float(ghostUV.x) * 15.0 + tTailF);
    half  tailWave   = half(waveF);
    half  tailFactor = smoothstep(half(0.0), half(0.3), ghostUV.y);
    ghostUV.y += tailWave * tailFactor;

    // Bottom pinch
    ghostUV.x *= mix(half(1.0), half(0.30), smoothstep(half(0.0), half(0.6), ghostUV.y));

    // === Ghost silhouette ====================================================
    half  radius  = half(0.40);
    half  sx      = half(0.75);
    half  sy      = half(1.06);
    half2 shapeUV = half2(ghostUV.x / sx, ghostUV.y / sy);

    // anti-aliased edge via smoothstep using pxAA
    half2 ellipticalUV = half2(shapeUV.x, shapeUV.y * half(0.90));
    float dF = length(vec2(ellipticalUV)) - float(radius);
    half ghostMask = half(1.0 - smoothstep(0.0, float(pxAA), dF));


    // === Lighting (use your global scene light) ==============================
    vec3 sceneLight = SCENE_L_DIR;

    // === Ghost-only composite with transparency ==============================
    vec3  finalColor = vec3(0.0);
    float alpha      = 0.0;

    if (ghostMask > half(0.0)) {
        // Blink & subtle eye drift
        float blink         = float(isBlinking(iTime));
        float moveCycle     = floor(iTime / 3.0);
        float cycleTime     = fract(iTime / 3.0);
        float moveProgress  = smoothstep(0.0, 0.2, cycleTime) *
                              (1.0 - smoothstep(0.8, 1.0, cycleTime));
        vec2  neutralOffset = vec2(0.0, 0.01);
        vec2  leftEye       = vec2(-0.10, -0.18);
        vec2  rightEye      = vec2( 0.10, -0.18);
        vec2  eyeRad        = vec2(0.065, mix(0.075, 0.005, blink));
        float glowMargin    = 0.010;

        vec2 rawOffset   = randomPupilOffset(moveCycle) * moveProgress * (1.0 - blink);
        vec2 safeOffset  = clampPupilOffset(rawOffset, eyeRad, glowMargin);
        vec2 pupilOffset = neutralOffset + safeOffset;

        // Face features
        EyeData        eyes           = drawEyes(faceUV, leftEye, rightEye, blink);
        PupilData      iris           = drawIrisUnderlay(faceUV, leftEye, rightEye, blink);
        PupilHighlight pupilHighlight = drawPupilHighlight(faceUV, leftEye + pupilOffset,
                                                          rightEye + pupilOffset, blink);
        BlackPupilData blackPupils    = drawBlackPupils(faceUV, leftEye + pupilOffset,
                                                        rightEye + pupilOffset, blink);
        MouthData      mouth          = drawMouth(faceUV, iTime, isSpeaking);

        // Body shading (your lighting path)
        vec3 ghostShadedColor = shadeGhostBodyStandard(vec2(shapeUV), float(radius), sceneLight);
        ghostShadedColor = mixEyeSocketColor(ghostShadedColor, faceUV, leftEye, rightEye);

        // Layer eyes/mouth
        finalColor = ghostShadedColor;
        finalColor = mixEyeColor(finalColor, eyes);
        finalColor = mixPupilColor(finalColor, iris);
        finalColor = mixBlackPupil(finalColor, blackPupils);
        finalColor = mixPupilHighlight(finalColor, pupilHighlight);
        finalColor = mixMouthColor(finalColor, mouth);

        alpha = float(ghostMask);
    }

    // Output premultiplied (Skia/Compose expects this)
    vec3 outRGB = finalColor * alpha;
    return half4(half3(outRGB), half(alpha));
}
    """.trimIndent()
}
