package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object EyesDark {
    @Language("AGSL")
    val eyes = """
// ====== structs ===============================================================
struct EyeData        { float mask; float gradient; };
struct PupilData      { float mask; float gradient; };
struct BlackPupilData { float mask; float rim; };
struct PupilHighlight { float mask; };

vec3  getInnerPupilEmotionColor(float emotionId);
vec3  getOutterPupilEmotionColor(float emotionId);
float isBlinking(float iTime);
vec2  randomPupilOffset(float baseTime);
vec2  clampPupilOffset(vec2 offset, vec2 eyeRadius, float margin);

// ====== core helpers ==========================================================
float sdEllipse(vec2 p, vec2 r) {
    r = max(r, vec2(0.02)); // guard small radii (blinks)
    vec2 q = p / r;
    return length(q) - 1.0;
}
float sdEllipse_fast(vec2 p, vec2 invRad) {
    return length(p * invRad) - 1.0;
}
bool outsideEyeBBox(vec2 uv, vec2 c, vec2 rad, float pad) {
    vec2 d = abs(uv - c);
    return (d.x > rad.x + pad) || (d.y > rad.y + pad);
}
// cheap ~x^0.6 curve without pow()
float pow06(float x){
    x = clamp(x, 0.0, 1.0);
    float s = sqrt(x);                 // x^0.5
    return mix(s, x, 0.2);             // ≈ x^0.6
}

vec2 eyeMaskAndRim(vec2 uv, vec2 center, vec2 rad, vec2 invRad) {
    float sdf  = sdEllipse_fast(uv - center, invRad);  // < 0 inside
    float mask = 1.0 - smoothstep(-0.01, 0.01, sdf);

    float norm = clamp(length((uv - center) * invRad), 0.0, 1.0);
    float rim  = smoothstep(0.75, 1.0, norm);
    return vec2(mask, rim);
}

// ===== iris underlay (bottom-bright) =========================================
vec2 irisForEye(vec2 p, vec2 rad, vec2 invRad, float pupilR) {
    float sdfEye = sdEllipse_fast(p, invRad);
    float clip   = 1.0 - smoothstep(-0.005, 0.005, sdfEye);

    float d = length(p);
    float inner = pupilR - 0.004;
    float mid   = pupilR + 0.015;
    float outer = pupilR + 0.080;

    float core  = 1.0 - smoothstep(inner, mid,   d);
    float skirt = 1.0 - smoothstep(mid,   outer, d);

    vec2  pn = p * invRad;
    const float IRIS_FADE_TOP    = -0.85;
    const float IRIS_FADE_BOTTOM =  0.55;
    float v = smoothstep(IRIS_FADE_TOP, IRIS_FADE_BOTTOM, pn.y);
    v = pow06(v);

    float side = 1.0 - smoothstep(0.80, 1.05, abs(pn.x));

    float mask = (0.55 * core + 0.45 * skirt) * v * side * clip;
    float grad = (0.80 * core + 0.20 * skirt) * v;

    mask = clamp(mask * 1.6, 0.0, 1.0);
    grad = clamp(grad * 1.35, 0.0, 1.0);
    return vec2(mask, grad);
}

PupilData drawIrisUnderlay(vec2 uv, vec2 leftCenter, vec2 rightCenter, float isBlinking) {
    float eyeRadiusX = 0.065;
    float eyeRadiusY = max(mix(0.075, 0.005, isBlinking), 0.02);
    vec2  rad    = vec2(eyeRadiusX, eyeRadiusY);
    vec2  invRad = 1.0 / rad;
    float pupilR = 0.022;

    float PAD = 0.04;
    if (outsideEyeBBox(uv, leftCenter,  rad, PAD) &&
        outsideEyeBBox(uv, rightCenter, rad, PAD)) {
        PupilData z; z.mask = 0.0; z.gradient = 0.0; return z;
    }

    vec2 L = irisForEye(uv - leftCenter,  rad, invRad, pupilR);
    vec2 R = irisForEye(uv - rightCenter, rad, invRad, pupilR);

    PupilData outIris;
    outIris.mask     = (L.x + R.x) * (1.0 - isBlinking);
    outIris.gradient = max(L.y, R.y);
    return outIris;
}

// ===== sockets (dark eye area) ===============================================
EyeData drawEyes(vec2 uv, vec2 leftEye, vec2 rightEye, float isBlinking) {
    float eyeRadiusX = 0.065;
    float eyeRadiusY = max(mix(0.075, 0.005, isBlinking), 0.02);
    vec2  rad    = vec2(eyeRadiusX, eyeRadiusY);
    vec2  invRad = 1.0 / rad;

    float PAD = 0.05;
    if (outsideEyeBBox(uv, leftEye,  rad, PAD) &&
        outsideEyeBBox(uv, rightEye, rad, PAD)) {
        EyeData z; z.mask = 0.0; z.gradient = 0.0; return z;
    }

    vec2 L = eyeMaskAndRim(uv, leftEye,  rad, invRad);
    vec2 R = eyeMaskAndRim(uv, rightEye, rad, invRad);

    float mask = max(L.x, R.x);
    float rim  = max(L.y, R.y);

    float vL = clamp((uv.y - leftEye.y)  / rad.y,  0.0, 1.0);
    float vR = clamp((uv.y - rightEye.y) / rad.y,  0.0, 1.0);
    float vert = max(vL, vR);

    float gradient = clamp(rim * 0.85 + vert * 0.15, 0.0, 1.0);

    EyeData outData;
    outData.mask = mask;
    outData.gradient = gradient;
    return outData;
}

// ===== black pupils ===========================================================
BlackPupilData drawBlackPupils(vec2 uv, vec2 leftCenter, vec2 rightCenter, float isBlinking) {
    float eyeRadiusX = 0.065;
    float eyeRadiusY = max(mix(0.075, 0.005, isBlinking), 0.02);
    vec2  rad    = vec2(eyeRadiusX, eyeRadiusY);
    vec2  invRad = 1.0 / rad;

    float pupilRx = 0.042;
    vec2  pupilRad    = vec2(pupilRx, pupilRx * (rad.y / rad.x));
    vec2  invPupilRad = 1.0 / pupilRad;

    float PAD = 0.045;
    if (outsideEyeBBox(uv, leftCenter,  rad, PAD) &&
        outsideEyeBBox(uv, rightCenter, rad, PAD)) {
        BlackPupilData z; z.mask = 0.0; z.rim = 0.0; return z;
    }

    vec2 pL = uv - leftCenter;
    float sL = sdEllipse_fast(pL, invPupilRad); // <0 inside pupil
    float pupilL = 1.0 - smoothstep(-0.06, 0.0, sL);
    float clipL  = 1.0 - smoothstep(-0.005, 0.005, sdEllipse_fast(pL, invRad));
    float leftMask = pupilL * clipL;

    vec2 pR = uv - rightCenter;
    float sR = sdEllipse_fast(pR, invPupilRad);
    float pupilR = 1.0 - smoothstep(-0.06, 0.0, sR);
    float clipR  = 1.0 - smoothstep(-0.005, 0.005, sdEllipse_fast(pR, invRad));
    float rightMask = pupilR * clipR;

    BlackPupilData outP;
    outP.mask = (leftMask + rightMask) * (1.0 - isBlinking);

    float rimL = smoothstep(-0.036, 0.0, sL) * leftMask;
    float rimR = smoothstep(-0.036, 0.0, sR) * rightMask;
    outP.rim = max(rimL, rimR);
    return outP;
}

// ===== highlight dot ==========================================================
PupilHighlight drawPupilHighlight(vec2 uv, vec2 leftCenter, vec2 rightCenter, float isBlinking) {
    float eyeRadiusX = 0.065;
    float eyeRadiusY = max(mix(0.075, 0.005, isBlinking), 0.02);
    vec2  rad    = vec2(eyeRadiusX, eyeRadiusY);
    vec2  invRad = 1.0 / rad;

    float pupilRx = 0.042;                         // keep in sync with pupils
    vec2  pupilRad    = vec2(pupilRx, pupilRx * (rad.y / rad.x));
    vec2  invPupilRad = 1.0 / pupilRad;

    float PAD = 0.06;
    if (outsideEyeBBox(uv, leftCenter,  rad, PAD) &&
        outsideEyeBBox(uv, rightCenter, rad, PAD)) {
        PupilHighlight z; z.mask = 0.0; return z;
    }

    float dotScaleN   = 0.24;
    float dotFeatherN = 0.06;
    vec2  dotOffsetN  = vec2(-0.28, -0.32);

    vec2 pL = (uv - (leftCenter  + dotOffsetN * pupilRad)) * invPupilRad;
    float dL = length(pL);
    float dotL = 1.0 - smoothstep(dotScaleN, dotScaleN + dotFeatherN, dL);
    float inPupilL = 1.0 - smoothstep(1.0, 1.02, length((uv - leftCenter) * invPupilRad));
    float left = dotL * inPupilL;

    vec2 pR = (uv - (rightCenter + dotOffsetN * pupilRad)) * invPupilRad;
    float dR = length(pR);
    float dotR = 1.0 - smoothstep(dotScaleN, dotScaleN + dotFeatherN, dR);
    float inPupilR = 1.0 - smoothstep(1.0, 1.02, length((uv - rightCenter) * invPupilRad));
    float right = dotR * inPupilR;

    PupilHighlight h; h.mask = (left + right) * (1.0 - isBlinking);
    return h;
}

// ===== mixes =================================================================
vec3 mixBlackPupil(vec3 base, BlackPupilData p) {
    if (p.mask <= 0.0) return base;
    vec3 pupil = vec3(0.0);
    pupil += vec3(0.03, 0.03, 0.035) * p.rim; // subtle spec/rim
    return mix(base, pupil, min(p.mask, 1.0));
}

vec3 mixEyeColor(vec3 base, EyeData eyes) {
    if (eyes.mask <= 0.0) return base;

    float centerDark = 0.55;  // 0 = black, 1 = unchanged
    float rimDark    = 0.25;
    float dark       = mix(centerDark, rimDark, eyes.gradient);
    vec3  tint       = vec3(0.00, 0.02, 0.03);

    vec3 eyeShade = base * dark + tint * 0.10 * (1.0 - eyes.gradient);
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

    // make the underlay pop a bit more
    pupilColor = clamp(pupilColor * vec3(1.10, 0.90, 0.60) * 1.20, 0.0, 2.0);

    float a = clamp(pupils.mask * 1.25, 0.0, 1.0);
    mixColor = mix(mixColor, pupilColor, a);
    mixColor += pupilColor * (0.22 * pupils.gradient * a);
    return mixColor;
}

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

// Helper: compute bevel ring, cavity, and top-rim highlight for one eye
void socketMasks(vec2 uv, vec2 center,
                 float rOuter, float rimWidth, float rimFeather,
                 float innerFeather, float ySquash,
                 out float ring, out float cavity, out float topRim) {

    vec2 p = vec2(uv.x - center.x, (uv.y - center.y) * ySquash);
    float d = length(p);
    float rInner = rOuter - rimWidth;

    // crisp ring between rOuter and rInner
    float outerStep = smoothstep(rOuter,      rOuter - rimFeather, d);
    float innerStep = smoothstep(rInner,      rInner - rimFeather, d);
    ring = clamp(outerStep - innerStep, 0.0, 1.0);

    // shallow cavity inside rInner (minimal gradient)
    cavity = 1.0 - smoothstep(rInner, rInner - innerFeather, d);

    // top highlight along the inner rim (y negative = up)
    vec2 dir = (d > 1e-4) ? (p / d) : vec2(0.0, -1.0);
    topRim = ring * smoothstep(0.1, 0.8, -dir.y);
}

// Eye sockets for a white ghost: top-weighted bevel ring + subtle cavity
vec3 mixEyeSocketColor(vec3 col, vec2 uv, vec2 leftEye, vec2 rightEye) {
    // tweakables
    float rOuter       = 0.085;
    float rimWidth     = 0.018;
    float rimFeather   = 0.006;
    float innerFeather = 0.010;
    float ySquash      = 0.92;

    // Bevel masks
    float ringL, cavL, topL;
    float ringR, cavR, topR;
    socketMasks(uv, leftEye,  rOuter, rimWidth, rimFeather, innerFeather, ySquash, ringL, cavL, topL);
    socketMasks(uv, rightEye, rOuter, rimWidth, rimFeather, innerFeather, ySquash, ringR, cavR, topR);

    float ring   = max(ringL, ringR);
    float cavity = max(cavL,  cavR);
    float topRim = max(topL,  topR);

    // TOP bias for the ring (y is negative upward in your space)
    float upL = smoothstep(-0.20, 0.80, -((uv.y - leftEye.y)  * ySquash) / max(rOuter, 1e-5));
    float upR = smoothstep(-0.20, 0.80, -((uv.y - rightEye.y) * ySquash) / max(rOuter, 1e-5));
    float ringTop = max(ringL * upL, ringR * upR);   // ring only in upper half

    // keep a hint on the bottom so it doesn’t vanish
    float ringWeighted = 0.35 * ring + 0.85 * ringTop;

    // cool ring color; boost on bright side so it doesn’t wash out
    vec3 ringColor = vec3(0.82, 0.86, 0.98);
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    float boost = 0.40 * smoothstep(0.75, 1.05, lum);
    col = mix(col, ringColor, (0.55 + boost) * ringWeighted);

    // shallow cavity
    vec3 cavColor = vec3(0.90, 0.94, 1.02);
    col = mix(col, cavColor, 0.22 * cavity);

    // tiny white sparkle along the top inner rim
    col += 0.04 * topRim;

    return col;
}

// screen-blend a white highlight dot on top of the pupil
vec3 mixPupilHighlight(vec3 base, PupilHighlight h) {
    if (h.mask <= 0.0) return base;
    float strength = 0.9; // 0.6–1.0
    vec3  w = vec3(1.0) * (strength * h.mask);
    // screen blend
    return 1.0 - (1.0 - base) * (1.0 - w);
}


// ===== glow under the pupil (kept) ===========================================
PupilData drawIrisGlow(vec2 uv, vec2 leftCenter, vec2 rightCenter, float isBlinking) {
    float eyeRadiusX = 0.065;
    float eyeRadiusY = max(mix(0.075, 0.005, isBlinking), 0.02);
    vec2  rad = vec2(eyeRadiusX, eyeRadiusY);

    vec2 bias = vec2(0.0, 0.006);
    float glowCore = 0.020;
    float glowFall = 0.055;

    vec2 pL = uv - (leftCenter + bias);
    float glowL = 1.0 - smoothstep(glowCore, glowFall, length(pL));
    float clipL = 1.0 - smoothstep(-0.005, 0.005, sdEllipse(pL, rad));
    glowL *= clipL;

    vec2 pR = uv - (rightCenter + bias);
    float glowR = 1.0 - smoothstep(glowCore, glowFall, length(pR));
    float clipR = 1.0 - smoothstep(-0.005, 0.005, sdEllipse(pR, rad));
    glowR *= clipR;

    PupilData outData;
    outData.mask     = (glowL + glowR) * (1.0 - isBlinking);
    outData.gradient = max(glowL, glowR);
    return outData;
}

// ===== your external functions (definitions restored) ========================
vec3 getInnerPupilEmotionColor(float emotionId) {
    if (emotionId == 1.0) return vec3(1.0, 0.4, 0.4); // Angry - red
    if (emotionId == 2.0) return vec3(1.0, 1.0, 0.5);
    if (emotionId == 3.0) return vec3(0.4, 0.4, 1.0);
    if (emotionId == 4.0) return vec3(0.6, 0.9, 0.6);
    if (emotionId == 5.0) return vec3(1.0, 0.6, 1.0);
    return vec3(1.15, 0.88, 0.28);
}
vec3 getOutterPupilEmotionColor(float emotionId) {
    if (emotionId == 1.0) return vec3(0.3, 0.0, 0.0); // Angry - red
    if (emotionId == 2.0) return vec3(0.4, 0.4, 0.0);
    if (emotionId == 3.0) return vec3(0.0, 0.0, 0.3);
    if (emotionId == 4.0) return vec3(0.0, 0.3, 0.0);
    if (emotionId == 5.0) return vec3(0.3, 0.0, 0.3);
    return vec3(0.25, 0.12, 0.02);
}
float isBlinking(float iTime) {
    float blinkSeed = floor(iTime / 6.0);
    float rand = fract(sin(blinkSeed * 91.345) * 47453.25);
    float nextBlinkTime = blinkSeed * 6.0 + rand * 3.0;
    float blinkDuration = 0.15;
    float timeSinceBlink = iTime - nextBlinkTime;
    return step(0.0, timeSinceBlink) * step(timeSinceBlink, blinkDuration);
}
vec2 randomPupilOffset(float baseTime) {
    vec2 randVec = vec2(
        fract(sin(baseTime * 12.9898) * 43758.5453),
        fract(sin(baseTime * 78.233)  * 96321.5487)
    );
    float angle = randVec.x * 6.2831;          // 2π
    float radius = 0.004 + 0.006 * randVec.y;  // small range
    return vec2(cos(angle), sin(angle)) * radius;
}
vec2 clampPupilOffset(vec2 offset, vec2 eyeRadius, float margin) {
    vec2 r = max(eyeRadius - vec2(margin), vec2(0.001));
    float d = length(offset / r);
    if (d > 1.0) offset /= d;
    return offset;
}
"""
}
