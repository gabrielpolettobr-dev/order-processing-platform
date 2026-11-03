import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'orders',
    children: [
      {
        path: '',
        loadComponent: () => import('./features/orders/components/order-list/order-list').then((module) => module.OrderListComponent),
      },
      {
        path: 'new',
        loadComponent: () => import('./features/orders/components/order-form/order-form').then((module) => module.OrderFormComponent),
      },
      {
        path: ':id',
        loadComponent: () => import('./features/orders/components/order-details/order-details').then((module) => module.OrderDetailsComponent),
      },
    ],
  },
  { path: '', pathMatch: 'full', redirectTo: 'orders' },
  { path: '**', redirectTo: 'orders' },
];
