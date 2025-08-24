
package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Main {
    @Language("AGSL")
    val main = """
half4 main(vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution;
    vec2 centered = (fragCoord - 0.5 * iResolution) / min(iResolution.x, iResolution.y);

    // === Moon ===
    const vec2 MOON_POS = vec2(0.20, -0.78);
    const float MOON_R   = 0.18;  // disc radius
    const float HALO_R   = 0.40;  // glow radius
    MoonData moon = drawMoon(centered, MOON_POS, MOON_R, HALO_R, 0.35, iTime);
    // Light from the moon
    vec3 sceneLight = sceneLightFromMoon(MOON_POS, 0.75);
    // === Floating animation ===
    float floatOffset = 0.03 * sin(iTime * 0.7);

    // === Ghost coords ===
    vec2 ghostUV = centered;
    ghostUV.y += floatOffset;
    vec2 faceUV = ghostUV;

    float tailWave   = 0.05 * sin(ghostUV.x * 15.0 + iTime * 2.0);
    float tailFactor = smoothstep(0.0, 0.3, ghostUV.y);
    ghostUV.y += tailWave * tailFactor;

    // Bottom pinch (slimmer toward the tail)
    ghostUV.x *= mix(1.0, 0.30, smoothstep(0.0, 0.6, ghostUV.y));

    // === Shape controls (size / aspect) ===
    float radius = 0.40;
    float sx = 0.75;
    float sy = 1.06;

    // Use scaled coords for silhouette (and later for body shading)
    vec2 shapeUV = vec2(ghostUV.x / sx, ghostUV.y / sy);

    // --- CRISP ghost silhouette (~1–2px AA) ---
    vec2 ellipticalUV = vec2(shapeUV.x, shapeUV.y * 0.90);
    float d  = length(ellipticalUV) - radius;
    float aa = 1.5 / min(iResolution.x, iResolution.y);
    float ghostMask = 1.0 - smoothstep(0.0, aa, d);

    // === Eyes / pupils ===
    float blink = isBlinking(iTime);
    float moveCycle = floor(iTime / 3.0);
    float cycleTime = fract(iTime / 3.0);
    float moveProgress = smoothstep(0.0, 0.2, cycleTime) * (1.0 - smoothstep(0.8, 1.0, cycleTime));
    vec2 neutralOffset = vec2(0.0, 0.01);

    vec2 leftEye  = vec2(-0.10, -0.18);
    vec2 rightEye = vec2( 0.10, -0.18);

    vec2 eyeRad = vec2(0.065, mix(0.075, 0.005, blink));
    vec2 rawOffset = randomPupilOffset(moveCycle) * moveProgress * (1.0 - blink);
    float glowMargin = 0.010;
    vec2 safeOffset = clampPupilOffset(rawOffset, eyeRad, glowMargin);
    vec2 pupilOffset = neutralOffset + safeOffset;

    EyeData eyes = drawEyes(faceUV, leftEye, rightEye, blink);
    PupilData iris = drawIrisUnderlay(faceUV, leftEye, rightEye, blink);
    PupilHighlight pupilHighlight = drawPupilHighlight(faceUV, leftEye + pupilOffset, rightEye + pupilOffset, blink);
    BlackPupilData blackPupils = drawBlackPupils(faceUV, leftEye + pupilOffset, rightEye + pupilOffset, blink);
    MouthData mouth = drawMouth(faceUV, iTime, isSpeaking);

    // === Mist background ===
    const float MIST_GAIN  = 0.30;  // <- master intensity (was ~0.5 effectively). Lower = less mist
    const float MIST_GAMMA = 1.20;  // >1.0 compresses brights (optional)
    vec2  mistUV    = uv * 3.0 + vec2(iTime * 0.08, iTime * 0.03);
    float mistNoise = fbm(mistUV);
    float mist      = pow(clamp(mistNoise, 0.0, 1.0), MIST_GAMMA);

    // darker base sky with scaled mist amount
    vec3  mistColor = vec3(0.85) * (mist * MIST_GAIN);

    // Lightning
    float lightningSeed = floor(iTime * 2.0);
    float lightningRand = fract(sin(lightningSeed * 91.345) * 47453.25);
    float lightningActive = step(0.95, lightningRand);
    float lightningFade = smoothstep(0.0, 0.5, fract(iTime)) * (1.0 - smoothstep(0.5, 1.0, fract(iTime)));
    float lightning = lightningActive * lightningFade;
    float lightningMask = smoothstep(1.0, 0.4, uv.y);
    mistColor += lightning * lightningMask * vec3(0.3, 0.4, 0.5);

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
    
    // === Wide sky tint from the moon (outside the halo) =========================
    // color + strength knobs
    const vec3  SKY_TINT_COLOR  = vec3(0.025, 0.07, 0.10); // gentle cyan/blue
    const float SKY_TINT_STRENGTH = 0.95;                  // overall intensity

    // distance from moon in your CENTERED space
    float dMoon = length(centered - MOON_POS);

    // fades from just outside the halo to ~3× the halo radius
    float tintFalloff = 1.0 - smoothstep(HALO_R, HALO_R * 3.0, dMoon);

    // reduce under thick front clouds; never affect inside the disc
    float tintMask = tintFalloff * (1.0 - 0.6 * cloudFront) * (1.0 - moon.mask);

    // apply (additive tint)
    moonColor += SKY_TINT_COLOR * (SKY_TINT_STRENGTH * tintMask);
    // 1) AA radius in your current space
    float pxAA = 1.5 / min(iResolution.x, iResolution.y);

    // 2) Near‑black ink for silhouettes
    vec3 INK = vec3(0.02);

    // === TREE: trunk on the left =================================================
    buildTree(moonColor, centered, pxAA);
    

    // === Ground shadow: horizontal oval + tiny contact =======================
    const float GROUND_LINE = 0.48;                    // must match drawGround()
    float footX = 0.015 * sin(iTime * 0.9);
    
    // slight push below the horizon so the “center” is inside the dirt
    vec2 ghostGroundCenter = vec2(footX, GROUND_LINE + 0.28 * radius);
    
        // === Ground (use drawGround mask so orientation stays correct) ============
    GroundData ground = drawGround(centered, iTime, sceneLight, ghostGroundCenter);
    vec3 withGround = mixGroundColor(moonColor, ground, ghostMask);


    
    // Ground shadow uses same XY; shallower Z for projection
    vec3 sceneLightShadow = normalize(vec3(sceneLight.xy, 0.45));
    vec2 L2 = normalize(sceneLightShadow.xy);

    // vertical float you already compute above
    // float floatOffset = 0.03 * sin(iTime * 0.7);

    // tiny contact right at the horizon (anchor only; not the main shape)
    float contact = groundContactShadowCentered(
        centered, footX, GROUND_LINE,
        0.75 * radius * sx,    // width
        0.55 * radius          // fade downward
    );

    // --- Oval center offset away from the light (down + left with top-right light)
    float offX = -L2.x * 0.35 * radius * sx;
    float offY =  max(0.0, -L2.y) * 0.28 * radius;
    float cx = footX + offX;
    float cy = GROUND_LINE + offY + 0.28 * radius;   // slightly below horizon

    // --- Size follows height (no pulse)
    // Normalize the float: +1 when ghost is lower/closer, -1 when higher
    float floatAmp = 0.03;
    float h = clamp(floatOffset / floatAmp, -1.0, 1.0);

    // Shadow scales with height: bigger/darker when ghost is closer (h>0)
    float sizeScale = 1.0 + 0.10 * h;                 // 22% range
    float shadowAlphaScale = 0.3; //Smaller = lighter
    float strengthScale = shadowAlphaScale * mix(0.70, 0.95, 0.5 + 0.5*h); // darkness factor

    float rx = (1.05 * radius * sx) * sizeScale;      // horizontal radius
    float ry = (0.55 * radius)     * sizeScale;       // vertical radius

    float oval = groundOvalShadowCentered(centered, cx, cy, rx, ry, 0.10 * radius);

    // Combine (oval does most of the work; contact just anchors near edge)
    float shadow = clamp(oval + 0.15 * contact, 0.0, 1.0); // was + 0.25 * contact
    
     // ------------------ temp for debugging -----------------------------------

  // vec3 finalColor = moonColor;
   
     // -----------------------
     
     float shadowMask = shadow * ground.mask * (1.0 - ghostMask);
     withGround = mix(withGround, withGround * 0.25, strengthScale * shadowMask);

     // === Ghost shading (same scene light) ====================================
     vec3 ghostShadedColor = shadeGhostBodyStandard(shapeUV, radius,sceneLight);
     ghostShadedColor = mixEyeSocketColor(ghostShadedColor, faceUV, leftEye, rightEye);

     // === Final composite ======================================================
     vec3 finalColor = mix(withGround, ghostShadedColor, ghostMask);
     finalColor = mixEyeColor(finalColor, eyes);
     finalColor = mixPupilColor(finalColor, iris);
     finalColor = mixBlackPupil(finalColor, blackPupils);
     finalColor = mixPupilHighlight(finalColor, pupilHighlight);
     finalColor = mixMouthColor(finalColor, mouth);


    return half4(finalColor, 1.0);
}
    """.trimIndent()
}

// float shadowMask = shadow * ground.mask * (1.0 - ghostMask);
// withGround = mix(withGround, withGround * 0.25, strengthScale * shadowMask);
//
// // === Ghost shading (same scene light) ====================================
// vec3 ghostShadedColor = shadeGhostBodyStandard(shapeUV, radius,sceneLight);
// ghostShadedColor = mixEyeSocketColor(ghostShadedColor, faceUV, leftEye, rightEye);
//
// // === Final composite ======================================================
// vec3 finalColor = mix(withGround, ghostShadedColor, ghostMask);
// finalColor = mixEyeColor(finalColor, eyes);
// finalColor = mixPupilColor(finalColor, iris);
// finalColor = mixBlackPupil(finalColor, blackPupils);
// finalColor = mixPupilHighlight(finalColor, pupilHighlight);
// finalColor = mixMouthColor(finalColor, mouth);
