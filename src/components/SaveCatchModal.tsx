import React, { useState } from 'react';
import { CatchRecord, ProductionRecognitionResult } from '../types';
import confetti from 'canvas-confetti';
import { X, Check, MapPin, Scale, Ruler, FileText, Trophy } from 'lucide-react';

interface SaveCatchModalProps {
  isOpen: boolean;
  onClose: () => void;
  recognitionResult: ProductionRecognitionResult | null;
  onSaved: (record: CatchRecord) => void;
}

export const SaveCatchModal: React.FC<SaveCatchModalProps> = ({
  isOpen,
  onClose,
  recognitionResult,
  onSaved,
}) => {
  if (!isOpen || !recognitionResult || !recognitionResult.prediction) return null;

  const top1 = recognitionResult.prediction.top1;

  const [weightKg, setWeightKg] = useState<string>('0.65');
  const [lengthCm, setLengthCm] = useState<string>('26.0');
  const [location, setLocation] = useState<string>('千岛湖野钓区');
  const [note, setNote] = useState<string>('浮漂稳稳送上一目，扬竿中鱼，手感极佳！');
  const [isNewRecord, setIsNewRecord] = useState<boolean>(true);

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();

    const record: CatchRecord = {
      id: `catch_${Date.now()}`,
      speciesId: top1.speciesKey,
      speciesName: top1.speciesName,
      confidence: Math.round(top1.confidence * 100),
      imageUrl: recognitionResult.imageUri || '',
      weightKg: parseFloat(weightKg) || 0.5,
      lengthCm: parseFloat(lengthCm) || 20,
      location: location.trim() || '常用水域',
      timeLabel: '刚刚',
      createdAt: new Date().toISOString(),
      note: note.trim(),
      isNewRecord,
      detectorBox: recognitionResult.assessment.primary?.box,
      cropBox: recognitionResult.assessment.cropBox || undefined,
      assessment: recognitionResult.assessment,
    };

    // Confetti effect
    try {
      confetti({
        particleCount: 80,
        spread: 70,
        origin: { y: 0.6 },
        colors: ['#388478', '#D6B56D', '#F29C38', '#5F9386'],
      });
    } catch (err) {
      console.warn('Confetti error', err);
    }

    onSaved(record);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4 bg-black/50 backdrop-blur-xs animate-in fade-in duration-200">
      <div className="w-full max-w-lg bg-white rounded-t-3xl sm:rounded-3xl p-6 shadow-2xl max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between border-b border-[#748782]/10 pb-4">
          <div>
            <span className="text-xs text-[#388478] font-bold tracking-wide uppercase">保存鱼获</span>
            <h3 className="text-xl font-bold text-[#172421]">记录这条 {top1.speciesName}</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 rounded-full bg-[#EAF4F2] flex items-center justify-center text-[#748782] hover:text-[#172421]"
          >
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSave} className="mt-5 flex flex-col gap-4">
          {/* Weight & Length Grid */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-[#172421] flex items-center gap-1 mb-1.5">
                <Scale size={13} className="text-[#388478]" />
                重量 (kg)
              </label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                value={weightKg}
                onChange={(e) => setWeightKg(e.target.value)}
                placeholder="例如 0.65"
                className="w-full px-3.5 py-2.5 rounded-xl border border-[#748782]/20 focus:border-[#388478] focus:ring-1 focus:ring-[#388478] outline-none text-sm font-medium"
                required
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-[#172421] flex items-center gap-1 mb-1.5">
                <Ruler size={13} className="text-[#388478]" />
                长度 (cm)
              </label>
              <input
                type="number"
                step="0.1"
                min="1"
                value={lengthCm}
                onChange={(e) => setLengthCm(e.target.value)}
                placeholder="例如 26.0"
                className="w-full px-3.5 py-2.5 rounded-xl border border-[#748782]/20 focus:border-[#388478] focus:ring-1 focus:ring-[#388478] outline-none text-sm font-medium"
                required
              />
            </div>
          </div>

          {/* Location */}
          <div>
            <label className="text-xs font-semibold text-[#172421] flex items-center gap-1 mb-1.5">
              <MapPin size={13} className="text-[#388478]" />
              钓点位置
            </label>
            <input
              type="text"
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder="例如 千岛湖野钓区、太湖西岸"
              className="w-full px-3.5 py-2.5 rounded-xl border border-[#748782]/20 focus:border-[#388478] focus:ring-1 focus:ring-[#388478] outline-none text-sm"
              required
            />
          </div>

          {/* Note */}
          <div>
            <label className="text-xs font-semibold text-[#172421] flex items-center gap-1 mb-1.5">
              <FileText size={13} className="text-[#388478]" />
              作钓心得 / 咬口手感
            </label>
            <textarea
              rows={3}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="记录用饵、线组、天气或者咬口细节..."
              className="w-full px-3.5 py-2.5 rounded-xl border border-[#748782]/20 focus:border-[#388478] focus:ring-1 focus:ring-[#388478] outline-none text-sm resize-none"
            />
          </div>

          {/* New Record Checkbox */}
          <label className="flex items-center gap-2.5 p-3 rounded-2xl bg-[#F6F8F7] border border-[#748782]/10 cursor-pointer">
            <input
              type="checkbox"
              checked={isNewRecord}
              onChange={(e) => setIsNewRecord(e.target.checked)}
              className="w-4 h-4 text-[#388478] rounded focus:ring-[#388478]"
            />
            <div className="flex items-center gap-1.5 text-xs text-[#172421] font-medium">
              <Trophy size={14} className="text-[#F29C38]" />
              <span>标为个人新记录 (Personal Best)</span>
            </div>
          </label>

          {/* Submit Button */}
          <button
            type="submit"
            className="w-full mt-2 py-3.5 px-4 rounded-full bg-[#388478] text-white font-semibold flex items-center justify-center gap-2 hover:bg-[#2E6F65] active:scale-[0.98] transition-all shadow-md"
          >
            <Check size={18} />
            <span>确认保存到“我的鱼获”</span>
          </button>
        </form>
      </div>
    </div>
  );
};
