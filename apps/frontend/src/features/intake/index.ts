/**
 * C1 intake public surface.
 *
 * App routes compose these exports only; autosave, clarification mapping, and conflict helpers stay
 * internal to the feature so the route layer remains routing-only.
 */
export { TripListPanel } from './components/trip-list-panel';
export { TripBriefEditor, type TripBriefEditorProps } from './components/trip-brief-editor';
export { ClarificationPanel, type ClarificationPanelProps } from './components/clarification-panel';
export { SaveStatus, type SaveState } from './components/save-status';
export { ConflictNotice, type ConflictNoticeProps } from './components/conflict-notice';
export { useTrips, useTrip, useCreateTrip, useRenameTrip, useArchiveTrip, useDeleteTrip } from './hooks/use-trips';
export { useTripBrief, useUpdateTripBrief, useAnswerTripBriefClarification } from './hooks/use-trip-brief';
