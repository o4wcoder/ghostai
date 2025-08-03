package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Mouth {
    @Language("AGSL")
    val mouth = """
             MouthData drawMouth(vec2 uv, float iTime, float isSpeaking) {
            float baseMouthY = 0.08;
            float mouthWidth = 0.15;

            float baseMouthHeight = 0.01;

            // Idle "breathing" motion — slow and subtle
            float idleWiggle = 0.005 * sin(iTime * 1.5);

            // Use cycle-based random height variation
            float mouthCycle = floor(iTime * 2.0); // every 0.5 seconds
            float randHeight = 0.6 + 0.4 * fract(sin(mouthCycle * 17.0) * 43758.5453); // random between 0.6–1.0
            float speakingAmplitude = randHeight * abs(sin(iTime * 6.0));
            float talkingHeight = 0.05 * speakingAmplitude;

            // Combine them
            float mouthHeight = baseMouthHeight + idleWiggle * (1.0 - isSpeaking) + talkingHeight * isSpeaking;

            float mouthWiggle = 0.01 * sin(iTime * 1.0) * (1.0 - isSpeaking);
            vec2 mouthDelta = uv - vec2(mouthWiggle, baseMouthY);

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
