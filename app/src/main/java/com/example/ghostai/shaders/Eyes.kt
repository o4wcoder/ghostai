package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Eyes {
    @Language("AGSL")
    val eyes = """
            EyeData drawEyes(vec2 uv, vec2 leftEye, vec2 rightEye, float isBlinking) {
            float eyeRadiusX = 0.065;
            float eyeRadiusY = mix(0.075, 0.005, isBlinking);
            vec2 leftDelta = uv - leftEye;
            vec2 rightDelta = uv - rightEye;

            vec2 leftNorm = vec2(leftDelta.x / eyeRadiusX, leftDelta.y / eyeRadiusY);
            vec2 rightNorm = vec2(rightDelta.x / eyeRadiusX, rightDelta.y / eyeRadiusY);

            float leftDist = length(leftNorm);
            float rightDist = length(rightNorm);

            float leftEyeShape = 1.0 - smoothstep(0.9, 1.0, leftDist);
            float rightEyeShape = 1.0 - smoothstep(0.9, 1.0, rightDist);

            float eyeMask = leftEyeShape + rightEyeShape;

            // Create gradient: 0 (center) to 1 (edge)
            float leftGradient = smoothstep(0.75, 1.0, leftDist) * leftEyeShape;
            float rightGradient = smoothstep(0.75, 1.0, rightDist) * rightEyeShape;
            
            // NEW: Add vertical falloff to simulate socket below eye center
            float leftVerticalOffset = clamp((uv.y - leftEye.y) / eyeRadiusY, 0.0, 1.0);
            float rightVerticalOffset = clamp((uv.y - rightEye.y) / eyeRadiusY, 0.0, 1.0);
            // Multiply the gradient by the vertical offset to emphasize shading below eye center
            leftGradient *= leftVerticalOffset;
            rightGradient *= rightVerticalOffset;

            float eyeGradient = max(leftGradient, rightGradient); // max keeps only strongest contribution

            EyeData result;
            result.mask = eyeMask;
            result.gradient = eyeGradient;
            return result;
        }

        PupilData drawPupils(vec2 uv, vec2 leftEye, vec2 rightEye, float isBlinking) {
            float pupilRadius = 0.025;

            vec2 leftDelta = uv - leftEye;
            vec2 rightDelta = uv - rightEye;

            float leftDist = length(leftDelta);
            float rightDist = length(rightDelta);

            float leftMask = step(leftDist, pupilRadius);
            float rightMask = step(rightDist, pupilRadius);

            float leftGradient = 1.0 - smoothstep(0.0, pupilRadius, leftDist);
            float rightGradient = 1.0 - smoothstep(0.0, pupilRadius, rightDist);

            float combinedMask = (leftMask + rightMask) * (1.0 - isBlinking);
            float combinedGradient = max(leftGradient * leftMask, rightGradient * rightMask);

            PupilData result;
            result.mask = combinedMask;
            result.gradient = combinedGradient;
            return result;
        }

        vec2 randomPupilOffset(float baseTime) {
            vec2 randVec = vec2(
                fract(sin(baseTime * 12.9898) * 43758.5453),
                fract(sin(baseTime * 78.233) * 96321.5487)
            );
            float angle = randVec.x * 6.2831; // 2π
            float radius = 0.004 + 0.006 * randVec.y; // small range
            return vec2(cos(angle), sin(angle)) * radius;
        }

        // Example: setting a color based on emotion
        vec3 getInnerPupilEmotionColor(float emotionId) {
            if (emotionId == 1.0) {         // Angry
                return vec3(1.0, 0.4, 0.4);    // Bright red-pink
            } else if (emotionId == 2.0) {  // Happy
                return vec3(1.0, 1.0, 0.5);    // Soft yellow
            } else if (emotionId == 3.0) {  // Sad
                return vec3(0.4, 0.4, 1.0);    // Soft blue
            } else if (emotionId == 4.0) {  // Spooky
                return vec3(0.6, 0.9, 0.6);    // Pale green (ghostly)
            } else if (emotionId == 5.0) {  // Funny
                return vec3(1.0, 0.6, 1.0);    // Pinkish
            } else {                        // Neutral (default)
               // return vec3(0.6, 0.9, 0.6);    // Pale green (neutral)
               return vec3(0.0);
            }
        }

        vec3 getOutterPupilEmotionColor(float emotionId) {
            if (emotionId == 1.0) {         // Angry
                return vec3(0.3, 0.0, 0.0);    // Dark red
            } else if (emotionId == 2.0) {  // Happy
                return vec3(0.4, 0.4, 0.0);    // Olive yellow
            } else if (emotionId == 3.0) {  // Sad
                return vec3(0.0, 0.0, 0.3);    // Dark blue
            } else if (emotionId == 4.0) {  // Spooky
                return vec3(0.0, 0.3, 0.0);    // Dark green
            } else if (emotionId == 5.0) {  // Funny
                return vec3(0.3, 0.0, 0.3);    // Dark magenta
            } else {                        // Neutral (default)
              //  return vec3(0.063, 0.302, 0.063);  // Dark green (neutral)
              return vec3(0.0);
            }
        }

        float isBlinking(float iTime) {
            float blinkSeed = floor(iTime / 6.0);
            float rand = fract(sin(blinkSeed * 91.345) * 47453.25);
            float nextBlinkTime = blinkSeed * 6.0 + rand * 3.0;
            float blinkDuration = 0.15;
            float timeSinceBlink = iTime - nextBlinkTime;
            return step(0.0, timeSinceBlink) * step(timeSinceBlink, blinkDuration);
        }
        
        vec3 mixEyeColor(vec3 finalColor, EyeData eyes) {
            if (eyes.mask > 0.0) {
                // Brighter edge for more contrast — like a recessed socket
                vec3 eyeOuterColor = vec3(0.4, 0.45, 0.4); 
                vec3 eyeInnerColor = vec3(1.0);   

                vec3 eyeGradientColor = mix(eyeInnerColor, eyeOuterColor, eyes.gradient);
                return mix(finalColor, eyeGradientColor, eyes.mask);
            } else {
               return finalColor;
            }
        }
        
        vec3 mixPupilColor(vec3 finalColor, PupilData pupils) {
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
              return mix(finalColor, pupilColor, pupils.mask);
          } else {
              return finalColor;
          }
        }
    """.trimIndent()
}
