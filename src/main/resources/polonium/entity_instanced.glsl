// Polonium: entity models drawn instanced. Vertices arrive in their model
// part's own space and are placed here, on the GPU: each part's matrix, and
// each entity's color, overlay and light, come from PoloniumInstances. One
// draw covers every entity sharing a model and texture (gl_InstanceID picks
// the entity). The game's own entity shader (core/entity) does the rest.

// x: this draw's first texel in PoloniumInstances; y: texels per entity.
layout(std140) uniform PoloniumDraw {
    ivec4 PoloniumBase;
};

// Per entity: color, then (overlay u, v, light u, v), then where its texture
// is (u, v offset and scale: a cell of Polonium's skin atlas, or 0, 0, 1, 1),
// then where its parts' poses are (x: -1 right after this header; else the
// texel they start at, another entity's: armor posed exactly like the body it's
// on uses the body's; y: 1 if they're in the skeleton format), then the poses.
//
// Each part's matrix: three texels per model part (3 rows of 4).
//
// Skeleton format (player models): the root pose (3 rows), which parts are
// drawn (a bit per part), then per moving part (head, body, arms, legs)
// x, y, z, xRot / yRot, zRot, xScale, yScale / zScale; every other part sits
// on its moving part (or the root) unposed. A part's matrix is worked out as
// ModelPart.translateAndRotate does it. Normals use the pose's inverse
// transpose, which is what Minecraft's normal matrix is.
uniform samplerBuffer PoloniumInstances;

in vec3 Position;
in vec2 UV0;
in ivec2 UV1; // x: the vertex's model part; y: its moving part (skeleton format; 6: the root)
in vec3 Normal;

// rotationZYX(z, y, x): Rz * Ry * Rx.
mat3 polonium_rotation(float x, float y, float z) {
    float sx = sin(x);
    float cx = cos(x);
    float sy = sin(y);
    float cy = cos(y);
    float sz = sin(z);
    float cz = cos(z);
    mat3 rx = mat3(1.0, 0.0, 0.0, 0.0, cx, sx, 0.0, -sx, cx);
    mat3 ry = mat3(cy, 0.0, -sy, 0.0, 1.0, 0.0, sy, 0.0, cy);
    mat3 rz = mat3(cz, sz, 0.0, -sz, cz, 0.0, 0.0, 0.0, 1.0);
    return rz * ry * rx;
}

void polonium_main() {
    int entity = PoloniumBase.x + gl_InstanceID * PoloniumBase.y;
    vec4 color = texelFetch(PoloniumInstances, entity);
    vec4 coords = texelFetch(PoloniumInstances, entity + 1);
    vec4 placement = texelFetch(PoloniumInstances, entity + 2);
    vec4 partsWhere = texelFetch(PoloniumInstances, entity + 3);
    int block = partsWhere.x < 0.0 ? entity + 4 : int(partsWhere.x);
    vec3 position;
    mat3 linear;
    if (partsWhere.y > 0.5) {
        vec4 root0 = texelFetch(PoloniumInstances, block);
        vec4 root1 = texelFetch(PoloniumInstances, block + 1);
        vec4 root2 = texelFetch(PoloniumInstances, block + 2);
        int drawn = int(texelFetch(PoloniumInstances, block + 3).x);
        vec3 local = Position;
        mat3 part = mat3(1.0);
        if (UV1.y < 6) {
            int at = block + 4 + UV1.y * 3;
            vec4 a = texelFetch(PoloniumInstances, at);
            vec4 b = texelFetch(PoloniumInstances, at + 1);
            vec4 c = texelFetch(PoloniumInstances, at + 2);
            mat3 rotation = (a.w != 0.0 || b.x != 0.0 || b.y != 0.0) ? polonium_rotation(a.w, b.x, b.y) : mat3(1.0);
            part = rotation * mat3(b.z, 0.0, 0.0, 0.0, b.w, 0.0, 0.0, 0.0, c.x);
            local = a.xyz / 16.0 + part * Position;
        }
        vec4 placed = vec4(local, 1.0);
        position = vec3(dot(root0, placed), dot(root1, placed), dot(root2, placed));
        linear = transpose(mat3(root0.xyz, root1.xyz, root2.xyz)) * part;
        if (((drawn >> UV1.x) & 1) == 0) {
            // Not drawn: its faces collapse to nothing (as a zero matrix does).
            position = vec3(0.0);
            linear = mat3(1.0);
        }
    } else {
        int part = block + UV1.x * 3;
        vec4 row0 = texelFetch(PoloniumInstances, part);
        vec4 row1 = texelFetch(PoloniumInstances, part + 1);
        vec4 row2 = texelFetch(PoloniumInstances, part + 2);
        vec4 local = vec4(Position, 1.0);
        linear = transpose(mat3(row0.xyz, row1.xyz, row2.xyz));
        position = vec3(dot(row0, local), dot(row1, local), dot(row2, local));
    }

    minecraft_Position = position;
    minecraft_Normal = normalize(transpose(inverse(linear)) * Normal);
    minecraft_Color = color;
    minecraft_UV0 = placement.xy + UV0 * placement.zw;
    minecraft_UV1 = ivec2(coords.xy);
    minecraft_UV2 = ivec2(coords.zw);
    minecraft_main();
}
