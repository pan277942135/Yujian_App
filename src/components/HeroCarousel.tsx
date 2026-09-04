import React, { useState } from 'react';
import { FishKnowledgeCard } from '../types';
import { FishIllustration } from './FishIllustration';
import { ChevronLeft, ChevronRight, Award } from 'lucide-react';

interface HeroCarouselProps {
  cards: FishKnowledgeCard[];
  speciesKey?: string;
  className?: string;
}

export const HeroCarousel: React.FC<HeroCarouselProps> = ({
  cards,
  speciesKey,
  className = '',
}) => {
  const [currentIndex, setCurrentIndex] = useState(0);

  if (!cards || cards.length === 0) {
    return (
      <div className={`w-full aspect-square max-h-[300px] rounded-3xl bg-gradient-to-b from-[#1C2422] to-[#2E2718] flex items-center justify-center text-[#D6B56D] ${className}`}>
        暂无鱼鉴卡
      </div>
    );
  }

  const current = cards[currentIndex] || cards[0];

  const handlePrev = (e: React.MouseEvent) => {
    e.stopPropagation();
    setCurrentIndex((prev) => (prev > 0 ? prev - 1 : cards.length - 1));
  };

  const handleNext = (e: React.MouseEvent) => {
    e.stopPropagation();
    setCurrentIndex((prev) => (prev < cards.length - 1 ? prev + 1 : 0));
  };

  return (
    <div className={`relative flex flex-col items-center select-none ${className}`}>
      {/* Main Square Card */}
      <div className="relative w-full aspect-square max-h-[320px] rounded-3xl overflow-hidden bg-gradient-to-b from-[#17201D] via-[#22241F] to-[#302718] text-white p-6 flex flex-col justify-between shadow-xl border border-[#D6B56D]/30 group">
        {/* Background Vector Watermark / Illustration */}
        <div className="absolute right-[-20px] bottom-[-20px] opacity-15 pointer-events-none scale-125">
          <FishIllustration size={280} speciesKey={speciesKey} />
        </div>

        {/* Top Header */}
        <div className="relative z-10 flex items-start justify-between">
          <div>
            <span className="text-xs font-bold text-[#D6B56D] tracking-widest uppercase flex items-center gap-1.5">
              <Award className="w-4 h-4" />
              {current.cardType}
            </span>
            <h2 className="text-2xl font-black text-white mt-1.5 tracking-tight drop-shadow-sm">
              {current.title}
            </h2>
          </div>

          <span className="bg-[#D6B56D]/20 text-[#D6B56D] border border-[#D6B56D]/40 text-xs px-2.5 py-1 rounded-full font-semibold">
            {currentIndex + 1} / {cards.length}
          </span>
        </div>

        {/* Center Illustration Feature */}
        <div className="relative z-10 flex items-center justify-center my-auto py-2">
          <FishIllustration size={160} speciesKey={speciesKey} />
        </div>

        {/* Bottom Details */}
        <div className="relative z-10 bg-black/40 backdrop-blur-md rounded-2xl p-3.5 border border-white/10">
          <p className="text-xs text-white/90 leading-relaxed line-clamp-2">
            {current.content.description || current.description || '沉浸式鱼类识别与钓鱼图鉴'}
          </p>
          {current.content.tag && (
            <div className="mt-2 flex items-center gap-2">
              <span className="text-[11px] bg-[#D6B56D] text-[#172421] font-bold px-2 py-0.5 rounded-md">
                {current.content.tag}
              </span>
            </div>
          )}
        </div>

        {/* Navigation Arrows */}
        {cards.length > 1 && (
          <>
            <button
              type="button"
              onClick={handlePrev}
              className="absolute left-3 top-1/2 -translate-y-1/2 w-8 h-8 rounded-full bg-black/40 text-white flex items-center justify-center hover:bg-black/70 active:scale-95 transition-all opacity-80 hover:opacity-100 z-20"
              aria-label="上一张"
            >
              <ChevronLeft size={18} />
            </button>
            <button
              type="button"
              onClick={handleNext}
              className="absolute right-3 top-1/2 -translate-y-1/2 w-8 h-8 rounded-full bg-black/40 text-white flex items-center justify-center hover:bg-black/70 active:scale-95 transition-all opacity-80 hover:opacity-100 z-20"
              aria-label="下一张"
            >
              <ChevronRight size={18} />
            </button>
          </>
        )}
      </div>

      {/* Pagination Indicator Dots */}
      {cards.length > 1 && (
        <div className="flex items-center gap-1.5 mt-3">
          {cards.map((_, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => setCurrentIndex(idx)}
              className={`h-1.5 rounded-full transition-all duration-300 ${
                idx === currentIndex ? 'w-6 bg-[#388478]' : 'w-1.5 bg-[#748782]/30'
              }`}
              aria-label={`跳转到第 ${idx + 1} 张`}
            />
          ))}
        </div>
      )}
    </div>
  );
};
