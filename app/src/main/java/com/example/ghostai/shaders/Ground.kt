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

GroundData drawGround(vec2 centered, float t) {
    GroundData g;

    // ───────── KNOBS ─────────
    const float groundY  = 0.48;
    const float feather  = 0.04;

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

    // Round dust flecks
    const float DUST_FREQ   = 22.0;
    const float DUST_RAD    = 0.035;
    const float DUST_SOFT   = 0.020;
    const float DUST_KEEP_TH= 0.65;   // ↑ for fewer flecks

    // Rocks (appearance)
    const float ROCK_FREQ_X        = 5.0;   // **frequency**, not fine grid
    const float ROCK_FREQ_Y        = 10.0;
    const float ROCK_DENSITY       = 0.30;
    const float ROCK_SIZE_NEAR_MIN = 0.16;
    const float ROCK_SIZE_NEAR_MAX = 0.28;
    const float ROCK_SIZE_FAR_MIN  = 0.08;
    const float ROCK_SIZE_FAR_MAX  = 0.16;
    const float ROCK_EDGE_SOFT     = 0.08;
    const float ROCK_HEIGHT_GAIN   = 1.6;
    const float ROCK_Z_BIAS        = 0.60;
    const float ROCK_BEVEL_EXP     = 1.25;
    const vec3  ROCK_A             = vec3(0.36, 0.35, 0.34);
    const vec3  ROCK_B             = vec3(0.48, 0.47, 0.45);
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

    // Round dust flecks (deterministic, soft)
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
            vec2  d2 = fracUV - c;
            float rad = DUST_RAD * (0.7 + 0.6 * hash21(cid + vec2(5.0, 11.0)));
            float sdf = length(d2) - rad;
            float m    = 1.0 - smoothstep(0.0, DUST_SOFT, sdf);
            float keep = step(DUST_KEEP_TH, hash21(cid + vec2(101.0, 203.0)));
            dustMask   = max(dustMask, m * keep);
        }
    }
    dirt = mix(dirt, vec3(0.17), clamp(dustMask, 0.0, 1.0));

    vec3 groundCol = dirt;

    // === ROCKS (deterministic int-cell at original frequency) ===============
    // Normalized lateral inside wedge
    float u = clamp((centered.x - xLvis) / max(xRvis - xLvis, 1e-5), 0.0, 1.0);

    // Integer cell IDs at your rock frequencies (device-independent)
    vec2  cellF = vec2(u * ROCK_FREQ_X, worldDepth * ROCK_FREQ_Y);
    vec2  iCell = floor(cellF);
    vec2  fCell = fract(cellF);

    // Neighborhood so ellipses render across borders
    for (int j = -1; j <= 1; ++j) {
        for (int i = -1; i <= 1; ++i) {
            vec2 id = iCell + vec2(float(i), float(j));

            float r0 = hash21(id + vec2(2.73, 3.74));   // density
            if (r0 > ROCK_DENSITY) continue;

            float r1 = hash21(id + vec2(17.0,  5.0));   // jitter x
            float r2 = hash21(id + vec2(37.0, 11.0));   // jitter y
            float r3 = hash21(id + vec2(59.0, 23.0));   // size/var

            vec2 c = vec2(float(i), float(j)) + vec2(r1, r2) * 0.80 - 0.40;

            float nearR = mix(ROCK_SIZE_NEAR_MIN, ROCK_SIZE_NEAR_MAX, r3);
            float farR  = mix(ROCK_SIZE_FAR_MIN,  ROCK_SIZE_FAR_MAX,  r3);
            float a = mix(farR, nearR, worldDepth);
            float b = a * mix(0.70, 1.10, r2);

            vec2 rel = fCell - c;
            vec2 e   = vec2(rel.x / max(a,1e-5), rel.y / max(b,1e-5));
            float d3 = length(e);
            float rim  = smoothstep(1.0, 1.0 + ROCK_EDGE_SOFT, d3);
            float mask = 1.0 - rim;
            if (mask <= 0.0) continue;

            float dome         = pow(clamp(1.0 - d3, 0.0, 1.0), ROCK_BEVEL_EXP);
            float slopeProfile = 4.0 * dome * (1.0 - dome);
            vec2  dirOut       = normalize(e + 1e-6);
            float slope        = ROCK_HEIGHT_GAIN * slopeProfile;
            float nz           = mix(1.0, ROCK_Z_BIAS, clamp(slope * 0.6, 0.0, 1.0));
            vec3  nrm          = normalize(vec3(dirOut * slope, nz));

            vec3 rcol = mix(ROCK_A, ROCK_B, r0) * (0.98 + 0.04 * (r3 - 0.5));
            vec3  dustTint = mix(baseBrown, lightBrown, 0.5);
            float dustAmt  = 0.15 + 0.20 * r2;
            dustAmt       *= mix(0.85, 1.00, worldDepth);
            rcol           = mix(rcol, dustTint, clamp(dustAmt, 0.0, 1.0));

            float NdL  = clamp(dot(nrm, L), 0.0, 1.0);
            float diff = mix(AMBIENT_MIN, 1.0, NdL);
            float spec = pow(max(dot(nrm, H), 0.0), ROCK_SPEC_POWER) * ROCK_SPEC_INT;

            vec3 lit = rcol * diff + SPEC_TINT * spec;

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
