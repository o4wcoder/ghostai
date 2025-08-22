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

        const vec3 GROUND_DARK  = vec3(0.02, 0.05, 0.05);
        const vec3 GROUND_LIGHT = vec3(0.06, 0.12, 0.11);

GroundData drawGround(vec2 centered, float t) {
    GroundData g;

    // Horizon / mask (unchanged)
    float groundY = 0.40;
    float feather = 0.04;
    g.mask = smoothstep(groundY - feather, groundY + feather, centered.y);

    // Ground depth: 0 at horizon, 1 at bottom
    float groundDepth = clamp((centered.y - groundY) / max(1e-5, (1.0 - groundY)), 0.0, 1.0);

    // === Path wedge (no guide lines) ========================================
    // Use your picked perspective lane scaling
    float laneScale = mix(0.55, 1.40, groundDepth);   // <- your values
    float laneOuter = 0.48;                            // path edge at bottom (tweak)
    float edgeSoft  = 0.010;                           // soften wedge edges

    // Outer rail x positions at this y
    float xL = -laneOuter * laneScale;
    float xR =  laneOuter * laneScale;

    // Inside-wedge mask (1 = inside path, 0 = outside)
    float leftGate  = smoothstep(xL - edgeSoft, xL + edgeSoft, centered.x);
    float rightGate = 1.0 - smoothstep(xR - edgeSoft, xR + edgeSoft, centered.x);
    float pathInside = leftGate * rightGate;

    // Optional: have the path “reveal” a little below the horizon
    float yStart = groundY + (1.0 - groundY) * 0.06;
    float appear = smoothstep(yStart - 0.015, yStart + 0.015, centered.y);
    pathInside *= appear;

    // === Dirt texture (perspective divide so features narrow upward) =========
    float persp = mix(1.0, 3.2, groundDepth);
    vec2 groundUV = vec2(centered.x / persp, centered.y);

    vec3 baseBrown  = vec3(0.25, 0.15, 0.08);
    vec3 lightBrown = vec3(0.42, 0.30, 0.16);
    float n = fbm(groundUV * 5.5);
    vec3 dirt = mix(baseBrown, lightBrown, n);

    float speck = fract(sin(dot(groundUV * 28.0, vec2(12.9898,78.233))) * 43758.5453);
    dirt = mix(dirt, vec3(0.17), step(0.88, speck));

    // === Black outside the wedge ============================================
    vec3 outsideColor = vec3(0.0);
    g.albedo = mix(outsideColor, dirt, pathInside);   // no guide lines at all

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
