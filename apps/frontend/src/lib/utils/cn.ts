import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Conditional class composition (PLAN §4.2.6-I).
 *
 * `twMerge` is the load-bearing half: Tailwind emits utilities in stylesheet order, not in the
 * order they appear in `className`, so `"p-4 p-6"` is decided by the build rather than by the
 * author. Merging resolves conflicts left-to-right instead, which is what makes a prop like
 * `className` able to override a component's own defaults.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
