package com.example.ghostai.oldui.shaders

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

float sdCircle(vec2 p, vec2 c, float r) {
    return length(p - c) - r;
}

// Polynomial smooth min (rounds both sides of the joint)
// k ~ radius of smoothing (in your 'centered' units)
float smin(float a, float b, float k){
    float h = clamp(0.5 + 0.5*(b - a) / max(k, 1e-6), 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

// Quadratic Bézier bits
vec2 qbez(vec2 A, vec2 C, vec2 B, float t){
    float u = 1.0 - t;
    return u*u*A + 2.0*u*t*C + t*t*B;
}
vec2 qbez_d1(vec2 A, vec2 C, vec2 B, float t){
    return 2.0*(mix(C, B, t) - mix(A, C, t));
}
vec2 qbez_d2(vec2 A, vec2 C, vec2 B){
    return 2.0*(B - 2.0*C + A);
}

// === FAST closest t on a quadratic Bézier to point P (1 Newton iter) =========
// Keeps your chord projection seed; does a single stable Newton step.
// This is ~3× cheaper than the original 3 iterations and looks the same at AA.
float closestTOnQuadraticFast(vec2 P, vec2 A, vec2 C, vec2 B){
    vec2 AB = B - A;
    float t = clamp(dot(P - A, AB) / max(dot(AB, AB), 1e-6), 0.0, 1.0);
    // 1 Newton iteration (good enough for branch tubes)
    vec2  Pt = qbez(A,C,B,t) - P;
    vec2  d1 = qbez_d1(A,C,B,t);
    vec2  d2 = qbez_d2(A,C,B);
    float f  = dot(Pt, d1);
    float fp = dot(d1, d1) + dot(Pt, d2);
    t -= f / max(fp, 1e-6);
    return clamp(t, 0.0, 1.0);
}

// Distance to a tapered quadratic tube (radius blends ra→rb along t)
float sdQuadraticTubeFast(vec2 p, vec2 A, vec2 C, vec2 B, float ra, float rb){
    float t = closestTOnQuadraticFast(p, A, C, B);
    vec2  q = qbez(A, C, B, t);
    float r = mix(ra, rb, t);
    return length(p - q) - r;
}

// Coarse bbox test for 3 points (triangle) with padding rPad
bool outsideBBoxTri(vec2 P, vec2 A, vec2 C, vec2 B, float rPad){
    float minx = min(min(A.x, C.x), B.x) - rPad;
    float maxx = max(max(A.x, C.x), B.x) + rPad;
    float miny = min(min(A.y, C.y), B.y) - rPad;
    float maxy = max(max(A.y, C.y), B.y) + rPad;
    return (P.x < minx || P.x > maxx || P.y < miny || P.y > maxy);
}

// Coarse bbox test for 2 points (segment) with padding rPad
bool outsideBBoxSeg(vec2 P, vec2 A, vec2 B, float rPad){
    float minx = min(A.x, B.x) - rPad;
    float maxx = max(A.x, B.x) + rPad;
    float miny = min(A.y, B.y) - rPad;
    float maxy = max(A.y, B.y) + rPad;
    return (P.x < minx || P.x > maxx || P.y < miny || P.y > maxy);
}

// -----------------------------------------------------------------------------
// renderBranch (two tapered segments with rounded inner/outer corner)
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
    float rVert = mix(rBase, 0.001, tV);
    float rTip  = 0.001;
    float rPad  = max(max(rBase, rVert), rTip) + pxAA * 2.0;

    // endpoints
    vec2 A    = basePos;
    vec2 dir1 = rot2(angle) * vec2(0.0, 1.0);
    vec2 V    = A + dir1 * len1;
    vec2 dir2 = rot2(angle + bendAngle) * vec2(0.0, 1.0);
    vec2 T    = V + dir2 * len2;

    // quick bbox reject (saves lots of SDF when far away)
    if (outsideBBoxTri(centered, A, V, T, rPad)) return;

    // tapered segments
    float d1 = sdTaperedCapsule(centered, A, V, rBase, rVert);
    float d2 = sdTaperedCapsule(centered, V, T, rVert, rTip);

    // outer corner smoothing (clamped so no bulge)
    float dHard   = min(d1, d2);
    float theta   = clamp(abs(bendAngle), 0.0, 3.14159265);
    float kOuter  = rVert * 0.30 * smoothstep(0.0, 1.0, theta / 1.5707963);
    float dOuterS = (kOuter > 0.0) ? smin(d1, d2, kOuter) : dHard;
    float dOuter  = max(dOuterS, dHard);

    // inner corner rounding
    float dj      = sdCircle(centered, V, rVert);
    float kInner  = rVert * 0.25;
    float dInnerU = smin(dOuter, dj, kInner);

    float fill = aaFill(dInnerU, pxAA);
    col = mix(col, ink, fill);
}

// -----------------------------------------------------------------------------
// renderCurvedBranch (fast Bézier tube; 1 Newton step + bbox reject)
// -----------------------------------------------------------------------------
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
    float tV   = clamp(bendT, 0.02, 0.98);
    float len1 = max(height * tV,        1e-3);  // base→control
    float len2 = max(height * (1.0-tV),  1e-3);  // control→tip

    float rBase = max(width * 0.5, 1e-4);
    float rCtrl = mix(rBase, 0.001, tV);
    float rTip  = 0.001;
    float rPad  = max(max(rBase, rCtrl), rTip) + pxAA * 2.0;

    // Points
    vec2 A    = basePos;
    vec2 dir1 = rot2(angle) * vec2(0.0, 1.0);
    vec2 C    = A + dir1 * len1;
    vec2 dir2 = rot2(angle + bendAngle) * vec2(0.0, 1.0);
    vec2 B    = C + dir2 * len2;

    // quick bbox reject for triangle A-C-B
    if (outsideBBoxTri(centered, A, C, B, rPad)) return;

    float d = sdQuadraticTubeFast(centered, A, C, B, rBase, rTip);

    float fill = aaFill(d, pxAA);
    col = mix(col, ink, fill);
}

