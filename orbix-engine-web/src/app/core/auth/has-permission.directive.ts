import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';
import { AuthService } from './auth.service';

/**
 * Structural directive that renders its template only when the current user
 * holds the given permission code.
 *
 *   <button *orbixHasPermission="'ITEM.CREATE'">New item</button>
 *
 * Reacts to login / logout / refresh because AuthService.permissions is a signal.
 */
@Directive({
  selector: '[orbixHasPermission]',
  standalone: true
})
export class HasPermissionDirective {
  private readonly auth = inject(AuthService);
  private readonly templateRef = inject(TemplateRef<unknown>);
  private readonly viewContainer = inject(ViewContainerRef);

  readonly orbixHasPermission = input.required<string>();

  private visible = false;

  constructor() {
    effect(() => {
      const granted = this.auth.hasPermission(this.orbixHasPermission());
      if (granted && !this.visible) {
        this.viewContainer.createEmbeddedView(this.templateRef);
        this.visible = true;
      } else if (!granted && this.visible) {
        this.viewContainer.clear();
        this.visible = false;
      }
    });
  }
}
