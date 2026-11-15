
// Polarium: chests drawn instanced. Vertices arrive in the chest model's own space (lid shut);
// each chest's place, facing, light, lid and texture come from PolariumChests.
layout(std140) uniform PolariumChestDraw {
    ivec4 PolariumBase;   // x: this draw's first texel; y: the texture table's first texel
    vec4 PolariumOrigin;  // xyz: the anchor from the camera's block (whole blocks); w: draw distance, squared
    vec4 PolariumCamera;  // xyz: the camera inside its block
    vec4 PolariumHinge;   // xyz: the lid's hinge
};

uniform samplerBuffer PolariumChests;

in vec3 Position;
in vec2 UV0;
in ivec2 UV1; // x: 1 on the lid and the lock
in vec3 Normal;

// cos, sin of the model's turn about the vertical, per facing (quarter turns).
const vec2 POLARIUM_TURN[4] = vec2[4](vec2(1.0, 0.0), vec2(0.0, -1.0), vec2(-1.0, 0.0), vec2(0.0, 1.0));

void polarium_main() {
    int at = PolariumBase.x + gl_InstanceID * 2;
    vec4 place = texelFetch(PolariumChests, at);
    vec4 look = texelFetch(PolariumChests, at + 1);
    vec4 sprite = texelFetch(PolariumChests, PolariumBase.y + int(look.w));

    vec3 p = Position;
    vec3 n = Normal;
    if (UV1.x != 0 && look.z != 0.0) {
        float angle = -look.z * 1.5707964;
        float c = cos(angle);
        float s = sin(angle);
        vec3 d = p - PolariumHinge.xyz;
        p = PolariumHinge.xyz + vec3(d.x, d.y * c - d.z * s, d.y * s + d.z * c);
        n = vec3(n.x, n.y * c - n.z * s, n.y * s + n.z * c);
    }
    vec2 turn = POLARIUM_TURN[int(place.w) & 3];
    vec3 d = p - vec3(0.5, 0.0, 0.5);
    p = vec3(0.5 + d.x * turn.x + d.z * turn.y, p.y, 0.5 - d.x * turn.y + d.z * turn.x);
    n = vec3(n.x * turn.x + n.z * turn.y, n.y, -n.x * turn.y + n.z * turn.x);

    vec3 block = place.xyz + PolariumOrigin.xyz;
    vec3 centre = block + (vec3(0.5) - PolariumCamera.xyz);

    minecraft_Position = block + (p - PolariumCamera.xyz);
    minecraft_Normal = n;
    minecraft_Color = vec4(1.0);
    minecraft_UV0 = sprite.xy + UV0 * sprite.zw;
    minecraft_UV1 = ivec2(0, 10);
    minecraft_UV2 = ivec2(look.xy);
    minecraft_main();
    // As the game: a chest is drawn while its block's middle is closer than the draw distance.
    if (dot(centre, centre) >= PolariumOrigin.w) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    }
}