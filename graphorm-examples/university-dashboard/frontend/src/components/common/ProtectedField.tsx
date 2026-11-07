import React from 'react';
import { Lock, ShieldCheck } from 'lucide-react';
import { AcademicRole } from '../../types/graphorm';

interface ProtectedFieldProps {
  currentRole: AcademicRole;
  requiredRoles: AcademicRole[];
  value: React.ReactNode;
  fallbackText?: string;
  fieldLabel?: string;
  showBadge?: boolean;
  className?: string;
}

export const ProtectedField: React.FC<ProtectedFieldProps> = ({
  currentRole,
  requiredRoles,
  value,
  fallbackText = 'Restricted',
  fieldLabel,
  showBadge = false,
  className = '',
}) => {
  const isAllowed = requiredRoles.includes(currentRole);

  if (isAllowed) {
    return (
      <span className={`inline-flex items-center gap-1.5 ${className}`}>
        {value}
        {showBadge && (
          <span
            className="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[9px] font-mono bg-emerald-950/40 text-emerald-400 border border-emerald-500/30"
            title={`Authorized via @auth(roles: [${requiredRoles.join(', ')}])`}
          >
            <ShieldCheck className="w-2.5 h-2.5" />
            <span>AUTH</span>
          </span>
        )}
      </span>
    );
  }

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md bg-[#21262D]/90 border border-amber-500/30 text-amber-300 text-[11px] font-mono select-none cursor-help ${className}`}
      title={`Protected field: requires ${requiredRoles.join(' or ')} privilege (@auth)`}
    >
      <Lock className="w-3 h-3 text-amber-400" />
      <span>{fallbackText}</span>
      <span className="text-[9px] px-1 py-0.2 rounded bg-amber-950/50 text-amber-400/80 border border-amber-500/20 uppercase font-sans">
        @auth
      </span>
    </span>
  );
};
