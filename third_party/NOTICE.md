# Third-party notices for Lake Glass V2 shaders

The Lake Glass V2 shader in `app/src/main/assets/lake_glass_v2/shaders/lake_glass_v2.agsl` is an adaptation of small algorithm and API patterns from the permissively licensed projects listed below. No third-party runtime library, Three.js bundle, `GLSurfaceView`, or EGL lifecycle is added. The unmodified license texts are retained in `third_party/licenses/`.

| Project | Pinned inspected commit | License | Upstream source reviewed | SHA-256 | Use in this repository |
|---|---|---|---|---|---|
| [ethanfox/water-caustics](https://github.com/ethanfox/water-caustics) | `de856eec62819b7c4176366db73b8fd37a1b1455` | MIT | `src/shaders.ts` | `c07d7ba264855c7531d2221ea7bc1348d8eea1052aa0f0576d545e664a1efb40` | Adapted the continuous, rotated, domain-warped multi-octave ridge field. Output is used only as a low-amplitude multiplier inside the existing Master-derived caustics mask. |
| [ScottieFox/caustic-volume](https://github.com/ScottieFox/caustic-volume) | `d87351bff19831aa9d11c0679605fad6797b133f` | MIT | `sandbox/src/20_caustic.js`, `sandbox/src/30_trace.js`, `lite/index.html` | `a6f0f419db07b3e6705a5d7df73f4d43f0a09c34e550d344766b919d259c6ba0`; `140349f8683cd0685c85d18c68ad501e34677c9ed6a6fdde18ac7dbed7d1971d`; `54241b8d920d2727fa01f64b86a33238c86900c7f25db4d7b57cb7555e8d0528` | Algorithm comparison only: its volumetric caustic projection, refractive interface solve, and 24 travelling waves require a 3D scene and texture volume. No implementation code copied. The Three.js demo is not bundled. |
| [AndroidPoet/mirage](https://github.com/AndroidPoet/mirage) | `9da9b00f678d57ce9989838ac513ffa88a11b038` | Apache-2.0 | `mirage/src/androidMain/kotlin/io/androidpoet/mirage/core/Program.android.kt`; `mirage/src/commonMain/kotlin/io/androidpoet/mirage/core/Agsl.kt` | `a26d4ebddd9665e8ef044ca5577a969c67d4e3ba0c6cda24e42c0e9167a22bbf`; `442ffb2de7aea047b2c652bc897b41736694a834e6772f07e40ea3d5086d9ed8` | Adapted guarded API 33 `RuntimeShader`/RenderEffect construction, reusable uniforms, and the Ashima 2D simplex-noise block for slow modulation. No Gradle dependency added. |
| [styropyr0/PrismalAGSL](https://github.com/styropyr0/PrismalAGSL) | `c853feb2e8b375fc8eb2dcce25a6d4f18250fb68` | MIT | `Prismal/src/main/assets/agsl/rect_refraction.agsl`; `Prismal/src/main/assets/agsl/rounded_rect_sdf.agsl` | `73b656a3208ff7967cfede6fab0a90db41d7c8c1ce9063a9d3f94fecfff37035`; `4b749aa81ad625ed818989bf106854a3bec9c945fb46ea99c08326194af09ac4` | Adapted signed-distance and gradient-based sampling principles. Refraction samples the composited base input, gated by the Master mask; subtle specular modulation is limited to existing baked highlights, with no newly painted highlight or droplet surface. |
| [frmlinn/raindrops-v2](https://github.com/frmlinn/raindrops-v2) | `6e95911694bac6bafd1aadbc0e85fbd9a2e56ada` | MIT | `src/simulation/RainMaterial.js` | `89b95eadd127eb5bad686259bb5fe7f7aa93f7f12b948f48029bacc4eb7048a6` | Adapted the screen-space displacement/sample model. Fixed Master-space bounds replace the upstream particle field; no particle simulation, Three.js, or rain layer is used. |

## Adaptation notes

- All copied/adapted portions are identified by source comments in the AGSL file and by the exact commits and file hashes above.
- The AGSL adaptation was authored for this project and changed to use the Lake Glass V2 Master coordinate system, existing mask channels, fixed light origin, fixed droplet bounds, and a single composited input surface.
- Caustics and sunlight cannot add color sheets or new light geometry. Droplets cannot move or draw a second RGBA copy. Shader parameters are bounded in `LakeGlassEffectParameters` and remain disabled by default in the formal host.
- `ScottieFox/caustic-volume` is included because it was specifically audited, but no source from it is redistributed. Its complete MIT text is retained with the other audit licenses for traceability.

## License files

- `third_party/licenses/ETHANFOX_WATER_CAUSTICS_MIT.txt`
- `third_party/licenses/SCOTTIEFOX_CAUSTIC_VOLUME_MIT.txt`
- `third_party/licenses/ANDROIDPOET_MIRAGE_APACHE_2.0.txt`
- `third_party/licenses/PRISMAL_AGSL_MIT.txt`
- `third_party/licenses/RAINDROPS_V2_MIT.txt`
