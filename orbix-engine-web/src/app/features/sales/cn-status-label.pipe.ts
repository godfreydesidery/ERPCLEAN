import { Pipe, PipeTransform } from '@angular/core';
import { CreditNoteStatus } from './sales.models';

/** Maps CreditNoteStatus enum value to a short human-readable label. */
@Pipe({ name: 'cnStatusLabel', standalone: true, pure: true })
export class CnStatusLabelPipe implements PipeTransform {
  transform(value: CreditNoteStatus): string {
    switch (value) {
      case 'POSTED':              return 'Posted';
      case 'PARTIALLY_ALLOCATED': return 'Partial';
      case 'FULLY_ALLOCATED':     return 'Allocated';
      case 'CANCELLED':           return 'Cancelled';
      default:                    return value;
    }
  }
}
