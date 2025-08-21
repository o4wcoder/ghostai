package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object GhostBody {
    @Language("AGSL")
    val ghostBody = """
        
// === Horizontal, overlapping tail layers ==================================
// U-shaped edge (lower in the middle, higher near the hips), gently windy.
float tailEdgeY(vec2 uv, float baseY, float amp, float freq, float phase) {
    float u = uv.x;
    float sag = 0.35 * amp * (1.0 - pow(1.0 - clamp(abs(u) / 0.9, 0.0, 1.0), 2.0)); // rises near sides
    float wave = amp * sin(u * freq + phase);
    return baseY + wave - sag;
}

// soft masks just above/below an edge
vec2 tailBands(vec2 uv, float edgeY, float radius) {
    float wHi = 0.030 * radius; // highlight below edge
    float wSh = 0.045 * radius; // shadow above edge
    float d = uv.y - edgeY;

    float hi = smoothstep(0.0, wHi, d) * (1.0 - smoothstep(wHi, 2.0*wHi, d));
    float sh = smoothstep(-wSh, 0.0, d) * (1.0 - smoothstep(0.0, wSh, d));

    // only active in tail region
    float tail = smoothstep(0.45*radius, 1.05*radius, uv.y);
    // fade near silhouette so edges don't “stick” to the border
    float sideFade = 1.0 - smoothstep(0.75, 0.98, abs(uv.x));
    return vec2(sh, hi) * tail * sideFade;
}

// utility: 1 if uv is below the given edge (used for occlusion)
float belowEdge(vec2 uv, float edgeY) { return smoothstep(0.0, 0.01, uv.y - edgeY); }


vec3 shadeGhostBody(vec2 uv, float radius, vec3 lightDir) {
    // === Base shading (sphere head -> cylinder tail) ======================
    vec3 albedo = vec3(1.0);

    vec2 euv = vec2(uv.x, uv.y * 0.88) / max(radius, 1e-5);
    float r2s = clamp(dot(euv, euv), 0.0, 1.0);
    float zS  = sqrt(1.0 - r2s);
    vec3 Ns   = normalize(vec3(euv, zS));            // spherical

    float x2  = clamp(euv.x * euv.x, 0.0, 1.0);
    float zC  = sqrt(1.0 - x2);
    vec3 Nc   = normalize(vec3(euv.x, 0.0, zC));     // cylindrical

    float belly  = smoothstep(-0.10 * radius, 0.90 * radius, uv.y);
    float cylMix = 0.75 * belly;
    vec3  N      = normalize(mix(Ns, Nc, cylMix));

    vec3 L = normalize(lightDir);
    vec3 V = vec3(0.0, 0.0, 1.0);

    float kWrap = 0.45;
    float ndl   = dot(N, L);
    float diffuse = clamp((ndl + kWrap) / (1.0 + kWrap), 0.0, 1.0);
    float spec = pow(max(dot(N, normalize(L + V)), 0.0), 20.0) * 0.06;

    float cap = smoothstep(-radius * 1.2, -radius * 0.2, uv.y) * smoothstep(0.2, 0.9, zS);
    float bottomAO = smoothstep(0.15 * radius, 0.95 * radius, uv.y);
    vec3 aoMul = mix(vec3(1.0), vec3(0.96, 0.97, 1.00), 0.06 * bottomAO);

    float bounce = smoothstep(0.0, radius, uv.y);
    vec3  bounceCol = vec3(1.02, 1.02, 1.00);
    float bounceAmt = 0.10 * bounce;

    vec3 base = albedo * (0.62 + 0.45 * diffuse);
    base *= aoMul;
    base += 0.05 * cap;
    base = mix(base, base * bounceCol, bounceAmt);

    // === Vertical pleats ==================================================
    // We treat each pleat like a wide vertical ridge (Gaussian across X),
    // and use its X‑gradient vs. light direction to create highlight/shadow.
    float t = iTime;

    // Sway so pleats lean with the wind more near the tail.
    float sway = (0.10 * radius) * sin(uv.y * 8.0 + t * 1.4);
    float swayFalloff = smoothstep(0.35 * radius, 1.05 * radius, uv.y);
    float xWarp = uv.x + sway * swayFalloff;

    // Tail zone + side fade (avoid sticking to silhouette)
    float tailZone = smoothstep(0.45 * radius, 1.05 * radius, uv.y);
    float sideFade = 1.0 - smoothstep(0.88, 1.02, abs(uv.x) / max(radius, 1e-5));

    // Pleat parameters (tweak here)
    float w   = 0.22 * radius;      // half‑width of a ridge
    float amp = 0.20;               // intensity scale of the normal-ish effect
    float fill = 0.06;              // soft light fill inside ridges

    // Center positions across the body (in uv.x units)
    float cx0 = -0.55 * radius;
    float cx1 = -0.25 * radius;
    float cx2 =  0.00 * radius;
    float cx3 =  0.25 * radius;
    float cx4 =  0.55 * radius;

    // Per-ridge strengths (slightly vary for organic feel)
    float k0 = 1.00;
    float k1 = 0.90;
    float k2 = 1.10;
    float k3 = 0.95;
    float k4 = 0.85;

    // Accumulators
    float hiAcc = 0.0;
    float shAcc = 0.0;
    float fillAcc = 0.0;

    // --- Ridge 0
    float dx0 = (xWarp - cx0);
    float g0  = exp(-0.5 * (dx0*dx0) / (w*w));              // ridge shape
    float d0  = (-dx0 / (w*w)) * g0;                        // x-gradient
    float lit0 = max(0.0,  d0 * L.x);
    float drk0 = max(0.0, -d0 * L.x);
    hiAcc   += k0 * lit0;
    shAcc   += k0 * drk0;
    fillAcc += k0 * g0;

    // --- Ridge 1
    float dx1 = (xWarp - cx1);
    float g1  = exp(-0.5 * (dx1*dx1) / (w*w));
    float d1  = (-dx1 / (w*w)) * g1;
    float lit1 = max(0.0,  d1 * L.x);
    float drk1 = max(0.0, -d1 * L.x);
    hiAcc   += k1 * lit1;
    shAcc   += k1 * drk1;
    fillAcc += k1 * g1;

    // --- Ridge 2
    float dx2 = (xWarp - cx2);
    float g2  = exp(-0.5 * (dx2*dx2) / (w*w));
    float d2  = (-dx2 / (w*w)) * g2;
    float lit2 = max(0.0,  d2 * L.x);
    float drk2 = max(0.0, -d2 * L.x);
    hiAcc   += k2 * lit2;
    shAcc   += k2 * drk2;
    fillAcc += k2 * g2;

    // --- Ridge 3
    float dx3 = (xWarp - cx3);
    float g3  = exp(-0.5 * (dx3*dx3) / (w*w));
    float d3  = (-dx3 / (w*w)) * g3;
    float lit3 = max(0.0,  d3 * L.x);
    float drk3 = max(0.0, -d3 * L.x);
    hiAcc   += k3 * lit3;
    shAcc   += k3 * drk3;
    fillAcc += k3 * g3;

    // --- Ridge 4
    float dx4 = (xWarp - cx4);
    float g4  = exp(-0.5 * (dx4*dx4) / (w*w));
    float d4  = (-dx4 / (w*w)) * g4;
    float lit4 = max(0.0,  d4 * L.x);
    float drk4 = max(0.0, -d4 * L.x);
    hiAcc   += k4 * lit4;
    shAcc   += k4 * drk4;
    fillAcc += k4 * g4;

    // Normalize & fade with height and sides so it’s strongest near tail
    float heightFade = smoothstep(0.30 * radius, 0.95 * radius, uv.y);
    float pleatMask  = tailZone * sideFade * heightFade;

    // Convert to color adjustments
    vec3 hiCol = vec3(0.20, 0.22, 0.26);
    float hiAmt = amp * hiAcc * pleatMask;
    float shAmt = amp * shAcc * pleatMask;
    float fiAmt = fill * (fillAcc / 3.5) * pleatMask; // gentle inner light

    base *= (1.0 - 0.8 * shAmt);     // darken on the lee side of each ridge
    base += hiAmt * hiCol;           // highlight on the lit side
    base += fiAmt * hiCol * 0.5;     // very soft fill so it’s not stripey

    // Slight extra AO at very bottom so depth increases
    base *= 1.0 - 0.05 * smoothstep(0.85 * radius, 1.05 * radius, uv.y);

    return clamp(base + spec, 0.0, 1.2);
}





            vec3 getGhostBodyColor(float radius, vec2 uv) {
            // Soft white inside
            vec3 ghostInnerColor = vec3(1.0, 1.0, 1.0);     
            // Slightly bluish gray edges
            vec3 ghostEdgeColor  = vec3(0.7, 0.8, 1.0);  
            float ghostDistFactor = smoothstep(0.0, radius, length(uv));
            return mix(ghostInnerColor, ghostEdgeColor, ghostDistFactor);
        }
    """.trimIndent()
}
