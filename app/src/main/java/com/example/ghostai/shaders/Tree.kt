package com.example.ghostai.shaders

import org.intellij.lang.annotations.Language

object Tree {
    @Language("AGSL")
    val tree = """
// -----------------------------------------------------------------------------
// Helpers (deterministic & lightweight)
// -----------------------------------------------------------------------------
float aaFill(float d, float pxAA) { return smoothstep(pxAA, 0.0, d); }

mat2 rot2(float a){ float c = cos(a), s = sin(a); return mat2(c,-s,s,c); }

// Tapered capsule SDF: radius blends from ra at A to rb at B (rb≈0 => pointed tip)
float sdTaperedCapsule(vec2 p, vec2 a, vec2 b, float ra, float rb){
    vec2 ba = b - a, pa = p - a;
    float bab = max(dot(ba, ba), 1e-6);
    float h   = clamp(dot(pa, ba) / bab, 0.0, 1.0);
    float r   = mix(ra, rb, h);
    vec2  c   = pa - ba * h;
    return length(c) - r;
}
// add this helper
float sdCircle(vec2 p, vec2 c, float r) {
    return length(p - c) - r;
}
// Polynomial smooth min (rounds both sides of the joint)
// k ~ radius of smoothing (in your 'centered' units)
float smin(float a, float b, float k){
    float h = clamp(0.5 + 0.5*(b - a) / max(k, 1e-6), 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}
vec2 qbez(vec2 A, vec2 C, vec2 B, float t){
    float u = 1.0 - t;
    return u*u*A + 2.0*u*t*C + t*t*B;
}
vec2 qbez_d1(vec2 A, vec2 C, vec2 B, float t){           // first derivative
    return 2.0*(mix(C, B, t) - mix(A, C, t));
}
vec2 qbez_d2(vec2 A, vec2 C, vec2 B){                    // second derivative
    return 2.0*(B - 2.0*C + A);
}

// Closest t on a quadratic Bézier to point P (few cheap Newton steps)
float closestTOnQuadratic(vec2 P, vec2 A, vec2 C, vec2 B){
    // Start from chord projection
    vec2  AB   = B - A;
    float t    = clamp(dot(P - A, AB) / max(dot(AB, AB), 1e-6), 0.0, 1.0);
    // 3 Newton iterations (fast & stable for this use)
    for (int i = 0; i < 3; i++){
        vec2  Pt = qbez(A,C,B,t) - P;
        vec2  d1 = qbez_d1(A,C,B,t);
        vec2  d2 = qbez_d2(A,C,B);
        float f  = dot(Pt, d1);
        float fp = dot(d1, d1) + dot(Pt, d2);
        t -= f / max(fp, 1e-6);
        t = clamp(t, 0.0, 1.0);
    }
    return t;
}

// Distance to a tapered quadratic tube (radius blends ra→rb along t)
float sdQuadraticTube(vec2 p, vec2 A, vec2 C, vec2 B, float ra, float rb){
    float t = closestTOnQuadratic(p, A, C, B);
    vec2  q = qbez(A, C, B, t);
    float r = mix(ra, rb, t);
    return length(p - q) - r;
}




// -----------------------------------------------------------------------------
// renderBranch
//
// Renders ONE black branch with a sharp tip, composed of two tapered segments.
// Call this multiple times to assemble a tree (no loops inside this function).
//
// Parameters:
//   col        : inout scene color
//   centered   : your world/screen coords (same space you’ve been using)
//   basePos    : (x, y) location of the branch BASE (the blunt end)
//   angle      : direction the branch initially points, in radians.
//                (0..±90° works great; use radians(…)).
//   height     : total branch length
//   width      : base thickness (at basePos). Tip auto-tapers to a point.
//   bendAngle  : the extra rotation to apply AFTER the vertex (radians).
//                Positive bends counter‑clockwise, negative bends clockwise.
//   bendT      : where the bend/vertex sits along the length, 0..1
//                (0.5 = middle, 0.3 = closer to base, 0.8 = near the tip).
//   ink        : branch color (near‑black), e.g., vec3(0.03)
//   pxAA       : pixel AA radius (pass your existing pxAA)
// -----------------------------------------------------------------------------
void renderBranch(
    inout vec3 col,
    vec2  centered,
    vec2  basePos,
    float angle,
    float height,
    float width,
    float bendAngle,
    float bendT,
    vec3  ink,
    float pxAA
){
    float tV   = clamp(bendT, 0.02, 0.98);
    float len1 = max(height * tV,        1e-3);
    float len2 = max(height * (1.0-tV),  1e-3);

    float rBase = max(width * 0.5, 1e-4);
    float rVert = mix(rBase, 0.001, tV);   // radius at the joint (don’t shrink by angle)
    float rTip  = 0.001;

    // endpoints
    vec2 A    = basePos;
    vec2 dir1 = rot2(angle) * vec2(0.0, 1.0);
    vec2 V    = A + dir1 * len1;

    vec2 dir2 = rot2(angle + bendAngle) * vec2(0.0, 1.0);
    vec2 T    = V + dir2 * len2;

    // tapered segments
    float d1 = sdTaperedCapsule(centered, A, V, rBase, rVert);
    float d2 = sdTaperedCapsule(centered, V, T, rVert, rTip);

    // --- OUTER corner: tiny smooth-union to avoid a sharp V (but no bulge)
    float dHard   = min(d1, d2);                  // reference union (no growth)
    float theta   = clamp(abs(bendAngle), 0.0, 3.14159265);
    float kOuter  = rVert * 0.30 * smoothstep(0.0, 1.0, theta / 1.5707963); // up to 90°
    float dOuterS = (kOuter > 0.0) ? smin(d1, d2, kOuter) : dHard;
    float dOuter  = max(dOuterS, dHard);          // clamp to forbid outward swelling

    // --- INNER corner: union a joint disk and blend it in (this rounds the concave side)
    float dj      = sdCircle(centered, V, rVert);                 // full joint radius
    float kInner  = rVert * 0.25;                                 // soft blend with arms
    float dInnerU = smin(dOuter, dj, kInner);                     // smooth union (inside only)

    // final
    float fill = aaFill(dInnerU, pxAA);
    col = mix(col, ink, fill);
}

void renderCurvedBranch(
    inout vec3 col,
    vec2  centered,
    vec2  basePos,
    float angle,
    float height,
    float width,
    float bendAngle,
    float bendT,
    vec3  ink,
    float pxAA
){
    // --- layout: define base A, control C, and tip B -------------------------
    float tV   = clamp(bendT, 0.02, 0.98);
    float len1 = max(height * tV,        1e-3);     // base→control leg length
    float len2 = max(height * (1.0-tV),  1e-3);     // control→tip  leg length

    // Radii: taper from base radius to a sharp tip
    float rBase = max(width * 0.5, 1e-4);
    float rCtrl = mix(rBase, 0.001, tV);
    float rTip  = 0.001;

    // Points
    vec2 dir1 = rot2(angle) * vec2(0.0, 1.0);
    vec2 A    = basePos;
    vec2 C    = A + dir1 * len1;                               // control (bend handle)
    vec2 dir2 = rot2(angle + bendAngle) * vec2(0.0, 1.0);
    vec2 B    = C + dir2 * len2;                               // tip

    // One smooth, curved tube (no joint/bulge), radius blends along t
    float d = sdQuadraticTube(centered, A, C, B, rBase, rTip);

    // Solid silhouette
    float fill = aaFill(d, pxAA);
    col = mix(col, ink, fill);
}


void buildTree(    inout vec3 inputColor, vec2  centered,   float pxAA) {
      // 2) Near‑black ink for silhouettes
    vec3 INK = vec3(0.02);
    
    // main trunk 
       renderBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.4, 0.48),          // basePos: left side, on ground
        3.14159265,                 // angle: up
        1.30,                       // height
        0.17,                       // width
        radians(-4.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
   renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.40, -0.30),          // basePos: left side, on ground
        3.34159265,                 // angle: up
        0.5,                       // height
        0.050,                       // width
        radians(28.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
      renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.32, -0.55),          // basePos: left side, on ground
        4.14159265,                 // angle: up
        0.17,                       // height
        0.020,                       // width
        radians(-25.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
       renderBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.42, -0.65),          // basePos: left side, on ground
        3.84159265,                 // angle: up
        0.17,                       // height
        0.020,                       // width
        radians(-25.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
       renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.40, -0.50),          // basePos: left side, on ground
        2.54159265,                 // angle: up
        0.5,                       // height
        0.025,                       // width
        radians(28.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
     renderBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.46, -0.60),          // basePos: left side, on ground
        3.04159265,                 // angle: up
        0.12,                       // height
        0.013,                       // width
        radians(-10.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
      renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.40, -0.17),          // basePos: left side, on ground
        3.64159265,                 // angle: up
        0.55,                       // height
        0.050,                       // width
        radians(15.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
       renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.367, -0.165),          // basePos: left side, on ground
        3.24159265,                 // angle: up
        0.17,                       // height
        0.025,                       // width
        radians(45.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
        renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.42, -0.30),          // basePos: left side, on ground
        2.64159265,                 // angle: up
        0.55,                       // height
        0.050,                       // width
        radians(15.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
           renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.427, -0.24),          // basePos: left side, on ground
        3.04159265,                 // angle: up
        0.17,                       // height
        0.025,                       // width
        radians(-30.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
      renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.40, 0.1),          // basePos: left side, on ground
        3.64159265,                 // angle: up
        0.55,                       // height
        0.080,                       // width
        radians(15.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
          renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.352, 0.12),          // basePos: left side, on ground
        3.24159265,                 // angle: up
        0.17,                       // height
        0.035,                       // width
        radians(45.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
         renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.25, -0.165),          // basePos: left side, on ground
        3.74159265,                 // angle: up
        0.37,                       // height
        0.025,                       // width
        radians(-35.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
          renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.28, -0.39),          // basePos: left side, on ground
        3.14159265,                 // angle: up
        0.17,                       // height
        0.020,                       // width
        radians(25.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
              renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.13, -0.39),          // basePos: left side, on ground
        3.64159265,                 // angle: up
        0.12,                       // height
        0.010,                       // width
        radians(-25.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
              renderBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.29, -0.12),          // basePos: left side, on ground
        3.24159265,                 // angle: up
        0.17,                       // height
        0.025,                       // width
        radians(45.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.20, -0.25),          // basePos: left side, on ground
        3.04159265,                 // angle: up
        0.20,                       // height
        0.025,                       // width
        radians(45.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
    
         renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.42, 0.0),          // basePos: left side, on ground
        2.64159265,                 // angle: up
        0.55,                       // height
        0.050,                       // width
        radians(15.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
         renderBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.45, 0.0),          // basePos: left side, on ground
        3.04159265,                 // angle: up
        0.12,                       // height
        0.013,                       // width
        radians(-10.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
    
            renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.50, 0.50),          // basePos: left side, on ground
        3.44159265,                 // angle: up
        0.4,                       // height
        0.050,                       // width
        radians(-25.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
    
                renderCurvedBranch(
        inputColor,                 // inout color buffer
        centered,                   // your world coords
        vec2(0.30, 0.50),          // basePos: left side, on ground
        2.84159265,                 // angle: up
        0.4,                       // height
        0.050,                       // width
        radians(25.0),               // bendAngle (leans a touch right)
        0.65,                       // bendT (bend above the middle)
        INK,
        pxAA
    );
}

    """.trimIndent()
}
