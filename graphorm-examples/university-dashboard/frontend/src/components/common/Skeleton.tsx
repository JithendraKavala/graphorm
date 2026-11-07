import React from 'react';

interface SkeletonProps {
  className?: string;
  variant?: 'text' | 'rect' | 'circle';
}

export const Skeleton: React.FC<SkeletonProps> = ({
  className = '',
  variant = 'rect',
}) => {
  const variantStyles = {
    text: 'h-3.5 w-full rounded',
    rect: 'rounded-xl',
    circle: 'rounded-full',
  }[variant];

  return (
    <div
      className={`animate-pulse bg-ink-750/50 border border-ink-700/30 ${variantStyles} ${className}`}
    />
  );
};
