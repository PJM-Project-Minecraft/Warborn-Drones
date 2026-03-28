#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;
uniform float SignalQuality;

in vec2 texCoord;
out vec4 fragColor;

// ============================================================
// REALISTIC FPV SHADER - Based on real analog/digital FPV feed
// ============================================================

// High quality hash for large-scale noise
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Large scale smooth noise - for realistic analog interference
float largeNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f); // Smooth interpolation
    
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Fractal noise for organic look
float fbm(vec2 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    
    for (int i = 0; i < octaves; i++) {
        value += amplitude * largeNoise(p * frequency);
        amplitude *= 0.5;
        frequency *= 2.0;
    }
    return value;
}

// Lens Distortion (Barrel Distortion)
vec2 lensDistortion(vec2 uv, float k) {
    vec2 t = uv - 0.5;
    float r2 = t.x * t.x + t.y * t.y;
    float f = 0.0;
    
    // Only distort if k is non-zero
    if(k == 0.0) return uv;
    
    f = 1.0 + r2 * k;
    
    // Scale to keep the image expected size (optional, but good for FPV)
    float scale = 1.0 + k * 0.5; // Adjusted scale to fit
    
    return t * f / scale + 0.5;
}

void main() {
    vec2 uv = texCoord;
    
    // =========================================================
    // 0. LENS DISTORTION (Wide Angle Effect)
    // =========================================================
    // FPV drones typically use 2.1mm or 1.8mm lenses which have distortion
    // k = 0.1 to 0.2 for mild wide angle
    float distStrength = 0.6; // Stronger distortion
    uv = lensDistortion(uv, distStrength);
    
    // Black out out-of-bounds UVs
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    
    // Signal loss: 0.0 = perfect, 1.0 = total loss
    float loss = 1.0 - clamp(SignalQuality, 0.0, 1.0);
    
    // =========================================================
    // 1. DIGITAL ARTIFACTS (Datamoshing / Block Displacement)
    // =========================================================
    // REMOVED: User requested removal of jumping rectangles
    vec2 digitalUV = uv;
    float chromaShift = 0.0;
    
    // if (loss > 0.05) { ... } // Logic removed
    
    uv = digitalUV;

    // =========================================================
    // 1.5 PIXELIZATION (Resolution drop)
    // =========================================================
    if (loss > 0.1) {
        // Progressive pixelization
        float pxScale = 1.0 + loss * 30.0; // Up to 30x pixel size
        
        // Quantize UV to simulate low resolution
        vec2 grid = vec2(InSize.x / pxScale, InSize.y / pxScale);
        vec2 pixelatedUV = floor(uv * grid) / grid;
        
        // Variable bitrate: some areas are blocky, others sharp
        // Use stepped time to prevent 'morphing' look
        float stepTime = floor(Time * 15.0); 
        float localBitrate = largeNoise(vec2(uv.y * 8.0, stepTime));
        if (localBitrate < loss * 0.85) {
            uv = pixelatedUV;
        }
    }
    
    // =========================================================
    // 2. HORIZONTAL TEARING / SYNC LOSS
    // =========================================================
    // Real analog FPV has horizontal displacement when signal is weak
    // Use cubic curve to crush tearing at mid-signal
    float tearStrength = pow(loss, 4.0) * 0.15;
    // Noisy tear lines, using stepped time
    float tearTime = floor(Time * 30.0);
    float tearLine = largeNoise(vec2(tearTime, uv.y * 1.5)); 
    if (tearLine > 0.65) {
        float tearAmount = (tearLine - 0.65) / 0.35;
        // Jumpy displacement
        uv.x += (hash(vec2(tearTime, floor(uv.y * 20.0))) - 0.5) * tearStrength * tearAmount;
    }
    
    // =========================================================
    // 3. ROLLING INTERFERENCE BANDS
    // =========================================================
    // Irregular rolling bands - NO SINE WAVES (Scripted look removed)
    float bandY = uv.y + Time * 0.35; // Faster, irregular scroll
    // Use hash/noise to create bands of varying thickness and intensity
    float bandNoise = largeNoise(vec2(0.0, bandY * 3.0));
    float band = step(0.6, bandNoise); // Sharp cuts
    // Fade edges of bands
    band *= smoothstep(0.6, 0.65, bandNoise);
    
    // Non-linear scaling for bands too
    float bandStrength = pow(loss, 4.0) * 0.6 * band;
    
    // =========================================================
    // 4. SAMPLE THE IMAGE
    // =========================================================
    vec2 sampleUV = clamp(uv, 0.001, 0.999);
    
    // Chromatic aberration
    float chromaOffset = 0.0005 + loss * 0.004; // Very subtle aberration
    float r = texture(DiffuseSampler, sampleUV + vec2(chromaOffset, 0.0)).r;
    float g = texture(DiffuseSampler, sampleUV).g;
    float b = texture(DiffuseSampler, sampleUV - vec2(chromaOffset, 0.0)).b;
    
    // Apply digital chroma glitch (purple/green tint on corrupted blocks)
    if (chromaShift > 0.5) {
        g *= 0.4; // Stronger purple/pink tint
        r *= 1.2;
        b *= 1.2;
    }
    
    vec3 col = vec3(r, g, b);
    
    // =========================================================
    // 5. COLORFUL ANALOG STATIC (RGB Noise)
    // =========================================================
    // Based on user reference: Heavy, colorful boiling static
    // NOT sliding diagonally, but "boiling" in place
    
    // We use discrete time steps to make it jump randomly per frame
    // This eliminates ALL sliding movement
    // Modulo 1000.0 prevents float precision issues after long uptime
    float noiseTime = floor(mod(Time, 1000.0) * 60.0); // 60 FPS update rate for noise
    vec2 noiseUV = uv * InSize / 4.0; // Pixel-perfect noise scale (1/4 resolution)
    
    // Generate a random INTEGER offset for this frame
    // This ensures the noise grid itself shifts randomly, preventing "stationary grid" artifacts
    // But locking it to integers prevents sub-pixel sliding
    vec2 randomShift = floor(vec2(
        hash(vec2(noiseTime, 1.0)),
        hash(vec2(noiseTime, 2.0))
    ) * 100.0);
    
    // R channel noise
    float rNoise = largeNoise(noiseUV + randomShift + vec2(17.0, 31.0));
    // G channel noise
    float gNoise = largeNoise(noiseUV + randomShift + vec2(13.0, 83.0));
    // B channel noise
    float bNoise = largeNoise(noiseUV + randomShift + vec2(47.0, 51.0));
    
    vec3 staticColor = vec3(rNoise, gNoise, bNoise);
    staticColor = (staticColor - 0.5) * 2.0; // Range -1.0 to 1.0
    
    // Mix primarily into dark areas (sensor noise) but visible everywhere
    // Intensity increases massively with signal loss
    // USE NON-LINEAR SCALING: pow(loss, 3.0)
    // This keeps the image clean at mid-signal (40-30%) and only gets heavy near total loss
    float staticStrength = 0.03 + pow(loss, 5.0) * 1.2; 
    
    // Add the colorful static
    col += staticColor * staticStrength;
    
    // =========================================================
    // 6. LUMINANCE NOISE (Black & White grit)
    // =========================================================
    // Finer grain for detail - also static, not sliding
    float grain = hash(uv + vec2(noiseTime * 11.0, noiseTime * 7.0)); 
    grain = (grain - 0.5) * 0.5;
    
    col += vec3(grain) * (0.03 + pow(loss, 4.0) * 0.4);
    
    // =========================================================
    // 7. APPLY INTERFERENCE BANDS
    // =========================================================
    col = mix(col, col + vec3(0.3), bandStrength);
    
    // =========================================================
    // 8. COLOR QUANTIZATION (Digital bitrate loss)
    // =========================================================
    // Real digital FPV reduces color depth when bitrate drops
    if (loss > 0.15) {
        float levels = 64.0 * (1.0 - loss * 0.85); // 64 levels down to ~10
        levels = max(8.0, levels);
        col = floor(col * levels + 0.5) / levels;
    }
    
    // =========================================================
    // 9. DESATURATION (Signal degradation)
    // =========================================================
    // Weak signal = less color information
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(luma), loss * 0.5);
    
    // =========================================================
    // 10. CONTRAST ADJUSTMENT
    // =========================================================
    // =========================================================
    // 10. CONTRAST ADJUSTMENT & ACES TONEMAPPING SIMULATION
    // =========================================================
    // Slight contrast boost for that FPV camera look
    col = (col - 0.5) * (1.1 + loss * 0.2) + 0.5;
    
    // Simple saturation boost
    float gray = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(gray), col, 1.2); // 20% saturation boost

    // =========================================================
    // 10.5 SCANLINES
    // =========================================================
    // Fine horizontal lines mimicking interlaced video
    float scanlineCount = InSize.y * 0.5; // Every other line
    float scanline = sin(uv.y * scanlineCount * 3.14159 * 2.0);
    float scanlineIntensity = 0.05 + loss * 0.1; // Faint usually, strong on signal loss
    
    // Darken lines
    col -= (scanline * 0.5 + 0.5) * scanlineIntensity;
    
    // =========================================================
    // 11. VIGNETTE (Camera lens effect)
    // =========================================================
    vec2 vignetteUV = texCoord * 2.0 - 1.0;
    float vignette = 1.0 - dot(vignetteUV, vignetteUV) * 0.3;
    vignette = clamp(vignette, 0.0, 1.0);
    col *= vignette;
    
    // =========================================================
    // 12. TOTAL SIGNAL LOSS (Snow/static)
    // =========================================================
    if (loss > 0.85) {
        // Full static when signal is nearly gone
        float staticAmount = (loss - 0.85) / 0.15;
        
        // Large blocky static, not fine grain
        // Use noiseTime to prevent sliding - boil in place
        // Random offset per frame
        vec2 staticOffset = vec2(noiseTime * 93.0, noiseTime * 27.0);
        float staticNoise = largeNoise(uv * 8.0 + staticOffset);
        vec3 staticCol = vec3(staticNoise);
        
        // Some color in the static - also boiling in place
        // hash(uv + constant_random_per_frame)
        staticCol.r += (hash(uv + vec2(noiseTime * 19.0, noiseTime * 7.0)) - 0.5) * 0.4;
        staticCol.b += (hash(uv + vec2(noiseTime * 23.0, noiseTime * 3.0)) - 0.5) * 0.4;
        
        col = mix(col, staticCol, staticAmount);
    }
    
    // Clamp final output
    col = clamp(col, 0.0, 1.0);
    
    fragColor = vec4(col, 1.0);
}
