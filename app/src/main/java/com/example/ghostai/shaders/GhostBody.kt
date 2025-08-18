package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object GhostBody {
    @Language("AGSL")
    val ghostBody = """
        
        vec3 getGhostBodyColor(float radius, vec2 uv) {
            vec3 ghostInnerColor = vec3(0.2, 1.0, 0.2);
            vec3 ghostEdgeColor  = vec3(0.0, 0.4, 0.0);
            float ghostDistFactor = smoothstep(0.0, radius, length(uv));
            return mix(ghostInnerColor, ghostEdgeColor, ghostDistFactor);
        }
    """.trimIndent()
}
