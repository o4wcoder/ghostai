package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Ground {
    @Language("AGSL")
    val ground = """
// ===== LiteGround (brown palette, zero heavy loops) ==========================
struct GroundData { float mask; vec3 color; };

// ---- tiny half-precision noise (unique names to avoid collisions) ----------
half g_hash12h(half2 p){
    p = fract(p * half(0.1031));
    p += dot(p, p.yx + half2(33.33, 33.33));
    return fract((p.x + p.y) * p.x);
}
half g_vnoise_h(half2 p){
    half2 i = floor(p), f = fract(p);
    half a = g_hash12h(i);
    half b = g_hash12h(i + half2(1,0));
    half c = g_hash12h(i + half2(0,1));
    half d = g_hash12h(i + half2(1,1));
    half2 u = f*f*(half2(3.0,3.0) - half2(2.0,2.0)*f);
    return mix(mix(a,b,u.x), mix(c,d,u.x), u.y);
}
// 2-octave “fbm”
half g_fbm2_h(half2 p){
    half s = g_vnoise_h(p);
    s += half(0.5) * g_vnoise_h(p * half(2.05));
    return s * half(1.0/1.5);
}

// ---- helpers your main already calls ---------------------------------------
float groundOvalShadowCentered(vec2 p, float cx, float cy, float rx, float ry, float feather){
    vec2 q = vec2((p.x - cx) / max(rx, 1e-6), (p.y - cy) / max(ry, 1e-6));
    float d = length(q);
    return 1.0 - smoothstep(1.0, 1.0 + feather, d);
}
float groundContactShadowCentered(vec2 p, float footX, float groundLine, float halfWidth, float fadeDown){
    float dx = abs(p.x - footX);
    float horz = 1.0 - smoothstep(halfWidth, halfWidth * 1.10, dx);
    float down = 1.0 - smoothstep(0.0, max(fadeDown, 1e-4), p.y - groundLine);
    return max(0.0, horz * down);
}
vec3 mixGroundColor(vec3 bg, GroundData g, float ghostMask){
    float a = g.mask * (1.0 - ghostMask);
    return mix(bg, g.color, a);
}

// ---- main ground ------------------------------------------------------------
GroundData drawGround(vec2 centered, float iTime, vec3 lightDir, vec2 ghostGroundCenter){
    GroundData g;

    // horizon mask (AA)
    const float GROUND_LINE  = 0.48;
    const float EDGE_FEATHER = 0.006;
    g.mask = smoothstep(GROUND_LINE - EDGE_FEATHER, GROUND_LINE + EDGE_FEATHER, centered.y);
    if (g.mask <= 0.0) { g.color = vec3(0.0); return g; }

    // approximate 0..1 depth below horizon (keep cheap & stable)
    float worldDepth = clamp((centered.y - GROUND_LINE) / 1.10, 0.0, 1.0);

    // perspective pinch (subtle)
    const float PERSPECTIVE_PINCH = 3.2;
    float persp = mix(1.0, PERSPECTIVE_PINCH, worldDepth);
    vec2  groundUV = vec2(centered.x / max(persp, 1e-5), centered.y);

    // === your brown palette (base → light via noise) ========================
    const vec3 baseBrown  = vec3(0.25, 0.15, 0.08);
    const vec3 lightBrown = vec3(0.42, 0.30, 0.16);
    const float DIRT_FREQ = 5.5;

    half  nH   = g_fbm2_h(half2(groundUV) * half(DIRT_FREQ));
    float n    = float(nH);                 // 0..1
    vec3  dirt = mix(baseBrown, lightBrown, n);

    // Dust flecks (very cheap, no loops): thresholded high-freq noise
    half  dnH  = g_vnoise_h(half2(groundUV) * half(22.0));
    float dust = smoothstep(0.86, 0.93, float(dnH));        // sparse 0..1
    dirt = mix(dirt, vec3(0.17), 0.25 * dust);              // light specks

    // Flat-lit dirt (keeps your Lighting shader)
    vec3 Nflat = vec3(0.0, 0.0, 1.0);
    vec3 groundCol = shadeStandardLD(dirt, Nflat, 0.90, 0.02, 1.0, 1.0, lightDir);

    // Horizon dark band just under the edge (your look)
    float band = 1.0 - smoothstep(GROUND_LINE + 0.02, GROUND_LINE + 0.16, centered.y);
    groundCol *= (1.0 - 0.22 * band);

    // Gentle lateral moon-side tint (very small so brown stays brown)
    float lx = clamp(0.5 + 0.5 * dot(normalize(vec2(1.0,0.0)), normalize(lightDir.xy)), 0.0, 1.0);
    groundCol += vec3(0.006, 0.010, 0.012) * (lx * (0.25 + 0.75 * (1.0 - worldDepth)));

    // === Global ground darkening (radial + toward horizon) ==================
    // (ported from your original)
    const float RAD_START = 0.45;
    const float RAD_END   = 1.05;
    float rN = smoothstep(RAD_START, RAD_END, length(centered - ghostGroundCenter));
    rN = pow(rN, 1.6);

    float horizonN = smoothstep(0.35, 1.00, 1.0 - worldDepth);

    const float RAD_STRENGTH     = 0.18;
    const float HORIZON_STRENGTH = 0.85;
    float darkAmt = clamp(RAD_STRENGTH * rN + HORIZON_STRENGTH * horizonN, 0.0, 1.0);
    float dark    = 0.60 * darkAmt;  // cap darkest
    groundCol = mix(groundCol, vec3(0.0), dark);

    g.color = clamp(groundCol, 0.0, 1.0);
    return g;
}
    """.trimIndent()
}
