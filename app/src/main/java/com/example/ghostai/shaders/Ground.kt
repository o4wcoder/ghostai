package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Ground {
    @Language("AGSL")
    val ground = """
        // ===== Ground =====
        struct GroundData {
            float mask;   // 0 sky, 1 ground
            vec3  albedo; // base ground color
        };
        
        
float getDustMask(vec2  groundUV) {
    const float DUST_FREQ   = 22.0;
    const float DUST_RAD    = 0.035;
    const float DUST_SOFT   = 0.020;
    const float DUST_KEEP_TH= 0.65;

    vec2  gUV      = groundUV * DUST_FREQ;
    vec2  baseCell = floor(gUV);
    vec2  fracUV   = fract(gUV);
    float dustMask = 0.0;
    for (int j = -1; j <= 1; ++j) {
        for (int i = -1; i <= 1; ++i) {
            vec2 cid = baseCell + vec2(float(i), float(j));
            float rx = hash21(cid + vec2(13.0, 71.0));
            float ry = hash21(cid + vec2(29.0, 97.0));
            vec2  c  = vec2(float(i), float(j)) + vec2(rx, ry);

            vec2  d2  = fracUV - c;
            float rad = DUST_RAD * (0.7 + 0.6 * hash21(cid + vec2(5.0, 11.0)));
            float sdf = length(d2) - rad;

            float m    = 1.0 - smoothstep(0.0, DUST_SOFT, sdf);
            float keep = step(DUST_KEEP_TH, hash21(cid + vec2(101.0, 203.0)));
            dustMask   = max(dustMask, m * keep);
        }
    }
    
    return dustMask;
}
GroundData drawGround(vec2 centered, float t) {
    GroundData g;

    // ───────── KNOBS ─────────
    const float groundY  = 0.48;
    const float feather  = 0.04;

    // Path wedge / perspective
    const float LANE_SCALE_H = 0.55;
    const float LANE_SCALE_B = 1.40;
    const float LANE_OUTER   = 0.48;
    const float EDGE_SOFT    = 0.010;
    const float REVEAL_FRAC  = 0.06;
    const float REVEAL_SOFT  = 0.015;

    const float PERSPECTIVE_PINCH = 3.2;

    // Dirt colors / noise
    const vec3  baseBrown   = vec3(0.25, 0.15, 0.08);
    const vec3  lightBrown  = vec3(0.42, 0.30, 0.16);
    const float DIRT_FREQ   = 5.5;

    // Rocks (appearance)
    const float ROCK_FREQ_X        = 5.0;   // integer-cell frequency across
    const float ROCK_FREQ_Y        = 10.0;  // integer-cell frequency down
    const float ROCK_DENSITY       = 0.30;
    const float ROCK_SIZE_NEAR_MIN = 0.16;
    const float ROCK_SIZE_NEAR_MAX = 0.28;
    const float ROCK_SIZE_FAR_MIN  = 0.08;
    const float ROCK_SIZE_FAR_MAX  = 0.16;
    const float ROCK_EDGE_SOFT     = 0.08;

    // Rock shaping / shading tweaks
    const float ROCK_HEIGHT_GAIN   = 1.35;   // gentler sides
    const float ROCK_BEVEL_EXP     = 1.25;
    const float ROCK_TWIST_MAX     = 0.9;    // max rotation (radians) per rock
    const float ROCK_SKIRT_W       = 0.20;   // width of dirt AO ring (in d units)
    const float ROCK_SKIRT_GAIN    = 0.35;   // strength of dirt AO ring

    const vec3  ROCK_A             = vec3(0.36, 0.35, 0.34);
    const vec3  ROCK_B             = vec3(0.58, 0.57, 0.55);
    const float ROCK_SPEC_POWER    = 28.0;
    const float ROCK_SPEC_INT      = 0.08;

    // Light
    const vec3 L = normalize(vec3(+0.60, -0.85, 0.75));
    const vec3 V = vec3(0.0, 0.0, 1.0);
    const vec3 H = normalize(L + V);
    const vec3 SPEC_TINT = vec3(0.95, 0.98, 1.00);
    const float AMBIENT_MIN = 0.12;

    // ── Horizon / mask
    g.mask = smoothstep(groundY - feather, groundY + feather, centered.y);

    // === Two depth references ===============================================
    float centeredBottomY = 0.5 * (iResolution.y / min(iResolution.x, iResolution.y));
    const float WORLD_BOTTOM_Y = 1.0;

    float screenDepth = clamp((centered.y - groundY) /
                              max(1e-5, (centeredBottomY - groundY)), 0.0, 1.0);
    float worldDepth  = clamp((centered.y - groundY) /
                              max(1e-5, (WORLD_BOTTOM_Y - groundY)), 0.0, 1.0);

    // === Path wedge (visual) =================================================
    float laneScaleVis = mix(LANE_SCALE_H, LANE_SCALE_B, worldDepth);
    float xLvis = -LANE_OUTER * laneScaleVis;
    float xRvis =  LANE_OUTER * laneScaleVis;

    float leftGate  = smoothstep(xLvis - EDGE_SOFT, xLvis + EDGE_SOFT, centered.x);
    float rightGate = 1.0 - smoothstep(xRvis - EDGE_SOFT, xRvis + EDGE_SOFT, centered.x);
    float pathInside = leftGate * rightGate;

    float yStart = groundY + (centeredBottomY - groundY) * REVEAL_FRAC;
    float appear = smoothstep(yStart - REVEAL_SOFT, yStart + REVEAL_SOFT, centered.y);
    pathInside *= appear;

    // === Dirt base ===========================================================
    float persp = mix(1.0, PERSPECTIVE_PINCH, worldDepth);
    vec2  groundUV = vec2(centered.x / max(persp, 1e-5), centered.y);

    float n = fbm(groundUV * DIRT_FREQ);
    vec3  dirt = mix(baseBrown, lightBrown, n);

    // Dust flecks (your helper)
    float dustMask = getDustMask(groundUV);
    dirt = mix(dirt, vec3(0.17), clamp(dustMask, 0.0, 1.0));

    vec3 groundCol = dirt;

    // === ROCKS (deterministic int-cell at original frequency) ===============
    float u = clamp((centered.x - xLvis) / max(xRvis - xLvis, 1e-5), 0.0, 1.0);
    vec2  cellF = vec2(u * ROCK_FREQ_X, worldDepth * ROCK_FREQ_Y);
    vec2  iCell = floor(cellF);
    vec2  fCell = fract(cellF);

    for (int j = -1; j <= 1; ++j) {
        for (int i = -1; i <= 1; ++i) {
            vec2 id = iCell + vec2(float(i), float(j));

            float r0 = hash21(id + vec2(2.73, 3.74));   // density
            if (r0 > ROCK_DENSITY) continue;

            float r1 = hash21(id + vec2(17.0,  5.0));   // jitter x
            float r2 = hash21(id + vec2(37.0, 11.0));   // jitter y
            float r3 = hash21(id + vec2(59.0, 23.0));   // size/var
            float r4 = hash21(id + vec2(83.0, 47.0));   // rotation
            float rCol = hash21(id + vec2(109.0, 61.0)); 

            // center inside the integer cell
            vec2 c = vec2(float(i), float(j)) + vec2(r1, r2) * 0.80 - 0.40;

            // Radii (bigger near bottom)
            float nearR = mix(ROCK_SIZE_NEAR_MIN, ROCK_SIZE_NEAR_MAX, r3);
            float farR  = mix(ROCK_SIZE_FAR_MIN,  ROCK_SIZE_FAR_MAX,  r3);
            float a = mix(farR, nearR, worldDepth);
            float b = a * mix(0.70, 1.10, r2);

            // --- rotate rock shape for variety ---
            float ang = (r4 - 0.5) * ROCK_TWIST_MAX;
            float s = sin(ang), cA = cos(ang);
            vec2 rel   = fCell - c;
            vec2 relR  = vec2(cA*rel.x - s*rel.y, s*rel.x + cA*rel.y);

            // Ellipse coords in rotated frame
            vec2 e = vec2(relR.x / max(a,1e-5), relR.y / max(b,1e-5));
            float d  = length(e);
            float rim  = smoothstep(1.0, 1.0 + ROCK_EDGE_SOFT, d);
            float mask = 1.0 - rim;
            if (mask <= 0.0) continue;

            // ---------- Flatter-top dome ----------
            float uedge = clamp(d, 0.0, 1.0);           // 0 center → 1 rim
            float hcap  = pow(1.0 - uedge*uedge, 1.05); // parabolic cap (flatter top)
            float slope = ROCK_HEIGHT_GAIN * (2.0 * uedge);
            vec2  dirOut = normalize(e + 1e-6);
            vec3  nrm   = normalize(vec3(dirOut * slope, 1.0));

            // Dirt AO "skirt" just OUTSIDE the rim to seat the rock
            float skirt = smoothstep(1.0, 1.0 + ROCK_SKIRT_W, d)
                        - smoothstep(1.0 + ROCK_SKIRT_W, 1.0 + 2.0*ROCK_SKIRT_W, d);
            groundCol = mix(groundCol, groundCol * (1.0 - ROCK_SKIRT_GAIN), skirt);

            // Rim AO on the rock itself
            float rimAO = mix(0.85, 1.0, 1.0 - smoothstep(0.72, 1.00, uedge));

            // Base rock color + variation
            vec3 rcol = mix(ROCK_A, ROCK_B, rCol) * (0.98 + 0.04 * (r3 - 0.5));

            // Brown dirt dusting
            vec3  dustTint = mix(baseBrown, lightBrown, 0.5);
            float dustAmt  = 0.15 + 0.20 * r2;
            dustAmt       *= mix(0.85, 1.00, worldDepth);
            rcol           = mix(rcol, dustTint, clamp(dustAmt, 0.0, 1.0));

            // Lighting (height-varying spec)
            float NdL  = clamp(dot(nrm, L), 0.0, 1.0);
            float diff = mix(AMBIENT_MIN, 1.0, NdL);
            float specPow = mix(18.0, ROCK_SPEC_POWER, hcap);  // tighter at crown
            float specInt = ROCK_SPEC_INT * mix(0.6, 1.0, hcap);
            float spec    = pow(max(dot(nrm, H), 0.0), specPow) * specInt;

            vec3 lit = (rcol * diff + SPEC_TINT * spec) * rimAO;

            // Blend rock over dirt
            groundCol = mix(groundCol, lit, clamp(mask, 0.0, 1.0));
        }
    }

    // === Outside the wedge ===================================================
    vec3 outsideColor = vec3(0.0);
    g.albedo = mix(outsideColor, groundCol, pathInside);

    return g;
}




// p is in CENTERED coords; y increases downward.
// Returns 0..1 (1 = darkest) just under the ghost near the horizon.
float groundContactShadowCentered(vec2 p, float footX, float groundY,
                                  float width /*~0.25*/, float falloffY /*~0.25*/) {
    // Gaussian along X around the foot
    float dx = p.x - footX;
    float lateral = exp(-(dx*dx) / (width*width));   // 1 at footX → 0 outward

    // Only BELOW the horizon, fade out as we go farther down
    float below    = step(groundY, p.y);                      // 1 if p.y >= groundY
    float vertical = 1.0 - smoothstep(0.0, falloffY, p.y - groundY); // 1 at horizon → 0 deeper

    return below * lateral * vertical;
}
// TOP-RIGHT light → shadow down-left.
// p, footX, groundY in CENTERED coords. L2_norm = normalize(sceneLight.xy).
float groundCastShadowCentered(vec2 p, float footX, float groundY, vec2 L2_norm,
                               float radius, float sx)
{
    // Contact point under the ghost at the horizon
    vec2 c0 = vec2(footX, groundY);

    // Cast direction on ground (away from the light)
    vec2 along = normalize(vec2(-L2_norm.x, -L2_norm.y));
    vec2 perp  = vec2(-along.y, along.x);

    // Subtle sync with float/tail
    float tailPhase  = sin(iTime * 2.0);
    float floatPhase = sin(iTime * 0.7);

    // Offset the cast origin a bit away from the light
    float offY = max(0.0, -L2_norm.y) * (1.05 + 0.10 * floatPhase) * radius;
    float offX = (-L2_norm.x)        * (0.65 + 0.08 * floatPhase) * radius * sx;
    vec2  c    = c0 + vec2(offX, offY);

    // Rotate into shadow frame
    vec2 rel = p - c;
    float y  = dot(rel, along); // forward (away from light)
    float x  = dot(rel, perp);  // lateral

    // --- Teardrop SDF (rounded, tapered) ----------------------------------
    float len = (1.55 + 0.15 * floatPhase) * radius; // total length
    float yClamped = clamp(y, 0.0, len);             // never behind the start
    float yN = yClamped / max(len, 1e-5);

    // gentle S-curve so it “breathes” with the tail
    float curve = (0.18 * radius * sx) * tailPhase * yN * (1.0 - yN);
    x -= curve;

    float r0 = (0.70 * radius * sx) * (1.0 + 0.10 * tailPhase); // near width
    float r1 = (0.20 * radius * sx) * (1.0 - 0.10 * tailPhase); // tip width

    // Smooth-min union of two circles at (0,0) and (0,len)
    float d0 = length(vec2(x, yClamped))       - r0;
    float d1 = length(vec2(x, yClamped - len)) - r1;
    float k  = 0.25 * radius; // smoothing factor
    float h  = clamp(0.5 + 0.5*(d1 - d0)/max(k, 1e-5), 0.0, 1.0);
    float d  = mix(d1, d0, h) - k*h*(1.0 - h);  // signed distance to teardrop

    // Penumbra + umbra from SDF
    float soft     = 0.10 * radius;
    float penumbra = 1.0 - smoothstep(-soft, soft, d);
    float core     = 1.0 - smoothstep(-0.4*soft, 0.4*soft, d + 0.15*r1);
    core *= exp(-yClamped / (0.5 * len)); // fades toward the tip

    // Gating: only below horizon, and a SOFT start near the contact
    float below     = step(groundY, p.y);
    float startFade = smoothstep(-0.25*radius, 0.10*radius, y); // no hard line

    return clamp((0.55*penumbra + 0.85*core) * below * startFade, 0.0, 1.0);
}

// Elliptical ground shadow with a *travelling rim wave* (no radial pulsing).
// p, (cx,cy) in CENTERED coords. rx/ry = ellipse radii. softness = edge feather.
float groundOvalShadowCentered(vec2 p, float cx, float cy,
                               float rx, float ry, float softness)
{
    // Subtle rotation so the oval “breathes” with the tail motion
    float rot = 0.12 * sin(iTime * 2.0);  // tweak 0.08–0.16
    float s = sin(rot), c = cos(rot);

    // Rotate into ellipse frame
    vec2 d = vec2(p.x - cx, p.y - cy);
    vec2 r = vec2( c*d.x + s*d.y, -s*d.x + c*d.y);

    // Normalized ellipse coords
    float nx = r.x / max(rx, 1e-5);
    float ny = r.y / max(ry, 1e-5);
    float ang = atan(ny, nx);                // angle around ellipse
    float rad = sqrt(nx*nx + ny*ny);         // radius in ellipse space

    // ---- Travelling wave on the rim (direction matches tail: leftward) ---
    const float waveLobes = 6.0;             // number of ripples around rim
    const float waveSpeed = 2.0;             // angular travel speed
    const float waveAmp   = 0.06;            // ripple amplitude (in normalized units)

    // Note the *minus* on time → same travel direction as tail
    float rim = 1.0 + waveAmp * sin(waveLobes * ang - iTime * waveSpeed);

    // Soft edge around the (wavy) rim
    float sft  = max(softness, 1e-5);
    float mask = 1.0 - smoothstep(rim - sft, rim + sft, rad);

    // Fade the upper portion so it blends near the horizon
    float topFade = smoothstep(0.0, ry * 0.6, p.y - (cy - ry));

    return clamp(mask * topFade, 0.0, 1.0);
}

        vec3 mixGroundColor(vec3 mixColor, GroundData ground, float ghostMask) {
    
            // Strength of the ground “overlay” (0 = invisible, 1 = full replace)
            float groundStrength = 0.5;  
    
            // If you want the dirt to be a bit translucent, set overlayStrength < 1
            float overlayStrength = 0.9;

            // Only below horizon (ground.mask) and never over the ghost
            float groundBlend = ground.mask * (1.0 - ghostMask);
    
            return mix(
               mixColor,
               mix(mixColor, ground.albedo, overlayStrength), // translucent dirt
               groundBlend
               );
        }


    """.trimIndent()
}
