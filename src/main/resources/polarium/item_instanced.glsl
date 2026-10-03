// Polarium: items drawn instanced. Vertices arrive in the item's own space and
// are placed here, on the GPU, from each entity's pose in PolariumInstances,
// which also holds its light, overlay and tint colors. Each vertex carries its
// quad's tint layer and light emission (UV1), applied as
// VertexConsumer.putBakedQuad does. The game's own item shader (core/item)
// does the rest.

layout(std140) uniform PolariumDraw {
    ivec4 PolariumBase;
};

uniform samplerBuffer PolariumInstances;

in vec3 Position;
in vec2 UV0;
in ivec2 UV1; // x: tint layer (-1 for none), y: light emission
in vec3 Normal;

void polarium_main() {
    int entity = PolariumBase.x + gl_InstanceID * PolariumBase.y;
    vec4 coords = texelFetch(PolariumInstances, entity);
    vec4 row0 = texelFetch(PolariumInstances, entity + 1);
    vec4 row1 = texelFetch(PolariumInstances, entity + 2);
    vec4 row2 = texelFetch(PolariumInstances, entity + 3);

    // LightCoordsUtil.lightCoordsWithEmission: each channel at least the emission.
    ivec2 lightCoords = ivec2(coords.zw);
    if (UV1.y > 0) {
        lightCoords = max(lightCoords >> 4, ivec2(UV1.y)) << 4;
    }
    vec4 local = vec4(Position, 1.0);
    mat3 linear = transpose(mat3(row0.xyz, row1.xyz, row2.xyz));

    minecraft_Position = vec3(dot(row0, local), dot(row1, local), dot(row2, local));
    minecraft_Normal = normalize(transpose(inverse(linear)) * Normal);
    minecraft_Color = UV1.x >= 0 ? texelFetch(PolariumInstances, entity + 4 + UV1.x) : vec4(1.0);
    minecraft_UV0 = UV0;
    minecraft_UV1 = ivec2(coords.xy);
    minecraft_UV2 = lightCoords;
    minecraft_main();
}
