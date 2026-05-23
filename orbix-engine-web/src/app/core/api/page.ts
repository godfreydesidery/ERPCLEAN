/**
 * Mirrors the backend `PageDto<T>` envelope (page index is zero-based, matching
 * Spring `Page`). The shared shape for every server-side-paginated list.
 */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** An empty page — handy as a signal's initial value before the first load. */
export function emptyPage<T>(size = 20): Page<T> {
  return { content: [], page: 0, size, totalElements: 0, totalPages: 0 };
}
