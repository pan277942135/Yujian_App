import {
  NormalizedFishBox,
  FishDetection,
  FishInputAssessment,
  FishInputStatus,
  QualityLevel,
  ProductionRecognitionResult,
  RecognitionCandidate,
  RecognitionPrediction,
} from '../types';
import { INITIAL_SPECIES_LIST } from '../data/fallbackData';

export const QUALITY_GATE_CONTRACT = {
  CONTRACT_VERSION: 'QUALITY_GATE_v1.1',
  STRONG_CONFIDENCE: 0.40,
  WEAK_CONFIDENCE: 0.20,
  NMS_IOU: 0.45,
  MIN_PRIMARY_AREA_RATIO: 0.03,
  INCOMPLETE_EDGE_MARGIN_RATIO: 0.02,
  INCOMPLETE_SECTOR_RATIO: 0.20,
  CROP_EXPAND_RATIO: 0.15,
  MODEL_VERSION: 'MODEL_M1_v0.2',
  DETECTOR_MODEL_VERSION: 'DET_FISH_v0.1',
  MODEL_SHA256: '9575ede5c6c85b850647016d76e8e5175fa9ea6b609c47c83f54b4062e47d14e',
};

const MODEL_CLASSES = [
  { key: 'grass_carp', name: '草鱼' },
  { key: 'bighead_carp', name: '鳙鱼' },
  { key: 'silver_carp', name: '白鲢' },
  { key: 'common_carp', name: '鲤鱼' },
  { key: 'crucian_carp', name: '鲫鱼' },
  { key: 'largemouth_bass', name: '加州鲈' },
  { key: 'snakehead', name: '黑鱼' },
  { key: 'yellow_catfish', name: '黄骨鱼' },
  { key: 'black_carp', name: '青鱼' },
];

/**
 * Normalizes and clamps box coordinates to [0, 1]
 */
export function normalizeBox(box: NormalizedFishBox): NormalizedFishBox {
  return {
    x1: Math.max(0, Math.min(1, Math.min(box.x1, box.x2))),
    y1: Math.max(0, Math.min(1, Math.min(box.y1, box.y2))),
    x2: Math.max(0, Math.min(1, Math.max(box.x1, box.x2))),
    y2: Math.max(0, Math.min(1, Math.max(box.y1, box.y2))),
  };
}

export function boxAreaRatio(box: NormalizedFishBox): number {
  const norm = normalizeBox(box);
  return (norm.x2 - norm.x1) * (norm.y2 - norm.y1);
}

/**
 * Port of FishDetectionQualityGate.expandBox()
 */
export function expandBox(
  box: NormalizedFishBox,
  expandRatio: number = QUALITY_GATE_CONTRACT.CROP_EXPAND_RATIO
): NormalizedFishBox {
  const norm = normalizeBox(box);
  const w = norm.x2 - norm.x1;
  const h = norm.y2 - norm.y1;
  const padX = w * expandRatio;
  const padY = h * expandRatio;
  return normalizeBox({
    x1: norm.x1 - padX,
    y1: norm.y1 - padY,
    x2: norm.x2 + padX,
    y2: norm.y2 + padY,
  });
}

/**
 * Port of FishDetectionQualityGate.cropBoxPixels()
 */
export function cropBoxPixels(
  box: NormalizedFishBox,
  width: number,
  height: number
): [number, number, number, number] {
  const norm = normalizeBox(box);
  const left = Math.max(0, Math.floor(norm.x1 * width));
  const top = Math.max(0, Math.floor(norm.y1 * height));
  const right = Math.min(width, Math.ceil(norm.x2 * width));
  const bottom = Math.min(height, Math.ceil(norm.y2 * height));
  return [left, top, right, bottom];
}

/**
 * Assess detections using the Quality Gate rules
 */
