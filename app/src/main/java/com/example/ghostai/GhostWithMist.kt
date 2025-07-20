package com.example.ghostai

import android.graphics.RuntimeShader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.tooling.preview.Preview
import com.example.ghostai.model.ConversationState
import com.example.ghostai.model.Emotion
import com.example.ghostai.model.GhostUiState
import com.example.ghostai.oldui.rememberStableTime
import com.example.ghostai.ui.theme.GhostAITheme
import com.example.ghostai.util.pointerTapEvents
import com.example.ghostai.util.rememberShaderTransitionState
import org.intellij.lang.annotations.Language

@Composable
fun GhostWithMist(
    ghostUiState: GhostUiState,
    modifier: Modifier = Modifier,
    onGhostThouched: () -> Unit,
    time: Float = rememberStableTime(),
) {
    @Language("AGSL")
    val ghostShader = """
        uniform vec2 iResolution;
        uniform float iTime;
        uniform float isSpeaking;
        uniform float uStartState;
        uniform float uTargetState;
        uniform float uTransitionProgress;

        struct EyeData {
            float mask;
            float gradient;
        };

        struct PupilData {
            float mask;
            float gradient;
        };

        struct MouthData {
            float mask;
            float gradient;
        };


        float hash(vec2 p) {
            return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
        }

        float noise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            float a = hash(i);
            float b = hash(i + vec2(1.0, 0.0));
            float c = hash(i + vec2(0.0, 1.0));
            float d = hash(i + vec2(1.0, 1.0));
            vec2 u = f * f * (3.0 - 2.0 * f);
            return mix(a, b, u.x) +
                   (c - a) * u.y * (1.0 - u.x) +
                   (d - b) * u.x * u.y;
        }

        float fbm(vec2 p) {
            float v = 0.0;
            float a = 0.5;
            vec2 shift = vec2(100.0);
            for (int i = 0; i < 5; ++i) {
                v += a * noise(p);
                p = p * 2.0 + shift;
                a *= 0.5;
            }
            return v;
        }

        EyeData drawEyes(vec2 uv, vec2 leftEye, vec2 rightEye, float isBlinking) {
            float eyeRadiusX = 0.05;
            float eyeRadiusY = mix(0.05, 0.005, isBlinking);
            vec2 leftDelta = uv - leftEye;
            vec2 rightDelta = uv - rightEye;

            vec2 leftNorm = vec2(leftDelta.x / eyeRadiusX, leftDelta.y / eyeRadiusY);
            vec2 rightNorm = vec2(rightDelta.x / eyeRadiusX, rightDelta.y / eyeRadiusY);

            float leftDist = length(leftNorm);
            float rightDist = length(rightNorm);

            float leftEyeShape = step(leftDist, 1.0);
            float rightEyeShape = step(rightDist, 1.0);

            float eyeMask = leftEyeShape + rightEyeShape;

            // Create gradient: 0 (center) to 1 (edge)
            float leftGradient = smoothstep(0.0, 1.0, leftDist) * leftEyeShape;
            float rightGradient = smoothstep(0.0, 1.0, rightDist) * rightEyeShape;

            float eyeGradient = max(leftGradient, rightGradient); // max keeps only strongest contribution

            EyeData result;
            result.mask = eyeMask;
            result.gradient = eyeGradient;
            return result;
        }

        PupilData drawPupils(vec2 uv, vec2 leftEye, vec2 rightEye, float isBlinking) {
            float pupilRadius = 0.020;

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
                return vec3(0.6, 0.9, 0.6);    // Pale green (neutral)
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
                return vec3(0.063, 0.302, 0.063);  // Dark green (neutral)
            }
        }




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

            vec2 pupilOffset = randomPupilOffset(moveCycle) * moveProgress * (1.0 - isBlinking);

            // === Eye shape and position ===
            vec2 leftEye = vec2(-0.12, -0.08);
            vec2 rightEye = vec2( 0.12, -0.08);
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

            // === Composite ghost over mist ===
            vec3 finalColor = mix(mistColor, ghostShadedColor, ghostMask);

            if (eyes.mask > 0.0) {
                // Brighter edge for more contrast — like a recessed socket
                vec3 eyeOuterColor = vec3(0.20, 0.3, 0.20); // shadowy green-gray
                vec3 eyeInnerColor = vec3(0.0);   // black center

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

    val shader = remember {
        RuntimeShader(ghostShader)
    }

    val animatedSpeaking by animateFloatAsState(
        targetValue = if (ghostUiState.isSpeaking) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
    )

    val emotionTransitionState = rememberShaderTransitionState(initialState = Emotion.Neutral)

    LaunchedEffect(ghostUiState.targetEmotion) {
        emotionTransitionState.transitionTo(ghostUiState.targetEmotion)
    }

    Canvas(
        modifier = modifier.fillMaxSize()
            .pointerTapEvents(
                onTap = {
                    onGhostThouched()
                },
                onDoubleTap = {
                },
            ),

    ) {
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("isSpeaking", animatedSpeaking)
        shader.setFloatUniform("uTransitionProgress", emotionTransitionState.transitionProgress)
        shader.setFloatUniform("uStartState", emotionTransitionState.startState.id)
        shader.setFloatUniform("uTargetState", emotionTransitionState.targetState.id)
        shader.setFloatUniform("iResolution", size.width, size.height)

        drawRect(
            brush = object : ShaderBrush() {
                override fun createShader(size: Size): Shader = shader
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GhostWithMistPreview() {
    GhostAITheme {
        GhostWithMist(ghostUiState = getGhostUiStatePreviewUiState(), time = 2.0F, modifier = Modifier.background(Color.Black), onGhostThouched = {})
    }
}

@Preview(showBackground = true)
@Composable
fun GhostWithMistAngryEmotionPreview() {
    GhostAITheme {
        GhostWithMist(ghostUiState = getGhostUiStatePreviewUiState(targetEmotion = Emotion.Angry), time = 2.0F, modifier = Modifier.background(Color.Black), onGhostThouched = {})
    }
}

fun getGhostUiStatePreviewUiState(
    conversationState: ConversationState = ConversationState.GhostTalking,
    startEmotion: Emotion = Emotion.Neutral,
    targetEmotion: Emotion = Emotion.Neutral,
) = GhostUiState(
    conversationState = conversationState,
    startEmotion = startEmotion,
    targetEmotion = targetEmotion,
)
