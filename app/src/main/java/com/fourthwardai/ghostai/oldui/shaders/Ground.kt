package com.fourthwardai.ghostai.oldui.shaders

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
// ---- tiny helpers for a single rock ----------------------------------------
float rockMaskR(vec2 P, vec2 C, vec2 R, float rot, float soft){
    float s = sin(rot), c = cos(rot);
    vec2 pr = vec2(c*(P.x-C.x) - s*(P.y-C.y), s*(P.x-C.x) + c*(P.y-C.y));
    vec2 q  = pr / max(R, vec2(1e-5));
    float d = length(q) - 1.0;
    return 1.0 - smoothstep(0.0, soft, d);
}
void shadeBlendRock(inout vec3 col, vec2 P, vec2 C, vec2 R, float rot,
                    vec3 baseCol, vec3 lightDir, float soft, float bboxPad){
    // cheap bounds so most pixels skip the math
    if (abs(P.x - C.x) > R.x * bboxPad || abs(P.y - C.y) > R.y * bboxPad) return;

    float m = rockMaskR(P, C, R, rot, soft);
    if (m <= 0.0) return;

    // dome-ish normal (fast)
    float s = sin(rot), c = cos(rot);
    vec2 pr = vec2(c*(P.x-C.x) - s*(P.y-C.y), s*(P.x-C.x) + c*(P.y-C.y));
    vec2 q  = pr / max(R, vec2(1e-5));
    float u = clamp(length(q), 0.0, 1.0);
    float h = 1.0 - u*u; // higher in the middle
    vec2  dir = normalize(q + 1e-6);
    vec3  N   = normalize(vec3(dir * (2.0*u*1.35), 1.0));

    float rough = mix(0.65, 0.45, h);
    float spec  = 0.08 * mix(0.60, 1.00, h);
    float ao    = mix(0.85, 1.00, 1.0 - u);

    vec3 lit = shadeStandardLD(baseCol, N, rough, spec, ao, 1.0, lightDir);
    col = mix(col, lit, m);
}

// Ultra-lite rock: soft ellipse, 2-tone rim, no lighting math
void shadeBlendRockLite(inout vec3 col, vec2 P, vec2 C, vec2 R, float rot,
                        vec3 innerCol, vec3 outerCol, float feather, float bboxPad)
{
    // tight precheck so most pixels skip immediately
    if (abs(P.x - C.x) > R.x * bboxPad || abs(P.y - C.y) > R.y * bboxPad) return;

    // rotate once (precompute cos/sin constants if you like)
    float s = sin(rot), c = cos(rot);
    vec2 pr = vec2(c*(P.x-C.x) - s*(P.y-C.y), s*(P.x-C.x) + c*(P.y-C.y));
    vec2 q  = pr / max(R, vec2(1e-5));
    float d = length(q);

    // soft edge
    float m = 1.0 - smoothstep(1.0, 1.0 + feather, d);
    if (m <= 0.0) return;

    // subtle rim tint so it looks beveled (no normals)
    float rim = smoothstep(0.75, 1.0, d);     // 0 center → 1 edge
    vec3  rockCol = mix(innerCol, outerCol, rim);

    col = mix(col, rockCol, m);
}


