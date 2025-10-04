package com.fourthwardai.ghostai.bookofshadersexamples

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.tooling.preview.Preview
import com.fourthwardai.ghostai.ui.theme.GhostAITheme
import com.fourthwardai.ghostai.util.rememberStableTime
import org.intellij.lang.annotations.Language

@Composable
fun ShaderExample(
    modifier: Modifier = Modifier,
    time: Float = rememberStableTime(),
) {
    @Language("AGSL")
    val exampleShader = """
        uniform float2 iResolution;
        uniform float iTime;
        
        const float PI = 3.14159265359;
        
        float plot(vec2 st, float pct){
        return  smoothstep( pct-0.02, pct, st.y) -
                smoothstep( pct, pct+0.02, st.y);
          

        }
        
        half4 main(float2 fragCoord) {
            vec2 st = fragCoord / iResolution;
            st.y = 1.0 - st.y; // Flip Y to match WebGL convention
            float y = 1.0 - pow(st.x, 0.5); //smoothstep(0.2,0.5,st.x) - smoothstep(0.5,0.8,st.x);
            
            vec3 color = vec3(y);
            
            //Plot a line
            float pct = plot(st, y);
            color = (1.0 - pct) * color + pct * vec3(0.0,1.0,0.0);

            return half4(color,1.0);
        }
    """.trimIndent()

    val shader = remember {
        RuntimeShader(exampleShader)
    }

    var canvasSize by remember { mutableStateOf(Size(1f, 1f)) }

    LaunchedEffect(time, canvasSize) {
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("iResolution", canvasSize.width, canvasSize.height)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        canvasSize = size
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("iResolution", size.width, size.height)

        drawRect(
            brush = object : ShaderBrush() {
                override fun createShader(size: Size): Shader = shader
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewShaderExample() {
    GhostAITheme {
        ShaderExample(time = 2.0F)
    }
}
