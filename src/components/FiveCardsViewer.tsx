import React from 'react';
import { FishKnowledgeCard } from '../types';
import { ShieldAlert, Compass, Wrench, Flame, Trophy } from 'lucide-react';

interface FiveCardsViewerProps {
  cards: FishKnowledgeCard[];
  onCardClick?: (card: FishKnowledgeCard) => void;
  className?: string;
}

const CARD_ICONS: Record<string, React.ReactNode> = {
  HERO: <Trophy className="w-4 h-4 text-[#D6B56D]" />,
  IDENTIFICATION: <Compass className="w-4 h-4 text-[#D6B56D]" />,
  ECO: <ShieldAlert className="w-4 h-4 text-[#D6B56D]" />,
  GEAR: <Wrench className="w-4 h-4 text-[#D6B56D]" />,
  SKILL: <Flame className="w-4 h-4 text-[#D6B56D]" />,
};

const CARD_LABELS: Record<string, string> = {
  HERO: '英雄卡',
  IDENTIFICATION: '识别卡',
  ECO: '生态卡',
  GEAR: '装备卡',
  SKILL: '作钓技术卡',
};

export const FiveCardsViewer: React.FC<FiveCardsViewerProps> = ({
  cards,
  onCardClick,
  className = '',
}) => {
  return (
    <div className={`flex flex-col gap-3 ${className}`}>
      <div className="flex items-center justify-between">
        <h3 className="text-base font-bold text-[#172421]">五张鱼鉴卡 · 左右滑动</h3>
        <span className="text-xs text-[#748782]">5 张卡片</span>
      </div>

      <div className="flex gap-3 overflow-x-auto pb-2 scrollbar-none snap-x snap-mandatory -mx-1 px-1">
        {cards.map((card) => {
          const structured = card.content;
          const label = CARD_LABELS[card.cardType] || '鱼鉴卡';
          const icon = CARD_ICONS[card.cardType];

          let supportingText = '';
          if (card.cardType === 'IDENTIFICATION' && structured.features) {
            supportingText = structured.features.map((f) => `${f.title}: ${f.text}`).join(' · ');
          } else if (card.cardType === 'ECO') {
            supportingText = [structured.waterLayer, structured.behavior].filter(Boolean).join(' · ');
          } else if (card.cardType === 'GEAR') {
            supportingText = [structured.rod, structured.hook, structured.bait?.join(',')].filter(Boolean).join(' · ');
          } else if (card.cardType === 'SKILL') {
            supportingText = structured.tip || '';
          } else {
            supportingText = structured.description || card.description;
          }

          return (
            <div
              key={card.id}
              onClick={() => onCardClick?.(card)}
              className="flex-shrink-0 w-[200px] h-[260px] rounded-2xl p-4 bg-gradient-to-b from-[#1C2422] to-[#2E2718] text-white flex flex-col justify-between shadow-md snap-start border border-[#D6B56D]/20 cursor-pointer hover:border-[#D6B56D]/50 transition-all duration-200 active:scale-[0.98]"
            >
              {/* Header */}
              <div>
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-bold text-[#D6B56D] tracking-wider uppercase flex items-center gap-1">
                    {icon}
                    {card.cardType}
                  </span>
                  <span className="text-[10px] bg-white/10 text-white/80 px-2 py-0.5 rounded-full font-medium">
                    {label}
                  </span>
                </div>

                <h4 className="text-base font-bold text-white mt-2 line-clamp-1">{card.title}</h4>
                {structured.tag && (
                  <p className="text-[11px] text-[#D6B56D] font-medium mt-1 line-clamp-1">
                    {structured.tag}
                  </p>
                )}

                {(structured.rarity || structured.power || structured.challenge) && (
                  <div className="flex items-center gap-2 text-[10px] text-white/70 mt-2 font-mono">
                    <span>稀有 {structured.rarity ?? 1}★</span>
                    <span>力量 {structured.power ?? 2}★</span>
                    <span>挑战 {structured.challenge ?? 2}★</span>
                  </div>
                )}
              </div>

              {/* Bottom Body */}
              <div className="bg-white/5 rounded-xl p-2.5 border border-white/5 backdrop-blur-xs">
                <p className="text-xs text-white/85 leading-relaxed line-clamp-4">
                  {supportingText || '内容整理中...'}
                </p>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
