import React from 'react';
import { Search, X } from 'lucide-react';

interface SearchInputProps {
  value: string;
  onChange: (val: string) => void;
  placeholder?: string;
  className?: string;
}

export const SearchInput: React.FC<SearchInputProps> = ({
  value,
  onChange,
  placeholder = 'Search ledger records...',
  className = '',
}) => {
  return (
    <div className={`relative flex items-center ${className}`}>
      <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3.5 pointer-events-none transition-colors" />
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full pl-9 pr-8 py-2 text-xs bg-ink-900/90 border border-ink-700/80 rounded-xl text-slate-100 placeholder-slate-500 focus:outline-none focus:border-gold-500/60 focus:ring-1 focus:ring-gold-500/25 transition-all"
      />
      {value ? (
        <button
          onClick={() => onChange('')}
          className="absolute right-2.5 p-1 text-slate-400 hover:text-white rounded-md hover:bg-ink-800 transition-colors cursor-pointer"
          aria-label="Clear query"
        >
          <X className="w-3 h-3" />
        </button>
      ) : null}
    </div>
  );
};
