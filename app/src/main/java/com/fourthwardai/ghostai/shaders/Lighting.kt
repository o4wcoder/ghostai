package com.fourthwardai.ghostai.shaders

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
        
        // CENTERED coords; y grows downward; +z is toward camera.
        vec3 sceneLightFromMoon(vec2 moonPos, float towardCam) {
            vec2 d2 = normalize(moonPos - vec2(0.0));     // center → moon
            return normalize(vec3(d2.x, d2.y, abs(towardCam)));
        }


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
        
        // Same as your shadeStandard, but with explicit lightDir.
        vec3 shadeStandardLD(vec3 albedo, vec3 N,
                             float roughness, float specular,
                             float ao, float shadow,
                             vec3 lightDir)
        {
            vec3 L = normalize(lightDir);
            vec3 H = normalize(L + vec3(0,0,1));
            float NdL = clamp(dot(N, L), 0.0, 1.0);

            // globals you already have:
            // const float SCENE_AMBIENT;
            // const vec3  SCENE_SPEC_TINT;
            float diff = mix(SCENE_AMBIENT, 1.0, NdL) * ao * shadow;

            // cheap x^8 spec (or use pow if you prefer)
            float x = max(dot(N, H), 0.0); x*=x; x*=x; float s = x*x; // ^8
            float tight = 1.0 - clamp(roughness, 0.0, 1.0);
            s *= specular * (0.5 + 0.5*tight) * shadow;

            return albedo * diff + SCENE_SPEC_TINT * s;
        }

        
vec3 shadeGhostBodyStandard(vec2 shapeUV, float radius, vec3 lightDir) {
    vec2 e = vec2(shapeUV.x, shapeUV.y * 0.90);
    vec2 p = e / max(radius, 1e-5);
    float z = sqrt(max(0.0, 1.0 - dot(p,p)));
    vec3  N = normalize(vec3(p.x, p.y, z * 1.35));

    vec3  albedo = vec3(0.94, 0.96, 1.00);
    float rough  = 0.85;
    float spec   = 0.06;
    float ao     = mix(0.78, 1.0, z);

    return shadeStandardLD(albedo, N, rough, spec, ao, 1.0, lightDir);
}



    """.trimIndent()
}
