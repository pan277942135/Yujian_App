import React from 'react';

interface TagChipProps {
  label: string;
  emphasized?: boolean;
  onClick?: () => void;
  className?: string;
}

export const TagChip: React.FC<TagChipProps> = ({
  label,
  emphasized = false,
  onClick,
  className = '',
}) => {
  return (
    <span
      onClick={onClick}
      className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-medium whitespace-nowrap transition-colors ${
        onClick ? 'cursor-pointer active:scale-95' : ''
      } ${
        emphasized
          ? 'bg-[#388478] text-white shadow-xs'
          : 'bg-white text-[#748782] border border-[#748782]/20 hover:border-[#388478]/40 hover:text-[#172421]'
      } ${className}`}
    >
      {label}
    </span>
  );
};
