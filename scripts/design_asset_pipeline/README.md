# Design Asset Pipeline

The pipeline validates immutable source SHA256, contract values, shared-master SHA256 and the
static recompose gate before Android code is built. `verify_empty_home_runtime_v2.py` is the
Android-only feature wrapper; it does not alter or replace the historical V1 verifier.
