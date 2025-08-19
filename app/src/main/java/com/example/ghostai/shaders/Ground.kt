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

        GroundData drawGround(vec2 centered,  float t) {
            GroundData g;

            // Horizon line in centered space (-1 bottom → +1 top)
            float groundY = 0.40;
            float feather = 0.04;

            g.mask = smoothstep(groundY - feather, groundY + feather, centered.y);

            // brown dirt (unmasked!)
            vec3 baseBrown  = vec3(0.25, 0.15, 0.08);
            vec3 lightBrown = vec3(0.42, 0.30, 0.16);
            float n = fbm(centered * 5.5);
            vec3 dirt = mix(baseBrown, lightBrown, n);

            // tiny speckles
            float speck = fract(sin(dot(centered * 28.0, vec2(12.9898,78.233))) * 43758.5453);
            dirt = mix(dirt, vec3(0.17), step(0.88, speck));

            g.albedo = dirt;         // <-- not multiplied by g.mask

            return g;
        }

        // p, foot are in CENTERED coords (-1..+1). groundY is your horizon in centered space.
        // Returns 0..1 (1 = darkest) right under the ghost.
        float groundContactShadowCentered(vec2 p, float footX, float groundY,
                                  float width /*~0.22*/, float falloffY /*~0.08*/) {
            // Gaussian along X around the ghost’s foot
            float dx = p.x - footX;
            float lateral = exp(-(dx*dx) / (width*width));  // 1 at footX → 0 outward

            // Limit to just BELOW the horizon, fade out as we go down
            float below   = step(p.y, groundY);                          // 1 if p.y <= groundY
            float vertical = 1.0 - smoothstep(0.0, falloffY, groundY - p.y); // 1 at horizon → 0 deeper

            return lateral * vertical * below;
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
