# R06 main 3 materials → 5 independent paths

Main uses three time-driven elliptical material loops, not five authored paths. Green maps to G01/G02, gold A01/A02, cyan C01. New Beziers use two segments each, unique shapes, phases, periods and front/back depth. No legacy and new double draw. Reference `236493.png` has much larger outer geometry on dark background; the candidate compresses its character into the existing 14dp allowance rather than expanding the layout. Final likeness and rendering need product visual approval. Release/Return are S2 events only.

## V2 resource and material closure

The current five frozen centerlines were retained. The JSON profile now defines separate green water-ribbon, cyan water-ribbon and warm-gold filament materials. The SVG stores centerlines only. Static boards show layer construction, front/back ordering, four deterministic motion samples and 68dp/80dp views on light and dark backgrounds.

The computed maximum expansions, including the material width multipliers, inherited wave-normal movement and deterministic control-point deformation, are G01 13.10 dp, G02 13.83 dp, A01 12.54 dp, A02 9.31 dp and C01 7.49 dp. All remain under the 14 dp formal bound. No extended runtime candidate was produced. These boards are static design evidence; product visual review and Android rendering remain pending.
