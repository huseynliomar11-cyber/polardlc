#version 150

uniform sampler2D Sampler0;
uniform vec2 u_Velocity;
uniform float u_Intensity;

in vec2 texCoord;
out vec4 OutColor;

const int SAMPLES = 9;

void main() {
    vec2 vel = u_Velocity * (u_Intensity * 0.14);
    float len = length(vel);

    if (len < 0.00008) {
        OutColor = texture(Sampler0, texCoord);
        return;
    }

    if (len > 0.035) {
        vel = (vel / len) * 0.035;
    }

    vec4 sum = vec4(0.0);
    float totalWeight = 0.0;

    for (int i = 0; i < SAMPLES; i++) {
        float t = float(i) / float(SAMPLES - 1) - 0.5;
        vec2 offset = vel * t;
        float weight = 1.0 - abs(t) * 1.35;
        sum += texture(Sampler0, clamp(texCoord + offset, vec2(0.001), vec2(0.999))) * weight;
        totalWeight += weight;
    }

    OutColor = sum / max(totalWeight, 0.001);
}