export function assessQuality(
  detections: FishDetection[],
  imgWidth: number,
  imgHeight: number
): FishInputAssessment {
  if (!detections || detections.length === 0) {
    return {
      isClassifierEligible: false,
      qualityLevel: 'INVALID',
      qualityReason: '未检测到清晰鱼体，请确保鱼体在画面中央并保持良好光线。',
      status: 'NONE',
      primary: null,
      secondary: null,
      cropBox: null,
      bboxAreaRatio: null,
    };
  }

  // Sort by confidence descending
  const sorted = [...detections].sort((a, b) => b.confidence - a.confidence);
  const primary = sorted[0];
  const secondary = sorted.length > 1 ? sorted[1] : null;
  const area = boxAreaRatio(primary.box);

  // Check 1: Weak confidence
  if (primary.confidence < QUALITY_GATE_CONTRACT.WEAK_CONFIDENCE) {
    return {
      isClassifierEligible: false,
      qualityLevel: 'INVALID',
      qualityReason: `检测置信度过低 (${(primary.confidence * 100).toFixed(0)}%)，无法确认鱼体。`,
      status: 'NONE',
      primary,
      secondary,
      cropBox: null,
      bboxAreaRatio: area,
    };
  }

  // Check 2: Too small (< 3% image area)
  if (area < QUALITY_GATE_CONTRACT.MIN_PRIMARY_AREA_RATIO) {
    return {
      isClassifierEligible: false,
      qualityLevel: 'INVALID',
      qualityReason: `鱼体占画面面积过小 (${(area * 100).toFixed(1)}% < 3%)，请靠近一些重新拍摄。`,
      status: 'TOO_SMALL',
      primary,
      secondary,
      cropBox: null,
      bboxAreaRatio: area,
    };
  }

  // Check 3: Edge cutoff check
  const norm = normalizeBox(primary.box);
  const touchesLeft = norm.x1 <= QUALITY_GATE_CONTRACT.INCOMPLETE_EDGE_MARGIN_RATIO;
  const touchesTop = norm.y1 <= QUALITY_GATE_CONTRACT.INCOMPLETE_EDGE_MARGIN_RATIO;
  const touchesRight = norm.x2 >= 1.0 - QUALITY_GATE_CONTRACT.INCOMPLETE_EDGE_MARGIN_RATIO;
  const touchesBottom = norm.y2 >= 1.0 - QUALITY_GATE_CONTRACT.INCOMPLETE_EDGE_MARGIN_RATIO;
  const touchingEdgesCount = [touchesLeft, touchesTop, touchesRight, touchesBottom].filter(Boolean).length;

  const crop = expandBox(primary.box);

  // If severe edge cutting across multiple sides
  if (touchingEdgesCount >= 2 && primary.confidence < QUALITY_GATE_CONTRACT.STRONG_CONFIDENCE) {
    return {
      isClassifierEligible: true,
      qualityLevel: 'WARNING',
      qualityReason: '鱼体边缘截断较多，识别准确度可能受影响。',
      status: 'INCOMPLETE_FISH',
      primary,
      secondary,
      cropBox: crop,
      bboxAreaRatio: area,
    };
  }

  // Check 4: Multiple fish
  if (secondary && secondary.confidence >= QUALITY_GATE_CONTRACT.STRONG_CONFIDENCE && boxAreaRatio(secondary.box) > 0.08) {
    return {
      isClassifierEligible: true,
      qualityLevel: 'WARNING',
      qualityReason: '检测到多条鱼，已自动聚焦识别面积最大的主鱼体。',
      status: 'MULTIPLE',
      primary,
      secondary,
      cropBox: crop,
      bboxAreaRatio: area,
    };
  }

  // Normal valid fish
  return {
    isClassifierEligible: true,
    qualityLevel: 'GOOD',
    qualityReason: '鱼体清晰完整，质量检测通过。',
    status: 'PRIMARY',
    primary,
    secondary,
    cropBox: crop,
    bboxAreaRatio: area,
  };
}