// ---- main ground ------------------------------------------------------------
GroundData drawGround(vec2 centered, float iTime, vec3 lightDir, vec2 ghostGroundCenter){
    GroundData g;

    // horizon mask (AA)
    const float GROUND_LINE  = 0.48;
    const float EDGE_FEATHER = 0.006;
    g.mask = smoothstep(GROUND_LINE - EDGE_FEATHER, GROUND_LINE + EDGE_FEATHER, centered.y);
    if (g.mask <= 0.0) { g.color = vec3(0.0); return g; }

    // depth below horizon (0..1)
    float worldDepth = clamp((centered.y - GROUND_LINE) / 1.10, 0.0, 1.0);

    // subtle perspective pinch
    const float PERSPECTIVE_PINCH = 3.2;
    float persp = mix(1.0, PERSPECTIVE_PINCH, worldDepth);
    vec2  groundUV = vec2(centered.x / max(persp, 1e-5), centered.y);

    // brown palette + dirt noise
    const vec3 baseBrown  = vec3(0.25, 0.15, 0.08);
    const vec3 lightBrown = vec3(0.42, 0.30, 0.16);
    const float DIRT_FREQ = 5.5;

    half  nH   = g_fbm2_h(half2(groundUV) * half(DIRT_FREQ));
    vec3  dirt = mix(baseBrown, lightBrown, float(nH));

    // dust flecks (cheap)
    float dust = smoothstep(0.86, 0.93, float(g_vnoise_h(half2(groundUV) * half(22.0))));
    dirt = mix(dirt, vec3(0.17), 0.25 * dust);

    // flat-lit ground
    vec3 Nflat = vec3(0.0, 0.0, 1.0);
    vec3 groundCol = shadeStandardLD(dirt, Nflat, 0.90, 0.02, 1.0, 1.0, lightDir);

    // horizon dark band just under edge
    float band = 1.0 - smoothstep(GROUND_LINE + 0.02, GROUND_LINE + 0.16, centered.y);
    groundCol *= (1.0 - 0.22 * band);

    // tiny lateral moon-side tint
    float lx = clamp(0.5 + 0.5 * dot(normalize(vec2(1.0,0.0)), normalize(lightDir.xy)), 0.0, 1.0);
    groundCol += vec3(0.006, 0.010, 0.012) * (lx * (0.25 + 0.75 * (1.0 - worldDepth)));

// === Hero rocks (fixed small set, aspect-safe) ===============================
//{
//    // Screen extents in your centered space
//    float halfMin         = min(iResolution.x, iResolution.y);
//    float halfX           = 0.5 * (iResolution.x / halfMin);
//    float centeredBottomY = 0.5 * (iResolution.y / halfMin);
//    float xL = -halfX, xR = halfX;
//
//    // Only draw once we’re a bit below the horizon (looser than before)
//    if (centered.y > GROUND_LINE + 0.25) {
//        // Pick three Y bands between horizon and bottom, leaving a small margin
//        float yNear = mix(GROUND_LINE + 0.30, centeredBottomY - 0.08, 0.30);
//        float yMid  = mix(GROUND_LINE + 0.30, centeredBottomY - 0.08, 0.55);
//        float yFar  = mix(GROUND_LINE + 0.30, centeredBottomY - 0.08, 0.80);
//
//        // Size scale that adapts to how tall the ground area is
//        float s = max(centeredBottomY - GROUND_LINE, 0.001);
//
//        vec3 RA = vec3(0.38, 0.37, 0.36);
//        vec3 RB = vec3(0.58, 0.57, 0.55);
//
//        // Five small rocks, spread across width; sizes are relative to 's'
//        shadeBlendRock(groundCol, centered, vec2(mix(xL, xR, 0.18), yMid), vec2(0.10*s, 0.06*s), -0.15, mix(RA,RB,0.35), lightDir, 0.08, 1.4);
//        shadeBlendRock(groundCol, centered, vec2(mix(xL, xR, 0.36), yNear), vec2(0.08*s, 0.05*s),  0.10, mix(RA,RB,0.55), lightDir, 0.08, 1.4);
//        shadeBlendRock(groundCol, centered, vec2(mix(xL, xR, 0.54), yFar), vec2(0.11*s, 0.07*s), -0.05, mix(RA,RB,0.40), lightDir, 0.08, 1.4);
//        shadeBlendRock(groundCol, centered, vec2(mix(xL, xR, 0.72), yMid), vec2(0.07*s, 0.05*s),  0.25, mix(RA,RB,0.50), lightDir, 0.08, 1.4);
//        shadeBlendRock(groundCol, centered, vec2(mix(xL, xR, 0.86), yFar), vec2(0.09*s, 0.06*s), -0.32, mix(RA,RB,0.60), lightDir, 0.08, 1.4);
//    }
//}
// === Hero rocks (ULTRA-LITE, 3 rocks, tight bounds) =========================
{
    // Centered-space extents
    float halfMin         = min(iResolution.x, iResolution.y);
    float halfX           = 0.5 * (iResolution.x / halfMin);
    float centeredBottomY = 0.5 * (iResolution.y / halfMin);
    float xL = -halfX, xR = halfX;

    // Only process a thin band where rocks live (cheap gate!)
    float yTop  = GROUND_LINE + 0.28;
    float yBot  = centeredBottomY - 0.06;
    if (centered.y > yTop && centered.y < yBot) {

        // Y rows as fractions of the ground height so they’re always visible
        float yA = mix(yTop, yBot, 0.35);
        float yB = mix(yTop, yBot, 0.60);
        float yC = mix(yTop, yBot, 0.82);

        // Scale sizes to ground height
        float s  = max(yBot - yTop, 0.001);

        // Colors (same family you used)
        vec3 RA = vec3(0.36, 0.35, 0.34);
        vec3 RB = vec3(0.58, 0.57, 0.55);

        // Three rocks total (vs five): far cheaper
        shadeBlendRockLite(groundCol, centered, vec2(mix(xL, xR, 0.25), yB),
                           vec2(0.10*s, 0.06*s), -0.10, mix(RA,RB,0.45), vec3(0.44,0.42,0.40),
                           0.08, 1.25);

        shadeBlendRockLite(groundCol, centered, vec2(mix(xL, xR, 0.55), yA),
                           vec2(0.12*s, 0.07*s),  0.05, mix(RA,RB,0.35), vec3(0.50,0.48,0.46),
                           0.08, 1.25);

        shadeBlendRockLite(groundCol, centered, vec2(mix(xL, xR, 0.78), yC),
                           vec2(0.09*s, 0.06*s), -0.22, mix(RA,RB,0.55), vec3(0.46,0.44,0.42),
                           0.08, 1.25);
    }
}



    // === Global ground darkening (radial + toward horizon) ===================
    const float RAD_START = 0.45;
    const float RAD_END   = 1.05;
    float rN = smoothstep(RAD_START, RAD_END, length(centered - ghostGroundCenter));
    rN = pow(rN, 1.6);

    float horizonN = smoothstep(0.35, 1.00, 1.0 - worldDepth);

    const float RAD_STRENGTH     = 0.18;
    const float HORIZON_STRENGTH = 0.85;
    float darkAmt = clamp(RAD_STRENGTH * rN + HORIZON_STRENGTH * horizonN, 0.0, 1.0);
    float dark    = 0.60 * darkAmt;
    groundCol = mix(groundCol, vec3(0.0), dark);

    g.color = clamp(groundCol, 0.0, 1.0);
    return g;
}

    """.trimIndent()
}
