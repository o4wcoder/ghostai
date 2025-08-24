package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Ground {
    @Language("AGSL")
    val ground = """
        // ===== Ground =====
        struct GroundData {
            float mask;   // 0 sky, 1 ground
            vec3  albedo; // final LIT ground color
        };

        // Distance mask for one curved blade in its local frame.
        // x = along blade [0..L], y = lateral. Curve y = k * (x/L)^2.
        // Width tapers from w0 → w1. Returns 0..1 alpha with soft edge.
        float bladeMaskLocal(vec2 p, float L, float w0, float w1, float k, float soft) {
            float x = p.x, y = p.y;
            float gate = step(0.0, x) * step(x, L);
            float t = clamp(x / max(L, 1e-5), 0.0, 1.0);
            float curve = k * t * t;
            float halfW = 0.5 * mix(w0, w1, t);
            float d = abs(y - curve) - halfW;
            return gate * (1.0 - smoothstep(0.0, soft, d));
        }

        // Upright needle tufts with bottom-seam fix.
        // centered: CENTERED coords
        // worldDepth: 0 at horizon → 1 at world bottom
        // xLvis/xRvis: wedge edges, pathInside: 0..1
        vec3 mixGrassClumps(
            vec2 centered, float worldDepth,
            float xLvis, float xRvis,
            float pathInside,
            vec3 groundCol,
            vec3 lightDir
        ){
            // ─── Count / placement (main knob = GRASS_RATE) ───
            const float GRASS_FREQ_X   = 5.0;
            const float GRASS_FREQ_Y   = 10.0;
            const float GRASS_RATE     = 0.25;   // ↑ for more clumps, ↓ for fewer

            // ─── Blade look ───
            const int   MAX_BLADES     = 9;
            const float BLADES_MIN     = 4.0;
            const float BLADES_MAX     = 7.0;

            const float BASE_R         = 0.055;  // tiny base mound just to anchor
            const float BASE_SOFT      = 0.035;

            const float L_MIN          = 0.58;
            const float L_MAX          = 0.95;
            const float W_BASE_MIN     = 0.050;
            const float W_BASE_MAX     = 0.075;
            const float W_TIP          = 0.012;

            const float CURVE_MAX      = 0.30;   // lateral bend at tip
            const float CURVE_BACK_BIAS= -0.12;  // curl slightly back toward the axis
            const float SOFT_EDGE      = 0.020;

            // Upright orientation (up = -Y in CENTERED coords)
            const float UP_ANGLE       = 1.57079633; // +π/2
            const float FAN_SPREAD     = 0.18;       // tight fan = upright feel
            const float ROT_JITTER     = 0.12;
            const float MAX_TILT       = 0.32;       // clamp away from sideways

            // Color: dark green with a brown tint
            const vec3  GRASS_DARK     = vec3(0.06, 0.11, 0.07);
            const vec3  GRASS_LIGHT    = vec3(0.14, 0.22, 0.12);
            const vec3  DIRT_TINT      = vec3(0.23, 0.17, 0.11);
            const float GRASS_DIRT_MIX = 0.18;

            // Early outs
            if (pathInside <= 0.0 || worldDepth <= 0.0) return groundCol;
            if (worldDepth >= 0.995) return groundCol; // avoid bottom seam

            // Lateral inside wedge (stable)
            float u = clamp((centered.x - xLvis) / max(xRvis - xLvis, 1e-5), 0.0, 1.0);

            // Use a Y that never lands exactly on a cell boundary
            float worldYForCells = min(worldDepth, 0.9995);
            vec2  cellF = vec2(u * GRASS_FREQ_X, worldYForCells * GRASS_FREQ_Y);
            vec2  iCell = floor(cellF);
            vec2  fCell = fract(cellF);

            // Fade blades near bottom so cutoff is invisible
            float bottomFade = 1.0 - smoothstep(0.985, 0.997, worldDepth);

            // Neighborhood so edges render across borders
            for (int j = -1; j <= 1; ++j) {
                for (int i = -1; i <= 1; ++i) {
                    vec2 id = iCell + vec2(float(i), float(j));

                    // Spawn gate (count knob)
                    float rGate = hash21(id + vec2(7.0, 19.0));
                    if (rGate > GRASS_RATE) continue;

                    // Per-clump randoms
                    float rJx = hash21(id + vec2(11.0, 31.0));
                    float rJy = hash21(id + vec2(23.0, 53.0));
                    float rN  = hash21(id + vec2(41.0, 17.0));
                    float rRot= hash21(id + vec2(59.0, 83.0));

                    // Clump center
                    vec2 C = vec2(float(i), float(j)) + vec2(rJx, rJy) * 0.70 - 0.35;

                    // Base mound (tiny, feathered)
                    vec2  relBase = fCell - C;
                    float base = 1.0 - smoothstep(BASE_R, BASE_R + BASE_SOFT, length(relBase));
                    vec3  baseCol = mix(GRASS_DARK, GRASS_LIGHT, 0.25);
                    baseCol = mix(baseCol, DIRT_TINT, GRASS_DIRT_MIX);

                    vec3 baseLit = shadeStandardLD(
                        baseCol, vec3(0.0,0.0,1.0),
                        0.95,    // rough
                        0.00,    // no specular on dirt tuft base
                        1.0,     // AO
                        1.0,     // shadow
                        lightDir
                    );
                    groundCol = mix(groundCol, baseLit, base * pathInside * bottomFade);

                    // Blade count
                    float want = mix(BLADES_MIN, BLADES_MAX, rN);
                    int blades = int(clamp(floor(want + 0.5), 1.0, float(MAX_BLADES)));

                    // Axis near straight up with tiny clump rotation
                    float clumpAngleBase = UP_ANGLE + (rRot - 0.5) * 0.25;

                    for (int b = 0; b < MAX_BLADES; ++b) {
                        if (b >= blades) break;

                        // Per-blade params
                        float rl = hash21(id + vec2(173.0, 29.0) + float(b));
                        float rw = hash21(id + vec2(191.0, 47.0) + float(b));
                        float rc = hash21(id + vec2(211.0, 71.0) + float(b));
                        float rj = hash21(id + vec2(233.0, 89.0) + float(b));

                        float L  = mix(L_MIN, L_MAX, rl) * mix(0.9, 1.15, worldDepth);
                        float w0 = mix(W_BASE_MIN, W_BASE_MAX, rw);
                        float w1 = W_TIP;
                        float k  = (rc - 0.5) * 2.0 * CURVE_MAX + CURVE_BACK_BIAS;

                        // Fan around vertical, clamp tilt so blades don’t lie sideways
                        float fan   = ((float(b) - 0.5*(float(blades)-1.0)) * FAN_SPREAD)
                                      + (rj - 0.5) * ROT_JITTER;
                        float angRaw = clumpAngleBase + fan;
                        float ang    = UP_ANGLE + clamp(angRaw - UP_ANGLE, -MAX_TILT, +MAX_TILT);

                        // Rotate world→blade frame: x points UP (toward -Y), y lateral
                        float s = sin(ang), c = cos(ang);
                        vec2 rel = fCell - C;
                        vec2 p   = vec2(c*rel.x - s*rel.y, s*rel.x + c*rel.y);

                        // Tapered curved blade mask (0..1), x in [0..L]
                        float m = bladeMaskLocal(p, L, w0, w1, k, SOFT_EDGE);
                        if (m <= 0.0) continue;

                        // Color/shading (tip brighter) + brown tint
                        float t = clamp(p.x / max(L, 1e-5), 0.0, 1.0);
                        vec3 gcol = mix(GRASS_DARK, GRASS_LIGHT, mix(0.15, 0.85, t));
                        gcol = mix(gcol, DIRT_TINT, GRASS_DIRT_MIX);

                        // subtle centerline darkening
                        float vein = smoothstep(0.0, 0.35, abs(p.y - k*t*t));
                        gcol *= mix(0.92, 1.0, vein);

                        vec3 litGrass = shadeStandardLD(
                            gcol, vec3(0.0,0.0,1.0),
                            0.95, 0.0, 1.0, 1.0, lightDir
                        );
                        groundCol = mix(groundCol, litGrass, m * pathInside * bottomFade);
                    }
                }
            }

            return groundCol;
        }

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

        GroundData drawGround(vec2 centered, float t, vec3 lightDir) {
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
            const float ROCK_FREQ_X        = 5.0;   // integer-cell freq across
            const float ROCK_FREQ_Y        = 10.0;  // integer-cell freq down
            const float ROCK_DENSITY       = 0.30;
            const float ROCK_SIZE_NEAR_MIN = 0.16;
            const float ROCK_SIZE_NEAR_MAX = 0.28;
            const float ROCK_SIZE_FAR_MIN  = 0.08;
            const float ROCK_SIZE_FAR_MAX  = 0.16;
            const float ROCK_EDGE_SOFT     = 0.08;

            // Rock shaping / shading tweaks
            const float ROCK_HEIGHT_GAIN   = 1.35;   // gentler sides
            const float ROCK_BEVEL_EXP     = 1.25;
            const float ROCK_TWIST_MAX     = 0.9;    // max rotation (radians)
            const float ROCK_SKIRT_W       = 0.20;   // dirt AO ring width (in d)
            const float ROCK_SKIRT_GAIN    = 0.35;   // dirt AO strength

            const vec3  ROCK_A             = vec3(0.36, 0.35, 0.34);
            const vec3  ROCK_B             = vec3(0.58, 0.57, 0.55);
            const float ROCK_SPEC_INT      = 0.08;

            // ── Horizon / mask
            g.mask = smoothstep(groundY - feather, groundY + feather, centered.y);

            // === Depth refs ===================================================
            float centeredBottomY = 0.5 * (iResolution.y / min(iResolution.x, iResolution.y));
            const float WORLD_BOTTOM_Y = 1.0;

            float screenDepth = clamp((centered.y - groundY) /
                                      max(1e-5, (centeredBottomY - groundY)), 0.0, 1.0);
            float worldDepth  = clamp((centered.y - groundY) /
                                      max(1e-5, (WORLD_BOTTOM_Y - groundY)), 0.0, 1.0);

           float centeredHalfX = 0.5 * (iResolution.x / min(iResolution.x, iResolution.y));

           // visible extents across the whole screen in `centered` space
           float xLvis = -centeredHalfX;
           float xRvis =  centeredHalfX;

           // keep the top “reveal” if you like; otherwise set appear = 1.0
           float yStart = groundY + (centeredBottomY - groundY) * REVEAL_FRAC;
           float appear = smoothstep(yStart - REVEAL_SOFT, yStart + REVEAL_SOFT, centered.y);

           // pathInside is now ONLY a vertical (Y) gate, not a left/right wedge
           float pathInside = appear;

            // === Dirt base ====================================================
            float persp = mix(1.0, PERSPECTIVE_PINCH, worldDepth);
            vec2  groundUV = vec2(centered.x / max(persp, 1e-5), centered.y);

            float n = fbm(groundUV * DIRT_FREQ);
            vec3  dirt = mix(baseBrown, lightBrown, n);

            // Dust flecks
            float dustMask = getDustMask(groundUV);
            dirt = mix(dirt, vec3(0.17), clamp(dustMask, 0.0, 1.0));

            vec3 Nflat = vec3(0,0,1);
            vec3 groundCol = shadeStandardLD(dirt, Nflat, 0.90, 0.02, 1.0, 1.0, lightDir);

            // === ROCKS ========================================================
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

                    // rotate rock shape for variety
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

                    // Flatter-top dome normal
                    float uedge = clamp(d, 0.0, 1.0);
                    float hcap  = pow(1.0 - uedge*uedge, 1.05);
                    float slope = ROCK_HEIGHT_GAIN * (2.0 * uedge);
                    vec2  dirOut = normalize(e + 1e-6);
                    vec3  nrm   = normalize(vec3(dirOut * slope, 1.0));

                    // Dirt AO "skirt" seated around the rock
                    float skirt = smoothstep(1.0, 1.0 + ROCK_SKIRT_W, d)
                                - smoothstep(1.0 + ROCK_SKIRT_W, 1.0 + 2.0*ROCK_SKIRT_W, d);
                    groundCol = mix(groundCol, groundCol * (1.0 - ROCK_SKIRT_GAIN), skirt);

                    // Rim AO on the rock
                    float rimAO = mix(0.85, 1.0, 1.0 - smoothstep(0.72, 1.00, uedge));

                    // Base rock color + variation
                    vec3 rcol = mix(ROCK_A, ROCK_B, rCol) * (0.98 + 0.04 * (r3 - 0.5));

                    // Brown dirt dusting
                    vec3  dustTint = mix(baseBrown, lightBrown, 0.5);
                    float dustAmt  = 0.15 + 0.20 * r2;
                    dustAmt       *= mix(0.85, 1.00, worldDepth);
                    rcol           = mix(rcol, dustTint, clamp(dustAmt, 0.0, 1.0));

                    // Shared lighting; height-varying rough/spec
                    float rough    = mix(0.65, 0.45, hcap);
                    float specular = ROCK_SPEC_INT * mix(0.60, 1.00, hcap);
                    vec3  lit      = shadeStandardLD(rcol, nrm, rough, specular, rimAO, 1.0, lightDir);

                    // Blend rock over dirt
                    groundCol = mix(groundCol, lit, clamp(mask, 0.0, 1.0));
                }
            }

            // Grass on top
            groundCol = mixGrassClumps(centered, worldDepth, xLvis, xRvis, pathInside,
                                       groundCol, lightDir);

            g.albedo = groundCol;
            return g;
        }

        // ─────────────────────────────────────────────────────────────────────
        // Shadows helpers (unchanged)
        float groundContactShadowCentered(vec2 p, float footX, float groundY,
                                          float width, float falloffY) {
            float dx = p.x - footX;
            float lateral = exp(-(dx*dx) / (width*width));
            float below    = step(groundY, p.y);
            float vertical = 1.0 - smoothstep(0.0, falloffY, p.y - groundY);
            return below * lateral * vertical;
        }

        float groundCastShadowCentered(vec2 p, float footX, float groundY, vec2 L2_norm,
                                       float radius, float sx)
        {
            vec2 c0 = vec2(footX, groundY);
            vec2 along = normalize(vec2(-L2_norm.x, -L2_norm.y));
            vec2 perp  = vec2(-along.y, along.x);

            float tailPhase  = sin(iTime * 2.0);
            float floatPhase = sin(iTime * 0.7);

            float offY = max(0.0, -L2_norm.y) * (1.05 + 0.10 * floatPhase) * radius;
            float offX = (-L2_norm.x)        * (0.65 + 0.08 * floatPhase) * radius * sx;
            vec2  c    = c0 + vec2(offX, offY);

            vec2 rel = p - c;
            float y  = dot(rel, along);
            float x  = dot(rel, perp);

            float len = (1.55 + 0.15 * floatPhase) * radius;
            float yClamped = clamp(y, 0.0, len);
            float yN = yClamped / max(len, 1e-5);

            float curve = (0.18 * radius * sx) * tailPhase * yN * (1.0 - yN);
            x -= curve;

            float r0 = (0.70 * radius * sx) * (1.0 + 0.10 * tailPhase);
            float r1 = (0.20 * radius * sx) * (1.0 - 0.10 * tailPhase);

            float d0 = length(vec2(x, yClamped))       - r0;
            float d1 = length(vec2(x, yClamped - len)) - r1;
            float k  = 0.25 * radius;
            float h  = clamp(0.5 + 0.5*(d1 - d0)/max(k, 1e-5), 0.0, 1.0);
            float d  = mix(d1, d0, h) - k*h*(1.0 - h);

            float soft     = 0.10 * radius;
            float penumbra = 1.0 - smoothstep(-soft, soft, d);
            float core     = 1.0 - smoothstep(-0.4*soft, 0.4*soft, d + 0.15*r1);
            core *= exp(-yClamped / (0.5 * len));

            float below     = step(groundY, p.y);
            float startFade = smoothstep(-0.25*radius, 0.10*radius, y);

            return clamp((0.55*penumbra + 0.85*core) * below * startFade, 0.0, 1.0);
        }

        float groundOvalShadowCentered(vec2 p, float cx, float cy,
                                       float rx, float ry, float softness)
        {
            float rot = 0.12 * sin(iTime * 2.0);
            float s = sin(rot), c = cos(rot);

            vec2 d = vec2(p.x - cx, p.y - cy);
            vec2 r = vec2( c*d.x + s*d.y, -s*d.x + c*d.y);

            float nx = r.x / max(rx, 1e-5);
            float ny = r.y / max(ry, 1e-5);
            float ang = atan(ny, nx);
            float rad = sqrt(nx*nx + ny*ny);

            const float waveLobes = 6.0;
            const float waveSpeed = 2.0;
            const float waveAmp   = 0.06;

            float rim = 1.0 + waveAmp * sin(waveLobes * ang - iTime * waveSpeed);

            float sft  = max(softness, 1e-5);
            float mask = 1.0 - smoothstep(rim - sft, rim + sft, rad);

            float topFade = smoothstep(0.0, ry * 0.6, p.y - (cy - ry));
            return clamp(mask * topFade, 0.0, 1.0);
        }

        vec3 mixGroundColor(vec3 bgColor, GroundData ground, float ghostMask) {
            // GroundData.albedo now holds a FINAL LIT COLOR
            float a = ground.mask * (1.0 - ghostMask); // only below horizon, never over ghost
            return mix(bgColor, ground.albedo, a);
        }
    """.trimIndent()
}
