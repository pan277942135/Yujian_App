import React, { useState } from 'react';
import { NormalizedFishBox, FishInputAssessment } from '../types';
import { FishIllustration } from './FishIllustration';
import { Eye, EyeOff, Sparkles, AlertTriangle, XCircle, CheckCircle2 } from 'lucide-react';

interface DetectorOverlayProps {
  imageUri?: string;
  detectorBox?: NormalizedFishBox | null;
  cropBox?: NormalizedFishBox | null;
  assessment?: FishInputAssessment | null;
  speciesKey?: string;
  className?: string;
}

export const DetectorOverlay: React.FC<DetectorOverlayProps> = ({
  imageUri,
  detectorBox,
  cropBox,
  assessment,
  speciesKey,
  className = '',
}) => {
  const [showDetector, setShowDetector] = useState(true);
  const [showCrop, setShowCrop] = useState(true);

  const quality = assessment?.qualityLevel || 'GOOD';

  return (
    <div className={`flex flex-col gap-2.5 ${className}`}>
      {/* Visual Canvas Box */}
      <div className="relative w-full aspect-[4/3] rounded-3xl overflow-hidden bg-[#EAF4F2] shadow-sm border border-[#388478]/10 flex items-center justify-center select-none">
        {/* Render actual image if provided, else vector illustration fallback */}
        {imageUri ? (
          <img
            src={imageUri}
            alt="待识别鱼体"
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center bg-gradient-to-b from-[#EAF4F2] to-[#D8EAE6]">
            <FishIllustration size={220} speciesKey={speciesKey} />
          </div>
        )}

        {/* SVG Overlays for Bounding Boxes */}
        <svg
          className="absolute inset-0 w-full h-full pointer-events-none"
          viewBox="0 0 100 100"
          preserveAspectRatio="none"
        >
          {/* Expanded Crop Box (Orange) */}
          {showCrop && cropBox && (
            <g className="transition-all duration-300">
              <rect
                x={cropBox.x1 * 100}
                y={cropBox.y1 * 100}
                width={(cropBox.x2 - cropBox.x1) * 100}
                height={(cropBox.y2 - cropBox.y1) * 100}
                fill="#F29C38"
                fillOpacity="0.08"
                stroke="#F29C38"
                strokeWidth="1.2"
                strokeDasharray="2,2"
                rx="1"
              />
            </g>
          )}

          {/* Primary Detector Box (Water Teal) */}
          {showDetector && detectorBox && (
            <g className="transition-all duration-300">
              <rect
                x={detectorBox.x1 * 100}
                y={detectorBox.y1 * 100}
                width={(detectorBox.x2 - detectorBox.x1) * 100}
                height={(detectorBox.y2 - detectorBox.y1) * 100}
                fill="#388478"
                fillOpacity="0.12"
                stroke="#388478"
                strokeWidth="1.8"
                rx="1.5"
              />
              {/* Corner brackets */}
              <line x1={detectorBox.x1 * 100} y1={detectorBox.y1 * 100} x2={detectorBox.x1 * 100 + 4} y2={detectorBox.y1 * 100} stroke="#388478" strokeWidth="2.5" />
              <line x1={detectorBox.x1 * 100} y1={detectorBox.y1 * 100} x2={detectorBox.x1 * 100} y2={detectorBox.y1 * 100 + 4} stroke="#388478" strokeWidth="2.5" />

              <line x1={detectorBox.x2 * 100} y1={detectorBox.y1 * 100} x2={detectorBox.x2 * 100 - 4} y2={detectorBox.y1 * 100} stroke="#388478" strokeWidth="2.5" />
              <line x1={detectorBox.x2 * 100} y1={detectorBox.y1 * 100} x2={detectorBox.x2 * 100} y2={detectorBox.y1 * 100 + 4} stroke="#388478" strokeWidth="2.5" />

              <line x1={detectorBox.x1 * 100} y1={detectorBox.y2 * 100} x2={detectorBox.x1 * 100 + 4} y2={detectorBox.y2 * 100} stroke="#388478" strokeWidth="2.5" />
              <line x1={detectorBox.x1 * 100} y1={detectorBox.y2 * 100} x2={detectorBox.x1 * 100} y2={detectorBox.y2 * 100 - 4} stroke="#388478" strokeWidth="2.5" />

              <line x1={detectorBox.x2 * 100} y1={detectorBox.y2 * 100} x2={detectorBox.x2 * 100 - 4} y2={detectorBox.y2 * 100} stroke="#388478" strokeWidth="2.5" />
              <line x1={detectorBox.x2 * 100} y1={detectorBox.y2 * 100} x2={detectorBox.x2 * 100} y2={detectorBox.y2 * 100 - 4} stroke="#388478" strokeWidth="2.5" />
            </g>
          )}
        </svg>

        {/* Quality Gate Status Badge */}
        <div className="absolute top-3 right-3 flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold backdrop-blur-md shadow-sm">
          {quality === 'GOOD' && (
            <div className="bg-[#388478]/90 text-white flex items-center gap-1 px-2.5 py-1 rounded-full">
              <CheckCircle2 size={13} />
              <span>检测通过 (GOOD)</span>
            </div>
          )}
          {quality === 'WARNING' && (
            <div className="bg-[#F29C38]/90 text-white flex items-center gap-1 px-2.5 py-1 rounded-full">
              <AlertTriangle size={13} />
              <span>轻微截断 (WARNING)</span>
            </div>
          )}
          {quality === 'INVALID' && (
            <div className="bg-[#D9534F]/90 text-white flex items-center gap-1 px-2.5 py-1 rounded-full">
              <XCircle size={13} />
              <span>检测未过 (INVALID)</span>
            </div>
          )}
        </div>

        {/* Model Pipeline watermark */}
        <div className="absolute bottom-3 left-3 bg-white/80 backdrop-blur-md px-2.5 py-1 rounded-full text-[10px] text-[#748782] font-medium border border-white/50">
          DET_FISH_v0.1 · QUALITY_GATE_v1.1
        </div>
      </div>

      {/* Control Legend & Toggles */}
      <div className="flex items-center justify-between px-2 text-xs">
        <div className="flex items-center gap-4">
          {/* WaterTeal Detector bbox */}
          <button
            type="button"
            onClick={() => setShowDetector(!showDetector)}
            className={`flex items-center gap-1.5 transition-opacity ${
              showDetector ? 'opacity-100 font-medium text-[#172421]' : 'opacity-40 text-[#748782]'
            }`}
          >
            <span className="w-3 h-1.5 rounded-full bg-[#388478]" />
            <span>鱼体 bbox</span>
            {showDetector ? <Eye size={12} className="text-[#388478]" /> : <EyeOff size={12} />}
          </button>

          {/* Orange Crop bbox */}
          <button
            type="button"
            onClick={() => setShowCrop(!showCrop)}
            className={`flex items-center gap-1.5 transition-opacity ${
              showCrop ? 'opacity-100 font-medium text-[#172421]' : 'opacity-40 text-[#748782]'
            }`}
          >
            <span className="w-3 h-1.5 rounded-full bg-[#F29C38]" />
            <span>expand 裁剪</span>
            {showCrop ? <Eye size={12} className="text-[#F29C38]" /> : <EyeOff size={12} />}
          </button>
        </div>

        {assessment?.bboxAreaRatio && (
          <span className="text-[#748782] text-[11px]">
            面积占比: {(assessment.bboxAreaRatio * 100).toFixed(1)}%
          </span>
        )}
      </div>
    </div>
  );
};
