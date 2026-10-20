# EdgeGlowView adaptation notice

This browser prototype adapts the drawing ideas of **EdgeGlowView** by WANG YU:

- Upstream repository: <https://github.com/yu-day/EdgeGlowView>
- Source file: `edgeglowview/src/main/java/com/github/edgeglowview/EdgeGlowView.kt`
- Upstream commit inspected: `46ce28221ed30bcd7763ff13ecdd96171756eb60`
- License: MIT; copyright `Copyright (c) 2025 WANG YU`.

The prototype keeps the three-pass halo structure and the path-distance comet idea, replacing the fixed rounded-rectangle path with the manual fish contour and replacing `PathMeasure.getSegment()` with browser SVG path length sampling. It is an independent Canvas 2D web adaptation; it does not execute the Kotlin View and is not Android runtime evidence.

The upstream MIT license text is reproduced below with its copyright notice.

> MIT License
>
> Copyright (c) 2025 WANG YU
>
> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the “Software”), to deal
> in the Software without restriction, including without limitation the rights
> to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
> SOFTWARE.
