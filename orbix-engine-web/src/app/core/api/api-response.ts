import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

/**
 * Standard envelope returned by every backend REST endpoint.
 * See backend ApiResponseDto + memory:feedback-api-response-envelope.
 */
export interface ApiResponse<T> {
  status: boolean;
  statusCode: number;
  responseCode: string;
  message: string;
  errors: ApiError[];
  data: T | null;
}

export interface ApiError {
  field?: string | null;
  code: string;
  message: string;
}

/** Extract `data` from an envelope observable. Throws if data is null on a successful response. */
export function unwrap<T>(source: Observable<ApiResponse<T>>): Observable<T> {
  return source.pipe(map(env => {
    if (env.data === null || env.data === undefined) {
      throw new Error(`Empty data in successful envelope: ${env.responseCode} ${env.message}`);
    }
    return env.data;
  }));
}
