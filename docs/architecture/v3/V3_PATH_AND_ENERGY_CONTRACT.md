# YuJian V3 — Path and Energy Identity Contract
Resident: continuous Orb-owned movement and ambient light. Five existing R06 Resident IDs (G01/G02/A01/A02/C01) are historical code facts. They are NOT a fixed count of task filaments. Orb internal glass ribbons are separate again.
Task: Attempt-scoped additional emitted energy; source high-fidelity visuals determine number and appearance in Stage1. It may split/rejoin and must never alter Resident lifecycle, count, alpha or angular velocity.

Routes:
- RELEASE: Orb -> real Photo Contact; independent of Mask.
- PERIMETER: Photo Contact -> rounded rectangle both ways -> joins; source for droplets/starbursts and main ring must be shared.
- FISH: photo perimeter -> P*. Only current trusted real Mask, matching PhotoTransform/MaskSHA/GeometryVersion; nearest outer valid contour point, not fish tail.
- RETURN: consume original Ledger Path ID and stored reverse segments; no recreated approximations.

Attempt identity: sessionId + attemptId + imageId + photoSha256 + photoContentVersion. Geometry adds geometryVersion and canonical source coordinate space. Task path adds pathId, maskSha256/maskVersion, P*, creation event/time. Reject missing FishPath, identity mismatch, stale GeometryVersion, untrusted Mask, event out-of-order. If fishPath missing but arrivalPath exists, draw ZERO fish-seeking geometry and no fake fish contact.
Only controller/ledger authorize events, renderer consumes valid snapshots. Actual visual visibility must be measured; a Draw call does not prove visible pixels.

Core invariants for later tests: Resident remains continuous across Release/Perimeter/Fish/Return; task failure never extinguishes Orb; exact path identity crosses Processing/Result.
