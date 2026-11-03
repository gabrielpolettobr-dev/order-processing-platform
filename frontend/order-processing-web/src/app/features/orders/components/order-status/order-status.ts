import { Component, input } from '@angular/core';
import { OrderStatus, orderStatusLabel } from '../../../../core/models/order.model';

@Component({
  selector: 'app-order-status',
  standalone: true,
  template: `<span class="status-badge" [class]="statusClass()">{{ label() }}</span>`,
  styleUrl: './order-status.scss',
})
export class OrderStatusComponent {
  readonly status = input.required<OrderStatus>();
  protected readonly label = () => orderStatusLabel(this.status());
  protected readonly statusClass = () => `status-badge status-${this.status().toLowerCase()}`;
}
