package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object GhostBody {
    @Language("AGSL")
    val ghostBody = """
// === Horizontal, overlapping tail layers ==================================
float tailEdgeY(vec2 uv, float baseY, float amp, float freq, float phase) {
    float u = uv.x;
    float sag = 0.35 * amp * (1.0 - pow(1.0 - clamp(abs(u) / 0.9, 0.0, 1.0), 2.0));
    float wave = amp * sin(u * freq + phase);
    return baseY + wave - sag;
}
vec2 tailBands(vec2 uv, float edgeY, float radius) {
    float wHi = 0.030 * radius;
    float wSh = 0.045 * radius;
    float d = uv.y - edgeY;
    float hi = smoothstep(0.0, wHi, d) * (1.0 - smoothstep(wHi, 2.0*wHi, d));
    float sh = smoothstep(-wSh, 0.0, d) * (1.0 - smoothstep(0.0, wSh, d));
    float tail = smoothstep(0.45*radius, 1.05*radius, uv.y);
    float sideFade = 1.0 - smoothstep(0.75, 0.98, abs(uv.x));
    return vec2(sh, hi) * tail * sideFade;
}
float belowEdge(vec2 uv, float edgeY) { return smoothstep(0.0, 0.01, uv.y - edgeY); }

// --- small helpers -----------------------------------------------------------
// exp(-0.5 * a) ≈ exp2(-0.7213475 * a)
float gaussExp2(float a){ return exp2(-0.7213475 * a); }

// x^20 without pow()
float pow20_fast(float x){
    float x2 = x * x;       // ^2
    float x4 = x2 * x2;     // ^4
    float x8 = x4 * x4;     // ^8
    float x16= x8 * x8;     // ^16
    return x16 * x4;        // ^20
}

// one ridge contribution: returns (hi, sh, fill)
vec3 ridgeContrib(float xWarp, float invW2, float Lx, float cx, float k){
    float dx  = xWarp - cx;
    float g   = gaussExp2(dx*dx * invW2);   // ridge shape
    float dX  = (-dx * invW2) * g;          // x-gradient
    float lit = max(0.0,  dX * Lx);
    float drk = max(0.0, -dX * Lx);
    return vec3(k * lit, k * drk, k * g);
}

vec3 shadeGhostBody(vec2 uv, float radius, vec3 lightDir) {
    // ==== Base normal blend: head (sphere) → tail (cylinder) =================
    vec3 albedo = vec3(1.0);

    float invR = 1.0 / max(radius, 1e-5);
    vec2  euv   = vec2(uv.x, uv.y * 0.88) * invR;

    float r2s = clamp(dot(euv, euv), 0.0, 1.0);
    float zS  = sqrt(1.0 - r2s);
    vec3  Ns  = normalize(vec3(euv, zS));           // spherical

    float x2  = clamp(euv.x * euv.x, 0.0, 1.0);
    float zC  = sqrt(1.0 - x2);
    vec3  Nc  = normalize(vec3(euv.x, 0.0, zC));    // cylindrical

    float belly  = smoothstep(-0.10 * radius, 0.90 * radius, uv.y);
    float cylMix = 0.75 * belly;
    vec3  N      = normalize(mix(Ns, Nc, cylMix));

    vec3 L = normalize(lightDir);
    vec3 V = vec3(0.0, 0.0, 1.0);

    // wrap diffuse
    float kWrap  = 0.45;
    float ndl    = dot(N, L);
    float diffuse= clamp((ndl + kWrap) / (1.0 + kWrap), 0.0, 1.0);

    // specular (fast power)
    float nh = max(dot(N, normalize(L + V)), 0.0);
    float spec = pow20_fast(nh) * 0.06;

    // caps / AO / bounce
    float cap      = smoothstep(-radius * 1.2, -radius * 0.2, uv.y) * smoothstep(0.2, 0.9, zS);
    float bottomAO = smoothstep(0.15 * radius, 0.95 * radius, uv.y);
    vec3  aoMul    = mix(vec3(1.0), vec3(0.96, 0.97, 1.00), 0.06 * bottomAO);

    float bounce   = smoothstep(0.0, radius, uv.y);
    vec3  bounceCol= vec3(1.02, 1.02, 1.00);
    float bounceAmt= 0.10 * bounce;

    vec3 base = albedo * (0.62 + 0.45 * diffuse);
    base *= aoMul;
    base += 0.05 * cap;
    base  = mix(base, base * bounceCol, bounceAmt);

    // ==== Vertical pleats ====================================================
    // Early-out: pleats only where they matter
    if (uv.y > 0.30 * radius) {
        float t = iTime;
        float sway = (0.10 * radius) * sin(uv.y * 8.0 + t * 1.4);
        float swayFalloff = smoothstep(0.35 * radius, 1.05 * radius, uv.y);
        float xWarp = uv.x + sway * swayFalloff;

        float tailZone = smoothstep(0.45 * radius, 1.05 * radius, uv.y);
        float sideFade = 1.0 - smoothstep(0.88, 1.02, abs(uv.x) * invR);

        float w     = 0.22 * radius;
        float invW2 = 1.0 / (w*w);
        float Lx    = L.x;

        // Centers & strengths
        float cx0 = -0.55 * radius, k0 = 1.00;
        float cx1 = -0.25 * radius, k1 = 0.90;
        float cx2 =  0.00 * radius, k2 = 1.10;
        float cx3 =  0.25 * radius, k3 = 0.95;
        float cx4 =  0.55 * radius, k4 = 0.85;

        vec3 acc = vec3(0.0);
        acc += ridgeContrib(xWarp, invW2, Lx, cx0, k0);
        acc += ridgeContrib(xWarp, invW2, Lx, cx1, k1);
        acc += ridgeContrib(xWarp, invW2, Lx, cx2, k2);
        acc += ridgeContrib(xWarp, invW2, Lx, cx3, k3);
        acc += ridgeContrib(xWarp, invW2, Lx, cx4, k4);

        float hiAcc   = acc.x;
        float shAcc   = acc.y;
        float fillAcc = acc.z;

        float heightFade = smoothstep(0.30 * radius, 0.95 * radius, uv.y);
        float pleatMask  = tailZone * sideFade * heightFade;

        vec3  hiCol = vec3(0.20, 0.22, 0.26);
        float amp   = 0.20;
        float fill  = 0.06;

        float hiAmt = amp * hiAcc * pleatMask;
        float shAmt = amp * shAcc * pleatMask;
        float fiAmt = fill * (fillAcc * (1.0 / 3.5)) * pleatMask;

        base *= (1.0 - 0.8 * shAmt);
        base += hiAmt * hiCol;
        base += fiAmt * hiCol * 0.5;
    }

    // extra AO near bottom
    base *= 1.0 - 0.05 * smoothstep(0.85 * radius, 1.05 * radius, uv.y);

    return clamp(base + spec, 0.0, 1.2);
}

// Convenience color (unchanged)
vec3 getGhostBodyColor(float radius, vec2 uv) {
    vec3 ghostInnerColor = vec3(1.0);
    vec3 ghostEdgeColor  = vec3(0.7, 0.8, 1.0);
    float ghostDistFactor = smoothstep(0.0, radius, length(uv));
    return mix(ghostInnerColor, ghostEdgeColor, ghostDistFactor);
}
    """.trimIndent()
}

