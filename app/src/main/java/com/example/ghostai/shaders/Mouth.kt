package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Mouth {
    @Language("AGSL")
    val mouth = """
        float getMouthFrownAmount(float emotionId) {
            if (emotionId == 1.0) {         // Angry
                return 1.0;                 // Deep frown
            } else if (emotionId == 2.0) {  // Happy
                return -0.5;                // Slight smile
            } else if (emotionId == 3.0) {  // Sad
                return 0.5;                 // Gentle frown
            } else {
                return 0.0;                 // Neutral / default
            }
        }
        
        MouthData drawMouth(vec2 uv, float iTime, float isSpeaking) {
            float baseMouthY = 0.08;

            float baseMouthHeight = 0.01;

            // Randomly trigger O-mouth shape while speaking ===
            // O-mouth logic: fade in and out smoothly
            float oShapeCycle = floor(iTime * 2.5); // every 0.4s
            float oShapeRand = fract(sin(oShapeCycle * 23.0) * 65437.234);
            float oShapeActive = step(0.82, oShapeRand) * isSpeaking;

            float cycleProgress = fract(iTime * 2.5); // progress through the 0.4s cycle
            float fadeIn = smoothstep(0.0, 0.2, cycleProgress);
            float fadeOut = 1.0 - smoothstep(0.8, 1.0, cycleProgress);
            float oShapeWeight = oShapeActive * min(fadeIn, fadeOut); // fades in and out

            float oShapeFactor = mix(1.0, 0.5, oShapeWeight);
            float oHeightFactor = mix(1.0, 1.8, oShapeWeight);

            // Idle "breathing" motion — slow and subtle
            float idleWiggle = 0.005 * sin(iTime * 1.5);

            // Use cycle-based random height variation
            float mouthCycle = floor(iTime * 2.0); // every 0.5 seconds
            float randHeight = 0.6 + 0.4 * fract(sin(mouthCycle * 17.0) * 43758.5453); // random between 0.6–1.0
            float speakingAmplitude = randHeight * abs(sin(iTime * 6.0));
            float talkingHeight = 0.05 * speakingAmplitude;

            // Combine them
            float mouthWidth = 0.12 * oShapeFactor;
            float mouthHeight = baseMouthHeight + idleWiggle * (1.0 - isSpeaking) + talkingHeight * isSpeaking;

            float mouthWiggle = 0.01 * sin(iTime * 1.0) * (1.0 - isSpeaking);
            vec2 mouthDelta = uv - vec2(mouthWiggle, baseMouthY);
            
                // === Emotion-based frown/smile curvature ===
            float frownStart = getMouthFrownAmount(uStartState);
            float frownTarget = getMouthFrownAmount(uTargetState);
            float frownAmount = mix(frownStart, frownTarget, uTransitionProgress);

            float curve = -frownAmount * (mouthDelta.x * mouthDelta.x - mouthWidth * mouthWidth);
            mouthDelta.y += curve;

            // Distort the sides of the mouth
            float phase = isSpeaking > 0.0 ? iTime * 5.0 : 0.0;
            float mouthShapeWarp = 1.0 + 0.1 * sin(mouthDelta.x * 8.0 + iTime * 2.0);

            vec2 warpedDelta = vec2(mouthDelta.x / mouthWidth, mouthDelta.y / (mouthHeight * mouthShapeWarp));
            float mouthMask = step(length(warpedDelta), 1.0);

            float mouthGradient = smoothstep(0.0, 1.0, length(vec2(mouthDelta.x / mouthWidth, mouthDelta.y / mouthHeight)));

            MouthData result;
            result.mask = mouthMask;
            result.gradient = mouthGradient;
            return result;
        }
    """.trimIndent()
}
