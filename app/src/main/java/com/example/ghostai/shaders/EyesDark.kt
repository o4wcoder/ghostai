package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object EyesDark {
    @Language("AGSL")
    val eyes = """

    struct BlackPupilData { float mask; float rim; };
    struct PupilHighlight { float mask; };
    
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


// helper: shape the bottom-bright iris for one eye
vec2 irisForEye(vec2 p, vec2 rad, float pupilR) {
    vec2  pn = p / rad;              // normalized in eye space
    float d  = length(p);            // radial distance in pixels

    // radial shaping around the pupil edge (no harsh rings)
    float inner = pupilR - 0.004;
    float mid   = pupilR + 0.015;
    float outer = pupilR + 0.080;

    float core  = 1.0 - smoothstep(inner, mid,   d); // strong near center
    float skirt = 1.0 - smoothstep(mid,   outer, d); // soft halo outside

    // vertical falloff: 0 at top → 1 at bottom (smooth, then eased)
    float v = pow(smoothstep(-0.25, 0.70, pn.y), 1.5);

    // side taper to avoid a full ring at far left/right
    float side = 1.0 - smoothstep(0.80, 1.05, abs(pn.x));

    // clip to eye ellipse (soft)
    float clip = 1.0 - smoothstep(-0.005, 0.005, sdEllipse(p, rad));

    float mask = (0.55 * core + 0.45 * skirt) * v * side * clip;
    float grad = (0.80 * core + 0.20 * skirt) * v;    // use for inner→outer amber mix
    
        mask = clamp(mask * 1.6, 0.0, 1.0);   // <— add this
        grad = clamp(pow(grad, 0.6) * 1.35, 0.0, 1.0);  
    return vec2(mask, grad);
}

// ===== IRIS UNDERLAY (bottom-bright crescent that fades upward) =====
PupilData drawIrisUnderlay(vec2 uv, vec2 leftCenter, vec2 rightCenter, float isBlinking) {
    float eyeRadiusX = 0.065;
    float eyeRadiusY = mix(0.075, 0.005, isBlinking);
    vec2  rad = vec2(eyeRadiusX, max(eyeRadiusY, 0.02));
    float pupilR = 0.022;

    // LEFT
    vec2 pL = uv - leftCenter;
    vec2 L  = irisForEye(pL, rad, pupilR);

    // RIGHT
    vec2 pR = uv - rightCenter;
    vec2 R  = irisForEye(pR, rad, pupilR);

    PupilData outIris;
    outIris.mask     = (L.x + R.x) * (1.0 - isBlinking);
    outIris.gradient = max(L.y, R.y);
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
        
BlackPupilData drawBlackPupils(vec2 uv, vec2 leftCenter, vec2 rightCenter, float isBlinking) {
    float eyeRadiusX = 0.065;
    float eyeRadiusY = mix(0.075, 0.005, isBlinking);
    vec2  rad = vec2(eyeRadiusX, max(eyeRadiusY, 0.02));

    // Elliptical pupil: keep the same aspect ratio as the eye
    float pupilRx = 0.042;                              // horizontal size
    vec2  pupilRad = vec2(pupilRx, pupilRx * (rad.y / rad.x));

    // Feather in *SDF space* (sdEllipse returns length(p/pupilRad) - 1)
    float featherN = 0.06;

    // LEFT
    vec2 pL = uv - leftCenter;
    float sL = sdEllipse(pL, pupilRad);                 // < 0 inside ellipse
    float pupilL = 1.0 - smoothstep(-featherN, 0.0, sL);
    float clipL  = 1.0 - smoothstep(-0.005, 0.005, sdEllipse(pL, rad));
    float leftMask = pupilL * clipL;

    // RIGHT
    vec2 pR = uv - rightCenter;
    float sR = sdEllipse(pR, pupilRad);
    float pupilR = 1.0 - smoothstep(-featherN, 0.0, sR);
    float clipR  = 1.0 - smoothstep(-0.005, 0.005, sdEllipse(pR, rad));
    float rightMask = pupilR * clipR;

    BlackPupilData outP;
    outP.mask = (leftMask + rightMask) * (1.0 - isBlinking);

    // tiny spec on the rim so it doesn’t look sticker-flat
    float rimL = smoothstep(-featherN*0.6, 0.0, sL) * leftMask;
    float rimR = smoothstep(-featherN*0.6, 0.0, sR) * rightMask;
    outP.rim = max(rimL, rimR);
    return outP;
}

PupilHighlight drawPupilHighlight(vec2 uv, vec2 leftCenter, vec2 rightCenter, float isBlinking) {
    float eyeRadiusX = 0.065;
    float eyeRadiusY = mix(0.075, 0.005, isBlinking);
    vec2  rad = vec2(eyeRadiusX, max(eyeRadiusY, 0.02));

    float pupilRx = 0.042;                         // keep in sync with drawBlackPupils
    vec2  pupilRad = vec2(pupilRx, pupilRx * (rad.y / rad.x));

    // 🔧 KNOBS (all in pupil-normalized space)
    float dotScaleN   = 0.24;                      // radius (0.10–0.22). Smaller = smaller dot.
    float dotFeatherN = 0.06;                      // edge softness (0.03–0.10)
    vec2  dotOffsetN  = vec2(-0.28, -0.32);        // position relative to pupil center

    // LEFT
    vec2 pL = (uv - (leftCenter  + dotOffsetN * pupilRad)) / pupilRad;
    float dL = length(pL);
    float dotL = 1.0 - smoothstep(dotScaleN, dotScaleN + dotFeatherN, dL);
    float inPupilL = 1.0 - smoothstep(1.0, 1.02, length((uv - leftCenter) / pupilRad));
    float left = dotL * inPupilL;

    // RIGHT
    vec2 pR = (uv - (rightCenter + dotOffsetN * pupilRad)) / pupilRad;
    float dR = length(pR);
    float dotR = 1.0 - smoothstep(dotScaleN, dotScaleN + dotFeatherN, dR);
    float inPupilR = 1.0 - smoothstep(1.0, 1.02, length((uv - rightCenter) / pupilRad));
    float right = dotR * inPupilR;

    PupilHighlight h;
    h.mask = (left + right) * (1.0 - isBlinking);
    return h;
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
            if (emotionId == 1.0) return vec3(1.0, 0.4, 0.4); //Angry - red
            if (emotionId == 2.0) return vec3(1.0, 1.0, 0.5);
            if (emotionId == 3.0) return vec3(0.4, 0.4, 1.0);
            if (emotionId == 4.0) return vec3(0.6, 0.9, 0.6);
            if (emotionId == 5.0) return vec3(1.0, 0.6, 1.0);
            return vec3(1.15, 0.88, 0.28);
        }
        vec3 getOutterPupilEmotionColor(float emotionId) {
            if (emotionId == 1.0) return vec3(0.3, 0.0, 0.0); //Angry - red
            if (emotionId == 2.0) return vec3(0.4, 0.4, 0.0);
            if (emotionId == 3.0) return vec3(0.0, 0.0, 0.3);
            if (emotionId == 4.0) return vec3(0.0, 0.3, 0.0);
            if (emotionId == 5.0) return vec3(0.3, 0.0, 0.3);
            return vec3(0.25, 0.12, 0.02);
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

vec3 mixPupilColor(vec3 mixColor, PupilData pupils) {
    if (pupils.mask <= 0.0) return mixColor;

    vec3 startOuter  = getOutterPupilEmotionColor(uStartState);
    vec3 startInner  = getInnerPupilEmotionColor(uStartState);
    vec3 targetOuter = getOutterPupilEmotionColor(uTargetState);
    vec3 targetInner = getInnerPupilEmotionColor(uTargetState);

    vec3 startBlend  = mix(startOuter,  startInner,  pupils.gradient);
    vec3 targetBlend = mix(targetOuter, targetInner, pupils.gradient);
    vec3 pupilColor  = mix(startBlend,   targetBlend, uTransitionProgress);

    // Stronger replacement where the iris exists
    float a = clamp(pupils.mask * 1.35, 0.0, 1.0);
    vec3 outCol = mix(mixColor, pupilColor, a);

    // Subtle emission so it doesn’t get flattened by the dark socket
    outCol += pupilColor * (0.35 * pupils.gradient * pupils.mask);

    return outCol;
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
        
        // screen-blend a white highlight on top
        vec3 mixPupilHighlight(vec3 base, PupilHighlight h) {
            if (h.mask <= 0.0) return base;
            float strength = 0.9; // 0.6–1.0
            vec3  w = vec3(1.0) * (strength * h.mask);
            // screen blend
            return 1.0 - (1.0 - base) * (1.0 - w);
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
