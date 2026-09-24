# Empty Home V2

Frozen source: `source/frozen/Empty_Home_Final_Design_V2.png`.

The page is the existing `HomeScreen` EMPTY state, entered only when `fishRecords.isEmpty()`.
It reuses the production capture/identify, album-picker and account-entry callbacks. It does
not introduce a second Home, camera or gallery flow.

The source file is immutable. `intermediate` retains reproducible layer masters and validation;
`shared/contracts` is the Android traceability contract; Android-only runtime derivatives live
under `app/src/main/assets/empty_home_runtime_v2`.
