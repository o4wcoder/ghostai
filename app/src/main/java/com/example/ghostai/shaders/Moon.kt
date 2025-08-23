package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Moon {
    @Language("AGSL")
    val moon = """
        // ===== Moon =====
        // uv is 0..1 space. Place moon near top-right by default.
        // Colors are baked-in to match your inspo.
        //
        // Example usage (inside your main shader text):
        //   MoonData moon = drawMoon(uv, vec2(0.84, 0.16), 0.12, 0.22, 0.35, iTime);
        //   vec3 moonGlow = moon.glow * GLOW_COLOR;           // soft halo (add below)
        //   vec3 moonCore = moon.mask * moon.color;           // solid disc (add above)
        //   // Composite suggestion:
        //   color = moonGlow * (1.0 - moon.mask) + moonCore + color * (1.0 - moon.mask);

        struct MoonData {
            float mask;     // 0..1, the crisp moon disc
            float glow;     // 0..1, soft outer halo
            float rim;      // 0..1, band just inside the rim
            vec3  color;    // premult not needed; we return plain rgb
        };

        // --- Fixed colors (match your reference) ---
        const vec3 MOON_COLOR = vec3(0.871, 0.922, 0.918); // #DEEBEA
        const vec3 GLOW_COLOR = vec3(0.251, 0.635, 0.757); // #40A2C1
        const float GLOW_ALPHA = 0.40;                      // halo strength

        float ring(vec2 p, vec2 c, float r) {
            return 1.0 - smoothstep(r - 0.0015, r + 0.0015, distance(p, c));
        }

        // crater pattern inside unit circle (moon-local coords)
        float craterMask(vec2 pNorm, float seed) {
            float a = 0.0;
            for (int i = 0; i < 7; i++) {
                float fi = float(i);
                float ang = hash21(vec2(seed, fi)) * 6.2831;
                float rad = 0.25 + 0.65 * hash21(vec2(seed + 1.0, fi));
                vec2  c   = vec2(cos(ang), sin(ang)) * rad * 0.6;
                float rr  = mix(0.03, 0.10, hash21(vec2(seed + 2.0, fi)));
                float limb = smoothstep(1.0, 0.7, length(pNorm));
                a += ring(pNorm, c, rr) * limb;
            }
            return clamp(a, 0.0, 1.0);
        }

        MoonData drawMoon(vec2 uv, vec2 moonCenter, float moonRadius, float glowRadius, float craterAmt, float baseTime) {
            // Distance in uv space
            float d = distance(uv, moonCenter);
            
               // feather width scales with radius so it looks consistent
            float seam = 0.012 * moonRadius;

            // disc mask (feathered)
            float edge = 1.0 - smoothstep(moonRadius - seam, moonRadius + seam, d);
            
            // radial halo that is 1 at the rim and fades to 0 by glowRadius
            float halo = 1.0 - smoothstep(moonRadius, glowRadius, d);
            // final glow alpha (no contribution inside the disc; we gate with (1 - mask) in main)
            float alphaGlow = halo * GLOW_ALPHA;

            // Crisp disc edge
           // float edge = 1.0 - smoothstep(moonRadius - 0.002, moonRadius + 0.002, d);
               // rim band INSIDE the disc only (used for slight glow underlap)
              float rimInside = 1.0 - smoothstep(moonRadius - seam, moonRadius, d);

            // Subtle limb shading toward the rim
            float limbShade = mix(1.0, 0.85, smoothstep(0.6 * moonRadius, moonRadius, d));

            // Craters in moon-local coords (normalize by radius)
            vec2 pNorm = (uv - moonCenter) / moonRadius;
            float craters = craterMask(pNorm, floor(baseTime * 0.1) + 1.0);
            float craterShade = mix(1.0, 0.84, craters * craterAmt);

            MoonData outData;
            outData.mask  = edge;
            outData.glow  = alphaGlow;
            outData.rim   = rimInside;
            outData.color = MOON_COLOR * limbShade * craterShade;
            return outData;
        }
    """.trimIndent()
}
