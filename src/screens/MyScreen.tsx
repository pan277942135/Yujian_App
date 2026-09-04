import React from 'react';
import { CatchRecord, UserSession } from '../types';
import { FishIllustration } from '../components/FishIllustration';
import {
  User,
  Trophy,
  Compass,
  Share2,
  Settings,
  LogOut,
  ChevronRight,
  Sparkles,
  ShieldCheck,
  Fish,
  Calendar,
} from 'lucide-react';

interface MyScreenProps {
  session: UserSession;
  allCatches: CatchRecord[];
  onOpenCatchDetail: (record: CatchRecord) => void;
  onOpenShareCenter: () => void;
  onOpenAuth: () => void;
  onLogout: () => void;
  onViewGuide: () => void;
}

export const MyScreen: React.FC<MyScreenProps> = ({
  session,
  allCatches,
  onOpenCatchDetail,
  onOpenShareCenter,
  onOpenAuth,
  onLogout,
  onViewGuide,
}) => {
  const uniqueSpeciesCount = new Set(allCatches.map((c) => c.speciesId)).size;
  const bestFish =
    allCatches.length > 0
      ? [...allCatches].sort((a, b) => b.weightKg - a.weightKg)[0]
      : null;

  return (
    <div className="flex flex-col min-h-screen bg-[#F6F8F7] pb-24">
      {/* User Banner Header */}
      <div className="bg-gradient-to-b from-[#172421] to-[#253935] text-white pt-8 pb-6 px-6 rounded-b-[36px] shadow-md">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3.5">
            <div className="w-16 h-16 rounded-full bg-gradient-to-tr from-[#388478] to-[#D6B56D] p-0.5 flex items-center justify-center shadow-md">
              <div className="w-full h-full rounded-full bg-[#172421] flex items-center justify-center text-[#D6B56D]">
                <User size={30} />
              </div>
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-xl font-black text-white">{session.nickname}</h2>
                <span className="text-[10px] bg-[#D6B56D] text-[#172421] font-bold px-2 py-0.5 rounded-full flex items-center gap-0.5">
                  <Sparkles size={10} />
                  黄金钓客
                </span>
              </div>
              <p className="text-xs text-white/60 font-mono mt-0.5">@{session.username}</p>
            </div>
          </div>

          <button
            type="button"
            onClick={session.isLoggedIn ? onLogout : onOpenAuth}
            className="p-2 rounded-full bg-white/10 hover:bg-white/20 text-white/80 hover:text-white transition-colors"
            title={session.isLoggedIn ? '退出登录' : '立即登录'}
          >
            {session.isLoggedIn ? <LogOut size={18} /> : <User size={18} />}
          </button>
        </div>

        {/* 3 Metrics Row */}
        <div className="grid grid-cols-3 gap-2 mt-6 pt-5 border-t border-white/10 text-center">
          <div className="flex flex-col">
            <span className="text-[11px] text-white/60">总鱼获</span>
            <span className="text-xl font-black text-white font-mono mt-0.5">
              {allCatches.length} <span className="text-xs font-normal">尾</span>
            </span>
          </div>

          <div className="flex flex-col border-x border-white/10">
            <span className="text-[11px] text-white/60">已解锁鱼类</span>
            <span className="text-xl font-black text-[#D6B56D] font-mono mt-0.5">
              {uniqueSpeciesCount} <span className="text-xs font-normal">种</span>
            </span>
          </div>

          <div className="flex flex-col">
            <span className="text-[11px] text-white/60">最大单尾</span>
            <span className="text-xl font-black text-white font-mono mt-0.5">
              {bestFish ? `${bestFish.weightKg}kg` : '-'}
            </span>
          </div>
        </div>
      </div>

      <main className="p-4 flex flex-col gap-4 max-w-lg mx-auto w-full -mt-2">
        {/* Quick Menu Actions */}
        <div className="bg-white rounded-3xl p-3 shadow-sm border border-[#748782]/10 flex flex-col">
          <button
            type="button"
            onClick={onOpenShareCenter}
            className="flex items-center justify-between p-3 rounded-2xl hover:bg-[#F6F8F7] transition-colors"
          >
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-[#EAF4F2] text-[#388478] flex items-center justify-center">
                <Share2 size={20} />
              </div>
              <div className="text-left">
                <span className="text-sm font-bold text-[#172421] block">战报与海报中心</span>
                <span className="text-[11px] text-[#748782]">一键生成精美图片分享到钓友群</span>
              </div>
            </div>
            <ChevronRight size={18} className="text-[#748782]/40" />
          </button>

          <button
            type="button"
            onClick={onViewGuide}
            className="flex items-center justify-between p-3 rounded-2xl hover:bg-[#F6F8F7] transition-colors"
          >
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-[#FFF8EC] text-[#D6B56D] flex items-center justify-center">
                <Compass size={20} />
              </div>
              <div className="text-left">
                <span className="text-sm font-bold text-[#172421] block">我的鱼类图鉴</span>
                <span className="text-[11px] text-[#748782]">查阅全国常见鱼类生物特征与钓法</span>
              </div>
            </div>
            <ChevronRight size={18} className="text-[#748782]/40" />
          </button>
        </div>

        {/* Recent Catches */}
        <div className="bg-white rounded-3xl p-5 shadow-sm border border-[#748782]/10 flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <h3 className="text-base font-bold text-[#172421] flex items-center gap-2">
              <Fish size={18} className="text-[#388478]" />
              近期鱼获记录
            </h3>
            <span className="text-xs text-[#748782]">共 {allCatches.length} 条</span>
          </div>

          <div className="flex flex-col gap-2.5">
            {allCatches.slice(0, 5).map((catchItem) => (
              <div
                key={catchItem.id}
                onClick={() => onOpenCatchDetail(catchItem)}
                className="flex items-center justify-between p-3 rounded-2xl bg-[#F6F8F7] hover:bg-[#EAF4F2] transition-colors cursor-pointer border border-[#748782]/5 active:scale-[0.99]"
              >
                <div className="flex items-center gap-3">
                  <div className="w-12 h-12 rounded-xl bg-white flex items-center justify-center overflow-hidden flex-shrink-0 border border-[#748782]/10">
                    <FishIllustration size={36} speciesKey={catchItem.speciesId} />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h4 className="text-sm font-bold text-[#172421]">{catchItem.speciesName}</h4>
                      {catchItem.isNewRecord && (
                        <span className="text-[10px] bg-[#D6B56D] text-[#172421] px-1.5 py-0.2 rounded font-bold">
                          PB
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-2 text-[11px] text-[#748782] mt-0.5">
                      <span>{catchItem.weightKg}kg</span>
                      <span>·</span>
                      <span>{catchItem.lengthCm}cm</span>
                      <span>·</span>
                      <span className="line-clamp-1">{catchItem.location}</span>
                    </div>
                  </div>
                </div>

                <ChevronRight size={16} className="text-[#748782]/50" />
              </div>
            ))}

            {allCatches.length === 0 && (
              <div className="p-6 text-center text-xs text-[#748782]">
                还没有记录任何鱼获，点击下方“拍照识别”开始吧！
              </div>
            )}
          </div>
        </div>

        {/* Engine Specs */}
        <div className="p-4 rounded-3xl bg-white border border-[#748782]/10 text-xs text-[#748782] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ShieldCheck size={18} className="text-[#388478]" />
            <div>
              <span className="font-semibold text-[#172421] block">智能端云双工模式</span>
              <span className="text-[10px]">DET_FISH_v0.1 · QUALITY_GATE_v1.1</span>
            </div>
          </div>
          <span className="text-[10px] bg-[#EAF4F2] text-[#388478] px-2 py-0.5 rounded-full font-mono">
            v1.0-Web
          </span>
        </div>
      </main>
    </div>
  );
};
