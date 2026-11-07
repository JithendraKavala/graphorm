import React from 'react';

export type BadgeVariant =
  | 'brand'
  | 'gold'
  | 'success'
  | 'laurel'
  | 'warning'
  | 'purple'
  | 'cobalt'
  | 'sky'
  | 'slate'
  | 'danger'
  | 'garnet';

interface BadgeProps {
  children: React.ReactNode;
  variant?: BadgeVariant;
  size?: 'sm' | 'md' | 'lg';
  dot?: boolean;
  className?: string;
}

export const Badge: React.FC<BadgeProps> = ({
  children,
  variant = 'brand',
  size = 'md',
  dot = false,
  className = '',
}) => {
  const variantStyles: Record<BadgeVariant, { container: string; dot: string }> = {
    brand: {
      container: 'bg-sky-500/10 text-sky-400 border-sky-500/30',
      dot: 'bg-sky-400',
    },
    sky: {
      container: 'bg-sky-500/10 text-sky-400 border-sky-500/30',
      dot: 'bg-sky-400',
    },
    cobalt: {
      container: 'bg-sky-500/10 text-sky-400 border-sky-500/30',
      dot: 'bg-sky-400',
    },
    purple: {
      container: 'bg-sky-500/10 text-sky-400 border-sky-500/30',
      dot: 'bg-sky-400',
    },
    success: {
      container: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30',
      dot: 'bg-emerald-400',
    },
    laurel: {
      container: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30',
      dot: 'bg-emerald-400',
    },
    gold: {
      container: 'bg-amber-500/10 text-amber-400 border-amber-500/30',
      dot: 'bg-amber-400',
    },
    warning: {
      container: 'bg-amber-500/10 text-amber-400 border-amber-500/30',
      dot: 'bg-amber-400',
    },
    danger: {
      container: 'bg-rose-500/10 text-rose-400 border-rose-500/30',
      dot: 'bg-rose-400',
    },
    garnet: {
      container: 'bg-rose-500/10 text-rose-400 border-rose-500/30',
      dot: 'bg-rose-400',
    },
    slate: {
      container: 'bg-[#21262D] text-[#8B949E] border-[#30363D]',
      dot: 'bg-[#8B949E]',
    },
  };

  const sizeStyles = {
    sm: 'text-[10px] font-mono px-2 py-0.5 font-medium tracking-normal',
    md: 'text-[11px] font-mono px-2.5 py-0.5 font-medium tracking-normal',
    lg: 'text-xs font-mono px-3 py-1 font-semibold tracking-normal',
  };

  const style = variantStyles[variant] || variantStyles.brand;

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded border ${style.container} ${sizeStyles[size]} ${className}`}
    >
      {dot && <span className={`w-1.5 h-1.5 rounded-full ${style.dot}`} />}
      {children}
    </span>
  );
};
