package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Uniforms {
    @Language("AGSL")
    val uniformDefs = """
        uniform vec2 iResolution;
        uniform float iTime;
        uniform float isSpeaking;
        uniform float uStartState;
        uniform float uTargetState;
        uniform float uTransitionProgress;

        struct MouthData {
            float mask;
            float gradient;
            float topLipShadow;
            float bottomLipShadow;
            float lipHighlight;
        };


        float hash(vec2 p) {
            return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
        }
        
        float hash11(float x){
            x = fract(x * 0.1031);
            x *= x + 33.33;
            x *= x + x;
            return fract(x);
        }
                // tiny helpers
        float hash21(vec2 p) {
            p = fract(p * vec2(123.34, 345.45));
            p += dot(p, p + 34.23);
            return fract(p.x * p.y);
        }
        
        vec2 hash22(vec2 p){
            float a = hash21(p);
            float b = hash21(p + 23.17);
            return vec2(a, b);
        }
        
        // Snap a 0..1 value to N discrete steps (prevents tiny precision flips)
        float snap01(float v, float steps) {
            return floor(clamp(v, 0.0, 1.0) * steps + 0.5) / steps;
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
    """.trimIndent()
}
