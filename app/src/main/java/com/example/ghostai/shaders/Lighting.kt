package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Lighting {
    @Language("AGSL")
    val lighting = """
        // ─── Global lighting knobs (one place to tweak) ─────────────────────────────
        const vec3  SCENE_L_DIR    = normalize(vec3(+0.60, -0.85, 0.75));
        const vec3  SCENE_V_DIR    = vec3(0.0, 0.0, 1.0);
        const float SCENE_AMBIENT  = 0.36;
        const vec3  SCENE_SPEC_TINT= vec3(0.95, 0.98, 1.00);

        // fast x^8 spec (cheap) — swap to pow(dot(N,H), power) later if you like
        float spec8(float x){ x = max(x, 0.0); x*=x; x*=x; return x*x; }

        // Uniform lambert + Blinn-ish spec, with AO and shadow factor
        vec3 shadeStandard(vec3 albedo, vec3 N,
                           float roughness,   // 0 smooth → tighter spec
                           float specular,    // spec intensity
                           float ao,          // 0..1
                           float shadow)      // 0..1 (1 = fully lit)
        {
            vec3 L = SCENE_L_DIR;
            vec3 H = normalize(L + SCENE_V_DIR);

            float NdL = clamp(dot(N, L), 0.0, 1.0);
            float diff = mix(SCENE_AMBIENT, 1.0, NdL) * ao * shadow;

            // roughness drives a simple tightness scale (cheap)
            float tight = 1.0 - clamp(roughness, 0.0, 1.0);
            float s = spec8(dot(N, H)) * specular * (0.5 + 0.5*tight) * shadow;

            return albedo * diff + SCENE_SPEC_TINT * s;
        }
        
        // Use the same elliptical silhouette you already use for the mask:
        // ellipticalUV = vec2(shapeUV.x, shapeUV.y * 0.90);
        // radius = your ghost radius

        vec3 shadeGhostBodyStandard(vec2 shapeUV, float radius) {
            // Rebuild the same ellipse used for the mask so normals line up
            vec2 e = vec2(shapeUV.x, shapeUV.y * 0.90);
            float r = max(radius, 1e-5);
            vec2  p = e / r;                          // -1..1 disk inside the ghost
            float r2 = dot(p, p);
            float z  = sqrt(clamp(1.0 - r2, 0.0, 1.0)); // hemisphere toward camera

            // Slight forward bias so the rim doesn’t go flat
            vec3 N = normalize(vec3(p.x, p.y, z * 1.35));

            // Ghost material
            vec3  albedo = vec3(0.94, 0.96, 1.00);    // soft white with tiny blue hint
            float rough  = 0.85;                      // very matte
            float spec   = 0.06;                      // tiny soft spec
            float ao     = mix(0.78, 1.0, z);         // darker toward rim = subtle rim AO
            float shadow = 1.0;                       // you can pass a cast-shadow factor later

            return shadeStandard(albedo, N, rough, spec, ao, shadow);
        }


    """.trimIndent()
}