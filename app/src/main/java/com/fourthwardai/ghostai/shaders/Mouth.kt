package com.fourthwardai.ghostai.shaders

import org.intellij.lang.annotations.Language

object Mouth {
    @Language("AGSL")
    val mouth = """
float getMouthFrownAmount(float emotionId) {
    if (emotionId == 1.0) return 1.0;   // Angry → deep frown
    if (emotionId == 2.0) return -0.5;  // Happy → slight smile
    if (emotionId == 3.0) return 0.5;   // Sad → gentle frown
    return 0.0;                         // Neutral / default
}

MouthData drawMouth(vec2 uv, float iTime, float isSpeaking) {
    // --- placement / base size ------------------------------------------------
    const float baseMouthY      = -0.02;
    const float baseMouthHeight =  0.01;

    // --- O-mouth (uniform across pixels) -------------------------------------
    float oShapeCycle   = floor(iTime * 2.5); // every 0.4s
    float oShapeRand    = fract(sin(oShapeCycle * 23.0) * 65437.234);
    float oShapeActive  = step(0.82, oShapeRand) * isSpeaking;
    float cycleProgress = fract(iTime * 2.5);
    float fadeIn        = smoothstep(0.0, 0.2, cycleProgress);
    float fadeOut       = 1.0 - smoothstep(0.8, 1.0, cycleProgress);
    float oShapeWeight  = oShapeActive * min(fadeIn, fadeOut);
    float oShapeFactor  = mix(1.0, 0.5, oShapeWeight);
    float oHeightFactor = mix(1.0, 1.8, oShapeWeight);

    // --- subtle idle wiggle + speaking height (uniform) ----------------------
    float idleWiggle        = 0.005 * sin(iTime * 1.5);
    float mouthCycle        = floor(iTime * 2.0); // every 0.5s
    float randHeight        = 0.6 + 0.4 * fract(sin(mouthCycle * 17.0) * 43758.5453);
    float speakingAmplitude = randHeight * abs(sin(iTime * 6.0));
    float talkingHeight     = 0.05 * speakingAmplitude;

    // --- derived dims ---------------------------------------------------------
    float mouthWidth  = 0.12 * oShapeFactor;
    float mouthHeight = baseMouthHeight + idleWiggle * (1.0 - isSpeaking) + talkingHeight * isSpeaking;

    // --- center / bbox (fast early out) --------------------------------------
    float mouthWiggle = 0.01 * sin(iTime * 1.0) * (1.0 - isSpeaking);
    vec2  mouthCenter = vec2(mouthWiggle, baseMouthY);

    // rectangle bounds in *un-normalized* space; small pad avoids popping
    float PAD_X = mouthWidth  * 1.25;
    float PAD_Y = mouthHeight * oHeightFactor * 2.2;
    vec2  dxy   = abs(uv - mouthCenter);
    if (dxy.x > PAD_X || dxy.y > PAD_Y) {
        MouthData zero; zero.mask = 0.0; zero.gradient = 0.0; zero.bottomLipShadow = 0.0; return zero;
    }

    // --- shape & curvature ----------------------------------------------------
    vec2 mouthDelta = uv - mouthCenter;

    // Emotion curvature (parabola)
    float frownStart  = getMouthFrownAmount(uStartState);
    float frownTarget = getMouthFrownAmount(uTargetState);
    float frownAmount = mix(frownStart, frownTarget, uTransitionProgress);
    mouthDelta.y += -frownAmount * (mouthDelta.x * mouthDelta.x - mouthWidth * mouthWidth);

    // Gentle “wobble” (y only)
    float mouthShapeWarp = 1.0 + 0.1 * sin(mouthDelta.x * 8.0 + iTime * 2.0);

    // Normalize once; reuse for mask & gradient
    float invW = 1.0 / mouthWidth;
    float invH = 1.0 / (mouthHeight * mouthShapeWarp);
    vec2  n    = vec2(mouthDelta.x * invW, mouthDelta.y * invH);
    float r    = length(n);

    float mouthMask     = step(r, 1.0);                  // inside ellipse
    float mouthGradient = smoothstep(0.0, 1.0, r);       // center→rim

    // --- bottom-lip shadow (cheap crescent) ----------------------------------
    // shift down by 1.0 in normalized space, slight horizontal stretch
    vec2  nShadow    = vec2(n.x * 1.2, n.y - 1.0);
    float rShadow    = length(nShadow);
    float baseShadow = smoothstep(1.0, 0.7, rShadow);
    float verticalCut= smoothstep(0.0, 0.4, nShadow.y);  // cuts off the top
    float crescent   = baseShadow * verticalCut;
    float bottomLipShadowStrength = 0.07 * crescent * isSpeaking;

    // --- pack result ----------------------------------------------------------
    MouthData result;
    result.mask            = mouthMask;
    result.gradient        = mouthGradient;
    result.bottomLipShadow = bottomLipShadowStrength;
    return result;
}

vec3 mixMouthColor(vec3 mixColor, MouthData mouth) {
    // Subtle shadow under bottom lip (underlay)
    vec3 lipShadowColor = vec3(0.0, 0.0, 0.1);
    mixColor = mix(mixColor, lipShadowColor, mouth.bottomLipShadow);

    if (mouth.mask > 0.0) {
        // dark rim toward edge, lighter center
        vec3 mouthOuterColor = vec3(0.2, 0.2, 0.3);
        vec3 mouthInnerColor = vec3(0.0);
        vec3 mouthColor      = mix(mouthInnerColor, mouthOuterColor, mouth.gradient);
        return mix(mixColor, mouthColor, mouth.mask);
    }
    return mixColor;
}
    """.trimIndent()
}
