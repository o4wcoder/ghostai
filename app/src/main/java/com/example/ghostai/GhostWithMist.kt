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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.ghostai.oldui.rememberStableTime
import com.example.ghostai.ui.theme.GhostAITheme
import org.intellij.lang.annotations.Language

@Composable
fun GhostWithMist(isSpeaking: Boolean,
                  modifier: Modifier = Modifier,
                  time: Float = rememberStableTime()) {

    @Language("AGSL")
    val ghostShader = """
        uniform float2 iResolution;
        uniform float iTime;
        uniform float isSpeaking;

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


        float hash(float2 p) {
            return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
        }

        float noise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float a = hash(i);
            float b = hash(i + float2(1.0, 0.0));
            float c = hash(i + float2(0.0, 1.0));
            float d = hash(i + float2(1.0, 1.0));
            float2 u = f * f * (3.0 - 2.0 * f);
            return mix(a, b, u.x) +
                   (c - a) * u.y * (1.0 - u.x) +
                   (d - b) * u.x * u.y;
        }

        float fbm(float2 p) {
            float v = 0.0;
            float a = 0.5;
            float2 shift = float2(100.0);
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
            float2 leftDelta = uv - leftEye;
            float2 rightDelta = uv - rightEye;

            float2 leftNorm = float2(leftDelta.x / eyeRadiusX, leftDelta.y / eyeRadiusY);
            float2 rightNorm = float2(rightDelta.x / eyeRadiusX, rightDelta.y / eyeRadiusY);

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
            float pupilRadius = 0.015;

            float2 leftDelta = uv - leftEye;
            float2 rightDelta = uv - rightEye;

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
            float2 mouthDelta = uv - float2(mouthWiggle, baseMouthY);

            // Distort the sides of the mouth
            float phase = isSpeaking > 0.0 ? iTime * 5.0 : 0.0;
            float mouthShapeWarp = 1.0 + 0.1 * sin(mouthDelta.x * 8.0 + iTime * 2.0);

            float2 warpedDelta = float2(mouthDelta.x / mouthWidth, mouthDelta.y / (mouthHeight * mouthShapeWarp));
            float mouthMask = step(length(warpedDelta), 1.0);

            float mouthGradient = smoothstep(0.0, 1.0, length(float2(mouthDelta.x / mouthWidth, mouthDelta.y / mouthHeight)));

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
            float2 randVec = vec2(
                fract(sin(baseTime * 12.9898) * 43758.5453),
                fract(sin(baseTime * 78.233) * 96321.5487)
            );
            float angle = randVec.x * 6.2831; // 2π
            float radius = 0.004 + 0.006 * randVec.y; // small range
            return vec2(cos(angle), sin(angle)) * radius;
        }


        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / iResolution;
            float2 centered = (fragCoord - 0.5 * iResolution) / min(iResolution.x, iResolution.y);

            // === Floating animation ===
            float floatOffset = 0.03 * sin(iTime * 0.7);
            float2 ghostCenterUV = float2(0.5, 0.5 + floatOffset);

            // === Ghost body shape with tail wave ===
            float2 ghostUV = centered;
            ghostUV.y += floatOffset;
            float2 faceUV = ghostUV; // Save before applying the tail wave
            float tailWave = 0.05 * sin(ghostUV.x * 15.0 + iTime * 2.0);
            float tailFactor = smoothstep(0.0, 0.3, ghostUV.y);
            ghostUV.y += tailWave * tailFactor;

            ghostUV.x *= mix(1.0, 0.4, smoothstep(0.0, 0.6, ghostUV.y));

            float radius = 0.4;
            // Stretch ghost vertically
            float2 ellipticalUV = float2(ghostUV.x, ghostUV.y * 0.9);
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
            float2 leftEye = float2(-0.12, -0.08);
            float2 rightEye = float2( 0.12, -0.08);
            EyeData eyes = drawEyes(faceUV, leftEye, rightEye, isBlinking);

            // === Pupils ===
             PupilData pupils = drawPupils(faceUV, leftEye + pupilOffset, rightEye + pupilOffset, isBlinking);

            // === Mouth ===
             MouthData mouth = drawMouth(faceUV, iTime, isSpeaking);

            // === Mist background using fbm noise ===
            float2 mistUV = uv * 3.0 + float2(iTime * 0.08, iTime * 0.03);
            float mistNoise = fbm(mistUV);
            float mistStrength = 0.5;
            float3 mistColor = float3(0.85) * (mistNoise * mistStrength);

            // === Ghost glow influence on mist ===
            float3 ghostGlowColor = float3(0.2 + 0.4 * isSpeaking, 1.0, 0.2 + 0.4 * isSpeaking);
            float ghostDist = length(ghostUV);
            float glowFalloff = smoothstep(0.5, 0.0, ghostDist);

            mistColor *= 1.0 - 0.3 * glowFalloff;
            mistColor += ghostGlowColor * glowFalloff * 1.5;

            // === Ghost body shading (3D effect) ===
            float3 ghostInnerColor = float3(0.2, 1.0, 0.2);  // bright green
            float3 ghostEdgeColor  = float3(0.0, 0.4, 0.0);  // darker green

            float ghostDistFactor = smoothstep(0.0, radius, length(ghostUV));
            float3 ghostShadedColor = mix(ghostInnerColor, ghostEdgeColor, ghostDistFactor);

            // === Composite ghost over mist ===
            float3 finalColor = mix(mistColor, ghostShadedColor, ghostMask);

            if (eyes.mask > 0.0) {
                // Brighter edge for more contrast — like a recessed socket
                float3 eyeOuterColor = float3(0.20, 0.3, 0.20); // shadowy green-gray
                float3 eyeInnerColor = float3(0.0);   // black center

                float3 eyeGradientColor = mix(eyeInnerColor, eyeOuterColor, eyes.gradient);
                finalColor = mix(finalColor, eyeGradientColor, eyes.mask);
            }

           if (pupils.mask > 0.0) {
               float3 pupilOuterColor = float3(0.063, 0.302, 0.063);
               float3 pupilCenterColor = float3(0.6, 0.9, 0.6);

               float3 pupilColor = mix(pupilOuterColor, pupilCenterColor, pupils.gradient);
               finalColor = mix(finalColor, pupilColor, pupils.mask);
           }

            // === Alpha fade at ghost edges ===
            float alphaFade = smoothstep(radius, radius - 0.05, length(ellipticalUV));
            float finalAlpha = mix(1.0, ghostMask * alphaFade, ghostMask);

            if (mouth.mask > 0.0) {
                float3 mouthOuterColor = float3(0.2, 0.3, 0.2);
                float3 mouthInnerColor = float3(0.0);
                float3 mouthColor = mix(mouthInnerColor, mouthOuterColor, mouth.gradient);
                finalColor = mix(finalColor, mouthColor, mouth.mask);
            }

            return half4(finalColor, finalAlpha);
        }
    """.trimIndent()

    val context = LocalContext.current

    val shader = remember {
        RuntimeShader(ghostShader)
    }

    val animatedSpeaking by animateFloatAsState(
        targetValue = if (isSpeaking) 1f else 0f,
        animationSpec = tween(durationMillis = 300)
    )

    var canvasSize by remember { mutableStateOf(Size(1f, 1f)) }

    LaunchedEffect(time, isSpeaking, canvasSize) {
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("isSpeaking", animatedSpeaking)
        shader.setFloatUniform("iResolution", canvasSize.width, canvasSize.height)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        canvasSize = size
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("isSpeaking", animatedSpeaking)
        shader.setFloatUniform("iResolution", size.width, size.height)

        drawRect(
            brush = object : ShaderBrush() {
                override fun createShader(size: Size): Shader = shader
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GhostWithMistPreview() {
    GhostAITheme {
        GhostWithMist(isSpeaking = true, time = 2.0F, modifier = Modifier.background(Color.Black))
    }
}

