package com.example.ghostai

import android.graphics.RuntimeShader
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

        float drawEyes(vec2 ghostUV, float2 leftEye, float2 rightEye, float isBlinking) {
            float eyeRadiusX = 0.05;
            float eyeRadiusY = mix(0.05, 0.005, isBlinking);

            float2 leftDelta = ghostUV - leftEye;
            float2 rightDelta = ghostUV - rightEye;

            float leftEyeShape = step(length(float2(leftDelta.x / eyeRadiusX, leftDelta.y / eyeRadiusY)), 1.0);
            float rightEyeShape = step(length(float2(rightDelta.x / eyeRadiusX, rightDelta.y / eyeRadiusY)), 1.0);
            return leftEyeShape + rightEyeShape;
        }

        float drawPupils(vec2 ghostUV, vec2 leftEye, vec2 rightEye, float isBlinking) {
          float pupilRadius = 0.015;
            float2 leftPupilDelta = ghostUV - leftEye;
            float2 rightPupilDelta = ghostUV - rightEye;

            float leftPupil = step(length(leftPupilDelta), pupilRadius);
            float rightPupil = step(length(rightPupilDelta), pupilRadius);
            return (leftPupil + rightPupil) * (1.0 - isBlinking);
        }

        float drawMouth(vec2 ghostUV, float iTime, float isSpeaking) {
           float baseMouthY = 0.08;
            float mouthWidth = 0.15;
            float idleMouthHeight = 0.01;
            float talkingMouthHeight = 0.07 * abs(sin(iTime * 6.0));
            float mouthHeight = mix(idleMouthHeight, talkingMouthHeight, isSpeaking);

            float2 mouthDelta = ghostUV - float2(0.0, baseMouthY);
            return step(length(float2(mouthDelta.x / mouthWidth, mouthDelta.y / mouthHeight)), 1.0);
        }

        float isBlinking(float iTime) {
            float blinkSeed = floor(iTime / 6.0);
            float rand = fract(sin(blinkSeed * 91.345) * 47453.25);
            float nextBlinkTime = blinkSeed * 6.0 + rand * 3.0;
            float blinkDuration = 0.15;
            float timeSinceBlink = iTime - nextBlinkTime;
            return step(0.0, timeSinceBlink) * step(timeSinceBlink, blinkDuration);
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

            // === Eye shape and position ===
            float2 leftEye = float2(-0.15, -0.08);
            float2 rightEye = float2( 0.15, -0.08);
            float eyes = drawEyes(ghostUV, leftEye, rightEye, isBlinking);

            // === Pupils ===
             float pupils = drawPupils(ghostUV, leftEye, rightEye, isBlinking);

            // === Mouth ===
             float mouth = drawMouth(ghostUV, iTime, isSpeaking);

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

            if (eyes > 0.0) {
                finalColor = mix(finalColor, float3(0.0), eyes);
            }
            if (pupils > 0.0) {
                float3 pupilGlowColor = float3(0.3, 1.0, 0.3);
                finalColor = mix(finalColor, pupilGlowColor, pupils * 0.6);
            }
            
            // === Alpha fade at ghost edges ===
            float alphaFade = smoothstep(radius, radius - 0.05, length(ellipticalUV));
            float finalAlpha = mix(1.0, ghostMask * alphaFade, ghostMask);

            if (mouth > 0.0) {
                finalColor = mix(finalColor, float3(0.0), mouth);
            }

            return half4(finalColor, finalAlpha);
        }
    """.trimIndent()

    val context = LocalContext.current

    val shader = remember {
//        val shaderCode = context.resources
//            .openRawResource(R.raw.ghost_and_mist_shader)
//            .bufferedReader()
//            .use { it.readText() }
        RuntimeShader(ghostShader)
    }

    var canvasSize by remember { mutableStateOf(Size(1f, 1f)) }

    LaunchedEffect(time, isSpeaking, canvasSize) {
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("isSpeaking", if (isSpeaking) 1f else 0f)
        shader.setFloatUniform("iResolution", canvasSize.width, canvasSize.height)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        canvasSize = size
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("isSpeaking", if (isSpeaking) 1f else 0f)
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
        GhostWithMist(isSpeaking = false, time = 1.0F, modifier = Modifier.background(Color.Black))
    }
}

