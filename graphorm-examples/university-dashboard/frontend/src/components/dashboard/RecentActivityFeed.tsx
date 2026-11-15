import React from 'react';
import { ActivityItem } from '../../types/graphorm';
import { Clock, GraduationCap, Award, UserPlus, BookPlus } from 'lucide-react';
import { Badge } from '../common/Badge';

interface RecentActivityFeedProps {
  activities: ActivityItem[];
}

export const RecentActivityFeed: React.FC<RecentActivityFeedProps> = ({ activities }) => {
  const getIcon = (type: ActivityItem['type']) => {
    switch (type) {
      case 'enrollment':
        return <GraduationCap className="w-4 h-4 text-accent" />;
      case 'grade':
        return <Award className="w-4 h-4 text-emerald-400" />;
      case 'student_created':
        return <UserPlus className="w-4 h-4 text-accent-secondary" />;
      case 'course_added':
        return <BookPlus className="w-4 h-4 text-amber-400" />;
      default:
        return <Clock className="w-4 h-4 text-text-muted" />;
    }
  };

  const getBadgeVariant = (type: ActivityItem['type']) => {
    switch (type) {
      case 'enrollment':
        return 'sky';
      case 'grade':
        return 'success';
      case 'student_created':
        return 'brand';
      case 'course_added':
        return 'warning';
      default:
        return 'slate';
    }
  };

  return (
    <div className="p-6 rounded-lg bg-surface border border-border shadow-sm">
      <div className="flex items-center justify-between mb-5">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="font-bold text-base text-text-primary tracking-tight">
              Registrar Audit Stream
            </h3>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-elevated text-text-muted border border-border">
              Live Transactions
            </span>
          </div>
          <p className="text-xs text-text-muted mt-1 font-mono">
            Real-time transactional journal across GraphORM mutations and view subscriptions
          </p>
        </div>
        <div className="flex items-center gap-1.5 text-xs font-mono text-emerald-400">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
          <span>Synchronized</span>
        </div>
      </div>

      <div className="space-y-2.5 max-h-[340px] overflow-y-auto pr-1">
        {activities.map((act) => (
          <div
            key={act.id}
            className="flex items-start gap-3.5 p-3.5 rounded-lg bg-canvas border border-border hover:border-accent transition-colors"
          >
            <div className="p-2 rounded-md bg-surface border border-border shrink-0 mt-0.5">
              {getIcon(act.type)}
            </div>

            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between gap-2">
                <p className="text-xs font-semibold text-text-primary truncate">
                  {act.title}
                </p>
                <span className="text-[10px] font-mono text-text-muted whitespace-nowrap">
                  {act.timestamp}
                </span>
              </div>
              <p className="text-xs text-text-muted mt-0.5 line-clamp-1">
                {act.description}
              </p>
            </div>

            <Badge variant={getBadgeVariant(act.type)} size="sm">
              {act.badge}
            </Badge>
          </div>
        ))}
      </div>
    </div>
  );
};
