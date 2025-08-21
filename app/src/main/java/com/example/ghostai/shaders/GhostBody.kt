package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object GhostBody {
    @Language("AGSL")
    val ghostBody = """
        
//vec3 shadeGhostBody(float radius, vec2 uv) {
//    vec3 albedo = vec3(0.98, 0.99, 1.00);
//
//    // Fake "height" shading – darker at bottom, lighter at top
//    float shade = smoothstep(-radius, radius, uv.y);
//
//    // Add a subtle highlight bias
//    float highlight = smoothstep(-0.3, 0.3, uv.x) * 0.2;
//
//    return albedo * (0.7 + 0.3 * shade + highlight);
//}


// Solid, 3D-looking ghost body (no edge gradient, no glow)
//vec3 shadeGhostBody(vec2 uv, float radius, vec3 lightDir) {
//    vec3 albedo = vec3(0.98, 0.99, 1.00);
//
//    // --- subtle vertical form (darker bottom -> lighter top) ---
//    float v        = smoothstep(-radius*0.9, radius*0.5, uv.y);
//    float vFactor  = mix(0.85, 1.05, v);  // only ~±10%
//
//    // --- softened directional lighting on an ellipsoid ---
//    vec2 ellUV = vec2(uv.x, uv.y * 0.85) / max(radius, 1e-5);
//    float r2   = dot(ellUV, ellUV);
//    float z    = sqrt(max(0.0, 1.0 - min(r2, 1.0)));
//    vec3 N     = normalize(vec3(ellUV, z));
//    vec3 L     = normalize(lightDir);
//    vec3 V     = vec3(0.0, 0.0, 1.0);
//
//    float diffuse = max(dot(N, L), 0.0);
//    diffuse = mix(0.5, diffuse, 0.35);        // compress range so “dark side” never goes gray
//    float spec    = pow(max(dot(reflect(-L, N), V), 0.0), 64.0) * 0.26;
//
//    float shade = 0.45 + 0.35*vFactor + 0.45*diffuse;  // mostly bright, gentle depth
//    return albedo * shade + spec;
//}

// Solid white body with gentle 3D + top bias + cool moonlit shadows
vec3 shadeGhostBody(vec2 uv, float radius, vec3 lightDir) {
    vec3 albedo = vec3(1.0);

    // --- ellipsoidal normal ---
    vec2 euv = vec2(uv.x, uv.y * 0.85) / max(radius, 1e-5);
    float r2 = dot(euv, euv);
    float z  = sqrt(max(0.0, 1.0 - min(r2, 1.0)));
    vec3 N   = normalize(vec3(euv, z));

    vec3 L = normalize(lightDir);
    vec3 V = vec3(0.0, 0.0, 1.0);

    // --- soft lighting (keeps body bright) ---
    float diffuse = max(dot(N, L), 0.0);
    diffuse = mix(0.7, diffuse, 0.3);              // compress range so shadows never go dull
    float spec    = pow(max(dot(reflect(-L, N), V), 0.0), 24.0) * 0.08; // soft, broad highlight

    // --- vertical bias (independent of light) ---
    float vTop      = smoothstep(-radius, 0.3*radius, uv.y);
    float topBoost  = mix(1.0, 1.12, 1.0 - vTop);  // brighter head
    float bottomSha = mix(0.95, 1.0, smoothstep(-0.2*radius, 0.9*radius, uv.y));

    // --- base brightness (white, not gray) ---
    float base = 0.82 + 0.35 * diffuse;

    // --- cool moonlit tint only in darker/bottom areas ---
    vec3 coolBlue = vec3(0.90, 0.95, 1.06);        // subtle blue bias
    float dark    = 1.0 - diffuse;                 // more tint in shadow
    float bottom  = smoothstep(-0.1*radius, radius, uv.y); // more tint toward bottom
    float coolAmt = 0.14 * dark * bottom;          // ~0..0.14
    vec3 tintMul  = mix(vec3(1.0), coolBlue, coolAmt);

    vec3 body = albedo * (base * topBoost * bottomSha);
    body *= tintMul;                                // apply cool tint to shaded areas

    return body + spec;
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
