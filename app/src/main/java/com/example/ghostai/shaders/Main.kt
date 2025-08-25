
package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Main {
    @Language("AGSL")
    val main = """
// --- tiny, cheap noise helpers ------------------------------------------------
half hash12h(half2 p){
    p = fract(p * half(0.1031));
    p += dot(p, p.yx + half2(33.33, 33.33));
    return fract((p.x + p.y) * p.x);
}
half vnoise_h(half2 p){
    half2 i = floor(p), f = fract(p);
    half a = hash12h(i);
    half b = hash12h(i + half2(1,0));
    half c = hash12h(i + half2(0,1));
    half d = hash12h(i + half2(1,1));
    half2 u = f*f*(half2(3.0,3.0) - half2(2.0,2.0)*f);
    return mix(mix(a,b,u.x), mix(c,d,u.x), u.y);
}
// 2-octave fbm (unrolled)
half fbm2_h(half2 p){
    half s = vnoise_h(p);
    s += half(0.5) * vnoise_h(p * half(1.9));
    return s * half(1.0/1.5);
}

half4 main(vec2 fragCoord) {
    // === Precompute common reciprocals / coords ===============================
    half2 f        = half2(fragCoord);
    half2 resH     = half2(iResolution);
    half  invMin   = half(1.0 / min(iResolution.x, iResolution.y));
    half2 invRes   = half2(1.0) / resH;

    half2 uv       = f * invRes;
    half2 centered = (f - resH * half(0.5)) * invMin;
    half  pxAA     = half(1.5) * invMin;

    // Shared horizon line (must match ground)
    const half HLINE = half(0.48);

    // === Moon =================================================================
    const half2 MOON_POS = half2(0.20, -0.78);
    const half  MOON_R   = half(0.18);
    const half  HALO_R   = half(0.40);
    MoonData moon = drawMoon(centered, MOON_POS, MOON_R, HALO_R, half(0.35), iTime);
    vec3 sceneLight = sceneLightFromMoon(MOON_POS, 0.75); // vec3 to match other fns

    // === Floating / tail ======================================================
    half tFloat = half(iTime * 0.7);
    half floatOffset = half(0.03) * half(sin(tFloat));

    half2 ghostUV = centered;
    ghostUV.y += floatOffset;
    half2 faceUV = ghostUV;

    half tTail = half(iTime * 2.0);
    half tailWave   = half(0.05) * half(sin(ghostUV.x * half(15.0) + tTail));
    half tailFactor = smoothstep(half(0.0), half(0.3), ghostUV.y);
    ghostUV.y += tailWave * tailFactor;

    // Bottom pinch
    ghostUV.x *= mix(half(1.0), half(0.30), smoothstep(half(0.0), half(0.6), ghostUV.y));

    // === Ghost silhouette =====================================================
    half  radius = half(0.40);
    half  sx = half(0.75);
    half  sy = half(1.06);
    half2 shapeUV = half2(ghostUV.x / sx, ghostUV.y / sy);

    half2 ellipticalUV = half2(shapeUV.x, shapeUV.y * half(0.90));
    half  d        = length(ellipticalUV) - radius;
    half  ghostMask = half(1.0) - smoothstep(half(0.0), pxAA, d);

    // === Mist background (cheap) =============================================
    const float MIST_GAIN = 0.30;
    half2 mistUV = uv * half(3.0) + half2(iTime * 0.08, iTime * 0.03);
    half  mistN  = fbm2_h(mistUV);
    // approx gamma 1.2 via mix with sqrt (avoids pow)
    half  mist   = mix(mistN, half(sqrt(max(mistN, 0.0))), half(0.4));
    vec3  mistColor = vec3(0.85) * (float(mist) * MIST_GAIN);

    // Lightning
    {
        half seed = half(floor(iTime * 2.0));
        half rnd  = half(fract(sin(seed * half(91.345)) * half(47453.25)));
        half active = step(half(0.95), rnd);
        half ph = half(fract(iTime));
        half fade = smoothstep(half(0.0), half(0.5), ph) * (half(1.0) - smoothstep(half(0.5), half(1.0), ph));
        half mask = smoothstep(half(1.0), half(0.4), uv.y);
        mistColor += (float(active * fade) * float(mask)) * vec3(0.3, 0.4, 0.5);
    }

    // === Clouds & moon composite (bounded but detailed) ======================
    half dMoon = half(length(centered - MOON_POS));
    half needCloud = step(dMoon, HALO_R * half(3.2)); // expand to 4.0 if you see cutoffs

    float cloudFront = 0.0;
    if (needCloud > half(0.5)) {
        half2 cuv = uv * half(2.1) + half2(iTime * 0.02, iTime * 0.015);

        // 3 unrolled octaves (still cheap, local to moon)
        half base = vnoise_h(cuv);
        half o2   = vnoise_h(cuv * half(1.9));
        half o3   = vnoise_h(cuv * half(3.7));
        half cloudN = (base + half(0.5)*o2 + half(0.25)*o3) * half(1.0/1.75);

        // micro-detail only in midtones (wispy look), very low cost
        half ridged  = abs(vnoise_h(cuv * half(5.3)) * half(2.0) - half(1.0)); // 0..1
        half midMask = cloudN * (half(1.0) - cloudN);                          // peaks at 0.5
        cloudN += ridged * midMask * half(0.15);

        // slightly tighter thresholds for contrast
        cloudFront = smoothstep(half(0.50), half(0.70), cloudN);
    }

    const float DISC_OCCLUDE    = 0.50;
    vec3 occludedDisc = mix(vec3(moon.color), vec3(moon.color) * (1.0 - DISC_OCCLUDE), cloudFront);
    vec3 moonColor    = mix(mistColor, occludedDisc, float(moon.mask));

    // Halo + near-halo scatter
    const float RIM_BLEED       = 0.20;
    const float CLOUD_OCCLUSION = 0.80;
    float haloMask = (1.0 - float(moon.mask)) + RIM_BLEED * float(moon.rim);
    moonColor += float(moon.glow) * GLOW_COLOR * haloMask * (1.0 - CLOUD_OCCLUSION * cloudFront);

    float nearHalo = clamp(float(moon.glow) * 1.3, 0.0, 1.0);
    moonColor += (1.0 - cloudFront) * nearHalo * vec3(0.02, 0.05, 0.07);

    // === Wide sky tint outside the halo =====================================
    const vec3  SKY_TINT_COLOR    = vec3(0.025, 0.07, 0.10);
    const float SKY_TINT_STRENGTH = 0.95;
    float tintFalloff = 1.0 - smoothstep(float(HALO_R), float(HALO_R) * 3.0, float(dMoon));
    float tintMask    = tintFalloff * (1.0 - 0.6 * cloudFront) * (1.0 - float(moon.mask));
    moonColor += SKY_TINT_COLOR * (SKY_TINT_STRENGTH * tintMask);

    // --- Sky horizon darkening (band just above the horizon) -----------------
    const half SKY_HORIZON_WIDTH    = half(0.28);
    const half SKY_HORIZON_STRENGTH = half(0.42);
    half distH   = half(abs(centered.y - HLINE));
    half nearH   = half(1.0) - smoothstep(half(0.0), SKY_HORIZON_WIDTH, distH);
    half skySide = half(1.0) - step(HLINE, centered.y); // 1 above horizon, 0 below
    half skyShade = nearH * skySide;
    moonColor *= (1.0 - float(SKY_HORIZON_STRENGTH * skyShade));

    // === Tree: bound to right region to avoid full-screen work ================
    if (centered.x > half(0.06) && centered.x < half(0.58) &&
        centered.y > half(-0.75) && centered.y < half(0.60)) {
        buildTree(moonColor, vec2(centered), float(pxAA)); // vec3 inout OK
    }

    // === Ground + shadow ======================================================
    const float GROUND_LINE = float(HLINE); // keep in sync with HLINE above
    half footX = half(0.015) * half(sin(iTime * 0.9));
    half2 ghostGroundCenter = half2(footX, half(GROUND_LINE) + half(0.28) * radius);

    GroundData ground = drawGround(centered, iTime, sceneLight, ghostGroundCenter);
    vec3 withGround   = mixGroundColor(moonColor, ground, ghostMask);

    // --- Ground horizon darkening (band just below the horizon) --------------
    const half GROUND_HORIZON_WIDTH    = half(0.30);
    const half GROUND_HORIZON_STRENGTH = half(0.28);
    half distHg     = half(abs(centered.y - HLINE));
    half nearHg     = half(1.0) - smoothstep(half(0.0), GROUND_HORIZON_WIDTH, distHg);
    half groundSide = step(HLINE, centered.y); // 1 below horizon, 0 above
    half groundShade = nearHg * groundSide;
    withGround *= (1.0 - float(GROUND_HORIZON_STRENGTH * groundShade));

    // Projected oval/contact shadow (same XY; shallower Z)
    vec3 sceneLightShadow = normalize(vec3(sceneLight.xy, 0.45));
    vec2 L2 = normalize(sceneLightShadow.xy);

    float contact = groundContactShadowCentered(
        vec2(centered), float(footX), GROUND_LINE,
        float(0.75) * float(radius) * float(sx),
        float(0.55) * float(radius)
    );

    float offX = -L2.x * 0.35 * float(radius) * float(sx);
    float offY =  max(0.0, -L2.y) * 0.28 * float(radius);
    float cx   = float(footX) + offX;
    float cy   = GROUND_LINE + offY + 0.28 * float(radius);

    float h = clamp(float(floatOffset / half(0.03)), -1.0, 1.0);
    float sizeScale        = 1.0 + 0.10 * h;
    float strengthScale    = 0.3 * mix(0.70, 0.95, 0.5 + 0.5 * h);

    float rx = (1.05 * float(radius) * float(sx)) * sizeScale;
    float ry = (0.55 * float(radius))             * sizeScale;

    float oval = groundOvalShadowCentered(vec2(centered), cx, cy, rx, ry, 0.10 * float(radius));
    float shadow = clamp(oval + 0.15 * contact, 0.0, 1.0);
    float shadowMask = shadow * float(ground.mask) * (1.0 - float(ghostMask));
    withGround = mix(withGround, withGround * 0.25, strengthScale * shadowMask);

    // === Final composite (gate face/body work by ghostMask) ==================
    vec3 finalColor = withGround;

    if (ghostMask > half(0.0)) {
        float blink = float(isBlinking(iTime));
        float moveCycle   = floor(iTime / 3.0);
        float cycleTime   = fract(iTime / 3.0);
        float moveProgress = smoothstep(0.0, 0.2, cycleTime) *
                             (1.0 - smoothstep(0.8, 1.0, cycleTime));
        vec2 neutralOffset = vec2(0.0, 0.01);
        vec2 leftEye  = vec2(-0.10, -0.18);
        vec2 rightEye = vec2( 0.10, -0.18);
        vec2 eyeRad   = vec2(0.065, mix(0.075, 0.005, blink));
        float glowMargin = 0.010;
        vec2 rawOffset = randomPupilOffset(moveCycle) * moveProgress * (1.0 - blink);
        vec2 safeOffset = clampPupilOffset(rawOffset, eyeRad, glowMargin);
        vec2 pupilOffset = neutralOffset + safeOffset;

        EyeData        eyes           = drawEyes(faceUV, leftEye, rightEye, blink);
        PupilData      iris           = drawIrisUnderlay(faceUV, leftEye, rightEye, blink);
        PupilHighlight pupilHighlight = drawPupilHighlight(faceUV, leftEye + pupilOffset, rightEye + pupilOffset, blink);
        BlackPupilData blackPupils    = drawBlackPupils(faceUV, leftEye + pupilOffset, rightEye + pupilOffset, blink);
        MouthData      mouth          = drawMouth(faceUV, iTime, isSpeaking);

        vec3 ghostShadedColor = shadeGhostBodyStandard(shapeUV, radius, sceneLight);
        ghostShadedColor = mixEyeSocketColor(ghostShadedColor, faceUV, leftEye, rightEye);

        finalColor = mix(withGround, ghostShadedColor, float(ghostMask));
        finalColor = mixEyeColor(finalColor, eyes);
        finalColor = mixPupilColor(finalColor, iris);
        finalColor = mixBlackPupil(finalColor, blackPupils);
        finalColor = mixPupilHighlight(finalColor, pupilHighlight);
        finalColor = mixMouthColor(finalColor, mouth);
    }

    return half4(half3(finalColor), 1.0);
}
    """.trimIndent()
}




