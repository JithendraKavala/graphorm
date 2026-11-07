import React from 'react';
import { Modal } from '../common/Modal';
import { DashboardWidgetConfig } from '../../types/graphorm';
import { Eye, EyeOff, MoveUp, MoveDown, RotateCcw } from 'lucide-react';

interface WidgetCustomizerModalProps {
  isOpen: boolean;
  onClose: () => void;
  widgets: DashboardWidgetConfig[];
  onUpdateWidgets: (widgets: DashboardWidgetConfig[]) => void;
}

export const WidgetCustomizerModal: React.FC<WidgetCustomizerModalProps> = ({
  isOpen,
  onClose,
  widgets,
  onUpdateWidgets,
}) => {
  const toggleVisibility = (id: string) => {
    const next = widgets.map((w) => (w.id === id ? { ...w, visible: !w.visible } : w));
    onUpdateWidgets(next);
  };

  const move = (index: number, direction: 'up' | 'down') => {
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= widgets.length) return;
    const sorted = [...widgets];
    const temp = sorted[index];
    sorted[index] = sorted[targetIndex];
    sorted[targetIndex] = temp;
    const reordered = sorted.map((w, i) => ({ ...w, order: i }));
    onUpdateWidgets(reordered);
  };

  const resetDefaults = () => {
    const defaults: DashboardWidgetConfig[] = [
      { id: 'kpis', title: 'Key Performance Summary Cards', visible: true, order: 0 },
      { id: 'trend', title: 'Enrollment & Completion Velocity Chart', visible: true, order: 1 },
      { id: 'departments', title: 'Department Allocation & Capacity Chart', visible: true, order: 2 },
      { id: 'gpa', title: 'Academic Merit & GPA Distribution', visible: true, order: 3 },
      { id: 'activity', title: 'Live Academic Activity Stream', visible: true, order: 4 },
      { id: 'shortcuts', title: 'Operational Shortcuts Bar', visible: true, order: 5 },
    ];
    onUpdateWidgets(defaults);
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Customize Executive Overview"
      subtitle="Configure modular analytical widgets and card ordering"
      maxWidth="md"
      badge="Preferences"
    >
      <div className="space-y-4">
        <p className="text-xs text-[#8B949E] leading-relaxed">
          Toggle widget visibility and adjust position to optimize your registrar desk view.
        </p>

        <div className="space-y-2">
          {widgets.map((widget, idx) => (
            <div
              key={widget.id}
              className="flex items-center justify-between p-3 rounded-lg bg-[#0D1117] border border-[#30363D] transition-colors"
            >
              <div className="flex items-center gap-3">
                <button
                  onClick={() => toggleVisibility(widget.id)}
                  className={`p-1.5 rounded-md transition-colors cursor-pointer ${
                    widget.visible
                      ? 'text-sky-400 bg-sky-500/10 hover:bg-sky-500/20 border border-sky-500/30'
                      : 'text-[#8B949E] bg-[#21262D] hover:bg-slate-700 border border-[#30363D]'
                  }`}
                  title={widget.visible ? 'Hide widget' : 'Display widget'}
                >
                  {widget.visible ? <Eye className="w-4 h-4" /> : <EyeOff className="w-4 h-4" />}
                </button>
                <span
                  className={`text-xs font-semibold ${
                    widget.visible ? 'text-[#F0F6FC]' : 'text-[#8B949E] line-through'
                  }`}
                >
                  {widget.title}
                </span>
              </div>

              <div className="flex items-center gap-1">
                <button
                  onClick={() => move(idx, 'up')}
                  disabled={idx === 0}
                  className="p-1.5 text-[#8B949E] hover:text-[#F0F6FC] disabled:opacity-25 disabled:pointer-events-none rounded hover:bg-[#21262D] transition-colors cursor-pointer"
                  title="Move Up"
                >
                  <MoveUp className="w-3.5 h-3.5" />
                </button>
                <button
                  onClick={() => move(idx, 'down')}
                  disabled={idx === widgets.length - 1}
                  className="p-1.5 text-[#8B949E] hover:text-[#F0F6FC] disabled:opacity-25 disabled:pointer-events-none rounded hover:bg-[#21262D] transition-colors cursor-pointer"
                  title="Move Down"
                >
                  <MoveDown className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          ))}
        </div>

        <div className="pt-4 border-t border-[#30363D] flex items-center justify-between">
          <button
            onClick={resetDefaults}
            className="flex items-center gap-1.5 text-xs text-[#8B949E] hover:text-[#F0F6FC] transition-colors cursor-pointer"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Reset Defaults</span>
          </button>
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-lg bg-sky-500 hover:bg-sky-400 text-[#0D1117] font-mono font-bold text-xs transition-colors cursor-pointer shadow-sm"
          >
            Save Layout
          </button>
        </div>
      </div>
    </Modal>
  );
};
