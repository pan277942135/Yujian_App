import React from 'react';
import { CatchRecord } from '../types';
import { YujianTopBar } from '../components/YujianTopBar';
import { DetectorOverlay } from '../components/DetectorOverlay';
import {
  Scale,
  Ruler,
  Sparkles,
  MapPin,
  Clock,
  Share2,
  Trophy,
  BookOpen,
  FileText,
} from 'lucide-react';

interface CatchDetailScreenProps {
  catchRecord: CatchRecord;
  onBack: () => void;
  onOpenShare: (catchRecord: CatchRecord) => void;
  onViewSpecies: (speciesId: string) => void;
}

export const CatchDetailScreen: React.FC<CatchDetailScreenProps> = ({
  catchRecord,
  onBack,
  onOpenShare,
  onViewSpecies,
}) => {
  return (
    <div className="flex flex-col min-h-screen bg-[#F6F8F7] pb-24">
      <YujianTopBar
        title="鱼获记录"
        subtitle={catchRecord.speciesName}
        onBack={onBack}
        rightAction={
          <button
            type="button"
            onClick={() => onOpenShare(catchRecord)}
            className="w-9 h-9 rounded-full bg-white shadow-xs border border-[#748782]/15 flex items-center justify-center text-[#388478] hover:bg-[#EAF4F2] active:scale-95"
            aria-label="生成海报"
          >
            <Share2 size={18} />
          </button>
        }
      />

      <main className="p-4 flex flex-col gap-4 max-w-lg mx-auto w-full">
        {/* Visual Media Overlay */}
        <DetectorOverlay
          imageUri={
            catchRecord.imageUrl.startsWith('data:') || catchRecord.imageUrl.startsWith('http')
              ? catchRecord.imageUrl
              : undefined
          }
          detectorBox={catchRecord.detectorBox}
          cropBox={catchRecord.cropBox}
          speciesKey={catchRecord.speciesId}
        />

        {/* Species & Badge Banner */}
        <div className="bg-white rounded-3xl p-5 shadow-sm border border-[#748782]/10 flex flex-col gap-4">
          <div className="flex items-start justify-between">
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-[#388478] bg-[#EAF4F2] px-2.5 py-0.5 rounded-full">
                  AI 智能认定
                </span>
                {catchRecord.isNewRecord && (
                  <span className="text-xs font-bold text-[#D6B56D] bg-[#172421] px-2.5 py-0.5 rounded-full flex items-center gap-1">
                    <Trophy size={12} />
                    新纪录
                  </span>
                )}
              </div>
              <h2 className="text-2xl font-black text-[#172421] mt-1.5">{catchRecord.speciesName}</h2>
            </div>

            <button
              type="button"
              onClick={() => onViewSpecies(catchRecord.speciesId)}
              className="px-3 py-1.5 rounded-full border border-[#388478] text-[#388478] text-xs font-semibold hover:bg-[#EAF4F2] flex items-center gap-1"
            >
              <BookOpen size={13} />
              <span>查看图鉴</span>
            </button>
          </div>

          {/* Metric Stats Cards */}
          <div className="grid grid-cols-3 gap-2 pt-2 border-t border-[#748782]/10 text-center">
            <div className="bg-[#F6F8F7] p-3 rounded-2xl">
              <span className="text-[10px] text-[#748782] font-medium flex items-center justify-center gap-1">
                <Scale size={11} className="text-[#388478]" />
                重量
              </span>
              <div className="text-base font-black text-[#172421] mt-0.5 font-mono">
                {catchRecord.weightKg} <span className="text-xs font-normal">kg</span>
              </div>
            </div>

            <div className="bg-[#F6F8F7] p-3 rounded-2xl">
              <span className="text-[10px] text-[#748782] font-medium flex items-center justify-center gap-1">
                <Ruler size={11} className="text-[#388478]" />
                体长
              </span>
              <div className="text-base font-black text-[#172421] mt-0.5 font-mono">
                {catchRecord.lengthCm} <span className="text-xs font-normal">cm</span>
              </div>
            </div>

            <div className="bg-[#F6F8F7] p-3 rounded-2xl">
              <span className="text-[10px] text-[#748782] font-medium flex items-center justify-center gap-1">
                <Sparkles size={11} className="text-[#D6B56D]" />
                置信度
              </span>
              <div className="text-base font-black text-[#172421] mt-0.5 font-mono">
                {catchRecord.confidence}%
              </div>
            </div>
          </div>
        </div>

        {/* Location and Time */}
        <div className="bg-white rounded-3xl p-5 shadow-sm border border-[#748782]/10 flex flex-col gap-3">
          <div className="flex items-center gap-3 text-xs text-[#172421]">
            <div className="w-8 h-8 rounded-full bg-[#EAF4F2] flex items-center justify-center text-[#388478]">
              <MapPin size={16} />
            </div>
            <div>
              <span className="text-[10px] text-[#748782] block">钓点钓位</span>
              <span className="font-semibold">{catchRecord.location}</span>
            </div>
          </div>

          <div className="flex items-center gap-3 text-xs text-[#172421]">
            <div className="w-8 h-8 rounded-full bg-[#EAF4F2] flex items-center justify-center text-[#388478]">
              <Clock size={16} />
            </div>
            <div>
              <span className="text-[10px] text-[#748782] block">作钓时间</span>
              <span className="font-semibold">{catchRecord.timeLabel}</span>
            </div>
          </div>

          {catchRecord.note && (
            <div className="flex items-start gap-3 text-xs text-[#172421] pt-2 border-t border-[#748782]/10">
              <div className="w-8 h-8 rounded-full bg-[#EAF4F2] flex items-center justify-center text-[#388478] flex-shrink-0">
                <FileText size={16} />
              </div>
              <div>
                <span className="text-[10px] text-[#748782] block">咬口心得 / 笔记</span>
                <p className="font-normal leading-relaxed mt-0.5 text-[#172421]/90">
                  {catchRecord.note}
                </p>
              </div>
            </div>
          )}
        </div>

        {/* Action Button */}
        <button
          type="button"
          onClick={() => onOpenShare(catchRecord)}
          className="w-full py-3.5 px-4 rounded-full bg-[#388478] text-white font-semibold text-sm flex items-center justify-center gap-2 hover:bg-[#2E6F65] active:scale-[0.98] transition-transform shadow-md shadow-[#388478]/25"
        >
          <Share2 size={18} />
          <span>生成战报海报 · 分享给钓友</span>
        </button>
      </main>
    </div>
  );
};
