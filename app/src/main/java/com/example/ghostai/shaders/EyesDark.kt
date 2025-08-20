package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object EyesDark {
    @Language("AGSL")
    val eyes = """

float sdEllipse(vec2 p, vec2 r) {
    r = max(r, vec2(0.02));     // <- floor radius to avoid NaNs during blinks
    vec2 q = p / r;
    return length(q) - 1.0;
}

vec2 eyeMaskAndRim(vec2 uv, vec2 center, vec2 radius) {
    radius = max(radius, vec2(0.02));           // guard again
    float sdf = sdEllipse(uv - center, radius); // sdf < 0 inside

    // Correct: ascending edges; 1 inside, 0 outside
    float mask = 1.0 - smoothstep(-0.01, 0.01, sdf);

    // Rim ramps up near edge; safe because radius is clamped
    float norm = clamp(length((uv - center) / radius), 0.0, 1.0);
    float rim  = smoothstep(0.75, 1.0, norm);

    return vec2(mask, rim);
}


// ===== IRIS UNDERLAY (yellow/orange only UNDER the pupil) =====
PupilData drawIrisUnderlay(vec2 uv, vec2 leftCenter, vec2 rightCenter, float isBlinking) {
    float eyeRadiusX = 0.065;
    float eyeRadiusY = mix(0.075, 0.005, isBlinking);
    vec2  rad = vec2(eyeRadiusX, max(eyeRadiusY, 0.02));

    const float pupilR   = 0.022;
    const float irisInner = pupilR - 0.002;   // strong right under pupil
    const float irisOuter = pupilR + 0.060;   // how far the glow extends outside
    vec2 bias = vec2(0.0, 0.010);             // nudge down to feel "under"

    // --- LEFT ---
    vec2 pL  = uv - (leftCenter + bias);
    vec2 pnL = pL / rad;
    float dL = length(pL);

    // base radial glow around the pupil radius
    float radialL = 1.0 - smoothstep(irisInner, irisOuter, dL);

    // bottom‑only weighting (kills the top half)
    float belowL  = smoothstep(-0.005, 0.060, pnL.y);

    // side taper (reduces far left/right so it feels like a lower crescent)
    float sideL   = 1.0 - smoothstep(0.75, 1.0, abs(pnL.x));

    // clip to eye ellipse
    float clipL   = 1.0 - smoothstep(-0.005, 0.005, sdEllipse(pL, rad));

    float irisL   = radialL * belowL * sideL * clipL;
    float gradL   = (1.0 - smoothstep(pupilR, irisOuter, dL)) * belowL;

    // --- RIGHT ---
    vec2 pR  = uv - (rightCenter + bias);
    vec2 pnR = pR / rad;
    float dR = length(pR);
    float radialR = 1.0 - smoothstep(irisInner, irisOuter, dR);
    float belowR  = smoothstep(-0.005, 0.060, pnR.y);
    float sideR   = 1.0 - smoothstep(0.75, 1.0, abs(pnR.x));
    float clipR   = 1.0 - smoothstep(-0.005, 0.005, sdEllipse(pR, rad));

    float irisR   = radialR * belowR * sideR * clipR;
    float gradR   = (1.0 - smoothstep(pupilR, irisOuter, dR)) * belowR;

    PupilData outIris;
    outIris.mask     = (irisL + irisR) * (1.0 - isBlinking);
    outIris.gradient = max(gradL, gradR);
    return outIris;
}




        // --- dark eye sockets with gentle inner lift --------------------------
        EyeData drawEyes(vec2 uv, vec2 leftEye, vec2 rightEye, float isBlinking) {
            // Eye ellipse radii; Y squashes on blink
            float eyeRadiusX = 0.065;
            float eyeRadiusY = mix(0.075, 0.005, isBlinking);
           // vec2 rad = vec2(eyeRadiusX, eyeRadiusY);
           vec2 rad = vec2(eyeRadiusX, max(eyeRadiusY, 0.02)); 

            vec2 L = eyeMaskAndRim(uv, leftEye,  rad);
            vec2 R = eyeMaskAndRim(uv, rightEye, rad);

            float mask = max(L.x, R.x);
            float rim  = max(L.y, R.y);

            // Vertical falloff (slightly brighter under the upper lid, darker at the bottom)
            float vL = clamp((uv.y - leftEye.y)  / eyeRadiusY,  0.0, 1.0);
            float vR = clamp((uv.y - rightEye.y) / eyeRadiusY,  0.0, 1.0);
            float vert = max(vL, vR);

            // Gradient guides the base dark-eye shading (center a hair brighter than rim)
            // 0 = center (brighter), 1 = rim (darker)
            float gradient = clamp(rim * 0.85 + vert * 0.15, 0.0, 1.0);

            EyeData outData;
            outData.mask = mask;
            outData.gradient = gradient;
            return outData;
        }

        // --- glowing pupil "blob" that moves inside the dark eye --------------
        // This is *not* a hard pupil disk—it's a clipped radial glow that slides around.
        PupilData drawPupils(vec2 uv, vec2 leftCenter, vec2 rightCenter, float isBlinking) {
            float eyeRadiusX = 0.065;
            float eyeRadiusY = mix(0.075, 0.005, isBlinking);
            vec2  rad = vec2(eyeRadiusX, eyeRadiusY);

            // Soft glow size (roughly pupil/iris “ember”)
            float glowCore = 0.020;   // inner hot spot
            float glowFall = 0.055;   // outer falloff radius

            // LEFT
            vec2  pL = uv - leftCenter;
            float distL = length(pL);
            float glowL = 1.0 - smoothstep(glowCore, glowFall, distL); // 1 at center → 0 outward
            // clip to eye ellipse
            float clipL = smoothstep(0.0, -0.005, sdEllipse(pL, rad));
            glowL *= clipL;

            // RIGHT
            vec2  pR = uv - rightCenter;
            float distR = length(pR);
            float glowR = 1.0 - smoothstep(glowCore, glowFall, distR);
            float clipR = smoothstep(0.0, -0.005, sdEllipse(pR, rad));
            glowR *= clipR;

            // Combine + blink suppression
            float mask = (glowL + glowR) * (1.0 - isBlinking);
            // gradient is used to mix outer→inner emotion colors; stronger near center
            float grad = max(glowL, glowR);

            // Subtle “look-direction stretch” to fake perspective when far to the side
            // (makes the glow a tad elliptical as it approaches rim)
            float edgeStretch = mix(1.0, 0.85, smoothstep(0.65, 1.0, max(
                length(pL / rad), length(pR / rad))));
            grad *= edgeStretch;

            PupilData outData;
            outData.mask = mask;
            outData.gradient = grad;
            return outData;
        }
        
        // ---- 1) Glow under the pupil (this will be passed to mixPupilColor) ----
        PupilData drawIrisGlow(vec2 uv, vec2 leftCenter, vec2 rightCenter, float isBlinking) {
            // Eye ellipse (match drawEyes)
            float eyeRadiusX = 0.065;
            float eyeRadiusY = mix(0.075, 0.005, isBlinking);
            vec2  rad = vec2(eyeRadiusX, max(eyeRadiusY, 0.02));

            // Small downward bias so the glow appears "under" the black pupil
            vec2 bias = vec2(0.0, 0.006);

            // Soft glow size
            float glowCore = 0.020;
            float glowFall = 0.055;

            // LEFT
            vec2 pL = uv - (leftCenter + bias);
            float glowL = 1.0 - smoothstep(glowCore, glowFall, length(pL));
            float clipL = 1.0 - smoothstep(-0.005, 0.005, sdEllipse(pL, rad));
            glowL *= clipL;

            // RIGHT
            vec2 pR = uv - (rightCenter + bias);
            float glowR = 1.0 - smoothstep(glowCore, glowFall, length(pR));
            float clipR = 1.0 - smoothstep(-0.005, 0.005, sdEllipse(pR, rad));
            glowR *= clipR;

            PupilData outData;
            outData.mask     = (glowL + glowR) * (1.0 - isBlinking); // where to apply color
            outData.gradient = max(glowL, glowR);                    // center→rim ramp
            return outData;
        }

        // ---- 2) Dark pupil occluder on top (separate from the glow) ----
        struct BlackPupilData { float mask; float rim; };

BlackPupilData drawBlackPupils(vec2 uv, vec2 leftCenter, vec2 rightCenter, float isBlinking) {
    float eyeRadiusX = 0.065;
    float eyeRadiusY = mix(0.075, 0.005, isBlinking);
    vec2  rad = vec2(eyeRadiusX, max(eyeRadiusY, 0.02));

    const float pupilR = 0.022;
    const float feather = 0.010;

    // LEFT
    vec2 pL = uv - leftCenter;
    float dL = length(pL);
    float pupilDiskL = 1.0 - smoothstep(pupilR, pupilR + feather, dL);
    float clipL = 1.0 - smoothstep(-0.005, 0.005, sdEllipse(pL, rad));
    float leftMask = pupilDiskL * clipL;

    // RIGHT
    vec2 pR = uv - rightCenter;
    float dR = length(pR);
    float pupilDiskR = 1.0 - smoothstep(pupilR, pupilR + feather, dR);
    float clipR = 1.0 - smoothstep(-0.005, 0.005, sdEllipse(pR, rad));
    float rightMask = pupilDiskR * clipR;

    BlackPupilData outP;
    outP.mask = (leftMask + rightMask) * (1.0 - isBlinking);
    // tiny rim lift to keep the edge from sticker‑flat
    outP.rim  = max(
        smoothstep(pupilR*0.75, pupilR, dL) * leftMask,
        smoothstep(pupilR*0.75, pupilR, dR) * rightMask
    );
    return outP;
}

vec3 mixBlackPupil(vec3 base, BlackPupilData p) {
    if (p.mask <= 0.0) return base;
    vec3 pupil = vec3(0.0);
    pupil += vec3(0.03, 0.03, 0.035) * p.rim; // subtle spec/rim
    return mix(base, pupil, min(p.mask, 1.0));
}

        // same random jitter you had (feel free to keep your original constants)
        vec2 randomPupilOffset(float baseTime) {
            vec2 randVec = vec2(
                fract(sin(baseTime * 12.9898) * 43758.5453),
                fract(sin(baseTime * 78.233) * 96321.5487)
            );
            float angle = randVec.x * 6.2831; // 2π
            float radius = 0.004 + 0.006 * randVec.y; // small range
            return vec2(cos(angle), sin(angle)) * radius;
        }

        // Keep the glow blob safely inside the eye ellipse (with a small margin)
        vec2 clampPupilOffset(vec2 offset, vec2 eyeRadius, float margin) {
            // shrink the allowed ellipse by 'margin' so the glow doesn't touch the rim
            vec2 r = max(eyeRadius - vec2(margin), vec2(0.001));
            float d = length(offset / r);
            if (d > 1.0) offset /= d;
            return offset;
        }


        // --- emotion palette helpers (unchanged, yours) -----------------------
        vec3 getInnerPupilEmotionColor(float emotionId) {
            if (emotionId == 1.0) return vec3(1.0, 0.4, 0.4);
            if (emotionId == 2.0) return vec3(1.0, 1.0, 0.5);
            if (emotionId == 3.0) return vec3(0.4, 0.4, 1.0);
            if (emotionId == 4.0) return vec3(0.6, 0.9, 0.6);
            if (emotionId == 5.0) return vec3(1.0, 0.6, 1.0);
            return vec3(1.00, 0.72, 0.25);
        }
        vec3 getOutterPupilEmotionColor(float emotionId) {
            if (emotionId == 1.0) return vec3(0.3, 0.0, 0.0);
            if (emotionId == 2.0) return vec3(0.4, 0.4, 0.0);
            if (emotionId == 3.0) return vec3(0.0, 0.0, 0.3);
            if (emotionId == 4.0) return vec3(0.0, 0.3, 0.0);
            if (emotionId == 5.0) return vec3(0.3, 0.0, 0.3);
            return vec3(0.12, 0.06, 0.01);
        }

        // --- blink timing (unchanged, yours) ----------------------------------
        float isBlinking(float iTime) {
            float blinkSeed = floor(iTime / 6.0);
            float rand = fract(sin(blinkSeed * 91.345) * 47453.25);
            float nextBlinkTime = blinkSeed * 6.0 + rand * 3.0;
            float blinkDuration = 0.15;
            float timeSinceBlink = iTime - nextBlinkTime;
            return step(0.0, timeSinceBlink) * step(timeSinceBlink, blinkDuration);
        }

vec3 mixEyeColor(vec3 base, EyeData eyes) {
    if (eyes.mask <= 0.0) return base;

    // Relative darkening of the body color inside the eye
    // Center is a bit lighter, rim is darker.
    float centerDark = 0.55;  // 0 = black, 1 = unchanged
    float rimDark    = 0.25;
    float dark       = mix(centerDark, rimDark, eyes.gradient);

    // A tiny cool tint so it doesn’t look like a void
    vec3 tint = vec3(0.00, 0.02, 0.03); // subtle blue-green

    // Shade relative to the ghost body, not absolute black
    vec3 eyeShade = base * dark + tint * 0.10 * (1.0 - eyes.gradient);

    // Don’t fully overwrite the base; keep a bit of body showing through
    return mix(base, eyeShade, min(eyes.mask, 0.85));
}


        // --- pupil/ember color blend (unchanged, uses your states) ------------
        vec3 mixPupilColor(vec3 mixColor, PupilData pupils) {
            if (pupils.mask <= 0.0) return mixColor;

            vec3 startOuter  = getOutterPupilEmotionColor(uStartState);
            vec3 startInner  = getInnerPupilEmotionColor(uStartState);
            vec3 targetOuter = getOutterPupilEmotionColor(uTargetState);
            vec3 targetInner = getInnerPupilEmotionColor(uTargetState);

            vec3 startBlend  = mix(startOuter,  startInner,  pupils.gradient);
            vec3 targetBlend = mix(targetOuter, targetInner, pupils.gradient);
            vec3 pupilColor  = mix(startBlend,   targetBlend, uTransitionProgress);

            return mix(mixColor, pupilColor, pupils.mask);
        }

        // --- rim highlight / sockets (your same API) --------------------------
        vec3 mixEyeRimHighlightColor(vec3 mixColor, vec2 uv, vec2 leftEye, vec2 rightEye) {
            vec2 aboveLeftEye  = leftEye  + vec2(0.0, -0.065);
            vec2 aboveRightEye = rightEye + vec2(0.0, -0.065);
            float highlightRadius = 0.06;
            float eyeRimHighlight = max(
                smoothstep(highlightRadius, 0.0, length(uv - aboveLeftEye)),
                smoothstep(highlightRadius, 0.0, length(uv - aboveRightEye))
            );
            return mix(mixColor, vec3(1.0), 0.25 * eyeRimHighlight);
        }

        vec3 mixEyeSocketColor(vec3 mixColor, vec2 uv, vec2 leftEye, vec2 rightEye) {
            vec2 leftShadowPos  = leftEye  + vec2(0.0, 0.04);
            vec2 rightShadowPos = rightEye + vec2(0.0, 0.04);
            float shadowRadius = 0.085;
            float leftSocketShadow  = smoothstep(shadowRadius, 0.0, length(uv - leftShadowPos));
            float rightSocketShadow = smoothstep(shadowRadius, 0.0, length(uv - rightShadowPos));
            float eyeSocketShadow = max(leftSocketShadow, rightSocketShadow);
            mixColor = mix(mixColor, vec3(0.0, 0.2, 0.0), 0.4 * eyeSocketShadow);

            float aoRadius = 0.09;
            float aoStrength = 0.35;
            float leftAO  = smoothstep(aoRadius, 0.05, length(uv - leftEye));
            float rightAO = smoothstep(aoRadius, 0.05, length(uv - rightEye));
            float eyeAO = max(leftAO, rightAO);
            mixColor = mix(mixColor, vec3(0.0, 0.15, 0.0), aoStrength * eyeAO);

            return mixEyeRimHighlightColor(mixColor, uv, leftEye, rightEye);
        }
    """.trimIndent()
}
