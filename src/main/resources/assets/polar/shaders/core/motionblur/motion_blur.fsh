#version 150

uniform sampler2D Sampler0;
uniform vec2 u_Velocity;
uniform float u_Intensity;
uniform float u_Chromatic;

in vec2 TexCoord;
in vec4 FragColor;

out vec4 fragColor;

const int SAMPLES = 17;

void main() {
    vec2 vel = u_Velocity * (u_Intensity * 0.28);
    float speed = length(vel);

    if (speed < 0.00008) {
        fragColor = texture(Sampler0, TexCoord);
        return;
    }

    if (speed > 0.085) {
        vel = (vel / speed) * 0.085;
    }

    float chromScale = u_Chromatic * min(speed * 0.85, 0.025);

    vec4 accum = vec4(0.0);
    float totalWeight = 0.0;

    for (int i = 0; i < SAMPLES; i++) {
        float step = float(i) / float(SAMPLES - 1) - 0.5;
        vec2 offset = vel * step;
        float weight = 1.0 - abs(step) * 1.35;

        vec2 uv = clamp(TexCoord + offset, vec2(0.001), vec2(0.999));

        if (chromScale > 0.0001) {
            vec2 rUv = clamp(TexCoord + offset * (1.0 + chromScale), vec2(0.001), vec2(0.999));
            vec2 gUv = uv;
            vec2 bUv = clamp(TexCoord + offset * (1.0 - chromScale), vec2(0.001), vec2(0.999));

            float r = texture(Sampler0, rUv).r;
            float g = texture(Sampler0, gUv).g;
            float b = texture(Sampler0, bUv).b;
            float a = texture(Sampler0, uv).a;

            accum += vec4(r, g, b, a) * weight;
        } else {
            accum += texture(Sampler0, uv) * weight;
        }

        totalWeight += weight;
    }

    fragColor = accum / max(totalWeight, 0.0001);
}
