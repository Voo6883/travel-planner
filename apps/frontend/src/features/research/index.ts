/**
 * C2 research public surface (UC-C2-01/02, tasks/23).
 *
 * App routes compose these exports only; the polling hook and job cache stay internal so the route
 * layer remains routing-only. Recommendations (task 25) will add their own surface here.
 */
export { ResearchJobPanel, type ResearchJobPanelProps } from './components/research-job-panel';
export { useStartResearch, useResearchJob, useResearchTrip } from './hooks/use-research-job';
