package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Main {
    @Language("AGSL")
    val main = """
            half4 main(vec2 fragCoord) {
            vec2 uv = fragCoord / iResolution;
            vec2 centered = (fragCoord - 0.5 * iResolution) / min(iResolution.x, iResolution.y);

            // === Floating animation ===
            float floatOffset = 0.03 * sin(iTime * 0.7);
            vec2 ghostCenterUV = vec2(0.5, 0.5 + floatOffset);

            // === Ghost body shape with tail wave ===
            vec2 ghostUV = centered;
            ghostUV.y += floatOffset;
            vec2 faceUV = ghostUV; // Save before applying the tail wave
            float tailWave = 0.05 * sin(ghostUV.x * 15.0 + iTime * 2.0);
            float tailFactor = smoothstep(0.0, 0.3, ghostUV.y);
            ghostUV.y += tailWave * tailFactor;

            ghostUV.x *= mix(1.0, 0.4, smoothstep(0.0, 0.6, ghostUV.y));

            float radius = 0.4;
            // Stretch ghost vertically
            vec2 ellipticalUV = vec2(ghostUV.x, ghostUV.y * 0.9);
            float ghostBody = smoothstep(radius, radius - 0.1, length(ellipticalUV));

            float ghostMask = smoothstep(0.01, 0.99, ghostBody);

            // === Blinking logic ===
            float isBlinking = isBlinking(iTime);

            // === Pupil Movement Logic ===
            float moveCycle = floor(iTime / 3.0); // change every 3 seconds
            float cycleTime = fract(iTime / 3.0); // 0 → 1 within cycle
            float moveProgress = smoothstep(0.0, 0.2, cycleTime) * (1.0 - smoothstep(0.8, 1.0, cycleTime)); // ease in/out
            
            vec2 neutralOffset = vec2(0.0, 0.01); // small downward look
            vec2 pupilOffset = neutralOffset + randomPupilOffset(moveCycle) * moveProgress * (1.0 - isBlinking);

            // === Eye shape and position ===
            vec2 leftEye = vec2(-0.10, -0.08);
            vec2 rightEye = vec2( 0.10, -0.08);
            EyeData eyes = drawEyes(faceUV, leftEye, rightEye, isBlinking);

            // === Pupils ===
             PupilData pupils = drawPupils(faceUV, leftEye + pupilOffset, rightEye + pupilOffset, isBlinking);

            // === Mouth ===
             MouthData mouth = drawMouth(faceUV, iTime, isSpeaking);

            // === Mist background using fbm noise ===
            vec2 mistUV = uv * 3.0 + vec2(iTime * 0.08, iTime * 0.03);
            float mistNoise = fbm(mistUV);
            float mistStrength = 0.5;
            vec3 mistColor = vec3(0.85) * (mistNoise * mistStrength);
            
            // === Lightning trigger (random flash like blinking) ===
            float lightningSeed = floor(iTime * 2.0);  // check twice a second
            float lightningRand = fract(sin(lightningSeed * 91.345) * 47453.25);
            float lightningActive = step(0.95, lightningRand); // ~0.5% chance per check

            // Fade lightning within frame
            float lightningFade = smoothstep(0.0, 0.5, fract(iTime)) * (1.0 - smoothstep(0.5, 01.0, fract(iTime)));
            float lightning = lightningActive * lightningFade;

            // Apply lightning as a bright flash
            // Example: lightning stronger at top of screen
            float lightningMask = smoothstep(1.0, 0.4, uv.y);  // fades from 0 at bottom to 1 at top
            mistColor += lightning * lightningMask * vec3(0.3, 0.4, 0.5); // bluish, not white

            // === Ghost glow influence on mist ===
            vec3 ghostGlowColor = vec3(0.2 + 0.4 * isSpeaking, 1.0, 0.2 + 0.4 * isSpeaking);
            float ghostDist = length(ghostUV);
            float glowFalloff = smoothstep(0.5, 0.0, ghostDist);

            mistColor *= 1.0 - 0.3 * glowFalloff;
            mistColor += ghostGlowColor * glowFalloff * 1.5;
            
            // === Ghost body shading (3D effect) ===
            vec3 ghostInnerColor = vec3(0.2, 1.0, 0.2);  // bright green
            vec3 ghostEdgeColor  = vec3(0.0, 0.4, 0.0);  // darker green

            float ghostDistFactor = smoothstep(0.0, radius, length(ghostUV));
            vec3 ghostShadedColor = mix(ghostInnerColor, ghostEdgeColor, ghostDistFactor);
            
            // === Eye socket shading on face (below eyes) ===
            vec2 leftShadowPos = leftEye + vec2(0.0, 0.04);
            vec2 rightShadowPos = rightEye + vec2(0.0, 0.04);
            float shadowRadius = 0.085;

            float leftSocketDist = length(faceUV - leftShadowPos);
            float rightSocketDist = length(faceUV - rightShadowPos);

            float leftSocketShadow = smoothstep(shadowRadius, 0.0, leftSocketDist);
            float rightSocketShadow = smoothstep(shadowRadius, 0.0, rightSocketDist);

            float eyeSocketShadow = max(leftSocketShadow, rightSocketShadow);

            vec3 socketTint = vec3(0.0, 0.2, 0.0); // subtle green shadow
            ghostShadedColor = mix(ghostShadedColor, socketTint, 0.4 * eyeSocketShadow);
            
            // brighten above each eye to exaggerate depth
            vec2 aboveLeftEye = leftEye + vec2(0.0, -0.065);
            vec2 aboveRightEye = rightEye + vec2(0.0, -0.065);
            float highlightRadius = 0.06;

            float leftHighlight = smoothstep(highlightRadius, 0.0, length(faceUV - aboveLeftEye));
            float rightHighlight = smoothstep(highlightRadius, 0.0, length(faceUV - aboveRightEye));
            float eyeRimHighlight = max(leftHighlight, rightHighlight);

            ghostShadedColor = mix(ghostShadedColor, vec3(1.0, 1.0, 1.0), 0.25 * eyeRimHighlight);
            
            // === Ambient Occlusion (AO) around eyes ===
            float aoRadius = 0.09;
            float aoStrength = 0.35;

            float leftAODist = length(faceUV - leftEye);
            float rightAODist = length(faceUV - rightEye);

            float leftAO = smoothstep(aoRadius, 0.05, leftAODist);
            float rightAO = smoothstep(aoRadius, 0.05, rightAODist);

            float eyeAO = max(leftAO, rightAO);

            // AO tint — subtle dark green
            vec3 aoColor = vec3(0.0, 0.15, 0.0);
            ghostShadedColor = mix(ghostShadedColor, aoColor, aoStrength * eyeAO);

            // === Composite ghost over mist ===
            vec3 finalColor = mix(mistColor, ghostShadedColor, ghostMask);

            if (eyes.mask > 0.0) {
                // Brighter edge for more contrast — like a recessed socket
                vec3 eyeOuterColor = vec3(0.4, 0.45, 0.4); 
                vec3 eyeInnerColor = vec3(1.0);   

                vec3 eyeGradientColor = mix(eyeInnerColor, eyeOuterColor, eyes.gradient);
               finalColor = mix(finalColor, eyeGradientColor, eyes.mask);
            }

          if (pupils.mask > 0.0) {
              // Colors for START emotion
              vec3 startOuterColor = getOutterPupilEmotionColor(uStartState);
              vec3 startInnerColor = getInnerPupilEmotionColor(uStartState);
              vec3 startBlendedColor = mix(startOuterColor, startInnerColor, pupils.gradient);

              // Colors for TARGET emotion
              vec3 targetOuterColor = getOutterPupilEmotionColor(uTargetState);
              vec3 targetInnerColor = getInnerPupilEmotionColor(uTargetState);
              vec3 targetBlendedColor = mix(targetOuterColor, targetInnerColor, pupils.gradient);

              // Smooth transition from start to target based on uTransitionProgress
              vec3 pupilColor = mix(startBlendedColor, targetBlendedColor, uTransitionProgress);

              // Apply pupil color to final image
              finalColor = mix(finalColor, pupilColor, pupils.mask);
          }


            // === Alpha fade at ghost edges ===
            float alphaFade = smoothstep(radius, radius - 0.05, length(ellipticalUV));
            float finalAlpha = mix(1.0, ghostMask * alphaFade, ghostMask);

            if (mouth.mask > 0.0) {
                vec3 mouthOuterColor = vec3(0.2, 0.3, 0.2);
                vec3 mouthInnerColor = vec3(0.0);
                vec3 mouthColor = mix(mouthInnerColor, mouthOuterColor, mouth.gradient);
                finalColor = mix(finalColor, mouthColor, mouth.mask);
            }

            return half4(finalColor, finalAlpha);
        }
    """.trimIndent()
}
