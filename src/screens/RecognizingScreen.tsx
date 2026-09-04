import React, { useEffect, useState } from 'react';
import { FishIllustration } from '../components/FishIllustration';
import { CheckCircle, Loader2 } from 'lucide-react';

interface RecognizingScreenProps {
  onCompleted: () => void;
}

const PIPELINE_STEPS = [
  { id: 1, label: '定位鱼体空间位置', detail: 'YOLOX-Nano DET_FISH_v0.1' },
  { id: 2, label: '评估拍摄与门禁质量', detail: 'QUALITY_GATE_v1.1 校验' },
  { id: 3, label: '生成专属裁剪与Letterbox', detail: 'expand_ratio 0.15 · 224x224' },
  { id: 4, label: '比对鱼类特征分类模型', detail: 'MODEL_M1_v0.2 Softmax推理' },
];

export const RecognizingScreen: React.FC<RecognizingScreenProps> = ({ onCompleted }) => {
  const [currentStep, setCurrentStep] = useState(1);

  useEffect(() => {
    const t1 = setTimeout(() => setCurrentStep(2), 500);
    const t2 = setTimeout(() => setCurrentStep(3), 1050);
    const t3 = setTimeout(() => setCurrentStep(4), 1600);
    const t4 = setTimeout(() => onCompleted(), 2200);

    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
      clearTimeout(t3);
      clearTimeout(t4);
    };
  }, [onCompleted]);

  return (
    <div className="fixed inset-0 z-50 flex flex-col items-center justify-between p-6 bg-gradient-to-b from-[#172421] via-[#1C2C29] to-[#121A18] text-white">
      {/* Top watermark */}
      <div className="pt-8 text-center">
        <span className="text-xs text-[#5F9386] font-bold uppercase tracking-widest">
          渔见 AI · 智能识别中
        </span>
        <h2 className="text-2xl font-black text-white mt-1">正在分析鱼体特征</h2>
      </div>

      {/* Radar scanning circle */}
      <div className="relative flex items-center justify-center my-auto">
        {/* Radar Rings */}
        <div className="absolute w-64 h-64 rounded-full border border-[#388478]/30 animate-ping opacity-25" />
        <div className="absolute w-52 h-52 rounded-full border border-[#388478]/40 animate-pulse" />
        <div className="absolute w-40 h-40 rounded-full border border-[#D6B56D]/30" />

        {/* Center Illustration */}
        <div className="relative z-10 w-32 h-32 rounded-full bg-[#388478]/20 backdrop-blur-md flex items-center justify-center border border-[#388478]/40 shadow-xl">
          <FishIllustration size={88} bodyColor="#5F9386" />
        </div>

        {/* Horizontal Laser Scanning Line */}
        <div className="absolute inset-x-[-20px] h-[2px] bg-gradient-to-r from-transparent via-[#D6B56D] to-transparent animate-bounce opacity-80 shadow-[0_0_12px_#D6B56D]" />
      </div>

      {/* Pipeline Status Checkpoints */}
      <div className="w-full max-w-sm bg-white/5 backdrop-blur-md rounded-3xl p-5 border border-white/10 mb-8 flex flex-col gap-3">
        {PIPELINE_STEPS.map((step) => {
          const isDone = currentStep > step.id;
          const isCurrent = currentStep === step.id;

          return (
            <div
              key={step.id}
              className={`flex items-center gap-3 transition-opacity duration-300 ${
                isDone || isCurrent ? 'opacity-100' : 'opacity-35'
              }`}
            >
              <div className="w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold">
                {isDone ? (
                  <CheckCircle size={18} className="text-[#388478]" />
                ) : isCurrent ? (
                  <Loader2 size={16} className="text-[#D6B56D] animate-spin" />
                ) : (
                  <span className="w-2 h-2 rounded-full bg-white/30" />
                )}
              </div>
              <div className="flex flex-col">
                <span className="text-xs font-semibold text-white">{step.label}</span>
                <span className="text-[10px] text-white/50">{step.detail}</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
