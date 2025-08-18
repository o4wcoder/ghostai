package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Main {
    @Language("AGSL")
    val main = """
half4 main(vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution;
    vec2 centered = (fragCoord - 0.5 * iResolution) / min(iResolution.x, iResolution.y);

    // === Moon ===
    MoonData moon = drawMoon(
        centered,
        vec2(0.20, -0.78),
        0.18,
        0.40,
        0.35,
        iTime
    );

    // === Floating animation ===
    float floatOffset = 0.03 * sin(iTime * 0.7);

    // === Ghost coords ===
    vec2 ghostUV = centered;
    ghostUV.y += floatOffset;
    vec2 faceUV = ghostUV;
    float tailWave = 0.05 * sin(ghostUV.x * 15.0 + iTime * 2.0);
    float tailFactor = smoothstep(0.0, 0.3, ghostUV.y);
    ghostUV.y += tailWave * tailFactor;
    ghostUV.x *= mix(1.0, 0.4, smoothstep(0.0, 0.6, ghostUV.y));

    float radius = 0.4;
    vec2 ellipticalUV = vec2(ghostUV.x, ghostUV.y * 0.9);
    float ghostBody = smoothstep(radius, radius - 0.1, length(ellipticalUV));
    float ghostMask = smoothstep(0.01, 0.99, ghostBody);

    // === Eyes / pupils ===
    float isBlinking = isBlinking(iTime);
    float moveCycle = floor(iTime / 3.0);
    float cycleTime = fract(iTime / 3.0);
    float moveProgress = smoothstep(0.0, 0.2, cycleTime) * (1.0 - smoothstep(0.8, 1.0, cycleTime));
    vec2 neutralOffset = vec2(0.0, 0.01);
    vec2 pupilOffset = neutralOffset + randomPupilOffset(moveCycle) * moveProgress * (1.0 - isBlinking);
    vec2 leftEye = vec2(-0.10, -0.08);
    vec2 rightEye = vec2( 0.10, -0.08);

    EyeData eyes = drawEyes(faceUV, leftEye, rightEye, isBlinking);
    PupilData pupils = drawPupils(faceUV, leftEye + pupilOffset, rightEye + pupilOffset, isBlinking);
    MouthData mouth = drawMouth(faceUV, iTime, isSpeaking);

    // === Mist background ===
    vec2 mistUV = uv * 3.0 + vec2(iTime * 0.08, iTime * 0.03);
    float mistNoise = fbm(mistUV);
    float mistStrength = 0.5;
    vec3 mistColor = vec3(0.85) * (mistNoise * mistStrength);

    // Lightning
    float lightningSeed = floor(iTime * 2.0);
    float lightningRand = fract(sin(lightningSeed * 91.345) * 47453.25);
    float lightningActive = step(0.95, lightningRand);
    float lightningFade = smoothstep(0.0, 0.5, fract(iTime)) * (1.0 - smoothstep(0.5, 1.0, fract(iTime)));
    float lightning = lightningActive * lightningFade;
    float lightningMask = smoothstep(1.0, 0.4, uv.y);
    mistColor += lightning * lightningMask * vec3(0.3, 0.4, 0.5);

    // Ghost glow on mist
    vec3 ghostGlowColor = vec3(0.2 + 0.4 * isSpeaking, 1.0, 0.2 + 0.4 * isSpeaking);
    float ghostDist = length(ghostUV);
    float glowFalloff = smoothstep(0.5, 0.0, ghostDist);
    mistColor *= 1.0 - 0.3 * glowFalloff;
    mistColor += ghostGlowColor * glowFalloff * 1.5;

    // === Clouds before moon composite ===
    vec2 cloudUV = (fragCoord / iResolution) * 2.2 + vec2(iTime * 0.02, iTime * 0.015);
    float cloudNoise = fbm(cloudUV);
    float cloudFront = smoothstep(0.52, 0.72, cloudNoise);

    // Moon disc with occlusion
    const float DISC_OCCLUDE = 0.50;
    vec3 occludedDisc = mix(moon.color, moon.color * (1.0 - DISC_OCCLUDE), cloudFront);
    vec3 moonColor = mix(mistColor, occludedDisc, moon.mask);

    // Halo
    const float RIM_BLEED = 0.20;
    const float CLOUD_OCCLUSION = 0.80;
    float haloMask = (1.0 - moon.mask) + RIM_BLEED * moon.rim;
    moonColor += moon.glow * GLOW_COLOR * haloMask * (1.0 - CLOUD_OCCLUSION * cloudFront);

    // Tiny blue scatter near halo
    float nearHalo = clamp(moon.glow * 1.3, 0.0, 1.0);
    moonColor += (1.0 - cloudFront) * nearHalo * vec3(0.02, 0.05, 0.07);

    // === Ground (UV space, bottom-only, never over ghost) ===
    float groundY = 0.82;     // raise/lower horizon in 0..1 space
    float feather = 0.03;     // softness of horizon edge
    GroundData ground = drawGround(centered, iTime);

    // Make a bottom mask ourselves; do NOT use ground.mask
    float fade = smoothstep(groundY - feather, groundY + feather, uv.y); // 0 below → 1 above
    float bottomMask = 1.0 - fade;                                       // 1 below → 0 above
    
    // Strength of the ground “overlay” (0 = invisible, 1 = full replace)
    float groundStrength = 0.5;  
    
    // If you want the dirt to be a bit translucent, set overlayStrength < 1
    float overlayStrength = 0.9;

    // Only below horizon (ground.mask) and never over the ghost
    float groundBlend = ground.mask * (1.0 - ghostMask);

    // Compose ground over moon/mist
    vec3 bottomGround = mix(
        moonColor,
        mix(moonColor, ground.albedo, overlayStrength), // translucent dirt
        groundBlend
    );

    // === Ghost shading ===
    vec3 ghostShadedColor = getGhostBodyColor(radius, ghostUV);

    // Socket tint and highlight
    ghostShadedColor = mixEyeSocketColor(ghostShadedColor, faceUV, leftEye, rightEye);

    // === Final composite ===
    vec3 finalColor = mix(bottomGround, ghostShadedColor, ghostMask);
    finalColor = mixEyeColor(finalColor, eyes);
    finalColor = mixPupilColor(finalColor, pupils);
    finalColor = mixMouthColor(finalColor, mouth);

    float alphaFade = smoothstep(radius, radius - 0.05, length(ellipticalUV));
    float finalAlpha = mix(1.0, ghostMask * alphaFade, ghostMask);

    return half4(finalColor, finalAlpha);
}

    """.trimIndent()
}
