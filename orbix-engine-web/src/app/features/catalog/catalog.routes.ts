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
  },
  {
    path: 'uoms',
    loadComponent: () => import('./uoms/uom.component').then(m => m.UomComponent)
  },
  {
    path: 'vat-groups',
    loadComponent: () => import('./vat-groups/vat-group.component').then(m => m.VatGroupComponent)
  },
  {
    path: 'price-lists',
    loadComponent: () => import('./price-lists/price-list.component').then(m => m.PriceListComponent)
  }
];
