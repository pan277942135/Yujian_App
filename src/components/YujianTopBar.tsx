import React from 'react';
import { ChevronLeft } from 'lucide-react';

interface YujianTopBarProps {
  title: string;
  subtitle?: string;
  onBack?: () => void;
  rightAction?: React.ReactNode;
  className?: string;
}

export const YujianTopBar: React.FC<YujianTopBarProps> = ({
  title,
  subtitle,
  onBack,
  rightAction,
  className = '',
}) => {
  return (
    <header className={`sticky top-0 z-30 bg-[#F6F8F7]/90 backdrop-blur-md px-5 py-3.5 flex items-center justify-between border-b border-[#748782]/10 ${className}`}>
      <div className="flex items-center gap-3">
        {onBack && (
          <button
            type="button"
            onClick={onBack}
            aria-label="返回上一页"
            className="w-9 h-9 rounded-full bg-white shadow-xs border border-[#748782]/15 flex items-center justify-center text-[#172421] active:scale-95 transition-transform hover:bg-[#EAF4F2]"
          >
            <ChevronLeft size={20} />
          </button>
        )}
        <div className="flex flex-col">
          <h1 className="text-lg font-bold text-[#172421] leading-tight line-clamp-1">{title}</h1>
          {subtitle && (
            <p className="text-xs text-[#748782] font-normal leading-tight line-clamp-1 mt-0.5">{subtitle}</p>
          )}
        </div>
      </div>
      {rightAction && <div>{rightAction}</div>}
    </header>
  );
};
