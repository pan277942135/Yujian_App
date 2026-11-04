import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';

const args = new Map();
for (let i = 2; i < process.argv.length; i += 2) {
  args.set(process.argv[i], process.argv[i + 1]);
}

const baseUrl = args.get('--url') ?? 'http://127.0.0.1:3000/';
const output = resolve(args.get('--output') ?? 'evidence/fish-optics-v2-2/demo-runtime/M07');
const profile = args.get('--profile') ?? 'M07';
const expectedDemoSha = args.get('--demo-sha') ?? 'a790f145620ab32f96fe525bc38dd902653bd960';
const viewport = { width: 390, height: 844 };
const deviceScaleFactor = 3;
const demoRoot = resolve(process.env.FISH_DEMO_ROOT ?? process.cwd());
const requireFromDemo = createRequire(resolve(demoRoot, 'package.json'));
const { chromium } = requireFromDemo('playwright');
const browser = await chromium.launch({
  headless: true,
  args: ['--no-sandbox', '--use-gl=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist'],
});

try {
  const context = await browser.newContext({
    viewport,
    deviceScaleFactor,
    isMobile: true,
    hasTouch: true,
  });
  const page = await context.newPage();
  const consoleErrors = [];
  const failedRequests = [];
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  page.on('requestfailed', (request) => failedRequests.push({
    url: request.url(),
    error: request.failure()?.errorText ?? 'unknown',
  }));
  await page.goto(baseUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await page.waitForFunction(() => {
    const photo = document.querySelector('#photo-container');
    const image = photo?.querySelector('img[alt="Fish Catch"]');
    return Boolean(photo && image?.complete && image.naturalWidth > 0 &&
      photo.querySelector('canvas[data-water-caustics]'));
  }, null, { timeout: 120_000 });

  const measured = await page.evaluate(() => {
    const rect = (element) => {
      const value = element.getBoundingClientRect();
      return {
        x: value.x,
        y: value.y,
        left: value.left,
        top: value.top,
        right: value.right,
        bottom: value.bottom,
        width: value.width,
        height: value.height,
      };
    };
    const photo = document.querySelector('#photo-container');
    const image = photo?.querySelector('img[alt="Fish Catch"]');
    const canvases = [...(photo?.querySelectorAll('canvas') ?? [])];
    const primary = photo?.querySelector('canvas[data-water-caustics]');
    const gl = primary?.getContext('webgl') ?? primary?.getContext('experimental-webgl');
    const style = photo ? getComputedStyle(photo) : null;
    const imageStyle = image ? getComputedStyle(image) : null;
    const measuredCanvas = (canvas, index) => {
      const bounds = rect(canvas);
      return {
        index,
        dataset: { ...canvas.dataset },
        boundsCssPx: bounds,
        backingWidthPx: canvas.width,
        backingHeightPx: canvas.height,
        scaleX: canvas.width / bounds.width,
        scaleY: canvas.height / bounds.height,
        cssBackingDeltaX: canvas.width / bounds.width - Math.min(window.devicePixelRatio || 1, 2),
        cssBackingDeltaY: canvas.height / bounds.height - Math.min(window.devicePixelRatio || 1, 2),
      };
    };
    const webglParameter = (key) => {
      if (!gl) return null;
      try { return gl.getParameter(gl[key]); } catch { return null; }
    };
    return {
      viewport: {
        innerWidth: window.innerWidth,
        innerHeight: window.innerHeight,
        outerWidth: window.outerWidth,
        outerHeight: window.outerHeight,
        devicePixelRatio: window.devicePixelRatio,
        screenWidth: window.screen.width,
        screenHeight: window.screen.height,
      },
      photoContainer: {
        boundsCssPx: photo ? rect(photo) : null,
        borderRadiusCss: style?.borderRadius ?? null,
        borderWidthsCss: {
          top: style?.borderTopWidth ?? null,
          right: style?.borderRightWidth ?? null,
          bottom: style?.borderBottomWidth ?? null,
          left: style?.borderLeftWidth ?? null,
        },
      },
      photoContent: {
        boundsCssPx: image ? rect(image) : null,
        naturalWidthPx: image?.naturalWidth ?? null,
        naturalHeightPx: image?.naturalHeight ?? null,
        objectFit: imageStyle?.objectFit ?? null,
        objectPosition: imageStyle?.objectPosition ?? null,
      },
      canvases: canvases.map(measuredCanvas),
      renderer: {
        kind: gl ? 'webgl' : 'not-webgl',
        version: webglParameter('VERSION'),
        renderer: webglParameter('RENDERER'),
        shadingLanguageVersion: webglParameter('SHADING_LANGUAGE_VERSION'),
      },
      expectedBackingScale: Math.min(window.devicePixelRatio || 1, 2),
    };
  });

  await mkdir(output, { recursive: true });
  const screenshotPath = resolve(output, 'demo-m07-390x844-dpr3.png');
  const reportPath = resolve(output, 'demo-geometry.json');
  await page.screenshot({ path: screenshotPath, fullPage: false });
  const screenshotBytes = await readFile(screenshotPath);
  const report = {
    gate: 'DEMO_RUNTIME_GEOMETRY',
    provenance: {
      sourceSha: process.env.FISH_OPTICS_SOURCE_SHA ?? null,
      harnessSha: process.env.FISH_OPTICS_HARNESS_SHA ?? null,
      ciRunId: process.env.GITHUB_RUN_ID ?? null,
    },
    profile,
    viewportCssPx: viewport,
    configuredDeviceScaleFactor: deviceScaleFactor,
    demo: {
      repository: '18694061757-ctrl/Fish-Mask-Gold-Contour-Lab',
      expectedCommit: expectedDemoSha,
      sourceCommitVerified: process.env.FISH_DEMO_ACTUAL_SHA === expectedDemoSha,
      actualCommit: process.env.FISH_DEMO_ACTUAL_SHA ?? null,
    },
    measured,
    screenshot: {
      path: 'demo-m07-390x844-dpr3.png',
      sha256: createHash('sha256').update(screenshotBytes).digest('hex'),
    },
    coordinateMapping: measured.renderer.kind === 'webgl' && measured.canvases.length >= 2
      ? 'MEASURED_M07_CSS_DPR_BACKING_STORE'
      : 'INCOMPLETE_RENDERER_OR_CANVAS_EVIDENCE',
    browserErrors: consoleErrors,
    failedRequests,
  };
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify(report, null, 2));
  if (report.demo.sourceCommitVerified !== true) throw new Error('Demo source SHA did not match the pinned commit');
  if (measured.renderer.kind !== 'webgl') throw new Error('Demo perimeter WebGL context was unavailable');
  if (measured.canvases.length < 2) throw new Error(`Expected both overlay canvases, got ${measured.canvases.length}`);
  for (const canvas of measured.canvases) {
    if (Math.abs(canvas.cssBackingDeltaX) > 0.02 || Math.abs(canvas.cssBackingDeltaY) > 0.02) {
      throw new Error(`Canvas backing scale mismatch for overlay ${canvas.index}`);
    }
  }
} finally {
  await browser.close();
}