// -----------------------------------------------------------------------------
// buildTree — your original calls, unchanged
// -----------------------------------------------------------------------------
void buildTree(inout vec3 inputColor, vec2 centered, float pxAA) {
    vec3 INK = vec3(0.02);

    renderBranch(      inputColor, centered, vec2(0.4, 0.48), 3.14159265, 1.30, 0.17, radians(-4.0), 0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.40,-0.30), 3.34159265, 0.50, 0.050, radians(28.0), 0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.32,-0.55), 4.14159265, 0.17, 0.020, radians(-25.0),0.65, INK, pxAA);
    renderBranch(      inputColor, centered, vec2(0.42,-0.65), 3.84159265, 0.17, 0.020, radians(-25.0),0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.40,-0.50), 2.54159265, 0.50, 0.025, radians(28.0), 0.65, INK, pxAA);
    renderBranch(      inputColor, centered, vec2(0.46,-0.60), 3.04159265, 0.12, 0.013, radians(-10.0),0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.40,-0.17), 3.64159265, 0.55, 0.050, radians(15.0), 0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.367,-0.165),3.24159265, 0.17, 0.025, radians(45.0),  0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.42,-0.30), 2.64159265, 0.55, 0.050, radians(15.0),  0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.427,-0.24), 3.04159265, 0.17, 0.025, radians(-30.0), 0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.40, 0.10),  3.64159265, 0.55, 0.080, radians(15.0),  0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.352,0.12),  3.24159265, 0.17, 0.035, radians(45.0),  0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.25,-0.165), 3.74159265, 0.37, 0.025, radians(-35.0), 0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.28,-0.39),  3.14159265, 0.17, 0.020, radians(25.0),  0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.13,-0.39),  3.64159265, 0.12, 0.010, radians(-25.0), 0.65, INK, pxAA);
    renderBranch(      inputColor, centered, vec2(0.29,-0.12),  3.24159265, 0.17, 0.025, radians(45.0),  0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.20,-0.25),  3.04159265, 0.20, 0.025, radians(45.0),  0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.42, 0.00),  2.64159265, 0.55, 0.050, radians(15.0),  0.65, INK, pxAA);
    renderBranch(      inputColor, centered, vec2(0.45, 0.00),  3.04159265, 0.12, 0.013, radians(-10.0), 0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.50, 0.50),  3.44159265, 0.40, 0.050, radians(-25.0), 0.65, INK, pxAA);
    renderCurvedBranch(inputColor, centered, vec2(0.30, 0.50),  2.84159265, 0.40, 0.050, radians(25.0),  0.65, INK, pxAA);
}
    """.trimIndent()
}