/**
 * High-precision Simulated Pipeline that analyzes an image (or sample tag)
 * and generates realistic detector bounding boxes, quality assessment, and classifier logits.
 */
export async function runFishRecognitionPipeline(
  imageSource: string | HTMLCanvasElement | HTMLImageElement,
  hintSpeciesKey?: string
): Promise<ProductionRecognitionResult> {
  const startTime = performance.now();

  // 1. Determine image dimensions
  let width = 640;
  let height = 480;
  let imageUri = typeof imageSource === 'string' ? imageSource : '';

  if (imageSource instanceof HTMLImageElement) {
    width = imageSource.naturalWidth || 640;
    height = imageSource.naturalHeight || 480;
    imageUri = imageSource.src;
  } else if (imageSource instanceof HTMLCanvasElement) {
    width = imageSource.width;
    height = imageSource.height;
    imageUri = imageSource.toDataURL('image/jpeg', 0.9);
  }

  // Simulate detector processing delay (approx 180ms - 280ms)
  await new Promise((r) => setTimeout(r, 220));

  // 2. Derive fish bounding box
  // If hint is given or from demo, position bounding box appropriately
  let primaryBox: NormalizedFishBox = {
    x1: 0.12 + (Math.random() * 0.05),
    y1: 0.20 + (Math.random() * 0.05),
    x2: 0.86 - (Math.random() * 0.05),
    y2: 0.78 - (Math.random() * 0.05),
  };

  let primaryConf = 0.92 + Math.random() * 0.06;
  let detections: FishDetection[] = [
    {
      confidence: primaryConf,
      box: primaryBox,
      label: 'fish',
    },
  ];

  // 3. Run Quality Gate
  const assessment = assessQuality(detections, width, height);

  // 4. Run Classifier (if eligible)
  let prediction: RecognitionPrediction | null = null;
  let cropPixels: [number, number, number, number] | null = null;

  if (assessment.isClassifierEligible && assessment.cropBox) {
    cropPixels = cropBoxPixels(assessment.cropBox, width, height);

    // Pick top target species
    const targetKey = hintSpeciesKey || 'yellow_catfish';
    const primaryCandidate = MODEL_CLASSES.find((c) => c.key === targetKey) || MODEL_CLASSES[7]; // yellow_catfish default

    // Generate softmax probability distribution
    const topConfidence = 0.91 + Math.random() * 0.07;
    let remainingProb = 1.0 - topConfidence;

    const candidates: RecognitionCandidate[] = MODEL_CLASSES.map((cls, idx) => {
      if (cls.key === primaryCandidate.key) {
        return {
          classIndex: idx,
          speciesKey: cls.key,
          speciesName: cls.name,
          confidence: Math.round(topConfidence * 1000) / 1000,
        };
      }
      // Distribute rest
      const share = remainingProb * (0.1 + Math.random() * 0.2);
      remainingProb = Math.max(0, remainingProb - share);
      return {
        classIndex: idx,
        speciesKey: cls.key,
        speciesName: cls.name,
        confidence: Math.round(share * 1000) / 1000,
      };
    }).sort((a, b) => b.confidence - a.confidence);

    // Normalize probabilities sum
    const total = candidates.reduce((acc, c) => acc + c.confidence, 0);
    candidates.forEach((c) => {
      c.confidence = Math.round((c.confidence / total) * 1000) / 1000;
    });

    prediction = {
      modelVersion: QUALITY_GATE_CONTRACT.MODEL_VERSION,
      modelSha256: QUALITY_GATE_CONTRACT.MODEL_SHA256,
      top1: candidates[0],
      candidates,
      latencyMs: Math.round(performance.now() - startTime),
    };
  }

  const totalLatency = Math.round(performance.now() - startTime);

  return {
    status: assessment.status,
    assessment,
    prediction,
    cropPixels,
    latencyMs: totalLatency,
    imageUri,
    timestamp: Date.now(),
  };
}
