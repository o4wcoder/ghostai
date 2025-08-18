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
            float mouthShapeWarp = 1.0 + 0.1 * sin(mouthDelta.x * 8.0 + iTime * 2.0);

            vec2 warpedDelta = vec2(mouthDelta.x / mouthWidth, mouthDelta.y / (mouthHeight * mouthShapeWarp));
            float mouthMask = step(length(warpedDelta), 1.0);
            
            // Top lip shadow — positioned above the top highlight
//            vec2 topShadowOffset = warpedDelta + vec2(0.0, 1.2); // further up than highlight
//            vec2 topShadowShape = vec2(topShadowOffset.x * 1.2, topShadowOffset.y); // stretch horizontally
//
//            float shadowFalloff = smoothstep(1.2, 0.8, length(topShadowShape));
//
//            // Fade it out above — this avoids a mustache effect
//            float verticalFadeShadow = smoothstep(0.6, 0.0, topShadowOffset.y);
//
//            // Optional: Taper at the sides
//            float horizontalFadeShadow = smoothstep(1.2, 0.5, abs(topShadowOffset.x));
//
//            float topLipShadow = shadowFalloff * verticalFadeShadow * horizontalFadeShadow;
//            float topListShadowStrength = topLipShadow * isSpeaking;
            
            // Top lip highlight — positioned above the mouth
            vec2 topOffset = warpedDelta + vec2(0.0, 0.5); // move "target" area upward
            vec2 topLipShape = vec2(topOffset.x * 1.4, topOffset.y); // slightly stretched

            float baseHighlight = smoothstep(1.1, 0.7, length(topLipShape));
            float verticalFade = smoothstep(0.4, 0.0, topOffset.y); // 1.0 above, fades out toward mouth
            float horizontalFade = smoothstep(1.2, 0.5, abs(topOffset.x));
            float topLipHighlight = baseHighlight * verticalFade * horizontalFade;


            // Shadow under mouth to simulate lip
            // Simulate a shadow just under the bottom of the mouth
            vec2 shadowOffset = warpedDelta  + vec2(0.0, -1.0); // move "target" area downward
            vec2 lipShapeOffset = vec2(shadowOffset.x * 1.2, shadowOffset.y);
            float baseShadow = smoothstep(1.0, 0.7, length(lipShapeOffset));

            // Clip off the upper part to make a crescent
            float verticalCutoff = smoothstep(0.0, 0.4, shadowOffset.y); // fades out above 0
            float crescentShadow = baseShadow * verticalCutoff;

            float bottomLipShadowStrength = 0.07 * crescentShadow * isSpeaking;

            float mouthGradient = smoothstep(0.0, 1.0, length(vec2(mouthDelta.x / mouthWidth, mouthDelta.y / mouthHeight)));

            MouthData result;
            result.mask = mouthMask;
            result.gradient = mouthGradient;
         //   result.topLipShadow = topListShadowStrength;
            result.bottomLipShadow = bottomLipShadowStrength;
          //  result.lipHighlight = topLipHighlight;
            return result;
        }
        
        vec3 mixMouthColor(vec3 mixColor, MouthData mouth) {
        //            vec3 lipShadowColor = vec3(0.0, 0.1, 0.0); // soft green-black shadow
//            finalColor = mix(finalColor, lipShadowColor, 0.05 * mouth.topLipShadow);
//            
//            // Subtle highlight above top lip
//            vec3 topLipColor = vec3(1.0); // white highlight
//            finalColor = mix(finalColor, topLipColor, 0.4 * mouth.lipHighlight);

            // === Subtle shadow under bottom lip ===
            // Must be added *before* drawing the actual mouth so it layers underneath
            vec3 lipShadowColor = vec3(0.0, 0.1, 0.0); // dark green, subtle
            mixColor = mix(mixColor, lipShadowColor, mouth.bottomLipShadow);

            if (mouth.mask > 0.0) {
                vec3 mouthOuterColor = vec3(0.2, 0.3, 0.2);
                vec3 mouthInnerColor = vec3(0.0);
                vec3 mouthColor = mix(mouthInnerColor, mouthOuterColor, mouth.gradient);
                return mix(mixColor, mouthColor, mouth.mask);
            } else {
                return mixColor;
            }
        }
    """.trimIndent()
}
