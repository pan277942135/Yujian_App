# YuJian V3 — Module and Render Ownership Matrix
| Responsibility | Unique logical owner | Constraint |
|---|---|---|
| Clock, 13 stages, model gates | RecognitionSceneAnimationController | No second production clock |
| Scene identity | existing RecognitionSceneFrame | All consumers use same Session/Attempt/Photo/Geometry |
| Mask trust and nearest P* | existing Mask decision and NearestContourContactPoint | Cannot use BBox as contour |
| Route and stored Return identity | WaterLightPathLedger | No path regeneration on Result |
| Orb crystal and resident motion | OrbCrystalGlRenderer / OrbResidentSystemsV1 | Task energy cannot dim resident motion |
| Task Release and Fish energy | Stage045GlRenderer | Task count dynamic; no Resident Path IDs |
| Perimeter ring | ONE selected existing host in Stage4 | Stage045 and legacy perimeter may not both own main ring |
| Photo source texture | existing Photo Owner | Optical layer can sample but not replace source identity |
| Local photo refraction | ONE Optical Owner selected in Stage4 | Never shift logical Mask/P* |
| Fish contour/gold | existing RevealOpticalRenderer | Trusted real Mask only |
| Glyph/Result stability | Result UI + RecognitionResultHandoffConsumer | Five ordered 13.1–13.5 milestones |
| Bloom/postprocessing | per GL host pipeline | Return explicit pass status |
| Durable evidence | RecognitionSessionEvidenceRecorder + EventSink + Store | No second competing logger |
| PixelCopy / ZIP | existing PixelCopyCapture / ZipExporter / ArchiveVerifier | Asynchronous bounded, verified |

Only switch visual ownership when new GL produces a valid first presented frame. On failure restore fallback; no blind opacity suppression. Physical EGL host consolidation is not required by the architecture.
