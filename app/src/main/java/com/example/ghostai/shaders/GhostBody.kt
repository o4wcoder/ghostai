package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object GhostBody {
    @Language("AGSL")
    val ghostBody = """
        

// Slimmer 3D body: spherical head → cylindrical bottom (no rim/edge gradient)
vec3 shadeGhostBody(vec2 uv, float radius, vec3 lightDir) {
    vec3 albedo = vec3(1.0);

    // --- build two normals ---
    vec2 euv = vec2(uv.x, uv.y * 0.88) / max(radius, 1e-5);

    // Spherical (good for the head/crown)
    float r2s = clamp(dot(euv, euv), 0.0, 1.0);
    float zS  = sqrt(1.0 - r2s);
    vec3 Ns   = normalize(vec3(euv, zS));

    // Cylindrical across X only (cloth hanging straight down)
    float x2  = clamp(euv.x * euv.x, 0.0, 1.0);
    float zC  = sqrt(1.0 - x2);
    vec3 Nc   = normalize(vec3(euv.x, 0.0, zC));

    // Blend: sphere at top → cylinder at bottom
    float belly = smoothstep(-0.10 * radius, 0.90 * radius, uv.y); // 0 top .. 1 bottom
    float cylMix = 0.75 * belly;                                   // strength of slimming
    vec3 N = normalize(mix(Ns, Nc, cylMix));

    // --- lighting ---
    vec3 L = normalize(lightDir);
    vec3 V = vec3(0.0, 0.0, 1.0);

    // Wrap diffuse (soft split)
    float kWrap = 0.45;
    float ndl = dot(N, L);
    float diffuse = clamp((ndl + kWrap) / (1.0 + kWrap), 0.0, 1.0);

    // Hemispheric ambient (cool sky above, very light ground below)
    vec3 sky    = vec3(1.05, 1.07, 1.10);
    vec3 ground = vec3(0.99, 0.99, 1.00);
    float hemiT = 0.5 - 0.5 * N.y; // 1 = faces up
    vec3 hemi   = mix(ground, sky, hemiT); // (kept implicit – helps keep it bright)

    // Soft specular
    vec3 H = normalize(L + V);
    float spec = pow(max(dot(N, H), 0.0), 20.0) * 0.06;

    // Tiny crown sheen (keeps head lively)
    float cap = smoothstep(-radius * 1.2, -radius * 0.2, uv.y) * smoothstep(0.2, 0.9, zS);

    // Gentle bottom AO (reduced so the belly doesn't look heavy)
    float bottomAO = smoothstep(0.15 * radius, 0.95 * radius, uv.y);
    vec3 aoMul = mix(vec3(1.0), vec3(0.96, 0.97, 1.00), 0.06 * bottomAO);

    // Subtle ground bounce to *lighten* bottom
    float bounce = smoothstep(0.0, radius, uv.y);
    vec3  bounceCol = vec3(1.02, 1.02, 1.00);
    float bounceAmt = 0.10 * bounce;

    // Compose (bright baseline so he stays white, not gray)
    vec3 base = albedo * (0.62 + 0.45 * diffuse);
    base *= aoMul;
    base += 0.05 * cap;
    base = mix(base, base * bounceCol, bounceAmt);

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
