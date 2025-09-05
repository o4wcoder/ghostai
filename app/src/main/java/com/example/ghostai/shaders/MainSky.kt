package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object MainSky {
    @Language("AGSL")
    val main = """
// --- tiny, cheap noise helpers ------------------------------------------------
float hash12(vec2 p){
    p = fract(p * 0.1031);
    p += dot(p, p.yx + vec2(33.33, 33.33));
    return fract((p.x + p.y) * p.x);
}
float vnoise(vec2 p){
    vec2 i = floor(p), f = fract(p);
    float a = hash12(i);
    float b = hash12(i + vec2(1,0));
    float c = hash12(i + vec2(0,1));
    float d = hash12(i + vec2(1,1));
    vec2 u = f*f*(vec2(3.0) - 2.0*f);
    return mix(mix(a,b,u.x), mix(c,d,u.x), u.y);
}

// --- main --------------------------------------------------------------------
half4 main(vec2 fragCoord) {
    // === Precompute common reciprocals / coords ===============================
    half2 f        = half2(fragCoord);
    half2 resH     = half2(iResolution);
    half  invMin   = half(1.0 / min(iResolution.x, iResolution.y));
    half2 invRes   = half2(1.0) / resH;

    half2 uv       = f * invRes;
    half2 centered = (f - resH * half(0.5)) * invMin;
    half  pxAA     = half(1.5) * invMin;

    // Shared horizon line (align this with your PNG horizon)
    const half HLINE = half(0.48);

    // === Quality controls =====================================================
    // uQuality: 0=low, 1=high   |  uFps: e.g., 30 on tablet, 60 on phone
    bool  HIGH   = (uQuality > 0.5);
    float bgStep = max(1.0 / max(uFps, 1.0), 1.0/60.0);   // never faster than 60Hz
    float timeBG = floor(iTime / bgStep) * bgStep;        // background-only clock

    // === Moon =================================================================
    const half2 MOON_POS = half2(0.20, -0.78);
    const half  MOON_R   = half(0.18);
    const half  HALO_R   = half(0.40);
    MoonData moon = drawMoon(centered, MOON_POS, MOON_R, HALO_R, half(0.35), iTime);

    // === Mist background (quality-gated & bg-timed) ===========================
    vec3  mistColor = vec3(0.0);
    const float MIST_GAIN = 0.30;

    // Only compute mist above horizon (sky region)
    if (centered.y < HLINE + half(0.02)) {
        half2 mistUV = uv * half(3.0) + half2(timeBG * 0.08, timeBG * 0.03);
        half  mistN  = vnoise(mistUV); // 1 octave
        if (HIGH) {
            // add 2nd octave only on high
            mistN = (mistN + half(0.5)*vnoise(mistUV * half(1.9))) * half(1.0/1.5);
        }
        // approx gamma 1.2 via mix with sqrt (avoids pow)
        half  mist = mix(mistN, half(sqrt(max(mistN, 0.0))), half(0.4));
        mistColor  = vec3(0.85) * (float(mist) * MIST_GAIN);

        // Lightning only on HIGH
        if (HIGH) {
            float seed = floor(timeBG * 2.0);
            float rnd  = fract(sin(seed * 91.345) * 47453.25);
            float active = step(0.95, rnd);
            float ph = fract(timeBG);
            float fade = smoothstep(0.0, 0.5, ph) * (1.0 - smoothstep(0.5, 1.0, ph));
            float mask = smoothstep(1.0, 0.4, uv.y);
            mistColor += (active * fade) * mask * vec3(0.3, 0.4, 0.5);
        }
    }

    // === Clouds around the moon ==============================================
half dMoon = half(length(centered - MOON_POS));
half cloudReach = HALO_R * (HIGH ? half(3.2) : half(2.5));
half needCloud  = step(dMoon, cloudReach);

float cloudFront = 0.0;
if (needCloud > half(0.5)) {
    vec2 drift = vec2(
        mod(timeBG * 0.02, 256.0),
        mod(timeBG * 0.015, 256.0)
    );
    vec2 cuv = vec2(uv) * 2.1 + drift;


    float base   = vnoise(cuv);
    float cloudN = base;

    if (HIGH) {
        float o2 = vnoise(cuv * 1.9);
        float o3 = vnoise(cuv * 3.7);
        cloudN   = (base + 0.5*o2 + 0.25*o3) * (1.0/1.75);

        float ridged  = abs(vnoise(cuv * 5.3) * 2.0 - 1.0);
        float midMask = cloudN * (1.0 - cloudN);
        cloudN += ridged * midMask * 0.15;
    } else {
        float o2 = vnoise(cuv * 1.9);
        cloudN   = (base + 0.5*o2) * (1.0/1.5);
    }

    cloudFront = smoothstep(0.46, 0.72, cloudN);
    cloudFront = pow(cloudFront, 1.1);
}



    // === Moon/halo composite ==================================================
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

    // === Wide sky tint outside the halo ======================================
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

    // === Final ================================================================
    return half4(half3(moonColor), 1.0);
}
    """.trimIndent()
}
