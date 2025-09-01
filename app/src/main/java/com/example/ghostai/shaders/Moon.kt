package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Moon {
    @Language("AGSL")
    val moon = """
// ===== Moon (high-contrast craters, still optimized) =========================
struct MoonData {
    float mask;     // 0..1, crisp moon disc
    float glow;     // 0..1, soft outer halo
    float rim;      // 0..1, band just inside the rim
    vec3  color;    // rgb
};

const vec3  MOON_COLOR  = vec3(0.871, 0.922, 0.918); // #DEEBEA
const vec3  GLOW_COLOR  = vec3(0.251, 0.635, 0.757); // #40A2C1
const float GLOW_ALPHA  = 0.30;

// --- local hash (self-contained) --------------------------------------------
float moon_hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}
vec2 moon_hash22(vec2 p){
    return vec2(moon_hash21(p), moon_hash21(p + vec2(17.1, 31.7)));
}

// Soft disk (1 inside r with soft edge f)
float diskMask(vec2 p, vec2 c, float r, float f){
    float d = length(p - c);
    return 1.0 - smoothstep(r - f, r + f, d);
}
// Thin ring around r with feather f
float ringMask(vec2 p, vec2 c, float r, float f){
    float d = abs(length(p - c) - r);
    return 1.0 - smoothstep(f, f * 2.0, d);
}

// Return (darkFloor, brightRim), both limb-weighted and directionally shaded,
// with guaranteed non-overlapping placement via ring layout + jitter.
vec2 craterBandsHiVis(vec2 pNorm, float seed){
    // limb factor (keep some effect near center so detail reads everywhere)
    float limb = mix(0.55, 1.0, smoothstep(0.35, 1.0, length(pNorm)));
    // fake light direction from upper-left for embossed look
    vec2  L2 = normalize(vec2(-0.35, -0.65));

    float darkAcc = 0.0;
    float liteAcc = 0.0;

    // helpers
    float TAU = 6.2831853;
    float PI  = 3.14159265;

    // ---------- ring 0: inner (2 craters) ----------
    {
        float R     = 0.28;                 // ring radius (unit = disc radius)
        float stepA = TAU / 2.0;            // angular spacing
        float baseA = TAU * moon_hash21(vec2(seed, 101.0)); // random ring rotation
        for (int i = 0; i < 2; i++) {
            float a0  = baseA + stepA * float(i);
            float jA  = (stepA * 0.18) * (moon_hash21(vec2(seed, 111.0 + float(i))) * 2.0 - 1.0);
            float jR  = R * 0.06 * (moon_hash21(vec2(seed, 121.0 + float(i))) * 2.0 - 1.0);
            float a   = a0 + jA;
            float r   = R + jR;

            // max safe radius so neighbors don't intersect: 0.5 * chord
            float chord   = 2.0 * R * sin(PI / 2.0);
            float maxSafe = 0.5 * chord * 0.85;           // 15% margin
            float rr      = min(maxSafe, mix(0.045, 0.11, moon_hash21(vec2(seed, 131.0 + float(i)))));

            vec2  c      = r * vec2(cos(a), sin(a));
            vec2  n      = normalize(pNorm - c);
            float hl     = clamp(0.5 + 0.5 * dot(n, L2), 0.0, 1.0);
            float fsz    = mix(0.0012, 0.0036, clamp(rr * 8.0, 0.0, 1.0));

            float floorMask = diskMask(pNorm, c, rr * 0.96, fsz * 1.2);
            float rimMask   = ringMask(pNorm, c, rr + fsz * 1.4, fsz * 0.9);

            darkAcc += floorMask * (0.90 - 0.40 * hl);
            liteAcc += rimMask   * (0.35 + 0.65 * hl);
        }
    }

    // ---------- ring 1: mid (3 craters) ----------
    {
        float R     = 0.48;
        float stepA = TAU / 3.0;
        float baseA = TAU * moon_hash21(vec2(seed, 201.0));
        for (int i = 0; i < 3; i++) {
            float a0  = baseA + stepA * float(i);
            float jA  = (stepA * 0.18) * (moon_hash21(vec2(seed, 211.0 + float(i))) * 2.0 - 1.0);
            float jR  = R * 0.06 * (moon_hash21(vec2(seed, 221.0 + float(i))) * 2.0 - 1.0);
            float a   = a0 + jA;
            float r   = R + jR;

            float chord   = 2.0 * R * sin(PI / 3.0);
            float maxSafe = 0.5 * chord * 0.85;
            float rr      = min(maxSafe, mix(0.050, 0.125, moon_hash21(vec2(seed, 231.0 + float(i)))));

            vec2  c      = r * vec2(cos(a), sin(a));
            vec2  n      = normalize(pNorm - c);
            float hl     = clamp(0.5 + 0.5 * dot(n, L2), 0.0, 1.0);
            float fsz    = mix(0.0012, 0.0038, clamp(rr * 7.5, 0.0, 1.0));

            float floorMask = diskMask(pNorm, c, rr * 0.96, fsz * 1.2);
            float rimMask   = ringMask(pNorm, c, rr + fsz * 1.4, fsz * 0.9);

            darkAcc += floorMask * (0.90 - 0.40 * hl);
            liteAcc += rimMask   * (0.35 + 0.65 * hl);
        }
    }

    // ---------- ring 2: outer (2 craters) ----------
    {
        float R     = 0.62;
        float stepA = TAU / 2.0;
        float baseA = TAU * moon_hash21(vec2(seed, 301.0));
        for (int i = 0; i < 2; i++) {
            float a0  = baseA + stepA * float(i);
            float jA  = (stepA * 0.18) * (moon_hash21(vec2(seed, 311.0 + float(i))) * 2.0 - 1.0);
            float jR  = R * 0.06 * (moon_hash21(vec2(seed, 321.0 + float(i))) * 2.0 - 1.0);
            float a   = a0 + jA;
            float r   = R + jR;

            float chord   = 2.0 * R * sin(PI / 2.0);
            float maxSafe = 0.5 * chord * 0.85;
            float rr      = min(maxSafe, mix(0.050, 0.12, moon_hash21(vec2(seed, 331.0 + float(i)))));

            vec2  c      = r * vec2(cos(a), sin(a));
            vec2  n      = normalize(pNorm - c);
            float hl     = clamp(0.5 + 0.5 * dot(n, L2), 0.0, 1.0);
            float fsz    = mix(0.0012, 0.0038, clamp(rr * 7.5, 0.0, 1.0));

            float floorMask = diskMask(pNorm, c, rr * 0.96, fsz * 1.2);
            float rimMask   = ringMask(pNorm, c, rr + fsz * 1.4, fsz * 0.9);

            darkAcc += floorMask * (0.90 - 0.40 * hl);
            liteAcc += rimMask   * (0.35 + 0.65 * hl);
        }
    }

    return vec2(darkAcc * limb, liteAcc * limb);
}


MoonData drawMoon(vec2 uv, vec2 moonCenter,
                  float moonRadius, float glowRadius,
                  float craterAmt, float baseTime)
{
    vec2  p    = uv - moonCenter;
    float d    = length(p);
    float seam = 0.012 * moonRadius; // edge feather (scaled)

    // Disc + rim
    float edge      = 1.0 - smoothstep(moonRadius - seam, moonRadius + seam, d);
    float rimInside = 1.0 - smoothstep(moonRadius - seam, moonRadius, d);

    // Halo
    float halo      = 1.0 - smoothstep(moonRadius, glowRadius, d);
    float alphaGlow = halo * GLOW_ALPHA;

    // Limb shading (gentler so details aren’t washed near edge)
    float dn        = d / max(moonRadius, 1e-5);
    float limbShade = mix(1.0, 0.90, smoothstep(0.65, 1.0, dn));

    vec3 color = MOON_COLOR * limbShade;

    // Craters: compute only near/inside the disc
    if ((d <= moonRadius + seam) && (craterAmt > 1e-4)) {
        vec2  pNorm = p / moonRadius;
        float seed  = floor(baseTime * 0.1) + 1.0;
        vec2  bands = craterBandsHiVis(pNorm, seed); // (darkFloor, brightRim)

        // Stronger contrast so it survives cloud occlusion
        float darkAmt = clamp(bands.x * craterAmt * 0.45, 0.0, 0.55);
        float liteAmt = clamp(bands.y * craterAmt * 0.22, 0.0, 0.30);

        color *= (1.0 - darkAmt);         // darken floor (keeps hue)
        color += vec3(1.0) * liteAmt;     // add a pale rim
    }

    MoonData outData;
    outData.mask  = edge;
    outData.glow  = alphaGlow;
    outData.rim   = rimInside;
    outData.color = clamp(color, 0.0, 1.6);
    return outData;
}
    """.trimIndent()
}
