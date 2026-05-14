import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./catalog.component').then(m => m.CatalogComponent)
  },
  {
    path: 'items',
    loadComponent: () => import('./items/item-list.component').then(m => m.ItemListComponent)
  },
  {
    path: 'items/new',
    loadComponent: () => import('./items/item-edit.component').then(m => m.ItemEditComponent)
  },
  {
    path: 'items/:id/edit',
    loadComponent: () => import('./items/item-edit.component').then(m => m.ItemEditComponent)
  },
  {
    path: 'groups',
    loadComponent: () => import('./groups/item-group.component').then(m => m.ItemGroupComponent)
  }
];
